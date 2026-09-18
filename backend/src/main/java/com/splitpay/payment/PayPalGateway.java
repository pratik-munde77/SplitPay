package com.splitpay.payment;

import com.google.gson.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

/** Credentials and all API requests stay on the server. The live host is deliberately unavailable. */
@Component
public class PayPalGateway {
 private static final String BASE="https://api-m.sandbox.paypal.com";
 private final Environment env;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 public PayPalGateway(Environment env){this.env=env;}
 public void configured(){
  if(env.getProperty("PAYPAL_CLIENT_ID","").isBlank()||env.getProperty("PAYPAL_CLIENT_SECRET","").isBlank())
   throw new IllegalArgumentException("Configure PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET from your PayPal Sandbox REST app on the backend.");
  returnUrl(); rate();
 }
 public BigDecimal rate(){
  try {var rate=new BigDecimal(env.getProperty("PAYPAL_SANDBOX_INR_PER_USD","100"));if(rate.signum()>0)return rate;}
  catch(NumberFormatException ignored){}
  throw new IllegalArgumentException("PAYPAL_SANDBOX_INR_PER_USD must be positive.");
 }
 public String returnUrl(){
  String value=env.getProperty("PAYPAL_RETURN_URL","http://localhost:8080/api/paypal/return");
  URI uri=URI.create(value);
  if(!"https".equals(uri.getScheme())&&!("http".equals(uri.getScheme())&&Set.of("localhost","127.0.0.1").contains(uri.getHost())))
   throw new IllegalArgumentException("PAYPAL_RETURN_URL must use HTTPS (or localhost for a USB demo).");
  return value;
 }
 public JsonObject get(String path){return request("GET",path,null,null);}
 public JsonObject post(String path,Object body,String requestId){return request("POST",path,body,requestId);}
 private JsonObject request(String method,String path,Object body,String requestId){
  configured();
  String credentials=Base64.getEncoder().encodeToString((env.getProperty("PAYPAL_CLIENT_ID")+":"+env.getProperty("PAYPAL_CLIENT_SECRET")).getBytes(StandardCharsets.UTF_8));
  var token=send(HttpRequest.newBuilder(URI.create(BASE+"/v1/oauth2/token")).timeout(Duration.ofSeconds(20))
   .header("Authorization","Basic "+credentials).header("Content-Type","application/x-www-form-urlencoded")
   .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build());
  var builder=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(25))
   .header("Authorization","Bearer "+token.get("access_token").getAsString()).header("Content-Type","application/json").header("Prefer","return=representation");
  if(requestId!=null){
   if(requestId.length()>38)throw new IllegalArgumentException("PayPal request ID exceeds 38 characters");
   builder.header("PayPal-Request-Id",requestId);
  }
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)));
  return send(builder.build());
 }
 private JsonObject send(HttpRequest request){
  try {
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalArgumentException("PayPal Sandbox request failed (HTTP "+response.statusCode()+"). Check sandbox credentials and buyer approval, then retry verification.");
   return JsonParser.parseString(response.body()).getAsJsonObject();
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalArgumentException("PayPal request interrupted. Check payment status before retrying.");}
  catch(java.io.IOException e){throw new IllegalArgumentException("PayPal is unavailable. Return to this payment and retry verification.");}
 }
 public void verifyWebhook(JsonObject event,org.springframework.http.HttpHeaders headers){
  String webhookId=env.getProperty("PAYPAL_WEBHOOK_ID","");
  if(webhookId.isBlank())throw new org.springframework.security.access.AccessDeniedException("Configure PAYPAL_WEBHOOK_ID on the backend.");
  Map<String,Object> body=new HashMap<>();
  for(String field:List.of("auth_algo","cert_url","transmission_id","transmission_sig","transmission_time")){
   String value=headers.getFirst("paypal-"+field.replace('_','-'));
   if(value==null||value.isBlank())throw new org.springframework.security.access.AccessDeniedException("Missing PayPal webhook signature headers");
   body.put(field,value);
  }
  body.put("webhook_id",webhookId);body.put("webhook_event",event);
  if(!"SUCCESS".equals(post("/v1/notifications/verify-webhook-signature",body,null).get("verification_status").getAsString()))
   throw new org.springframework.security.access.AccessDeniedException("Invalid PayPal webhook signature");
 }
 public static String id(String id){if(id==null||!id.matches("[A-Za-z0-9]{5,100}"))throw new IllegalArgumentException("Invalid PayPal order identifier");return id;}
}
