package com.splitpay.payment;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="payments")
public class PaymentAttempt {
 @Id public UUID id=UUID.randomUUID();
 @Column(nullable=false) public UUID settlementId;
 @Column(nullable=false,length=100) public String providerOrderId;
 @Column(nullable=false,unique=true,length=100) public String providerPaymentId;
 @Column(nullable=false,length=30) public String status;
 @Column(nullable=false) public Instant createdAt=Instant.now();
}
