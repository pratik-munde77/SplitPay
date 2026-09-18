package com.example.splitpay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitpay.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(val session: SessionRepository, private val api: SplitPayApi, private val personal: PersonalApi,private val cache:GroupCacheDao) : ViewModel() {
    val account = session.account
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val profile = MutableStateFlow<UserProfile?>(null)
    init { viewModelScope.launch { account.collectLatest { owner ->
        profile.value=null
        if(owner.isNotEmpty()){
            refresh()
            cache.observe(owner,"profile").collect { row -> if(account.value==owner&&row!=null)profile.value=com.google.gson.Gson().fromJson(row.payload,UserProfile::class.java) }
        }
    } } }
    fun login(email: String, password: String, name: String, register: Boolean) = action { session.login(email, password, name, register) }
    fun demo() = action { session.demo() }
    fun refresh() = action {
        val owner=account.value
        val result=api.me()
        if(owner==account.value) {
            profile.value=result; session.setServerUserId(result.id,owner)
            cache.save(GroupCache(owner,"profile",com.google.gson.Gson().toJson(result)))
            if(session.configured) try { val token=com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await(); personal.registerDevice(mapOf("token" to token)) } catch(e:CancellationException){throw e} catch(_:Exception){}
        }
    }
    fun update(value: UserProfile) = action { val owner=account.value;val result=api.updateMe(value);if(owner==account.value){profile.value=result;cache.save(GroupCache(owner,"profile",com.google.gson.Gson().toJson(result)))} }
    fun logout() { session.logout(); profile.value = null; error.value = null }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true; error.value = null
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.userMessage() }
        finally { busy.value = false }
    }
}
