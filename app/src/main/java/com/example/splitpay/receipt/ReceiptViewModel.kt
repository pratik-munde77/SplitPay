package com.example.splitpay.receipt
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
@HiltViewModel class ReceiptViewModel @Inject constructor(private val state:SavedStateHandle):ViewModel() {
 val draft=MutableStateFlow(state.get<String>("receipt")?.let{Gson().fromJson(it,ReceiptDraft::class.java)} ?: ReceiptDraft())
 fun scanned(text:String,image:String){update(ReceiptParser.parse(text,image))}
 fun update(value:ReceiptDraft){draft.value=value;state["receipt"]=Gson().toJson(value)}
}
