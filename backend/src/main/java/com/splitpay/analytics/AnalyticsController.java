package com.splitpay.analytics;
import com.splitpay.user.*;
import com.splitpay.personal.*;
import com.splitpay.group.*;
import com.splitpay.budget.*;
import java.math.*;
import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/api") @Transactional
public class AnalyticsController {
 @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
 private final CurrentUser current;private final PersonalRepository personal;private final GroupService groups;private final BudgetRepository budgets;
 public AnalyticsController(CurrentUser current,PersonalRepository personal,GroupService groups,BudgetRepository budgets){this.current=current;this.personal=personal;this.groups=groups;this.budgets=budgets;}
 public record BudgetInput(@NotBlank String month,@NotBlank String category,@NotNull @DecimalMin("0.01") @Digits(integer=9,fraction=2) BigDecimal amount){}
 public record BudgetStatus(UUID id,String category,BigDecimal amount,BigDecimal spent,BigDecimal remaining,int percent){}
 public record Monthly(String month,BigDecimal total,BigDecimal personalTotal,BigDecimal sharedTotal,BigDecimal previousTotal,BigDecimal youOwe,BigDecimal youAreOwed,Map<String,BigDecimal> categories,Map<String,BigDecimal> daily,Map<String,BigDecimal> topMerchants,List<BudgetStatus> budgets){}
 @PostMapping("/budgets") public Budget saveBudget(@Valid @RequestBody BudgetInput input){
  YearMonth.parse(input.month());
  if(!Set.of("ALL","Food","Travel","Groceries","Shopping","Rent","Bills","Entertainment","Health","Education","Subscriptions","Other").contains(input.category()))throw new IllegalArgumentException("Invalid budget category");
  UUID me=current.get().id;Budget budget=budgets.findByUserIdAndMonthAndCategory(me,input.month(),input.category()).orElseGet(Budget::new);
  budget.userId=me;budget.month=input.month();budget.category=input.category();budget.amount=input.amount().setScale(2);budgets.saveAndFlush(budget);
  events.publishEvent(new com.splitpay.notification.SpendingChanged(me));return budget;
 }
 @GetMapping("/budgets/current") public Object budget(){return monthly(null).budgets();}
 @GetMapping("/analytics/monthly") public Monthly monthly(@RequestParam(required=false) String month){
  YearMonth period=month==null?YearMonth.now():YearMonth.parse(month);UUID me=current.get().id;
  BigDecimal personalTotal=BigDecimal.ZERO,sharedTotal=BigDecimal.ZERO,previous=BigDecimal.ZERO,owe=BigDecimal.ZERO,owed=BigDecimal.ZERO;
  Map<String,BigDecimal> categories=new TreeMap<>(),daily=new TreeMap<>(),merchants=new TreeMap<>();
  for(var item:personal.findByUserIdOrderByDateDesc(me)){
   if(YearMonth.from(item.date).equals(period)){personalTotal=personalTotal.add(item.amount);categories.merge(item.category,item.amount,BigDecimal::add);daily.merge(item.date.toString(),item.amount,BigDecimal::add);merchants.merge(item.merchant,item.amount,BigDecimal::add);}
   if(YearMonth.from(item.date).equals(period.minusMonths(1)))previous=previous.add(item.amount);
  }
  for(var group:groups.list()){
   var detail=groups.details(group.id);BigDecimal balance=detail.balances().getOrDefault(me,BigDecimal.ZERO);
   if(balance.signum()>0)owed=owed.add(balance);else owe=owe.add(balance.negate());
   for(var item:detail.expenses()){
    var split=item.splits.get(me);if(split==null)continue;
    if(YearMonth.from(item.date).equals(period)){sharedTotal=sharedTotal.add(split.amount);categories.merge(item.category,split.amount,BigDecimal::add);daily.merge(item.date.toString(),split.amount,BigDecimal::add);}
    if(YearMonth.from(item.date).equals(period.minusMonths(1)))previous=previous.add(split.amount);
   }
  }
  BigDecimal total=personalTotal.add(sharedTotal);
  var progress=budgets.findByUserIdAndMonth(me,period.toString()).stream().map(b->{
   BigDecimal spent=b.category.equals("ALL")?total:categories.getOrDefault(b.category,BigDecimal.ZERO);
   int percent=spent.multiply(new BigDecimal("100")).divide(b.amount,0,RoundingMode.DOWN).min(new BigDecimal("100000")).intValueExact();
   return new BudgetStatus(b.id,b.category,b.amount,spent,b.amount.subtract(spent),percent);
  }).toList();
  return new Monthly(period.toString(),total,personalTotal,sharedTotal,previous,owe,owed,categories,daily,merchants,progress);
 }
}
