package com.splitpay.payment;

import com.google.gson.*;
import com.splitpay.settlement.*;
import com.splitpay.group.*;
import com.splitpay.expense.ExpenseRepository;
import com.splitpay.user.CurrentUser;
import com.splitpay.activity.*;
import com.splitpay.split.BalanceEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.time.Instant;

@Service @Transactional
public class PayPalService {
 private final PayPalGateway gateway;
 private final SettlementRepository settlements;
 private final GroupRepository groupRepository;
 private final GroupService groups;
 private final ExpenseRepository expenses;
 private final CurrentUser current;
 private final PaymentRepository payments;
 private final ActivityRepository activities;
 private final org.springframework.context.ApplicationEventPublisher events;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 public PayPalService(PayPalGateway gateway,SettlementRepository settlements,GroupRepository groupRepository,GroupService groups,ExpenseRepository expenses,CurrentUser current,PaymentRepository payments,ActivityRepository activities,org.springframework.context.ApplicationEventPublisher events){
  this.gateway=gateway;this.settlements=settlements;this.groupRepository=groupRepository;this.groups=groups;this.expenses=expenses;this.current=current;this.payments=payments;this.activities=activities;this.events=events;
 }
 public record Checkout(String settlementId,String orderId,String approvalUrl,String amount,String currency,String inrPerUsd){}
 private Settlement locked(UUID id,boolean authorize){
  Settlement item=settlements.findById(id).orElseThrow();groupRepository.locked(item.groupId).orElseThrow();em.refresh(item);
  if(authorize&&!item.payerUserId.equals(current.get().id))throw new AccessDeniedException("Only the payer can check out");
  if(!"PAYPAL_SANDBOX".equals(item.method))throw new IllegalArgumentException("This is not a PayPal Sandbox settlement");
  return item;
 }
 private void validateDebt(Settlement item){
  var group=groupRepository.findById(item.groupId).orElseThrow();
  new BalanceEngine().validateSettlement(groups.balances(group,expenses.findByGroupIdOrderByDateDesc(item.groupId)),new BalanceEngine.Transfer(item.payerUserId,item.receiverUserId,item.amount));
 }
 public Checkout order(UUID id){
  Settlement item=locked(id,true);
  if(Set.of("SUCCESS","FAILED","CANCELLED").contains(item.status))throw new IllegalArgumentException("This checkout is closed. Check its status or create a new settlement.");
  validateDebt(item);
  JsonObject order;
  if(item.providerOrderId==null){
   // Stay inside PayPal's default six-hour request-id retention window after an uncertain response.
   if(item.createdAt.isBefore(Instant.now().minusSeconds(5*3600)))throw new IllegalArgumentException("Checkout expired. Cancel it and start a new settlement.");
   var unit=Map.of("reference_id",item.id.toString(),"custom_id",item.id.toString(),"description","SplitPay sandbox demonstration; INR ledger "+item.amount,
    "amount",Map.of("currency_code",item.checkoutCurrency,"value",item.checkoutAmount.toPlainString()));
   var context=Map.of("return_url",gateway.returnUrl(),"cancel_url",gateway.returnUrl(),"shipping_preference","NO_SHIPPING","user_action","PAY_NOW","brand_name","SplitPay Sandbox");
   order=gateway.post("/v2/checkout/orders",Map.of("intent","CAPTURE","purchase_units",List.of(unit),"payment_source",Map.of("paypal",Map.of("experience_context",context))),item.id.toString());
   item.providerOrderId=PayPalGateway.id(order.get("id").getAsString());
  }else order=gateway.get("/v2/checkout/orders/"+PayPalGateway.id(item.providerOrderId));
  validateOrder(item,order);
  String approval="";
  if(order.has("links"))for(var entry:order.getAsJsonArray("links")){
   var link=entry.getAsJsonObject();
   if(Set.of("approve","payer-action").contains(link.get("rel").getAsString()))approval=link.get("href").getAsString();
  }
  if(!approval.isEmpty()){
   var uri=java.net.URI.create(approval);
   if(!"https".equals(uri.getScheme())||!"www.sandbox.paypal.com".equals(uri.getHost()))throw new IllegalArgumentException("Invalid sandbox approval URL");
  }
  item.status="PENDING";settlements.saveAndFlush(item);
  return new Checkout(item.id.toString(),item.providerOrderId,approval,item.checkoutAmount.toPlainString(),item.checkoutCurrency,item.checkoutRate.toPlainString());
 }
 private JsonObject validateOrder(Settlement item,JsonObject order){
  if(!item.providerOrderId.equals(order.get("id").getAsString())||!"CAPTURE".equals(order.get("intent").getAsString()))throw new IllegalArgumentException("PayPal order mismatch");
  var units=order.getAsJsonArray("purchase_units");
  if(units.size()!=1)throw new IllegalArgumentException("Unexpected PayPal purchase units");
  var unit=units.get(0).getAsJsonObject();
  if(!item.id.toString().equals(unit.get("custom_id").getAsString()))throw new IllegalArgumentException("PayPal settlement mismatch");
  validateAmount(item,unit.getAsJsonObject("amount"));return unit;
 }
 private void validateAmount(Settlement item,JsonObject amount){
  if(!item.checkoutCurrency.equals(amount.get("currency_code").getAsString())||item.checkoutAmount.compareTo(amount.get("value").getAsBigDecimal())!=0)
   throw new IllegalArgumentException("PayPal amount or currency does not match the stored quote. Balance unchanged.");
 }
 public Settlement verify(String orderId){
  Settlement found=settlements.findByProviderOrderId(PayPalGateway.id(orderId)).orElseThrow();
  return reconcile(locked(found.id,true),true);
 }
 private Settlement reconcile(Settlement item,boolean captureApproved){
  if("SUCCESS".equals(item.status))return item;
  JsonObject order=gateway.get("/v2/checkout/orders/"+PayPalGateway.id(item.providerOrderId));
  validateOrder(item,order);
  if("APPROVED".equals(order.get("status").getAsString())&&captureApproved&&!Set.of("CANCELLED","FAILED").contains(item.status)){
   validateDebt(item);
   gateway.post("/v2/checkout/orders/"+item.providerOrderId+"/capture",Map.of(),"C"+item.id);
   order=gateway.get("/v2/checkout/orders/"+item.providerOrderId);
  }
  JsonObject unit=validateOrder(item,order);
  if(!unit.has("payments")||!unit.getAsJsonObject("payments").has("captures"))return item;
  var captures=unit.getAsJsonObject("payments").getAsJsonArray("captures");
  if(captures.size()!=1)throw new IllegalArgumentException("Unexpected PayPal captures; manual review required");
  var capture=captures.get(0).getAsJsonObject();validateAmount(item,capture.getAsJsonObject("amount"));
  String state=capture.get("status").getAsString();
  if(Set.of("REFUNDED","PARTIALLY_REFUNDED","DECLINED","FAILED").contains(state)){item.status="FAILED";return item;}
  if(!"COMPLETED".equals(state)){
   item.status="PENDING";return item;
  }
  String paymentId=PayPalGateway.id(capture.get("id").getAsString());
  var previous=payments.findByProviderPaymentId(paymentId);
  if(previous.isPresent()){
   if(!previous.get().settlementId.equals(item.id))throw new IllegalArgumentException("Capture already belongs to another settlement");
   if("REFUNDED".equals(previous.get().status)){item.status="FAILED";return item;}
  }
  var attempt=previous.orElseGet(PaymentAttempt::new);attempt.settlementId=item.id;attempt.providerOrderId=item.providerOrderId;attempt.providerPaymentId=paymentId;
  boolean apply=true;try{validateDebt(item);}catch(IllegalArgumentException error){apply=false;}
  if(!apply){
   var refund=gateway.post("/v2/payments/captures/"+paymentId+"/refund",Map.of(),"R"+item.id);
   if(!"COMPLETED".equals(refund.get("status").getAsString()))throw new IllegalArgumentException("Refund pending. Retry verification; balance unchanged.");
   attempt.status="REFUNDED";payments.saveAndFlush(attempt);item.status="FAILED";return item;
  }
  attempt.status="SUCCESS";payments.saveAndFlush(attempt);
  item.status="SUCCESS";item.providerPaymentId=paymentId;item.completedAt=Instant.now();settlements.saveAndFlush(item);
  groupRepository.findById(item.groupId).orElseThrow().updatedAt=Instant.now();
  var activity=new Activity();activity.groupId=item.groupId;activity.userId=item.payerUserId;activity.type="SETTLEMENT_COMPLETED";
  activity.description="PayPal Sandbox settlement verified: INR "+item.amount+" (test USD "+item.checkoutAmount+")";activities.save(activity);
  events.publishEvent(new com.splitpay.notification.GroupChanged(item.groupId,item.payerUserId,activity.type,activity.description,activity.id));
  return item;
 }
 public Settlement cancel(UUID id){
  Settlement item=locked(id,true);
  if(item.providerOrderId!=null){
   reconcile(item,false);
   if(Set.of("SUCCESS","FAILED").contains(item.status))return item;
   // A pending capture can still settle: do not release its debt reservation.
   var order=gateway.get("/v2/checkout/orders/"+item.providerOrderId);
   var unit=validateOrder(item,order);
   if(unit.has("payments")&&unit.getAsJsonObject("payments").has("captures"))return item;
  }
  item.status="CANCELLED";return item;
 }
 public void webhook(JsonObject event,org.springframework.http.HttpHeaders headers){
  gateway.verifyWebhook(event,headers);
  String type=event.get("event_type").getAsString();String orderId=null;
  var resource=event.getAsJsonObject("resource");
  if("CHECKOUT.ORDER.APPROVED".equals(type))orderId=resource.get("id").getAsString();
  else if("PAYMENT.CAPTURE.COMPLETED".equals(type))orderId=resource.getAsJsonObject("supplementary_data").getAsJsonObject("related_ids").get("order_id").getAsString();
  if(orderId==null)return;
  var item=settlements.findByProviderOrderId(PayPalGateway.id(orderId));
  if(item.isPresent())reconcile(locked(item.get().id,false),true);
 }
}
