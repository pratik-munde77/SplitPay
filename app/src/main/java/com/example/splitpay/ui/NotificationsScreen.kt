package com.example.splitpay.ui
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
@Composable fun NotificationsScreen(model:MainViewModel,openGroup:(String)->Unit) {
 val items by model.notifications.collectAsStateWithLifecycle()
 val loading by model.notificationsLoading.collectAsStateWithLifecycle()
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
 LaunchedEffect(Unit){model.refreshNotifications()}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Text("Notifications",style=MaterialTheme.typography.headlineMedium)}
  item{OutlinedButton(onClick={model.refreshNotifications()}){Text("Refresh")}}
  if(Build.VERSION.SDK_INT>=33)item{OutlinedButton(onClick={permission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Enable push notifications")}}
  if(loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
  if(items.isEmpty()&&!loading)item{EmptyState("You're all caught up","Group changes and budget alerts will appear here.")}
  items(items,key={it.id}){notification->Card(onClick={model.readNotification(notification.id);notification.groupId?.let(openGroup)},modifier=Modifier.fillMaxWidth()){
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text(notification.message);Text(notification.createdAt.take(16).replace('T',' '),style=MaterialTheme.typography.bodySmall)
    if(notification.readAt==null)Text("Unread",color=MaterialTheme.colorScheme.primary)
   }
  }}
 }
}
