package com.example.splitpay.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitpay.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.http.*
import javax.inject.Inject

data class CheckoutInfo(val settlementId:String,val orderId:String,val approvalUrl:String,val amount:String,val currency:String,val inrPerUsd:String)
data class Verification(val orderId:String)
interface PaymentApi {
 @POST("api/paypal/order") suspend fun order(@Body input:Map<String,String>):CheckoutInfo
 @POST("api/paypal/verify") suspend fun verify(@Body input:Verification):Settlement
 @POST("api/paypal/{id}/cancel") suspend fun cancel(@Path("id")id:String):Settlement
}
@HiltViewModel class PaymentViewModel @Inject constructor(private val api:PaymentApi,private val groups:GroupApi,private val saved:SavedStateHandle):ViewModel() {
 private val gson=com.google.gson.Gson()
 val status=MutableStateFlow(saved.get<String>("paypalStatus")?.let { if(it in setOf("PREPARING","VERIFYING")) "RETRY" else it } ?: "IDLE")
 val message=MutableStateFlow("")
 val checkout=MutableStateFlow<CheckoutInfo?>(null)
 val quote=MutableStateFlow(saved.get<String>("paypalQuote")?.let{gson.fromJson(it,CheckoutInfo::class.java)})
 private var settlementId:String?=saved["paypalSettlementId"]
 private var operation:kotlinx.coroutines.Job?=null
 fun prepare(requestId:String){
  if(saved.get<String>("paypalRequestId")!=requestId){
   operation?.cancel()
   saved["paypalRequestId"]=requestId;saved["paypalActiveRequest"]=null;settlementId=null;saved["paypalSettlementId"]=null
   quote.value=null;saved["paypalQuote"]=null;checkout.value=null;setStatus("IDLE");message.value=""
  }
 }
 fun start(groupId:String,receiver:String,amount:String,requestId:String)=run {
  val newRequest=if(status.value in setOf("CANCELLED","FAILED")) java.util.UUID.randomUUID().toString() else (saved.get<String>("paypalActiveRequest") ?: requestId)
  if(status.value in setOf("CANCELLED","FAILED")){quote.value=null;saved["paypalQuote"]=null}
  saved["paypalActiveRequest"]=newRequest
  setStatus("PREPARING")
  val settlement=groups.settle(groupId,SettlementInput(newRequest,receiver,amount,"PAYPAL_SANDBOX"))
  settlementId=settlement.id;saved["paypalSettlementId"]=settlement.id
  if(settlement.status in setOf("SUCCESS","CANCELLED","FAILED")){applyResult(settlement);return@run}
  loadQuote(settlement.id)
 }
 private suspend fun loadQuote(id:String){
  val info=api.order(mapOf("settlementId" to id))
  quote.value=info;saved["paypalQuote"]=gson.toJson(info);setStatus("READY")
 }
 fun openCheckout(){quote.value?.let { info ->
  if(info.approvalUrl.isBlank()){verify();return}
  checkout.value=info;setStatus("CHECKOUT")
 }}
 fun consumeCheckout(){checkout.value=null}
 fun browserFailed(){setStatus("RETRY");message.value="Could not open a browser. Retry checkout or check payment status."}
 fun verify()=run {
  val info=quote.value ?: throw IllegalArgumentException("Load the checkout before checking payment status.")
  setStatus("VERIFYING");applyResult(api.verify(Verification(info.orderId)))
 }
 fun cancel()=run {
  val id=settlementId ?: throw IllegalArgumentException("No checkout to cancel.")
  setStatus("VERIFYING");applyResult(api.cancel(id))
 }
 private fun applyResult(result:Settlement){
  setStatus(when(result.status){"SUCCESS","CANCELLED","FAILED"->result.status;else->"CHECKOUT"})
  message.value=when(result.status){
   "SUCCESS"->"PayPal Sandbox capture verified. The INR balance has been updated."
   "CANCELLED"->"Checkout cancelled. No payment is confirmed."
   "FAILED"->"This checkout could not be applied. Check the sandbox transaction for its payment or refund status."
   else->"Payment is not completed yet. Approve it in PayPal, then check again. Pending captures do not change your balance."
  }
 }
 private fun setStatus(value:String){status.value=value;saved["paypalStatus"]=value}
 private fun run(block:suspend ()->Unit){
  if(operation?.isActive==true)return
  operation=viewModelScope.launch{
   try{block()}catch(e:CancellationException){throw e}catch(e:Exception){setStatus("RETRY");message.value=e.userMessage()}
  }
 }
}
