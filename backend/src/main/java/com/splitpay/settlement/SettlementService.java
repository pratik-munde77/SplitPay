package com.splitpay.settlement;
import com.splitpay.group.*;
import com.splitpay.user.*;
import com.splitpay.split.BalanceEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional
public class SettlementService {
 private final GroupService groups; private final SettlementRepository settlements; private final CurrentUser current;
 private final com.splitpay.payment.RazorpayGateway gateway;
 @org.springframework.beans.factory.annotation.Autowired private com.splitpay.payment.PayPalGateway paypal;
 public SettlementService(GroupService groups,SettlementRepository settlements,CurrentUser current,com.splitpay.payment.RazorpayGateway gateway){this.groups=groups;this.settlements=settlements;this.current=current;this.gateway=gateway;}
 public record Input(@NotNull UUID requestId,@NotNull UUID receiverUserId,@NotNull @DecimalMin("0.01") @Digits(integer=9,fraction=2) BigDecimal amount,@Pattern(regexp="CASH|MANUAL|RAZORPAY_TEST|PAYPAL_SANDBOX") @NotNull String method) {}
 public Settlement create(UUID groupId,Input input) {
  SharedGroup group=groups.access(groupId,true); UUID payer=current.get().id;
  if(input.method().equals("RAZORPAY_TEST"))gateway.keyId();
  boolean online=Set.of("RAZORPAY_TEST","PAYPAL_SANDBOX").contains(input.method());
  if(input.method().equals("PAYPAL_SANDBOX"))paypal.configured();
  var previous=settlements.findById(input.requestId());
  if(previous.isPresent()) {
   Settlement item=previous.get();
   if(!item.groupId.equals(groupId)||!item.payerUserId.equals(payer)||!item.receiverUserId.equals(input.receiverUserId())||item.amount.compareTo(input.amount())!=0||!item.method.equals(input.method())) throw new IllegalArgumentException("This request ID was already used for another settlement.");
   return item;
  }
  Map<UUID,BigDecimal> available=new HashMap<>(groups.details(groupId).balances());
  if(online)for(Settlement pending:settlements.findByGroupIdOrderByCreatedAtDesc(groupId))if(pending.method.equals(input.method())&&Set.of("CREATED","PENDING").contains(pending.status)&&pending.payerUserId.equals(payer)&&pending.receiverUserId.equals(input.receiverUserId())&&pending.amount.compareTo(input.amount())==0)return pending;
  for(Settlement pending:settlements.findByGroupIdOrderByCreatedAtDesc(groupId)) if(Set.of("CREATED","PENDING").contains(pending.status)) {
   available.compute(pending.payerUserId,(id,value)->value.add(pending.amount));
   available.compute(pending.receiverUserId,(id,value)->value.subtract(pending.amount));
  }
  new BalanceEngine().validateSettlement(available,new BalanceEngine.Transfer(payer,input.receiverUserId(),input.amount()));
  Settlement item=new Settlement();item.id=input.requestId();item.groupId=groupId;item.payerUserId=payer;item.receiverUserId=input.receiverUserId();item.amount=input.amount().setScale(2);item.method=input.method();
  item.status=online?"CREATED":"SUCCESS";
  if(item.status.equals("SUCCESS")) item.completedAt=Instant.now(); else item.provider=input.method();
  if(input.method().equals("PAYPAL_SANDBOX")){
   item.checkoutRate=paypal.rate().setScale(6,java.math.RoundingMode.HALF_UP);
   item.checkoutCurrency="USD";
   item.checkoutAmount=item.amount.divide(item.checkoutRate,2,java.math.RoundingMode.HALF_UP);
   if(item.checkoutAmount.signum()<=0)throw new IllegalArgumentException("Amount is too small for USD sandbox checkout. Use a manual settlement.");
  }
  settlements.saveAndFlush(item);groups.activity(group,item.status.equals("SUCCESS")?"SETTLEMENT_COMPLETED":"SETTLEMENT_CREATED",current.get().name+(item.status.equals("SUCCESS")?" settled INR ":" started settlement INR ")+item.amount);
  return item;
 }
 public List<Settlement> list(UUID groupId){groups.access(groupId,false);return settlements.findByGroupIdOrderByCreatedAtDesc(groupId);}
}
