package com.splitpay;
import com.splitpay.payment.Signatures;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SignatureTest {
 @Test void liveKeysAreRejected(){var env=new org.springframework.mock.env.MockEnvironment().withProperty("RAZORPAY_KEY_ID","rzp_live_forbidden").withProperty("RAZORPAY_KEY_SECRET","not-a-real-secret");assertThrows(IllegalArgumentException.class,()->new com.splitpay.payment.RazorpayGateway(env).keyId());}
 @Test void knownHmacVectorAndTampering(){
  byte[] message="The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
  String expected="f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
  assertEquals(expected,Signatures.hmac("key",message));assertTrue(Signatures.valid("key",message,expected));
  assertFalse(Signatures.valid("wrong",message,expected));assertFalse(Signatures.valid("key","changed".getBytes(StandardCharsets.UTF_8),expected));assertFalse(Signatures.valid("key",message,"invalid"));
 }
}
