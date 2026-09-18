package com.splitpay.split;

import java.math.BigDecimal;
import java.util.*;

public final class BalanceEngine {
    public record Expense(UUID payer, BigDecimal amount, Map<UUID, BigDecimal> shares) {}
    public record Transfer(UUID fromUser, UUID toUser, BigDecimal amount) {}
    /** Bilateral ledger before net simplification; reverse obligations cancel pairwise. */
    public List<Transfer> direct(List<Expense> expenses,List<Transfer> settlements) {
        record Pair(UUID first,UUID second) {}
        Map<Pair,BigDecimal> ledger=new HashMap<>();
        java.util.function.BiConsumer<Transfer,BigDecimal> add=(transfer,multiplier)->{
            if(transfer.fromUser.equals(transfer.toUser))return;
            boolean forward=transfer.fromUser.compareTo(transfer.toUser)<0;
            Pair pair=new Pair(forward?transfer.fromUser:transfer.toUser,forward?transfer.toUser:transfer.fromUser);
            BigDecimal amount=transfer.amount.multiply(multiplier);
            ledger.merge(pair,forward?amount:amount.negate(),BigDecimal::add);
        };
        for(var expense:expenses)expense.shares.forEach((user,amount)->add.accept(new Transfer(user,expense.payer,amount),BigDecimal.ONE));
        settlements.forEach(transfer->add.accept(transfer,BigDecimal.ONE.negate()));
        return ledger.entrySet().stream().filter(e->e.getValue().signum()!=0)
            .map(e->e.getValue().signum()>0?new Transfer(e.getKey().first,e.getKey().second,e.getValue()):new Transfer(e.getKey().second,e.getKey().first,e.getValue().negate()))
            .sorted(Comparator.comparing(Transfer::fromUser).thenComparing(Transfer::toUser)).toList();
    }

    public Map<UUID, BigDecimal> calculate(Collection<UUID> members, List<Expense> expenses, List<Transfer> completedSettlements) {
        Map<UUID, BigDecimal> balances = new TreeMap<>();
        members.forEach(id -> balances.put(id, new BigDecimal("0.00")));
        for (Expense expense : expenses) {
            if (expense.shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(expense.amount) != 0) throw new IllegalArgumentException("Expense shares do not match amount");
            add(balances, expense.payer, expense.amount);
            expense.shares.forEach((id, share) -> add(balances, id, share.negate()));
        }
        for (Transfer settlement : completedSettlements) {
            add(balances, settlement.fromUser, settlement.amount);
            add(balances, settlement.toUser, settlement.amount.negate());
        }
        return balances;
    }

    public List<Transfer> simplify(Map<UUID, BigDecimal> balances) {
        if (balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() != 0) throw new IllegalArgumentException("Balances must sum to zero");
        record Position(UUID id, BigDecimal amount) {}
        Comparator<Position> order = Comparator.comparing(Position::amount).reversed().thenComparing(Position::id);
        var creditors = new PriorityQueue<>(order);
        var debtors = new PriorityQueue<>(order);
        balances.forEach((id, value) -> { if (value.signum() > 0) creditors.add(new Position(id, value)); else if (value.signum() < 0) debtors.add(new Position(id, value.negate())); });
        List<Transfer> transfers = new ArrayList<>();
        while (!debtors.isEmpty()) {
            Position debtor = debtors.remove(), creditor = creditors.remove();
            BigDecimal amount = debtor.amount.min(creditor.amount);
            transfers.add(new Transfer(debtor.id, creditor.id, amount));
            if (debtor.amount.compareTo(amount) > 0) debtors.add(new Position(debtor.id, debtor.amount.subtract(amount)));
            if (creditor.amount.compareTo(amount) > 0) creditors.add(new Position(creditor.id, creditor.amount.subtract(amount)));
        }
        return transfers;
    }

    public void validateSettlement(Map<UUID, BigDecimal> balances, Transfer transfer) {
        if (transfer.fromUser.equals(transfer.toUser) || transfer.amount.signum() <= 0) throw new IllegalArgumentException("Invalid settlement");
        transfer.amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        BigDecimal debt = balances.getOrDefault(transfer.fromUser, BigDecimal.ZERO).negate();
        BigDecimal credit = balances.getOrDefault(transfer.toUser, BigDecimal.ZERO);
        if (transfer.amount.compareTo(debt.min(credit)) > 0) throw new IllegalArgumentException("Settlement exceeds outstanding balance");
    }

    private void add(Map<UUID, BigDecimal> balances, UUID id, BigDecimal amount) {
        if (!balances.containsKey(id)) throw new IllegalArgumentException("Unknown group member");
        balances.compute(id, (key, value) -> value.add(amount));
    }
}
