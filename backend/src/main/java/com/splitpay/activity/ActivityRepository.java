package com.splitpay.activity;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ActivityRepository extends JpaRepository<Activity,UUID> {
 List<Activity> findTop100ByGroupIdOrderByCreatedAtDesc(UUID groupId);
 void deleteByGroupId(UUID groupId);
}
