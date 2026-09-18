package com.splitpay.notification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="device_tokens")
public class DeviceToken {
 @Id @Column(length=64) public String id;
 @Column(nullable=false) public UUID userId;
 @Column(nullable=false,length=2048) public String token;
 @Column(nullable=false) public Instant updatedAt=Instant.now();
}
