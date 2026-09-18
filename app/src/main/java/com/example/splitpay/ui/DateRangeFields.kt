package com.example.splitpay.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable fun DateRangeFields(from:String,to:String,changeFrom:(String)->Unit,changeTo:(String)->Unit){
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
  OutlinedTextField(from,changeFrom,label={Text("From YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f),isError=from.isNotBlank()&&runCatching{java.time.LocalDate.parse(from)}.isFailure)
  OutlinedTextField(to,changeTo,label={Text("To YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f),isError=to.isNotBlank()&&runCatching{java.time.LocalDate.parse(to)}.isFailure)
 }
 if(from.isNotBlank()&&to.isNotBlank()&&from>to)Text("Start date must be before end date.",color=MaterialTheme.colorScheme.error)
}
