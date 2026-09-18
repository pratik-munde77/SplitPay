package com.splitpay.personal;
import com.splitpay.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/personal-expenses") @Transactional
public class PersonalController {
 @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
 private final PersonalRepository expenses;private final CurrentUser current;private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 public PersonalController(PersonalRepository expenses,CurrentUser current,org.springframework.jdbc.core.JdbcTemplate jdbc){this.expenses=expenses;this.current=current;this.jdbc=jdbc;}
 public record Input(@NotNull UUID id,@NotBlank @Size(max=200) String merchant,@NotNull @DecimalMin("0.01") @Digits(integer=9,fraction=2) BigDecimal amount,
  @NotBlank String category,@NotNull LocalDate date,@Pattern(regexp="MANUAL|RECEIPT") @NotNull String source,
  @Pattern(regexp="CASH|UPI|CARD|BANK|OTHER") @NotNull String paymentMethod,@Size(max=2000) String notes,@Size(max=2000) String receiptUrl) {}
 @GetMapping public List<PersonalExpense> list(){return expenses.findByUserIdOrderByDateDesc(current.get().id);}
 @PostMapping public PersonalExpense create(@Valid @RequestBody Input input){
  var existing=expenses.findById(input.id());
  if(existing.isPresent()){own(existing.get());return existing.get();}
  PersonalExpense item=new PersonalExpense();item.id=input.id();item.userId=current.get().id;return save(item,input);
 }
 @PutMapping("/{id}") public PersonalExpense update(@PathVariable UUID id,@Valid @RequestBody Input input){
  PersonalExpense item=expenses.findById(id).orElseThrow();own(item);return save(item,input);
 }
 @DeleteMapping("/{id}") public void delete(@PathVariable UUID id){expenses.findById(id).ifPresent(item->{own(item);expenses.delete(item);});}
 private void own(PersonalExpense item){if(!item.userId.equals(current.get().id))throw new AccessDeniedException("Not your expense");}
 private PersonalExpense save(PersonalExpense item,Input input){
  if(!Set.of("Food","Travel","Groceries","Shopping","Rent","Bills","Entertainment","Health","Education","Subscriptions","Other").contains(input.category()))throw new IllegalArgumentException("Invalid category");
  item.merchant=input.merchant().trim();item.description=item.merchant;item.amount=input.amount().setScale(2);item.category=input.category();item.date=input.date();
  item.source=input.source();item.paymentMethod=input.paymentMethod();item.notes=input.notes()==null?"":input.notes();item.receiptUrl=input.receiptUrl()==null?"":input.receiptUrl();item.updatedAt=Instant.now();
  expenses.saveAndFlush(item);
  if(item.source.equals("RECEIPT")){
   jdbc.update("DELETE FROM receipts WHERE personal_expense_id=?",item.id);
   jdbc.update("INSERT INTO receipts(id,personal_expense_id,user_id,merchant,amount,date,created_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),item.id,item.userId,item.merchant,item.amount,item.date,java.sql.Timestamp.from(Instant.now()));
  }
  events.publishEvent(new com.splitpay.notification.SpendingChanged(item.userId));
  return item;
 }
}
