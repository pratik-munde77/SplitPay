package com.example.splitpay

import androidx.lifecycle.SavedStateHandle
import com.example.splitpay.data.*
import com.example.splitpay.payment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
 private val requests=mutableListOf<SettlementInput>()
 private val info=CheckoutInfo("settlement","ORDER123","https://www.sandbox.paypal.com/checkoutnow?token=ORDER123","1.00","USD","100")
 private var verifiedStatus="PENDING"
 private var orderCalls=0
 private var failOrder=false
 private var verificationCalls=0
 private val api=object:PaymentApi {
  override suspend fun order(input:Map<String,String>):CheckoutInfo {orderCalls++;if(failOrder)throw IllegalArgumentException("Temporary failure");return info}
  override suspend fun verify(input:Verification):Settlement {assertEquals("ORDER123",input.orderId);verificationCalls++;return settlement(verifiedStatus)}
  override suspend fun cancel(id:String):Settlement=settlement("CANCELLED")
 }
 private val unusedGroups=java.lang.reflect.Proxy.newProxyInstance(GroupApi::class.java.classLoader,arrayOf(GroupApi::class.java)){_,_,_->throw UnsupportedOperationException()} as GroupApi
 private val groups=object:GroupApi by unusedGroups {
  override suspend fun settle(id:String,input:SettlementInput):Settlement {requests.add(input);return settlement("CREATED")}
 }
 private fun settlement(status:String)=Settlement("settlement","group","payer","receiver","100",status,"PAYPAL_SANDBOX")
 @Before fun setup(){Dispatchers.setMain(UnconfinedTestDispatcher())}
 @After fun cleanup(){Dispatchers.resetMain()}
 private fun start(model:PaymentViewModel){model.prepare("request");model.start("group","receiver","100","request")}
 @Test fun quoteMustBeReviewedBeforeOpeningBrowserAndPendingNeverMeansSuccess(){
  val model=PaymentViewModel(api,groups,SavedStateHandle());start(model)
  assertEquals("PAYPAL_SANDBOX",requests.single().method);assertEquals("READY",model.status.value);assertNull(model.checkout.value)
  model.openCheckout();assertEquals(info,model.checkout.value);model.consumeCheckout();model.verify()
  assertEquals("CHECKOUT",model.status.value)
  verifiedStatus="SUCCESS";model.verify();assertEquals("SUCCESS",model.status.value)
 }
 @Test fun savedCheckoutCanBeVerifiedAfterRecreation(){
  val saved=SavedStateHandle();val model=PaymentViewModel(api,groups,saved);start(model);model.openCheckout();model.consumeCheckout()
  val restored=PaymentViewModel(api,groups,saved);restored.prepare("request")
  assertEquals(info,restored.quote.value);assertNull(restored.checkout.value)
  verifiedStatus="SUCCESS";restored.verify();assertEquals("SUCCESS",restored.status.value);assertEquals(1,orderCalls)
 }
 @Test fun failedOrderRetryUsesSameRequestAndCancellationStartsNewRequest(){
  val model=PaymentViewModel(api,groups,SavedStateHandle());failOrder=true;start(model)
  assertEquals("RETRY",model.status.value)
  failOrder=false;model.start("group","receiver","100","request")
  assertEquals(requests[0].requestId,requests[1].requestId)
  model.cancel();assertEquals("CANCELLED",model.status.value)
  model.start("group","receiver","100","request")
  assertNotEquals(requests[1].requestId,requests[2].requestId)
 }
 @Test fun navigationToAnotherSettlementClearsPreviousQuoteAndRequest(){
  val model=PaymentViewModel(api,groups,SavedStateHandle());start(model);model.prepare("another-request")
  assertNull(model.quote.value);assertEquals("IDLE",model.status.value)
  model.start("group","receiver","100","another-request");assertEquals("another-request",requests.last().requestId)
 }
 @Test fun interruptedVerificationRestoresAsRetry(){
  val model=PaymentViewModel(api,groups,SavedStateHandle(mapOf("paypalStatus" to "VERIFYING")))
  assertEquals("RETRY",model.status.value)
 }
}
