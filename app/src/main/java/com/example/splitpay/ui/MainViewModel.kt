package com.example.splitpay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitpay.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpenseState(val loading: Boolean = true, val expenses: List<PersonalExpense> = emptyList(), val error: String? = null)

@HiltViewModel
class MainViewModel @Inject constructor(private val repository: ExpenseRepository, private val backend: BackendRepository, private val preferences: PreferencesRepository, private val sync:PersonalSyncRepository,private val personalApi:PersonalApi,private val session:SessionRepository) : ViewModel() {
    val analytics=MutableStateFlow<MonthlyAnalytics?>(null)
    val notifications=MutableStateFlow<List<AppNotification>>(emptyList())
    val notificationsLoading=MutableStateFlow(false)
    fun refreshNotifications()=viewModelScope.launch {
        val owner=session.account.value; notificationsLoading.value=true
        try { val result=personalApi.notifications();if(owner==session.account.value)notifications.value=result }
        catch(e:CancellationException){throw e}catch(e:Exception){_message.value=e.userMessage()}
        finally{notificationsLoading.value=false}
    }
    fun readNotification(id:String)=viewModelScope.launch {
        try{personalApi.readNotification(id);refreshNotifications()}catch(e:CancellationException){throw e}catch(e:Exception){_message.value=e.userMessage()}
    }
    val refreshing=MutableStateFlow(false)
    fun classify(merchant:String,done:(String)->Unit)=viewModelScope.launch {
        try {done(personalApi.classify(mapOf("merchant" to merchant,"description" to merchant)).category)}catch(e:CancellationException){throw e}catch(e:Exception){done("Other");_message.value="Could not classify. Please choose a category."}
    }
    init {viewModelScope.launch {session.account.collectLatest {analytics.value=null;notifications.value=emptyList();if(it.isNotEmpty())refresh()}}}
    fun refresh()=viewModelScope.launch {
        if(refreshing.value)return@launch
        refreshing.value=true
        val owner=session.account.value
        try {sync.sync();val result=personalApi.analytics();if(owner==session.account.value)analytics.value=result}catch(e:CancellationException){throw e}catch(e:Exception){_message.value=e.userMessage()}finally{refreshing.value=false}
    }
    val budgetSaving=MutableStateFlow(false)
    fun budget(category:String,amount:String,done:()->Unit)=viewModelScope.launch {
        if(budgetSaving.value)return@launch
        budgetSaving.value=true
        try {require(amount.toBigDecimalOrNull()?.signum()==1){"Enter a positive budget."};personalApi.budget(BudgetInput(java.time.YearMonth.now().toString(),category,amount));analytics.value=personalApi.analytics();done();_message.value="Budget saved."}catch(e:CancellationException){throw e}catch(e:Exception){_message.value=e.userMessage()}finally{budgetSaving.value=false}
    }
    val darkTheme = preferences.darkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch {
        try { preferences.setDarkTheme(enabled) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _message.value = e.userMessage() }
    }
    val state = repository.expenses.map { ExpenseState(false, it) }
        .catch { emit(ExpenseState(false, error = it.userMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseState())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    fun dismissMessage() { _message.value = null }
    fun save(merchant: String, amount: String, category: String, date: String, notes: String, onSuccess: () -> Unit, id:String?=null,source:String="MANUAL",paymentMethod:String="OTHER",receiptUrl:String="") {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            try { if(id==null)repository.save(merchant, amount, category, date, notes,source,paymentMethod,receiptUrl) else sync.edit(id,merchant,amount,category,date,notes,paymentMethod); onSuccess(); _message.value = "Expense saved on this device.";refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _message.value = e.userMessage() }
            finally { _saving.value = false }
        }
    }
    fun delete(id: String) = viewModelScope.launch {
        try { sync.delete(id); _message.value = "Expense deleted locally.";refresh() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _message.value = e.userMessage() }
    }
    fun checkBackend() = viewModelScope.launch {
        _message.value = "Connecting to backend…"
        _message.value = when (val result = backend.health()) {
            is ApiResult.Success -> "Backend: ${result.value.status}"
            is ApiResult.Failure -> result.message
        }
    }
}
