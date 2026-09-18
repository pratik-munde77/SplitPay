package com.splitpay.payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import java.util.*;

@RestController @RequestMapping("/api")
public class PayPalController {
 private final PayPalService service;
 public PayPalController(PayPalService service){this.service=service;}
 public record OrderInput(@NotNull UUID settlementId){}
 public record VerifyInput(@NotBlank String orderId){}
 @PostMapping("/paypal/order") public Object order(@Valid @RequestBody OrderInput input){return service.order(input.settlementId());}
 @PostMapping("/paypal/verify") public Object verify(@Valid @RequestBody VerifyInput input){return service.verify(input.orderId());}
 @PostMapping("/paypal/{id}/cancel") public Object cancel(@PathVariable UUID id){return service.cancel(id);}
 @PostMapping("/webhooks/paypal") public Object webhook(@RequestBody String body,@RequestHeader HttpHeaders headers){
  if(body.length()>1000000)throw new IllegalArgumentException("Webhook too large");
  service.webhook(com.google.gson.JsonParser.parseString(body).getAsJsonObject(),headers);return Map.of("received",true);
 }
 @GetMapping(value="/paypal/return",produces="text/html") public String returned(){
  return "<!doctype html><html lang='en'><meta name='viewport' content='width=device-width,initial-scale=1'><title>SplitPay Sandbox</title><body><h1>Return to SplitPay</h1><p>Switch back to the app and tap Check payment status. Approval alone does not confirm payment. SplitPay verifies the capture with PayPal.</p><p>This is a sandbox demonstration. No real money is transferred.</p></body></html>";
 }
}
