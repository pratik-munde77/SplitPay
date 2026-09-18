package com.splitpay.group;
import com.splitpay.user.*;
import com.splitpay.expense.*;
import com.splitpay.activity.*;
import com.splitpay.split.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
@Service @Transactional
public class GroupService {
 @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
 private final GroupRepository groups;
 private final UserRepository users;
 private final CurrentUser current;
 private final ExpenseRepository expenses;
 private final ActivityRepository activities;
 private final com.splitpay.settlement.SettlementRepository settlements;
 public GroupService(GroupRepository groups,UserRepository users,CurrentUser current,ExpenseRepository expenses,ActivityRepository activities,com.splitpay.settlement.SettlementRepository settlements) {
  this.groups=groups; this.users=users; this.current=current; this.expenses=expenses; this.activities=activities; this.settlements=settlements;
 }
 public record GroupInput(@NotBlank @Size(max=100) String name,@Size(max=500) String description) {}
 public record ExpenseInput(@NotBlank @Size(max=200) String description,@NotNull @DecimalMin("0.01") @Digits(integer=9,fraction=2) BigDecimal amount,
  @NotNull UUID payerId,@NotBlank String category,@NotNull LocalDate date,@Size(max=2000) String notes,
  @NotNull SplitCalculator.Type splitType,@NotEmpty Map<UUID,BigDecimal> participants,Long version) {}
 public record Details(SharedGroup group,List<UserProfile> members,List<SharedExpense> expenses,Map<UUID,BigDecimal> balances,
  List<BalanceEngine.Transfer> simplified,List<Activity> activity,List<BalanceEngine.Transfer> direct,List<com.splitpay.settlement.Settlement> settlements) {}
 public List<SharedGroup> list() { return groups.forUser(current.get().id); }
 public SharedGroup access(UUID id,boolean lock) {
  SharedGroup group=(lock?groups.locked(id):groups.findById(id)).orElseThrow();
  if(!group.memberIds.contains(current.get().id)) throw new AccessDeniedException("Group membership required");
  return group;
 }
 public SharedGroup create(GroupInput input) {
  UserProfile me=current.get(); SharedGroup group=new SharedGroup(); group.name=input.name().trim();
  group.description=input.description()==null?"":input.description().trim(); group.createdBy=me.id; group.memberIds.add(me.id);
  groups.saveAndFlush(group); activity(group,"GROUP_CREATED",me.name+" created "+group.name); return group;
 }
 public SharedGroup update(UUID id,GroupInput input) {
  SharedGroup group=access(id,true); owner(group); group.name=input.name().trim(); group.description=input.description()==null?"":input.description();
  activity(group,"GROUP_UPDATED",current.get().name+" updated the group"); return group;
 }
 public void delete(UUID id) {
  SharedGroup group=access(id,true); owner(group);
  if(expenses.existsByGroupId(id)||settlements.existsByGroupId(id)) throw new IllegalArgumentException("Only groups without financial history can be deleted.");
  activities.deleteByGroupId(id); groups.delete(group);
 }
 public SharedGroup addMember(UUID id,String email) {
  SharedGroup group=access(id,true); owner(group);
  UserProfile user=users.findByEmailIgnoreCase(email.trim()).orElseThrow(()->new IllegalArgumentException("This email must register in SplitPay first."));
  if(group.memberIds.add(user.id)) activity(group,"MEMBER_JOINED",user.name+" joined "+group.name); return group;
 }
 public void removeMember(UUID id,UUID userId) {
  SharedGroup group=access(id,true); owner(group);
  if(group.createdBy.equals(userId)) throw new IllegalArgumentException("The group creator cannot be removed.");
  if(settlements.findByGroupIdOrderByCreatedAtDesc(id).stream().anyMatch(s->s.payerUserId.equals(userId)||s.receiverUserId.equals(userId))) throw new IllegalArgumentException("A member with settlement history cannot be removed.");
  if(expenses.findByGroupIdOrderByDateDesc(id).stream().anyMatch(e->e.payerId.equals(userId)||e.splits.containsKey(userId)))
   throw new IllegalArgumentException("A member with expense history cannot be removed.");
  if(group.memberIds.remove(userId)) activity(group,"MEMBER_REMOVED",current.get().name+" removed a member");
 }
 public Details details(UUID id) {
  SharedGroup group=access(id,false); List<SharedExpense> entries=expenses.findByGroupIdOrderByDateDesc(id);
  Map<UUID,BigDecimal> balances=balances(group,entries);
  var history=settlements.findByGroupIdOrderByCreatedAtDesc(id);
  var original=entries.stream().map(e->{Map<UUID,BigDecimal> shares=new HashMap<>();e.splits.forEach((user,share)->shares.put(user,share.amount));return new BalanceEngine.Expense(e.payerId,e.amount,shares);}).toList();
  var paid=history.stream().filter(s->s.status.equals("SUCCESS")).map(s->new BalanceEngine.Transfer(s.payerUserId,s.receiverUserId,s.amount)).toList();
  return new Details(group,users.findAllById(group.memberIds),entries,balances,new BalanceEngine().simplify(balances),activities.findTop100ByGroupIdOrderByCreatedAtDesc(id),new BalanceEngine().direct(original,paid),history);
 }
 public Map<UUID,BigDecimal> balances(SharedGroup group,List<SharedExpense> entries) {
  var calculated=entries.stream().map(e->{
   Map<UUID,BigDecimal> shares=new HashMap<>(); e.splits.forEach((user,value)->shares.put(user,value.amount));
   return new BalanceEngine.Expense(e.payerId,e.amount,shares);
  }).toList();
  var completed=settlements.findByGroupIdOrderByCreatedAtDesc(group.id).stream().filter(s->s.status.equals("SUCCESS")).map(s->new BalanceEngine.Transfer(s.payerUserId,s.receiverUserId,s.amount)).toList();
  return new BalanceEngine().calculate(group.memberIds,calculated,completed);
 }
 public SharedExpense expense(UUID id) { SharedExpense expense=expenses.findById(id).orElseThrow(); access(expense.groupId,false); return expense; }
 public SharedExpense saveExpense(UUID groupId,UUID expenseId,ExpenseInput input) {
  SharedGroup group=access(groupId,true);
  noPendingPayments(groupId);
  if(!group.memberIds.contains(input.payerId())||!group.memberIds.containsAll(input.participants().keySet())) throw new IllegalArgumentException("Payer and all participants must belong to this group.");
  if(!Set.of("Food","Travel","Groceries","Shopping","Rent","Bills","Entertainment","Health","Education","Subscriptions","Other").contains(input.category())) throw new IllegalArgumentException("Choose a supported category.");
  if(input.participants().values().stream().anyMatch(w->w==null||w.precision()>14||w.scale()>6)) throw new IllegalArgumentException("Split values are too precise or too large.");
  var shares=new SplitCalculator().calculate(input.amount(),input.splitType(),input.participants());
  SharedExpense expense=expenseId==null?new SharedExpense():expenses.findById(expenseId).orElseThrow();
  if(expenseId!=null) {
   if(!expense.groupId.equals(groupId)) throw new AccessDeniedException("Wrong group");
   editor(group,expense);
   if(input.version()==null||input.version()!=expense.version) throw new org.springframework.orm.ObjectOptimisticLockingFailureException(SharedExpense.class,expenseId);
  } else { expense.groupId=groupId; expense.createdBy=current.get().id; }
  expense.description=input.description().trim(); expense.amount=input.amount().setScale(2); expense.payerId=input.payerId();
  expense.category=input.category(); expense.date=input.date(); expense.notes=input.notes()==null?"":input.notes(); expense.splitType=input.splitType().name();
  expense.splits.clear(); shares.forEach((id,value)->expense.splits.put(id,new SharedExpense.Share(value,input.splitType()==SplitCalculator.Type.EQUAL?BigDecimal.ONE:input.participants().get(id))));
  expense.updatedAt=Instant.now(); expenses.saveAndFlush(expense);
  activity(group,expenseId==null?"EXPENSE_CREATED":"EXPENSE_UPDATED",current.get().name+(expenseId==null?" added ":" edited ")+expense.description+" — INR "+expense.amount);
  return expense;
 }
 public void deleteExpense(UUID id) {
  SharedExpense expense=expenses.findById(id).orElseThrow(); SharedGroup group=access(expense.groupId,true); editor(group,expense);
  noPendingPayments(group.id);
  expenses.delete(expense); activity(group,"EXPENSE_DELETED",current.get().name+" deleted "+expense.description);
 }
 public void activity(SharedGroup group,String type,String description) {
  group.updatedAt=Instant.now(); Activity item=new Activity(); item.groupId=group.id; item.userId=current.get().id; item.type=type; item.description=description; activities.save(item);
  events.publishEvent(new com.splitpay.notification.GroupChanged(group.id,item.userId,type,description,item.id));
 }
 private void owner(SharedGroup group) { if(!group.createdBy.equals(current.get().id)) throw new AccessDeniedException("Only the creator can manage this group"); }
 private void noPendingPayments(UUID groupId) { if(settlements.findByGroupIdOrderByCreatedAtDesc(groupId).stream().anyMatch(s->Set.of("CREATED","PENDING").contains(s.status))) throw new IllegalArgumentException("Complete the pending payment before changing expenses."); }
 private void editor(SharedGroup group,SharedExpense expense) {
  UUID me=current.get().id;
  if(!me.equals(group.createdBy)&&!me.equals(expense.createdBy)&&!me.equals(expense.payerId)) throw new AccessDeniedException("Only the author, payer or group creator can edit this expense");
 }
}
