package com.splitpay.config;
import com.splitpay.auth.Identity;
import com.splitpay.user.*;
import com.splitpay.group.*;
import com.splitpay.split.SplitCalculator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Configuration @Profile("demo")
public class DemoSeed {
 @Bean ApplicationRunner demoData(UserRepository users,CurrentUser current,GroupService groups,com.splitpay.personal.PersonalRepository personal,com.splitpay.budget.BudgetRepository budgets) {return args->{
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new Identity("demo-pratik","pratik@splitpay.demo","Pratik"),null,List.of()));
  try {
   var me=current.get(); Map<String,UserProfile> people=new HashMap<>();people.put("Pratik",me);
   if(personal.findByUserIdOrderByDateDesc(me.id).isEmpty()) {
    String[][] samples={{"Swiggy","450","Food"},{"Uber","280","Travel"},{"Fresh Market","1250","Groceries"},{"Netflix","199","Entertainment"}};
    for(int i=0;i<samples.length;i++){
     var sample=new com.splitpay.personal.PersonalExpense();sample.id=UUID.randomUUID();sample.userId=me.id;sample.merchant=samples[i][0];sample.description=sample.merchant;sample.amount=new BigDecimal(samples[i][1]);sample.category=samples[i][2];sample.date=LocalDate.now().withDayOfMonth(Math.max(1,LocalDate.now().getDayOfMonth()-i));sample.notes="Demo expense";sample.source="MANUAL";sample.paymentMethod="UPI";personal.saveAndFlush(sample);
    }
   }
   String month=java.time.YearMonth.now().toString();
   if(budgets.findByUserIdAndMonthAndCategory(me.id,month,"ALL").isEmpty()){
    var budget=new com.splitpay.budget.Budget();budget.userId=me.id;budget.month=month;budget.category="ALL";budget.amount=new BigDecimal("15000");budgets.saveAndFlush(budget);
   }
   for(String name:List.of("Rahul","Aman","Rohit")) {
    String uid="demo-"+name.toLowerCase(Locale.ROOT);
    UserProfile user=users.findByFirebaseUid(uid).orElseGet(()->{UserProfile item=new UserProfile();item.firebaseUid=uid;item.name=name;item.email=name.toLowerCase(Locale.ROOT)+"@splitpay.demo";return users.saveAndFlush(item);});people.put(name,user);
   }
   if(groups.list().stream().noneMatch(g->g.name.equals("Goa Trip"))) {
    var group=groups.create(new GroupService.GroupInput("Goa Trip","Beach days, shared meals and good memories"));
    for(String name:List.of("Rahul","Aman","Rohit")) groups.addMember(group.id,people.get(name).email);
    Map<UUID,BigDecimal> split=new HashMap<>();people.values().forEach(p->split.put(p.id,BigDecimal.ONE));
    String[][] entries={{"Hotel","8000","Rahul","Travel"},{"Dinner","2000","Pratik","Food"},{"Taxi","1200","Aman","Travel"}};
    for(String[] entry:entries) groups.saveExpense(group.id,null,new GroupService.ExpenseInput(entry[0],new BigDecimal(entry[1]),people.get(entry[2]).id,entry[3],LocalDate.now(),"Demo expense",SplitCalculator.Type.EQUAL,split,null));
   }
  } finally {SecurityContextHolder.clearContext();}
 };}
}
