package com.example.splitpay.data

import android.content.Context
import com.example.splitpay.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val auth: FirebaseAuth? = if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
    private val _account = MutableStateFlow(initialAccount())
    val account = _account.asStateFlow()
    val configured get() = auth != null
    val serverUserId get() = if (preferences.getString("serverAccount", "") == account.value && account.value.isNotEmpty()) preferences.getString("serverUserId", "").orEmpty() else ""
    fun setServerUserId(id: String, owner: String) { if(owner == account.value) preferences.edit().putString("serverUserId", id).putString("serverAccount", owner).apply() }
    init { auth?.addAuthStateListener { if (!isDemo()) _account.value = it.currentUser?.uid ?: "" } }
    private fun isDemo() = BuildConfig.DEBUG && BuildConfig.DEMO_TOKEN.isNotEmpty() && preferences.getBoolean("demo", false)
    private fun initialAccount() = if (isDemo()) "demo-pratik" else auth?.currentUser?.uid ?: ""
    suspend fun login(email: String, password: String, name: String?, register: Boolean) {
        val firebase = auth ?: throw IllegalArgumentException("Firebase is not configured. Add app/google-services.json and enable Email/Password in Firebase Console, then rebuild.")
        require(email.isNotBlank() && password.length >= 6) { "Enter an email and password of at least 6 characters." }
        if (register) {
            require(!name.isNullOrBlank()) { "Enter your name." }
            val result = firebase.createUserWithEmailAndPassword(email.trim(), password).await()
            result.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name!!.trim()).build())?.await()
        } else firebase.signInWithEmailAndPassword(email.trim(), password).await()
        preferences.edit().putBoolean("demo", false).apply()
        _account.value = firebase.currentUser?.uid ?: ""
    }
    fun demo() {
        require(BuildConfig.DEBUG && BuildConfig.DEMO_TOKEN.length >= 32) { "Configure the local demo first using scripts/configure-demo.ps1." }
        auth?.signOut(); preferences.edit().putBoolean("demo", true).apply(); _account.value = "demo-pratik"
    }
    fun logout() { preferences.edit().remove("demo").remove("serverUserId").remove("serverAccount").apply(); auth?.signOut(); _account.value = ""; context.getSystemService(android.app.NotificationManager::class.java).cancelAll() }
    suspend fun token(): String? = if (isDemo()) BuildConfig.DEMO_TOKEN else auth?.currentUser?.getIdToken(false)?.await()?.token
}
