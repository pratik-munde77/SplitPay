package com.splitpay.personal;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PersonalRepository extends JpaRepository<PersonalExpense,UUID> {
 List<PersonalExpense> findByUserIdOrderByDateDesc(UUID userId);
}
