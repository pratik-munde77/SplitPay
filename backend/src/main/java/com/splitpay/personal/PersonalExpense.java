package com.splitpay.personal;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="personal_expenses")
public class PersonalExpense {
 @Id public UUID id;
 @Column(nullable=false) public UUID userId;
 @Column(nullable=false,length=200) public String merchant;
 @Column(nullable=false,length=200) public String description;
 @Column(nullable=false,precision=14,scale=2) public BigDecimal amount;
 @Column(nullable=false,length=30) public String category;
 @Column(nullable=false) public LocalDate date;
 @Column(nullable=false,length=20) public String source;
 @Column(nullable=false,length=20) public String paymentMethod;
 @Column(nullable=false,length=2000) public String notes="";
 @Column(nullable=false,length=2000) public String receiptUrl="";
 @Column(nullable=false) public Instant createdAt=Instant.now();
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 @Version public long version;
}
