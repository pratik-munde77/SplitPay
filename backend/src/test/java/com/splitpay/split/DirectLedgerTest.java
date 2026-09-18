package com.splitpay.split;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DirectLedgerTest {
 @Test void originalLedgerAndSimplifiedSuggestionsPreserveTheSameNetPosition(){
  UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
  var engine=new BalanceEngine();
  var expenses=List.of(new BalanceEngine.Expense(b,new BigDecimal("500"),Map.of(a,new BigDecimal("500"))),new BalanceEngine.Expense(c,new BigDecimal("500"),Map.of(b,new BigDecimal("500"))));
  assertEquals(2,engine.direct(expenses,List.of()).size());
  var suggestions=engine.simplify(engine.calculate(List.of(a,b,c),expenses,List.of()));
  assertEquals(List.of(new BalanceEngine.Transfer(a,c,new BigDecimal("500.00"))),suggestions);
  assertTrue(engine.calculate(List.of(a,b,c),expenses,suggestions).values().stream().allMatch(v->v.signum()==0));
 }
}
