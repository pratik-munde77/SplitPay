package com.splitpay;
import com.splitpay.payment.PayPalGateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class PayPalConfigurationTest {
 @Test void missingSecretsFailWithSetupInstructions(){
  var error=assertThrows(IllegalArgumentException.class,()->new PayPalGateway(new MockEnvironment()).configured());
  assertTrue(error.getMessage().contains("PAYPAL_CLIENT_ID"));
 }
 @Test void insecureRemoteReturnAndInvalidRatesAreRejected(){
  var env=new MockEnvironment().withProperty("PAYPAL_RETURN_URL","http://example.com/return");
  assertThrows(IllegalArgumentException.class,()->new PayPalGateway(env).returnUrl());
  env.withProperty("PAYPAL_SANDBOX_INR_PER_USD","0");
  assertThrows(IllegalArgumentException.class,()->new PayPalGateway(env).rate());
 }
 @Test void webhookRequiresItsConfiguredIdAndAllSignatureHeaders(){
  var env=new MockEnvironment();var gateway=new PayPalGateway(env);
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->gateway.verifyWebhook(new com.google.gson.JsonObject(),new org.springframework.http.HttpHeaders()));
  env.withProperty("PAYPAL_WEBHOOK_ID","WH12345");
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->gateway.verifyWebhook(new com.google.gson.JsonObject(),new org.springframework.http.HttpHeaders()));
 }
}
