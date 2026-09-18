package com.splitpay.settlement;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="settlements")
public class Settlement {
 @Id public UUID id;
 @Column(nullable=false) public UUID groupId;
 @Column(nullable=false) public UUID payerUserId;
 @Column(nullable=false) public UUID receiverUserId;
 @Column(nullable=false,precision=14,scale=2) public BigDecimal amount;
 @Column(nullable=false,length=3) public String currency="INR";
 @Column(nullable=false,length=20) public String method;
 @Column(nullable=false,length=20) public String status;
 @Column(nullable=false,length=20) public String provider="NONE";
 @Column(unique=true,length=100) public String providerOrderId;
 @Column(unique=true,length=100) public String providerPaymentId;
 @Column(precision=14,scale=2) public BigDecimal checkoutAmount;
 @Column(length=3) public String checkoutCurrency;
 @Column(precision=18,scale=6) public BigDecimal checkoutRate;
 @Column(nullable=false) public Instant createdAt=Instant.now();
 public Instant completedAt;
 @Version public long version;
}
