package com.splitpay.split;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;

/** Currency arithmetic is exact; participant IDs provide a stable rounding tie break. */
public final class SplitCalculator {
    public enum Type { EQUAL, EXACT, PERCENTAGE, SHARES }

    public Map<UUID, BigDecimal> calculate(BigDecimal amount, Type type, Map<UUID, BigDecimal> participants) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        BigInteger cents = amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).toBigIntegerExact();
        if (type == null) throw new IllegalArgumentException("Choose a split type");
        if (participants == null || participants.isEmpty() || participants.keySet().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Choose participants");
        var ids = participants.keySet().stream().sorted().toList();
        var result = new LinkedHashMap<UUID, BigDecimal>();
        if (type == Type.EXACT) {
            BigDecimal total = BigDecimal.ZERO;
            for (UUID id : ids) {
                BigDecimal value = participants.get(id);
                if (value == null || value.signum() < 0) throw new IllegalArgumentException("Exact amounts cannot be negative");
                value = value.setScale(2, RoundingMode.UNNECESSARY);
                result.put(id, value); total = total.add(value);
            }
            if (total.compareTo(amount) != 0) throw new IllegalArgumentException("Exact amounts must add up to the expense");
            return result;
        }
        var weights = new LinkedHashMap<UUID, BigDecimal>();
        for (UUID id : ids) {
            BigDecimal weight = type == Type.EQUAL ? BigDecimal.ONE : participants.get(id);
            if (weight == null || weight.signum() < 0 || (type == Type.SHARES && weight.signum() == 0)) throw new IllegalArgumentException("Invalid split weight");
            weights.put(id, weight);
        }
        BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw new IllegalArgumentException("Weights must be positive");
        if (type == Type.PERCENTAGE && total.compareTo(new BigDecimal("100")) != 0) throw new IllegalArgumentException("Percentages must total 100");
        var remainders = new HashMap<UUID, BigDecimal>();
        BigInteger allocated = BigInteger.ZERO;
        for (UUID id : ids) {
            BigDecimal numerator = new BigDecimal(cents).multiply(weights.get(id));
            BigInteger share = numerator.divideToIntegralValue(total).toBigIntegerExact();
            remainders.put(id, numerator.remainder(total));
            result.put(id, new BigDecimal(share, 2));
            allocated = allocated.add(share);
        }
        var roundingOrder = ids.stream().sorted(Comparator.<UUID, BigDecimal>comparing(remainders::get).reversed().thenComparing(Comparator.naturalOrder())).toList();
        int left = cents.subtract(allocated).intValueExact();
        for (int i = 0; i < left; i++) result.compute(roundingOrder.get(i), (id, value) -> value.add(new BigDecimal("0.01")));
        return result;
    }
}
