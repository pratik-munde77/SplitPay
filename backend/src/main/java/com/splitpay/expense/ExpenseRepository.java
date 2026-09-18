package com.splitpay.expense;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExpenseRepository extends JpaRepository<SharedExpense,UUID> {
 List<SharedExpense> findByGroupIdOrderByDateDesc(UUID groupId);
 boolean existsByGroupId(UUID groupId);
}
