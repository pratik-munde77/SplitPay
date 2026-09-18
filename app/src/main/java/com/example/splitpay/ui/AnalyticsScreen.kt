package com.example.splitpay.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitpay.data.*
import java.math.BigDecimal
@Composable fun BudgetCard(budget:BudgetStatus) {
 Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text(if(budget.category=="ALL")"Monthly budget" else budget.category,style=MaterialTheme.typography.titleMedium)
  Text(cash(budget.spent)+" / "+cash(budget.amount))
  LinearProgressIndicator(progress={budget.percent.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth())
  Text(cash(budget.remaining)+" remaining")
  if(budget.percent>=80) Text(if(budget.percent>=100)"Budget reached" else "80% budget alert",color=MaterialTheme.colorScheme.error)
 }}
}
@Composable fun BudgetScreen(model:MainViewModel) {
 val analytics by model.analytics.collectAsStateWithLifecycle()
 var category by rememberSaveable{mutableStateOf("ALL")}
 var amount by rememberSaveable{mutableStateOf("")}
 val submitting by model.budgetSaving.collectAsStateWithLifecycle()
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item{Text("Plan your month",style=MaterialTheme.typography.headlineMedium);Text("Budgets include personal spending and your share of group expenses.")}
  item{ChoiceField("Budget for",category,(listOf("ALL")+categories).associateWith{if(it=="ALL")"Entire month" else it}){category=it}}
  item{OutlinedTextField(amount,{amount=it},label={Text("Monthly amount (INR)")},modifier=Modifier.fillMaxWidth())}
  item{Button(onClick={model.budget(category,amount){amount=""}},enabled=!submitting&&amount.toBigDecimalOrNull()?.signum()==1){Text(if(submitting)"Saving…" else "Save budget")}}
  items(analytics?.budgets ?: emptyList(),key={it.id}){BudgetCard(it)}
 }
}
@Composable fun AnalyticsScreen(model:MainViewModel,local:List<PersonalExpense>,budget:()->Unit) {
 val data by model.analytics.collectAsStateWithLifecycle()
 val refreshing by model.refreshing.collectAsStateWithLifecycle()
 LaunchedEffect(Unit){model.refresh()}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  item{Text("Your money, understood",style=MaterialTheme.typography.headlineMedium);if(refreshing)LinearProgressIndicator(Modifier.fillMaxWidth())}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={model.refresh()},enabled=!refreshing){Text("Refresh & sync")};Button(onClick=budget){Text("Budgets")}}}
  data?.let{value->
   item{Text(cash(value.total),style=MaterialTheme.typography.headlineLarge);Text(value.month+" · Previous month "+cash(value.previousTotal))}
   item{Text("Personal "+cash(value.personalTotal)+" · Shared portion "+cash(value.sharedTotal))}
   item{Text("Category breakdown",style=MaterialTheme.typography.titleLarge)}
   if(value.categories.isEmpty())item{EmptyState("No spending this month","Add an expense to see your category breakdown.")}
   value.categories.toList().sortedByDescending{it.second.toBigDecimal()}.forEach{(category,amount)->item{ChartRow(category,amount,value.total)}}
   item{Text("Daily spending",style=MaterialTheme.typography.titleLarge)}
   val largest=value.daily.values.map{it.toBigDecimal()}.maxOrNull() ?: BigDecimal.ONE
   value.daily.forEach{(day,amount)->item{ChartRow(day.takeLast(5),amount,largest.toPlainString())}}
   item{Text("Top merchants",style=MaterialTheme.typography.titleLarge)}
   value.topMerchants.toList().sortedByDescending{it.second.toBigDecimal()}.take(5).forEach{(merchant,amount)->item{Text(merchant+" · "+cash(amount))}}
   items(value.budgets,key={it.id}){BudgetCard(it)}
  } ?: run {
   item{Text("Offline personal overview",style=MaterialTheme.typography.titleLarge)}
   val month=java.time.YearMonth.now().toString()
   val rows=local.filter{it.date.startsWith(month)}
   val total=rows.sumOf{it.amountMinor}
   item{Text(money(total),style=MaterialTheme.typography.headlineLarge);Text("Connect to the backend to include group spending and budgets.")}
   rows.groupBy{it.category}.forEach{(name,items)->item{Text(name+" · "+money(items.sumOf{it.amountMinor}))}}
  }
 }
}
@Composable fun ChartRow(label:String,amount:String,total:String) {
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(label+" · "+cash(amount))
  LinearProgressIndicator(progress={if(total.toBigDecimal().signum()==0)0f else amount.toBigDecimal().divide(total.toBigDecimal(),6,java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
 }}
}
