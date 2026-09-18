package com.example.splitpay.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitpay.data.SettlementInput
import java.util.UUID
@Composable fun SettlementScreen(groupId:String,receiver:String,suggested:String,model:GroupViewModel,paypal:(String,String)->Unit,done:()->Unit) {
 val details by model.detail.collectAsStateWithLifecycle()
 val busy by model.busy.collectAsStateWithLifecycle()
 var amount by rememberSaveable {mutableStateOf(suggested)}
 var method by rememberSaveable {mutableStateOf("MANUAL")}
 val requestId=rememberSaveable {UUID.randomUUID().toString()}
 var confirm by remember {mutableStateOf(false)}
 Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  Text("Settle up",style=MaterialTheme.typography.headlineLarge)
  Text("Paying "+(details?.members?.find{it.id==receiver}?.name ?: "group member"))
  GroupStatus(model){model.open(groupId)}
  OutlinedTextField(amount,{amount=it},label={Text("Amount (INR)")},modifier=Modifier.fillMaxWidth())
  ChoiceField("Method",method,mapOf("MANUAL" to "Paid outside SplitPay","CASH" to "Cash","PAYPAL_SANDBOX" to "PayPal Sandbox")){method=it}
  Text(if(method=="PAYPAL_SANDBOX")"PayPal Sandbox — No real payment" else "This records a payment you have already made. It does not move money.")
  Button(onClick={if(method=="PAYPAL_SANDBOX")paypal(amount,requestId) else confirm=true},enabled=!busy && amount.toBigDecimalOrNull()?.signum()==1,modifier=Modifier.fillMaxWidth()){Text(if(method=="PAYPAL_SANDBOX")"Review test payment" else "Mark as paid")}
 }
 if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Confirm payment received?")},text={Text("Record "+cash(amount)+" as paid to this member? Only confirm after paying them.")},confirmButton={TextButton(onClick={confirm=false;model.settle(groupId,SettlementInput(requestId,receiver,amount,method)){done()}}){Text("Confirm payment")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}
