package com.example.splitpay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitpay.BuildConfig

@Composable fun AuthGate(auth: AuthViewModel, content: @Composable () -> Unit) {
    val account by auth.account.collectAsStateWithLifecycle()
    if (account.isNotEmpty()) { key(account){content()}; return }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var register by rememberSaveable { mutableStateOf(false) }
            var name by rememberSaveable { mutableStateOf("") }
            var email by rememberSaveable { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            val busy by auth.busy.collectAsStateWithLifecycle()
            val error by auth.error.collectAsStateWithLifecycle()
            Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(36.dp)); Text("SplitPay", style = MaterialTheme.typography.headlineLarge)
                Text("Share expenses. Keep friendships simple.", style = MaterialTheme.typography.titleMedium)
                Text(if (register) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineSmall)
                if (register) OutlinedTextField(name, { name=it }, label={Text("Name")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                OutlinedTextField(email, { email=it }, label={Text("Email")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                OutlinedTextField(password, { password=it }, label={Text("Password")}, visualTransformation=PasswordVisualTransformation(), modifier=Modifier.fillMaxWidth(), singleLine=true)
                error?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                Button(onClick={auth.login(email,password,name,register)}, enabled=!busy, modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Please wait…" else if(register) "Register" else "Sign in") }
                TextButton(onClick={register=!register}, enabled=!busy) { Text(if(register) "Already registered? Sign in" else "Create an account") }
                if(BuildConfig.DEBUG && BuildConfig.DEMO_TOKEN.isNotEmpty()) OutlinedButton(onClick={auth.demo()},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Explore local demo as Pratik") }
            }
        }
    }
}

@Composable fun ProfileScreen(auth: AuthViewModel) {
    val profile by auth.profile.collectAsStateWithLifecycle()
    val busy by auth.busy.collectAsStateWithLifecycle()
    val error by auth.error.collectAsStateWithLifecycle()
    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var phone by remember(profile) { mutableStateOf(profile?.phone ?: "") }
    var upi by remember(profile) { mutableStateOf(profile?.upiId ?: "") }
    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Your profile", style=MaterialTheme.typography.headlineMedium)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,color=MaterialTheme.colorScheme.error); OutlinedButton(onClick={auth.refresh()}) { Text("Retry") } }
        profile?.let { user ->
            if(user.photoUrl.isNotBlank())coil.compose.AsyncImage(model=user.photoUrl,contentDescription="Profile picture",modifier=Modifier.size(72.dp))
            else Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.primaryContainer){Text(user.name.take(1).uppercase(),modifier=Modifier.padding(22.dp),style=MaterialTheme.typography.headlineSmall)}
            Text(user.email)
            OutlinedTextField(name,{name=it},label={Text("Name")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(phone,{phone=it},label={Text("Phone")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(upi,{upi=it},label={Text("UPI ID (optional)")},modifier=Modifier.fillMaxWidth())
            Text("Currency: INR")
            Button(onClick={auth.update(user.copy(name=name,phone=phone,upiId=upi))}, enabled=!busy && name.isNotBlank()) { Text("Save profile") }
        }
        OutlinedButton(onClick=auth::logout) { Text("Log out") }
        if(BuildConfig.DEBUG) Text("PayPal Sandbox — No real payment",style=MaterialTheme.typography.bodySmall)
    }
}
