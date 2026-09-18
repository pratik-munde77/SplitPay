package com.example.splitpay.payment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitpay.ui.cash

@Composable fun PaymentScreen(groupId:String,receiver:String,receiverName:String,amount:String,requestId:String,model:PaymentViewModel,done:()->Unit){
 val status by model.status.collectAsStateWithLifecycle()
 val message by model.message.collectAsStateWithLifecycle()
 val quote by model.quote.collectAsStateWithLifecycle()
 LaunchedEffect(requestId){model.prepare(requestId)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Text(if(status=="SUCCESS")"Sandbox payment successful" else "Settle with $receiverName",style=MaterialTheme.typography.headlineLarge)
  Text(cash(amount),style=MaterialTheme.typography.displaySmall)
  Text("PayPal Sandbox - no real money is transferred.")
  Text("The sandbox business account receives test funds. This demonstrates a settlement; it does not pay your group member.")
  quote?.let { Text("PayPal checkout: ${it.currency} ${it.amount}",style=MaterialTheme.typography.titleLarge);Text("Demo conversion: INR ${it.inrPerUsd} per USD. This is a configured test rate, not a live exchange rate. Your ledger remains in INR.") }
  if(message.isNotBlank())Text(message)
  when(status){
   "IDLE"->Button(onClick={model.start(groupId,receiver,amount,requestId)}){Text("Prepare PayPal Sandbox checkout")}
   "PREPARING","VERIFYING"->{LinearProgressIndicator(Modifier.fillMaxWidth());Text("Checking with the server...")}
   "READY","CHECKOUT","RETRY"->{
    if(quote!=null){
     if(quote!!.approvalUrl.isNotBlank())Button(onClick={model.openCheckout()}){Text("Open PayPal Sandbox")}
     Text("After approving in your browser, switch back here and check payment status.")
     Button(onClick={model.verify()}){Text("Check payment status")}
    }
    if(status=="RETRY")OutlinedButton(onClick={model.start(groupId,receiver,amount,requestId)}){Text("Reload checkout")}
    OutlinedButton(onClick={model.cancel()}){Text("Cancel / check unfinished checkout")}
   }
   "SUCCESS"->Button(onClick=done){Text("Return to group")}
   else->Button(onClick={model.start(groupId,receiver,amount,requestId)}){Text("Start a new sandbox checkout")}
  }
  if(status !in setOf("SUCCESS","PREPARING","VERIFYING"))TextButton(onClick=done){Text("Return to group")}
 }
}
