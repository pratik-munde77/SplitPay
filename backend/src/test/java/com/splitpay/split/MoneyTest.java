package com.splitpay.split;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    final UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
    final SplitCalculator calculator = new SplitCalculator();
    final BalanceEngine engine = new BalanceEngine();
    BigDecimal money(String amount) { return new BigDecimal(amount); }
    Map<UUID, BigDecimal> weights(String x, String y, String z) { return Map.of(a, money(x), b, money(y), c, money(z)); }

    @Test void equalDistributesEveryCentDeterministically() {
        assertEquals(weights("3.34", "3.33", "3.33"), calculator.calculate(money("10"), SplitCalculator.Type.EQUAL, weights("1", "1", "1")));
    }
    @Test void exactMustMatchTotal() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(money("10"), SplitCalculator.Type.EXACT, weights("3", "3", "3")));
        assertEquals(weights("5.00", "3.00", "2.00"), calculator.calculate(money("10"), SplitCalculator.Type.EXACT, weights("5", "3", "2")));
    }
    @Test void percentageMustTotalOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(money("10"), SplitCalculator.Type.PERCENTAGE, weights("50", "30", "10")));
        assertEquals(weights("5.00", "3.00", "2.00"), calculator.calculate(money("10"), SplitCalculator.Type.PERCENTAGE, weights("50", "30", "20")));
    }
    @Test void sharesAreProportional() {
        assertEquals(weights("600.00", "300.00", "300.00"), calculator.calculate(money("1200"), SplitCalculator.Type.SHARES, weights("2", "1", "1")));
    }
    @Test void rejectsFractionalCentsAndZeroShares() {
        assertThrows(ArithmeticException.class, () -> calculator.calculate(money("1.001"), SplitCalculator.Type.EQUAL, weights("1", "1", "1")));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(money("1"), SplitCalculator.Type.SHARES, weights("1", "0", "1")));
    }
    @Test void paymentsAndSharesGiveZeroSumBalances() {
        var balances = engine.calculate(List.of(a,b,c), List.of(new BalanceEngine.Expense(a, money("1200"), weights("400", "400", "400"))), List.of());
        assertEquals(weights("800.00", "-400.00", "-400.00"), balances);
        var suggestions = engine.simplify(balances);
        assertEquals(2, suggestions.size());
        var cleared = engine.calculate(List.of(a,b,c), List.of(new BalanceEngine.Expense(a, money("1200"), weights("400", "400", "400"))), suggestions);
        assertTrue(cleared.values().stream().allMatch(x -> x.signum() == 0));
    }
    @Test void intermediaryDebtIsRemovedWithoutMutatingInputs() {
        var balances = weights("-500", "0", "500");
        assertEquals(List.of(new BalanceEngine.Transfer(a,c,money("500"))), engine.simplify(balances));
        assertEquals(money("-500"), balances.get(a));
    }
    @Test void settlementCannotOverpayOrReverseDirection() {
        var balances = weights("-500", "0", "500");
        assertDoesNotThrow(() -> engine.validateSettlement(balances, new BalanceEngine.Transfer(a,c,money("500"))));
        assertThrows(IllegalArgumentException.class, () -> engine.validateSettlement(balances, new BalanceEngine.Transfer(a,c,money("500.01"))));
        assertThrows(IllegalArgumentException.class, () -> engine.validateSettlement(balances, new BalanceEngine.Transfer(c,a,money("1"))));
    }
}
