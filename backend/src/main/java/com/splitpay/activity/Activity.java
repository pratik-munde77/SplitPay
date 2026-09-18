package com.splitpay.activity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="activity_logs")
public class Activity {
 @Id public UUID id=UUID.randomUUID();
 @Column(nullable=false) public UUID groupId;
 @Column(nullable=false) public UUID userId;
 @Column(nullable=false,length=40) public String type;
 @Column(nullable=false,length=500) public String description;
 @Column(nullable=false) public Instant createdAt=Instant.now();
}
