package com.example.splitpay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.splitpay.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GroupViewModel @Inject constructor(private val repository: GroupRepository, private val session: SessionRepository) : ViewModel() {
 val busy=MutableStateFlow(false)
 val error=MutableStateFlow<String?>(null)
 private var liveJob:kotlinx.coroutines.Job?=null
 val liveChanges=MutableSharedFlow<Unit>(extraBufferCapacity=1)
 fun connect(){if(liveJob?.isActive==true)return;liveJob=viewModelScope.launch{
  repository.updates.collect {id->try{repository.refresh();if(selected.value==id)repository.refresh(id);liveChanges.tryEmit(Unit)}catch(e:CancellationException){throw e}catch(_:Exception){}}
 }}
 fun disconnect(){liveJob?.cancel();liveJob=null}
 val groups=repository.groups.catch { error.value=it.userMessage(); emit(emptyList()) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private val selected=MutableStateFlow("")
 val detail=selected.flatMapLatest { if(it.isEmpty()) flowOf(null) else repository.details(it) }.catch { error.value=it.userMessage() }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
 init { viewModelScope.launch { session.account.collectLatest { if(it.isNotEmpty()) refresh() } } }
 fun refresh()=work { repository.refresh() }
 fun settle(groupId: String,input: SettlementInput,done:(Settlement)->Unit)=work {val result=repository.api.settle(groupId,input);done(result);repository.refresh(groupId);repository.refresh()}
 fun open(id: String) { selected.value=id; work { repository.refresh(id) } }
 fun create(name: String,description: String,done:(String)->Unit)=work {
  require(name.isNotBlank()) { "Enter a group name." }
  val result=repository.api.create(GroupInput(name.trim(),description.trim())); done(result.id); repository.refresh()
 }
 fun edit(id: String,name: String,description: String,done:()->Unit)=work {
  require(name.isNotBlank()) { "Enter a group name." }
  repository.api.update(id,GroupInput(name.trim(),description.trim())); done(); repository.refresh(id); repository.refresh()
 }
 fun deleteGroup(id: String,done:()->Unit)=work { repository.api.delete(id); repository.removeCache(id); done(); repository.refresh() }
 fun addMember(id: String,email: String,done:()->Unit)=work {
  require(email.contains("@")) { "Enter the member's registered email." }
  repository.api.addMember(id,mapOf("email" to email.trim())); done(); repository.refresh(id); repository.refresh()
 }
 fun removeMember(id: String,userId: String)=work { repository.api.removeMember(id,userId); repository.refresh(id); repository.refresh() }
 fun save(groupId: String,expenseId: String?,input: SharedExpenseInput,done:()->Unit)=work {
  require(input.description.isNotBlank()) { "Enter an expense description." }
  val amount=input.amount.toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid amount.")
  require(amount.signum()>0 && amount.stripTrailingZeros().scale()<=2) { "Use a positive amount with at most two decimals." }
  require(input.participants.isNotEmpty()) { "Select at least one participant." }
  if(expenseId==null) repository.api.createExpense(groupId,input) else repository.api.updateExpense(expenseId,input)
  done(); repository.refresh(groupId); repository.refresh()
 }
 fun deleteExpense(groupId: String,id: String,done:()->Unit)=work { repository.api.deleteExpense(id); done(); repository.refresh(groupId); repository.refresh() }
 private fun work(block:suspend ()->Unit)=viewModelScope.launch {
  busy.value=true; error.value=null
  try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { error.value=e.userMessage() } finally { busy.value=false }
 }
}
