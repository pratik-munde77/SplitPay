package com.splitpay.budget;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="budgets",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","budget_month","category"}))
public class Budget {
 @Id public UUID id=UUID.randomUUID();
 @Column(nullable=false) public UUID userId;
 @Column(name="budget_month",nullable=false,length=7) public String month;
 @Column(nullable=false,length=30) public String category;
 @Column(nullable=false,precision=14,scale=2) public BigDecimal amount;
}
