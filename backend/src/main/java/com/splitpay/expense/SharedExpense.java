package com.splitpay.expense;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="expenses")
public class SharedExpense {
 @Id public UUID id=UUID.randomUUID();
 @Column(nullable=false) public UUID groupId;
 @Column(nullable=false,length=200) public String description;
 @Column(nullable=false,precision=14,scale=2) public BigDecimal amount;
 @Column(nullable=false,length=3) public String currency="INR";
 @Column(nullable=false) public UUID payerId;
 @Column(nullable=false,length=30) public String category;
 @Column(nullable=false) public LocalDate date;
 @Column(nullable=false,length=2000) public String notes="";
 @Column(nullable=false,length=20) public String splitType;
 @Column(nullable=false) public UUID createdBy;
 @Column(nullable=false) public Instant createdAt=Instant.now();
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 @Version public long version;
 @ElementCollection(fetch=FetchType.EAGER)
 @CollectionTable(name="expense_splits",joinColumns=@JoinColumn(name="expense_id"))
 @MapKeyColumn(name="user_id")
 public Map<UUID,Share> splits=new HashMap<>();
 @Embeddable public static class Share {
  @Column(nullable=false,precision=14,scale=2) public BigDecimal amount;
  @Column(nullable=false,precision=20,scale=6) public BigDecimal weight;
  public Share() {}
  public Share(BigDecimal amount,BigDecimal weight) { this.amount=amount; this.weight=weight; }
 }
}
