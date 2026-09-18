package com.splitpay.payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class PaymentController {
 private final PaymentService service;
 public PaymentController(PaymentService service){this.service=service;}
 public record OrderInput(@NotNull UUID settlementId){}
 public record VerifyInput(@NotBlank String razorpayPaymentId,@NotBlank String razorpayOrderId,@NotBlank String razorpaySignature){}
 @PostMapping("/payments/order") public Object order(@Valid @RequestBody OrderInput input){return service.order(input.settlementId());}
 @PostMapping("/payments/verify") public Object verify(@Valid @RequestBody VerifyInput input){return service.verify(input.razorpayOrderId(),input.razorpayPaymentId(),input.razorpaySignature());}
 @PostMapping("/payments/{id}/cancel") public Object cancel(@PathVariable UUID id){return service.cancel(id);}
 @PostMapping("/webhooks/razorpay") public Object webhook(@RequestBody byte[] body,@RequestHeader("X-Razorpay-Signature")String signature,@RequestHeader(value="X-Razorpay-Event-Id",required=false)String eventId){service.webhook(body,signature,eventId);return Map.of("received",true);}
}
