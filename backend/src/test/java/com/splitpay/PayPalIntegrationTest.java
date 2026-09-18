package com.splitpay;

import com.google.gson.*;
import com.splitpay.payment.PayPalGateway;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "SPLITPAY_DEMO_TOKEN=test-token-only-012345678901234567890123456789",
 "spring.datasource.url=jdbc:h2:mem:paypal-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class PayPalIntegrationTest {
 @LocalServerPort int port;
 @org.springframework.test.context.bean.override.mockito.MockitoBean PayPalGateway gateway;
 @org.springframework.beans.factory.annotation.Autowired com.splitpay.user.UserRepository users;
 @org.springframework.beans.factory.annotation.Autowired com.splitpay.settlement.SettlementRepository settlements;
 private String group,me,settlement,orderId;
 private JsonObject providerOrder;
 private final Gson gson=new Gson();
 @BeforeEach void setup() throws Exception {
  when(gateway.rate()).thenReturn(new BigDecimal("100"));
  when(gateway.returnUrl()).thenReturn("http://localhost:8080/api/paypal/return");
  me=call("GET","/users/me",null,200).get("id").getAsString();
  String rahul=users.findByFirebaseUid("demo-rahul").orElseThrow().id.toString();
  group=call("POST","/groups",Map.of("name","PayPal contract","description","Sandbox"),200).get("id").getAsString();
  call("POST","/groups/"+group+"/members",Map.of("email","rahul@splitpay.demo"),200);
  call("POST","/groups/"+group+"/expenses",Map.of("description","Dinner","amount","200","payerId",rahul,"category","Food","date",java.time.LocalDate.now().toString(),"notes","","splitType","EQUAL","participants",Map.of(me,"1",rahul,"1")),200);
  settlement=call("POST","/groups/"+group+"/settlements",Map.of("requestId",UUID.randomUUID().toString(),"receiverUserId",rahul,"amount","100","method","PAYPAL_SANDBOX"),200).get("id").getAsString();
  orderId="ORDER"+settlement.replace("-","");
  providerOrder=gson.toJsonTree(Map.of("id",orderId,"intent","CAPTURE","status","CREATED",
   "purchase_units",List.of(Map.of("custom_id",settlement,"amount",Map.of("value","1.00","currency_code","USD"))),
   "links",List.of(Map.of("rel","payer-action","href","https://www.sandbox.paypal.com/checkoutnow?token="+orderId)))).getAsJsonObject();
  when(gateway.post(eq("/v2/checkout/orders"),any(),eq(settlement))).thenAnswer(inv->{
   var request=gson.toJsonTree(inv.getArgument(1,Object.class)).getAsJsonObject();
   var amount=request.getAsJsonArray("purchase_units").get(0).getAsJsonObject().getAsJsonObject("amount");
   assertEquals("1.00",amount.get("value").getAsString());assertEquals("USD",amount.get("currency_code").getAsString());
   return providerOrder.deepCopy();
  });
  when(gateway.get("/v2/checkout/orders/"+orderId)).thenAnswer(inv->providerOrder.deepCopy());
  when(gateway.post(eq("/v2/checkout/orders/"+orderId+"/capture"),any(),eq("C"+settlement))).thenAnswer(inv->{captured("COMPLETED");return providerOrder.deepCopy();});
 }
 private JsonObject call(String method,String path,Object body,int expected) throws Exception {
  var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).header("Authorization","Bearer test-token-only-012345678901234567890123456789").header("Content-Type","application/json");
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
  var result=HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(expected,result.statusCode(),result.body());
  return result.body().startsWith("{")?JsonParser.parseString(result.body()).getAsJsonObject():new JsonObject();
 }
 private JsonObject createOrder() throws Exception{return call("POST","/paypal/order",Map.of("settlementId",settlement,"amount","999999","currency","EUR"),200);}
 private JsonObject check(int expected) throws Exception{return call("POST","/paypal/verify",Map.of("orderId",orderId),expected);}
 private void captured(String status){
  providerOrder.addProperty("status","COMPLETED");
  providerOrder.getAsJsonArray("purchase_units").get(0).getAsJsonObject().add("payments",gson.toJsonTree(Map.of("captures",List.of(Map.of("id","CAPTURE"+settlement.replace("-",""),"status",status,"amount",Map.of("value","1.00","currency_code","USD"))))));
 }
 private void balance(String expected) throws Exception {assertEquals(0,new BigDecimal(expected).compareTo(call("GET","/groups/"+group+"/balances",null,200).get(me).getAsBigDecimal()));}
 @Test void quoteIsStoredAndCaptureIsVerifiedExactlyOnce() throws Exception {
  when(gateway.rate()).thenReturn(new BigDecimal("200")); // Existing quotes cannot drift with config changes.
  var checkout=createOrder();assertEquals("1.00",checkout.get("amount").getAsString());assertEquals("USD",checkout.get("currency").getAsString());
  createOrder();verify(gateway,times(1)).post(eq("/v2/checkout/orders"),any(),eq(settlement));
  assertEquals("PENDING",check(200).get("status").getAsString());balance("-100");
  providerOrder.addProperty("status","APPROVED");
  assertEquals("SUCCESS",check(200).get("status").getAsString());check(200);balance("0");
  verify(gateway,times(1)).post(eq("/v2/checkout/orders/"+orderId+"/capture"),any(),eq("C"+settlement));
  var activity=call("GET","/groups/"+group,null,200).getAsJsonArray("activity");
  assertEquals(1,activity.asList().stream().filter(e->"SETTLEMENT_COMPLETED".equals(e.getAsJsonObject().get("type").getAsString())).count());
 }
 @Test void mismatchedAmountAndCurrencyNeverChangeBalance() throws Exception {
  createOrder();captured("COMPLETED");
  var amount=providerOrder.getAsJsonArray("purchase_units").get(0).getAsJsonObject().getAsJsonObject("payments").getAsJsonArray("captures").get(0).getAsJsonObject().getAsJsonObject("amount");
  amount.addProperty("value","999");check(422);balance("-100");
  amount.addProperty("value","1.00");amount.addProperty("currency_code","EUR");check(422);balance("-100");
 }
 @Test void pendingCaptureCannotBeCancelledAndCanLaterComplete() throws Exception {
  createOrder();captured("PENDING");assertEquals("PENDING",check(200).get("status").getAsString());
  assertEquals("PENDING",call("POST","/paypal/"+settlement+"/cancel",null,200).get("status").getAsString());balance("-100");
  captured("COMPLETED");assertEquals("SUCCESS",check(200).get("status").getAsString());balance("0");
 }
 @Test void cancellingApprovalDoesNotCaptureMoney() throws Exception {
  createOrder();providerOrder.addProperty("status","APPROVED");
  assertEquals("CANCELLED",call("POST","/paypal/"+settlement+"/cancel",null,200).get("status").getAsString());
  assertEquals("CANCELLED",check(200).get("status").getAsString());
  verify(gateway,never()).post(eq("/v2/checkout/orders/"+orderId+"/capture"),any(),any());balance("-100");
 }
 @Test void completedCaptureIsReconciledEvenWhenUserCancels() throws Exception {
  createOrder();captured("COMPLETED");
  assertEquals("SUCCESS",call("POST","/paypal/"+settlement+"/cancel",null,200).get("status").getAsString());balance("0");
 }
 @Test void anotherPayerCannotCreateOrVerifyOrder() throws Exception {
  createOrder();var item=settlements.findById(UUID.fromString(settlement)).orElseThrow();
  item.payerUserId=users.findByFirebaseUid("demo-aman").orElseThrow().id;settlements.saveAndFlush(item);
  call("POST","/paypal/order",Map.of("settlementId",settlement),403);check(403);
  call("POST","/paypal/"+settlement+"/cancel",null,403);
 }
 @Test void staleDebtRefundsCaptureWithoutApplyingAnotherSettlement() throws Exception {
  createOrder();call("POST","/paypal/"+settlement+"/cancel",null,200);
  String receiver=settlements.findById(UUID.fromString(settlement)).orElseThrow().receiverUserId.toString();
  call("POST","/groups/"+group+"/settlements",Map.of("requestId",UUID.randomUUID().toString(),"receiverUserId",receiver,"amount","100","method","MANUAL"),200);
  captured("COMPLETED");String captureId="CAPTURE"+settlement.replace("-","");
  when(gateway.post(eq("/v2/payments/captures/"+captureId+"/refund"),any(),eq("R"+settlement))).thenReturn(JsonParser.parseString("{\"status\":\"COMPLETED\"}").getAsJsonObject());
  assertEquals("FAILED",check(200).get("status").getAsString());check(200);balance("0");
  verify(gateway,times(1)).post(eq("/v2/payments/captures/"+captureId+"/refund"),any(),eq("R"+settlement));
 }
 @Test void signedWebhookReconcilesIdempotentlyAndInvalidSignatureIsRejected() throws Exception {
  createOrder();captured("COMPLETED");
  var event=Map.of("id","WH12345","event_type","PAYMENT.CAPTURE.COMPLETED","resource",Map.of("supplementary_data",Map.of("related_ids",Map.of("order_id",orderId))));
  doThrow(new org.springframework.security.access.AccessDeniedException("Invalid signature")).when(gateway).verifyWebhook(any(),any());
  call("POST","/webhooks/paypal",event,403);balance("-100");
  doNothing().when(gateway).verifyWebhook(any(),any());
  call("POST","/webhooks/paypal",event,200);call("POST","/webhooks/paypal",event,200);balance("0");
 }
}
