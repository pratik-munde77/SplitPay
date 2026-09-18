package com.splitpay.payment;
import com.google.gson.*;
import com.splitpay.settlement.*;
import com.splitpay.group.*;
import com.splitpay.expense.*;
import com.splitpay.user.*;
import com.splitpay.activity.*;
import com.splitpay.split.BalanceEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service @Transactional
public class PaymentService {
 @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
 private final RazorpayGateway gateway;private final SettlementRepository settlements;private final GroupRepository groupRepository;
 private final GroupService groups;private final ExpenseRepository expenses;private final CurrentUser current;private final PaymentRepository payments;private final ActivityRepository activities;private final JdbcTemplate jdbc;
 public PaymentService(RazorpayGateway gateway,SettlementRepository settlements,GroupRepository groupRepository,GroupService groups,ExpenseRepository expenses,CurrentUser current,PaymentRepository payments,ActivityRepository activities,JdbcTemplate jdbc){
  this.gateway=gateway;this.settlements=settlements;this.groupRepository=groupRepository;this.groups=groups;this.expenses=expenses;this.current=current;this.payments=payments;this.activities=activities;this.jdbc=jdbc;
 }
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
 public record Checkout(String settlementId,String orderId,String keyId,long amount,String currency){}
 private Settlement locked(UUID id,boolean authorize){
  Settlement snapshot=settlements.findById(id).orElseThrow();groupRepository.locked(snapshot.groupId).orElseThrow();
  Settlement item=settlements.findById(id).orElseThrow();entityManager.refresh(item);
  if(authorize&&!item.payerUserId.equals(current.get().id))throw new AccessDeniedException("Only the payer can check out");
  if(!item.method.equals("RAZORPAY_TEST"))throw new IllegalArgumentException("This is not a Razorpay test settlement");
  return item;
 }
 public Checkout order(UUID id){
  String keyId=gateway.keyId();Settlement item=locked(id,true);
  if(item.status.equals("SUCCESS"))throw new IllegalArgumentException("This settlement is already paid.");
  if(item.status.equals("CANCELLED")||item.status.equals("FAILED")){
   var group=groupRepository.findById(item.groupId).orElseThrow();
   new BalanceEngine().validateSettlement(groups.balances(group,expenses.findByGroupIdOrderByDateDesc(item.groupId)),new BalanceEngine.Transfer(item.payerUserId,item.receiverUserId,item.amount));
  }
  long amount=item.amount.movePointRight(2).longValueExact();
  if(item.providerOrderId==null){
   var existing=gateway.get("orders?receipt="+item.id+"&count=100").getAsJsonArray("items");
   JsonObject order=null;for(var candidate:existing){var o=candidate.getAsJsonObject();if(o.has("receipt")&&!o.get("receipt").isJsonNull()&&o.get("receipt").getAsString().equals(item.id.toString())){order=o;break;}}
   if(order==null)order=gateway.post("orders",Map.of("amount",amount,"currency","INR","receipt",item.id.toString(),"partial_payment",false));
   if(order.get("amount").getAsLong()!=amount||!order.get("currency").getAsString().equals("INR"))throw new IllegalArgumentException("Provider order does not match settlement");
   item.providerOrderId=order.get("id").getAsString();
  }
  item.status="PENDING";settlements.saveAndFlush(item);
  return new Checkout(item.id.toString(),item.providerOrderId,keyId,amount,"INR");
 }
 public Settlement verify(String orderId,String paymentId,String signature){
  RazorpayGateway.id(orderId);RazorpayGateway.id(paymentId);
  Settlement found=settlements.findByProviderOrderId(orderId).orElseThrow();Settlement item=locked(found.id,true);
  if(!Signatures.valid(gateway.secret(),(item.providerOrderId+"|"+paymentId).getBytes(StandardCharsets.UTF_8),signature))throw new IllegalArgumentException("Payment signature verification failed. Balance unchanged.");
  return complete(item,paymentId);
 }
 private Settlement complete(Settlement item,String paymentId){
  if(item.status.equals("SUCCESS")&&paymentId.equals(item.providerPaymentId))return item;
  var payment=gateway.get("payments/"+RazorpayGateway.id(paymentId));
  long minor=item.amount.movePointRight(2).longValueExact();
  if(!payment.get("order_id").getAsString().equals(item.providerOrderId)||payment.get("amount").getAsLong()!=minor||!payment.get("currency").getAsString().equals("INR"))throw new IllegalArgumentException("Payment does not match the stored settlement.");
  if(payment.get("status").getAsString().equals("authorized"))payment=gateway.post("payments/"+paymentId+"/capture",Map.of("amount",minor,"currency","INR"));
  if(!payment.get("status").getAsString().equals("captured"))throw new IllegalArgumentException("Payment is not captured. Retry verification shortly.");
  if(payment.has("amount_refunded")&&payment.get("amount_refunded").getAsLong()>0){item.status="FAILED";return item;}
  var existing=payments.findByProviderPaymentId(paymentId);
  if(existing.isPresent()&&existing.get().status.equals("REFUNDED"))return item;
  SharedGroup group=groupRepository.findById(item.groupId).orElseThrow();
  boolean apply=!item.status.equals("SUCCESS");
  if(apply)try{new BalanceEngine().validateSettlement(groups.balances(group,expenses.findByGroupIdOrderByDateDesc(group.id)),new BalanceEngine.Transfer(item.payerUserId,item.receiverUserId,item.amount));}catch(IllegalArgumentException error){apply=false;}
  PaymentAttempt attempt=existing.orElseGet(PaymentAttempt::new);attempt.settlementId=item.id;attempt.providerOrderId=item.providerOrderId;attempt.providerPaymentId=paymentId;
  if(!apply){
   if(!payment.has("amount_refunded")||payment.get("amount_refunded").getAsLong()<minor)gateway.post("payments/"+paymentId+"/refund",Map.of("amount",minor,"notes",Map.of("reason","Settlement no longer payable")));
   attempt.status="REFUNDED";payments.save(attempt);
   if(!item.status.equals("SUCCESS"))item.status="FAILED";
   return item;
  }
  attempt.status="SUCCESS";payments.saveAndFlush(attempt);
  item.status="SUCCESS";item.providerPaymentId=paymentId;item.completedAt=Instant.now();settlements.saveAndFlush(item);
  activity(item,"SETTLEMENT_COMPLETED","Razorpay Test settlement completed — INR "+item.amount);
  return item;
 }
 public Settlement cancel(UUID id){
  Settlement item=locked(id,true);if(item.status.equals("SUCCESS"))return item;
  if(item.providerOrderId!=null){
   for(var entry:gateway.get("orders/"+RazorpayGateway.id(item.providerOrderId)+"/payments").getAsJsonArray("items")){
    var p=entry.getAsJsonObject();String state=p.get("status").getAsString();if(state.equals("captured")||state.equals("authorized"))return complete(item,p.get("id").getAsString());
   }
  }
  item.status="CANCELLED";activity(item,"SETTLEMENT_CANCELLED","Test checkout cancelled; no balance change.");return item;
 }
 public void webhook(byte[] body,String signature,String eventId){
  if(!Signatures.valid(gateway.webhookSecret(),body,signature))throw new AccessDeniedException("Invalid webhook signature");
  if(body.length>1000000)throw new IllegalArgumentException("Webhook too large");
  JsonObject root=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();
  String type=root.get("event").getAsString();if(!Set.of("payment.captured","payment.failed","order.paid").contains(type))return;
  JsonObject payload=root.getAsJsonObject("payload"),payment=null;
  if(payload.has("payment"))payment=payload.getAsJsonObject("payment").getAsJsonObject("entity");
  if(payment==null&&payload.has("order")){
   String orderId=payload.getAsJsonObject("order").getAsJsonObject("entity").get("id").getAsString();
   for(var p:gateway.get("orders/"+RazorpayGateway.id(orderId)+"/payments").getAsJsonArray("items"))if(p.getAsJsonObject().get("status").getAsString().equals("captured")){payment=p.getAsJsonObject();break;}
  }
  if(payment==null)return;
  String orderId=payment.get("order_id").getAsString(),paymentId=payment.get("id").getAsString();
  var found=settlements.findByProviderOrderId(orderId);if(found.isEmpty())return;
  Settlement item=locked(found.get().id,false);
  String dedupe=Signatures.hmac(gateway.webhookSecret(),body);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhooks WHERE event_id=?",Integer.class,dedupe)>0)return;
  if(type.equals("payment.failed")){
   var attempt=payments.findByProviderPaymentId(paymentId).orElseGet(PaymentAttempt::new);attempt.settlementId=item.id;attempt.providerOrderId=orderId;attempt.providerPaymentId=paymentId;
   if(!"SUCCESS".equals(attempt.status))attempt.status="FAILED";payments.save(attempt);
  }else complete(item,paymentId);
  jdbc.update("INSERT INTO payment_webhooks(event_id,created_at) VALUES(?,?)",dedupe,java.sql.Timestamp.from(Instant.now()));
 }
 private void activity(Settlement item,String type,String description){
  groupRepository.findById(item.groupId).orElseThrow().updatedAt=Instant.now();
  Activity event=new Activity();event.groupId=item.groupId;event.userId=item.payerUserId;event.type=type;event.description=description;activities.save(event);
  events.publishEvent(new com.splitpay.notification.GroupChanged(item.groupId,item.payerUserId,type,description,event.id));
 }
}
