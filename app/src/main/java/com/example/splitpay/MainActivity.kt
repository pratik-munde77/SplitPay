package com.example.splitpay

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.splitpay.ui.MainViewModel
import com.example.splitpay.ui.SplitPayApp
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val auth: com.example.splitpay.ui.AuthViewModel by viewModels()
    private val groups: com.example.splitpay.ui.GroupViewModel by viewModels()
    private val receipt: com.example.splitpay.receipt.ReceiptViewModel by viewModels()
    private val payment: com.example.splitpay.payment.PaymentViewModel by viewModels()
    private val notificationGroup=kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationGroup.value=intent.getStringExtra("groupId")?.takeIf{it.isNotBlank()}
        enableEdgeToEdge()
        setContent {
            val targetGroup by notificationGroup.collectAsStateWithLifecycle()
            val checkout by payment.checkout.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(checkout) {checkout?.let{info->payment.consumeCheckout();try{
                val uri=android.net.Uri.parse(info.approvalUrl)
                require(uri.scheme=="https" && uri.host=="www.sandbox.paypal.com")
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,uri))
            }catch(_:Exception){payment.browserFailed()}}}
            com.example.splitpay.ui.AuthGate(auth) { SplitPayApp(model, auth, groups,receipt,payment,targetGroup){notificationGroup.value=null} }
        }
    }
    override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);notificationGroup.value=intent.getStringExtra("groupId")?.takeIf{it.isNotBlank()}}
}
