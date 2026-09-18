package com.splitpay.payment;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
public final class Signatures {
 public static String hmac(String secret,byte[] message) {
  try {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(message));}
  catch(Exception e){throw new IllegalStateException("Signature computation unavailable",e);}
 }
 public static boolean valid(String secret,byte[] message,String signature){
  return secret!=null&&!secret.isBlank()&&signature!=null&&signature.matches("[a-fA-F0-9]{64}")&&MessageDigest.isEqual(hmac(secret,message).getBytes(StandardCharsets.UTF_8),signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
 }
}
