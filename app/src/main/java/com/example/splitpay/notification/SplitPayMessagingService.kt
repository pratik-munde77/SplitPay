package com.example.splitpay.notification
import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.splitpay.MainActivity
import com.example.splitpay.data.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
@AndroidEntryPoint class SplitPayMessagingService:FirebaseMessagingService() {
 @Inject lateinit var session:SessionRepository
 @Inject lateinit var api:PersonalApi
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 override fun onNewToken(token:String){scope.launch{if(session.account.value.isNotEmpty())try{api.registerDevice(mapOf("token" to token))}catch(_:Exception){/* Retry on the next foreground login. */}}}
 override fun onMessageReceived(message:RemoteMessage){
  if(message.data["userId"]!=session.serverUserId||session.serverUserId.isEmpty())return
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("expenses","Expense updates",NotificationManager.IMPORTANCE_DEFAULT))
  val intent=Intent(this,MainActivity::class.java).putExtra("groupId",message.data["groupId"]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  val id=(message.data["notificationId"] ?: message.messageId ?: "expense").hashCode()
  val pending=PendingIntent.getActivity(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  manager.notify(id,NotificationCompat.Builder(this,"expenses").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SplitPay").setContentText(message.data["body"]).setStyle(NotificationCompat.BigTextStyle().bigText(message.data["body"])).setContentIntent(pending).setAutoCancel(true).build())
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
