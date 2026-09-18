package com.splitpay.payment;
import com.google.gson.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
@Component
public class RazorpayGateway {
 private final Environment env;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 public RazorpayGateway(Environment env){this.env=env;}
 public String keyId(){String id=env.getProperty("RAZORPAY_KEY_ID","");if(!id.startsWith("rzp_test_")||secret().isBlank())throw new IllegalArgumentException("Configure backend RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET using Test Mode keys.");return id;}
 public String secret(){return env.getProperty("RAZORPAY_KEY_SECRET","");}
 public String webhookSecret(){return env.getProperty("RAZORPAY_WEBHOOK_SECRET","");}
 public JsonObject get(String path){return request("GET",path,null);}
 public JsonObject post(String path,Object body){return request("POST",path,body);}
 private JsonObject request(String method,String path,Object body){
  String credentials=Base64.getEncoder().encodeToString((keyId()+":"+secret()).getBytes(StandardCharsets.UTF_8));
  try{
   var builder=HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/"+path)).timeout(Duration.ofSeconds(20)).header("Authorization","Basic "+credentials).header("Content-Type","application/json");
   builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)));
   var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalArgumentException("Razorpay Test Mode could not complete this request. Check configuration or retry.");
   return JsonParser.parseString(response.body()).getAsJsonObject();
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalArgumentException("Payment request interrupted. Retry verification.");}
  catch(java.io.IOException e){throw new IllegalArgumentException("Razorpay is unavailable. Retry; your balance has not been changed.");}
 }
 public static String id(String value){if(value==null||!value.matches("(pay|order)_[A-Za-z0-9]+"))throw new IllegalArgumentException("Invalid provider identifier");return value;}
}
