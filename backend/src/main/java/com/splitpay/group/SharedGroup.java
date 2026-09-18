package com.splitpay.group;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="expense_groups")
public class SharedGroup {
 @Id public UUID id=UUID.randomUUID();
 @Column(nullable=false,length=100) public String name;
 @Column(nullable=false,length=500) public String description="";
 @Column(nullable=false,length=3) public String currency="INR";
 @Column(nullable=false) public UUID createdBy;
 @Column(nullable=false) public Instant createdAt=Instant.now();
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 @Version public long version;
 @ElementCollection(fetch=FetchType.EAGER)
 @CollectionTable(name="group_members",joinColumns=@JoinColumn(name="group_id"))
 @Column(name="user_id",nullable=false)
 public Set<UUID> memberIds=new HashSet<>();
}
