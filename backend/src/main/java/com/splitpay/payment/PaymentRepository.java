package com.splitpay.payment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<PaymentAttempt,UUID> {
 Optional<PaymentAttempt> findByProviderPaymentId(String paymentId);
}
