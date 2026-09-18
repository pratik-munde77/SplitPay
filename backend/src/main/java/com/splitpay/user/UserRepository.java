package com.splitpay.user;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByFirebaseUid(String uid);
    Optional<UserProfile> findByEmailIgnoreCase(String email);
}
