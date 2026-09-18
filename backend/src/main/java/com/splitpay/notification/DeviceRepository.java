package com.splitpay.notification;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeviceRepository extends JpaRepository<DeviceToken,String>{List<DeviceToken> findByUserId(UUID userId);}
