package com.splitpay.notification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="notifications")
public class NotificationItem {
 @Id public UUID id=UUID.randomUUID();
 @Column(nullable=false) public UUID userId;
 public UUID groupId;
 @Column(nullable=false,length=40) public String type;
 @Column(nullable=false,length=500) public String message;
 @Column(unique=true,length=200) public String alertKey;
 @Column(nullable=false) public Instant createdAt=Instant.now();
 public Instant readAt;
}
