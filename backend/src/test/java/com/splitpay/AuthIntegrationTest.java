package com.splitpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("demo")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "SPLITPAY_DEMO_TOKEN=test-token-only-012345678901234567890123456789",
    "spring.datasource.url=jdbc:h2:mem:auth-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AuthIntegrationTest {
    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired com.splitpay.user.UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.splitpay.payment.RazorpayGateway gateway;
    @org.springframework.beans.factory.annotation.Autowired com.splitpay.notification.NotificationRepository notifications;
    @Test void liveEventsRequireAuthenticationAndNotificationsRequireOwnership() throws Exception {
        URI uri=URI.create("ws://localhost:"+port+"/api/live");
        assertThrows(java.util.concurrent.CompletionException.class,()->HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri,new WebSocket.Listener(){}).join());
        var event=new java.util.concurrent.CompletableFuture<String>();
        WebSocket socket=HttpClient.newHttpClient().newWebSocketBuilder()
            .header("Authorization","Bearer test-token-only-012345678901234567890123456789")
            .buildAsync(uri,new WebSocket.Listener(){
                public void onOpen(WebSocket webSocket){webSocket.request(1);}
                public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket,CharSequence data,boolean last){event.complete(data.toString());webSocket.request(1);return null;}
            }).get(10,java.util.concurrent.TimeUnit.SECONDS);
        try {
            String group=call("POST","/groups",java.util.Map.of("name","Live test","description","Authorized events"),200).get("id").getAsString();
            assertEquals(group,com.google.gson.JsonParser.parseString(event.get(10,java.util.concurrent.TimeUnit.SECONDS)).getAsJsonObject().get("groupId").getAsString());
        } finally {socket.abort();}
        var item=new com.splitpay.notification.NotificationItem();
        item.userId=users.findByFirebaseUid("demo-rahul").orElseThrow().id;item.type="TEST";item.message="Private";
        notifications.saveAndFlush(item);
        call("POST","/notifications/"+item.id+"/read",null,403);
        call("POST","/devices",java.util.Map.of("token","test-device-token"),200);
        call("POST","/devices/unregister",java.util.Map.of("token","test-device-token"),200);
    }
    private com.google.gson.JsonObject call(String method,String path,Object body,int expected) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).header("Authorization","Bearer test-token-only-012345678901234567890123456789").header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(new com.google.gson.Gson().toJson(body)));
        var result=HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(expected,result.statusCode(),result.body());
        return result.body().startsWith("{")?com.google.gson.JsonParser.parseString(result.body()).getAsJsonObject():new com.google.gson.JsonObject();
    }
    @Test void allSplitsAndDuplicateSettlementWorkThroughHttp() throws Exception {
        String me=call("GET","/users/me",null,200).get("id").getAsString();
        String rahul=users.findByFirebaseUid("demo-rahul").orElseThrow().id.toString();
        String group=call("POST","/groups",java.util.Map.of("name","Contract test","description","HTTP validation"),200).get("id").getAsString();
        call("POST","/groups/"+group+"/members",java.util.Map.of("email","rahul@splitpay.demo"),200);
        for(String type:java.util.List.of("EQUAL","EXACT","PERCENTAGE","SHARES")) {
            String weight=(type.equals("EXACT")||type.equals("PERCENTAGE"))?"50":"1";
            call("POST","/groups/"+group+"/expenses",java.util.Map.of("description",type,"amount","100.00","payerId",rahul,"category","Food","date","2026-09-14","notes","","splitType",type,"participants",java.util.Map.of(me,weight,rahul,weight)),200);
        }
        var balances=call("GET","/groups/"+group+"/balances",null,200);
        assertEquals(0,new java.math.BigDecimal("-200").compareTo(balances.get(me).getAsBigDecimal()));
        var request=java.util.Map.of("requestId",java.util.UUID.randomUUID().toString(),"receiverUserId",rahul,"amount","200.00","method","MANUAL");
        call("POST","/groups/"+group+"/settlements",request,200);
        call("POST","/groups/"+group+"/settlements",request,200);
        assertEquals(0,call("GET","/groups/"+group+"/balances",null,200).get(me).getAsBigDecimal().signum());
        call("POST","/groups/"+group+"/settlements",java.util.Map.of("requestId",java.util.UUID.randomUUID().toString(),"receiverUserId",rahul,"amount","1","method","MANUAL"),422);
    }
    @Test void personalReceiptsAreIdempotentAndBudgetsReflectSpending() throws Exception {
        String id=java.util.UUID.randomUUID().toString();
        var record=java.util.Map.of("id",id,"merchant","Receipt coffee","amount","12.34","category","Food","date",java.time.LocalDate.now().toString(),"source","RECEIPT","paymentMethod","UPI","notes","Reviewed","receiptUrl","");
        call("POST","/personal-expenses",record,200);call("POST","/personal-expenses",record,200);
        call("POST","/budgets",java.util.Map.of("month",java.time.YearMonth.now().toString(),"category","Food","amount","10.00"),200);
        var analytics=call("GET","/analytics/monthly",null,200);
        assertTrue(analytics.get("personalTotal").getAsBigDecimal().compareTo(new java.math.BigDecimal("12.34"))>=0);
        assertTrue(analytics.getAsJsonArray("budgets").asList().stream().anyMatch(b->b.getAsJsonObject().get("percent").getAsInt()>=100));
        call("DELETE","/personal-expenses/"+id,null,200);call("DELETE","/personal-expenses/"+id,null,200);
    }
    @Test void paymentRequiresSignatureAndProviderCaptureAndIsIdempotent() throws Exception {
        org.mockito.Mockito.when(gateway.keyId()).thenReturn("rzp_test_contract");
        org.mockito.Mockito.when(gateway.secret()).thenReturn("test-signature-secret");
        org.mockito.Mockito.when(gateway.webhookSecret()).thenReturn("test-webhook-secret");
        org.mockito.Mockito.when(gateway.get(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->{
            String path=invocation.getArgument(0);
            return com.google.gson.JsonParser.parseString(path.startsWith("orders?")?"{\"items\":[]}":"{\"id\":\"pay_contract\",\"order_id\":\"order_contract\",\"amount\":2500,\"currency\":\"INR\",\"status\":\"captured\",\"amount_refunded\":0}").getAsJsonObject();
        });
        org.mockito.Mockito.when(gateway.post(org.mockito.ArgumentMatchers.eq("orders"),org.mockito.ArgumentMatchers.any())).thenAnswer(invocation->{
            java.util.Map<?,?> input=invocation.getArgument(1);assertEquals(2500L,input.get("amount"));
            return com.google.gson.JsonParser.parseString("{\"id\":\"order_contract\",\"amount\":2500,\"currency\":\"INR\"}").getAsJsonObject();
        });
        String me=call("GET","/users/me",null,200).get("id").getAsString();
        String rahul=users.findByFirebaseUid("demo-rahul").orElseThrow().id.toString();
        String group=call("POST","/groups",java.util.Map.of("name","Test payment","description","Server verified"),200).get("id").getAsString();
        call("POST","/groups/"+group+"/members",java.util.Map.of("email","rahul@splitpay.demo"),200);
        call("POST","/groups/"+group+"/expenses",java.util.Map.of("description","Dinner","amount","50","payerId",rahul,"category","Food","date",java.time.LocalDate.now().toString(),"notes","","splitType","EQUAL","participants",java.util.Map.of(me,"1",rahul,"1")),200);
        String settlement=call("POST","/groups/"+group+"/settlements",java.util.Map.of("requestId",java.util.UUID.randomUUID().toString(),"receiverUserId",rahul,"amount","25","method","RAZORPAY_TEST"),200).get("id").getAsString();
        assertEquals(2500,call("POST","/payments/order",java.util.Map.of("settlementId",settlement,"amount",999999),200).get("amount").getAsInt());
        call("POST","/payments/verify",java.util.Map.of("razorpayPaymentId","pay_contract","razorpayOrderId","order_contract","razorpaySignature","invalid"),422);
        String signature=com.splitpay.payment.Signatures.hmac("test-signature-secret","order_contract|pay_contract".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var verification=java.util.Map.of("razorpayPaymentId","pay_contract","razorpayOrderId","order_contract","razorpaySignature",signature);
        assertEquals("SUCCESS",call("POST","/payments/verify",verification,200).get("status").getAsString());
        call("POST","/payments/verify",verification,200);
        assertEquals(0,call("GET","/groups/"+group+"/balances",null,200).get(me).getAsBigDecimal().signum());
        String body="{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_contract\",\"order_id\":\"order_contract\"}}}}";
        String webhookSignature=com.splitpay.payment.Signatures.hmac("test-webhook-secret",body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for(int i=0;i<2;i++)assertEquals(200,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/webhooks/razorpay")).header("Content-Type","application/json").header("X-Razorpay-Signature",webhookSignature).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var activity=call("GET","/groups/"+group,null,200).getAsJsonArray("activity");
        assertEquals(1,activity.asList().stream().filter(e->e.getAsJsonObject().get("type").getAsString().equals("SETTLEMENT_COMPLETED")).count());
    }
    @Test void anonymousCannotReadProfileAndVerifiedIdentityCreatesOne() throws Exception {
        var client = HttpClient.newHttpClient();
        URI uri=URI.create("http://localhost:"+port+"/api/users/me");
        assertEquals(401, client.send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var request=HttpRequest.newBuilder(uri).header("Authorization","Bearer test-token-only-012345678901234567890123456789").GET().build();
        var first=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode(),first.body());
        assertTrue(first.body().contains("pratik@splitpay.demo"));
        var second=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,second.statusCode());
        assertEquals(com.google.gson.JsonParser.parseString(first.body()).getAsJsonObject().get("id"),
            com.google.gson.JsonParser.parseString(second.body()).getAsJsonObject().get("id"));
    }
}
