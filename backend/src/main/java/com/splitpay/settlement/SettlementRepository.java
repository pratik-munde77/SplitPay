package com.splitpay.settlement;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SettlementRepository extends JpaRepository<Settlement,UUID> {
 List<Settlement> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
 Optional<Settlement> findByProviderOrderId(String orderId);
 boolean existsByGroupId(UUID groupId);
}
