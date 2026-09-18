package com.splitpay.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="users")
public class UserProfile {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable=false, unique=true, length=128) public String firebaseUid;
    @Column(nullable=false, length=120) public String name;
    @Column(nullable=false, unique=true, length=254) public String email;
    @Column(nullable=false, length=30) public String phone = "";
    @Column(nullable=false, length=2000) public String photoUrl = "";
    @Column(nullable=false, length=3) public String defaultCurrency = "INR";
    @Column(nullable=false, length=120) public String upiId = "";
    @Column(nullable=false) public Instant createdAt = Instant.now();
    @Column(nullable=false) public Instant updatedAt = Instant.now();
    @Version public long version;
}
