package com.splitpay.notification;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<NotificationItem,UUID>{
 List<NotificationItem> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
 boolean existsByAlertKey(String alertKey);
}
