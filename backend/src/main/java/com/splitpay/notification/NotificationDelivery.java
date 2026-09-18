package com.splitpay.notification;
import com.splitpay.group.*;
import com.splitpay.expense.*;
import com.splitpay.personal.*;
import com.splitpay.budget.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.*;
@Service
public class NotificationDelivery {
 private final GroupRepository groups;private final ExpenseRepository expenses;private final PersonalRepository personal;
 private final BudgetRepository budgets;private final NotificationRepository notifications;private final DeviceRepository devices;
 public NotificationDelivery(GroupRepository groups,ExpenseRepository expenses,PersonalRepository personal,BudgetRepository budgets,NotificationRepository notifications,DeviceRepository devices){this.groups=groups;this.expenses=expenses;this.personal=personal;this.budgets=budgets;this.notifications=notifications;this.devices=devices;}
 @Async @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void group(GroupChanged event){
  try{groups.findById(event.groupId()).ifPresent(group->{for(UUID member:group.memberIds){
   if(!member.equals(event.actorId()))record(member,event.groupId(),event.type(),event.description(),event.activityId()+":"+member);
   budgetAlerts(member);
  }});}catch(Exception ignored){/* Financial commit must not be undone by notification failure. */}
 }
 @Async @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void spending(SpendingChanged event){try{budgetAlerts(event.userId());}catch(Exception ignored){/* Available on the next spending change. */}}
 private synchronized void budgetAlerts(UUID user){
  YearMonth month=YearMonth.now();Map<String,BigDecimal> totals=new HashMap<>();
  for(var expense:personal.findByUserIdOrderByDateDesc(user))if(YearMonth.from(expense.date).equals(month))totals.merge(expense.category,expense.amount,BigDecimal::add);
  for(var group:groups.forUser(user))for(var expense:expenses.findByGroupIdOrderByDateDesc(group.id)){
   var share=expense.splits.get(user);if(share!=null&&YearMonth.from(expense.date).equals(month))totals.merge(expense.category,share.amount,BigDecimal::add);
  }
  for(var budget:budgets.findByUserIdAndMonth(user,month.toString())){
   BigDecimal spent=budget.category.equals("ALL")?totals.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add):totals.getOrDefault(budget.category,BigDecimal.ZERO);
   int threshold=spent.compareTo(budget.amount)>=0?100:spent.compareTo(budget.amount.multiply(new BigDecimal("0.8")))>=0?80:0;
   if(threshold>0)record(user,null,"BUDGET_ALERT",(budget.category.equals("ALL")?"Monthly":budget.category)+" budget has reached "+threshold+"%.","budget:"+user+":"+month+":"+budget.category+":"+threshold);
  }
 }
 private void record(UUID user,UUID group,String type,String message,String key){
  if(notifications.existsByAlertKey(key))return;
  var item=new NotificationItem();item.userId=user;item.groupId=group;item.type=type;item.message=message;item.alertKey=key;notifications.saveAndFlush(item);
  if(FirebaseApp.getApps().isEmpty())return;
  for(var device:devices.findByUserId(user)){
   try{
    var push=Message.builder().setToken(device.token).putData("userId",user.toString()).putData("notificationId",item.id.toString()).putData("title","SplitPay AI").putData("body",message).putData("groupId",group==null?"":group.toString()).build();
    FirebaseMessaging.getInstance().send(push);
   }catch(FirebaseMessagingException error){if(error.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED)devices.delete(device);}
  }
 }
}
