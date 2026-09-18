package com.splitpay.budget;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BudgetRepository extends JpaRepository<Budget,UUID> {
 List<Budget> findByUserIdAndMonth(UUID userId,String month);
 Optional<Budget> findByUserIdAndMonthAndCategory(UUID userId,String month,String category);
}
