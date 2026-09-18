package com.example.splitpay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitpay.data.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

fun cash(amount: String): String = "₹" + (amount.toBigDecimalOrNull()?.setScale(2,RoundingMode.HALF_UP)?.toPlainString() ?: amount)

@Composable fun GroupStatus(model: GroupViewModel,retry:()->Unit) {
 val busy by model.busy.collectAsStateWithLifecycle()
 val error by model.error.collectAsStateWithLifecycle()
 if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
 error?.let { Text(it,color=MaterialTheme.colorScheme.error); TextButton(onClick=retry,enabled=!busy) { Text("Retry / refresh") } }
}
@Composable fun GroupsScreen(model: GroupViewModel,open:(String)->Unit,create:()->Unit) {
 val groups by model.groups.collectAsStateWithLifecycle()
 val busy by model.busy.collectAsStateWithLifecycle()
 LaunchedEffect(Unit) { model.refresh() }
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item { Text("Better together",style=MaterialTheme.typography.headlineMedium); Text("Trips, homes and everything you share.") }
  item { GroupStatus(model) {model.refresh()} }
  item { Button(onClick=create,modifier=Modifier.fillMaxWidth()) { Text("+ Create group") } }
  if(groups.isEmpty() && !busy) item { EmptyState("No groups yet","Create a group, then add friends using their registered email addresses.") }
  items(groups,key={it.id}) { group -> Card(onClick={open(group.id)},modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text(group.name,style=MaterialTheme.typography.titleLarge)
    if(group.description.isNotBlank()) Text(group.description)
    Text(group.memberIds.size.toString()+" members · "+group.currency,style=MaterialTheme.typography.bodySmall)
   }
  } }
 }
}
@Composable fun GroupForm(model: GroupViewModel,group: SharedGroup?=null,done:(String)->Unit) {
 var name by rememberSaveable(group?.id) { mutableStateOf(group?.name ?: "") }
 var description by rememberSaveable(group?.id) { mutableStateOf(group?.description ?: "") }
 val busy by model.busy.collectAsStateWithLifecycle()
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item { Text(if(group==null) "Create a group" else "Edit group",style=MaterialTheme.typography.headlineMedium) }
  item { GroupStatus(model) {model.refresh()} }
  item { OutlinedTextField(name,{name=it},label={Text("Group name")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
  item { OutlinedTextField(description,{description=it},label={Text("Description")},modifier=Modifier.fillMaxWidth()) }
  item { Text("Currency: INR") }
  item { Button(onClick={if(group==null) model.create(name,description,done) else model.edit(group.id,name,description) {done(group.id)}},enabled=!busy && name.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Save group") } }
 }
}
@Composable fun GroupDetailsScreen(id:String,model:GroupViewModel,me:String,edit:()->Unit,add:()->Unit,expense:(String)->Unit,simplify:()->Unit,deleted:()->Unit,resumePayment:(Settlement)->Unit) {
 val loaded by model.detail.collectAsStateWithLifecycle()
 val detail=loaded?.takeIf {it.group.id==id}
 val busy by model.busy.collectAsStateWithLifecycle()
 var tab by rememberSaveable(id) { mutableStateOf(0) }
 var search by rememberSaveable(id) {mutableStateOf("")}
 var fromDate by rememberSaveable(id) {mutableStateOf("")}
 var toDate by rememberSaveable(id) {mutableStateOf("")}
 var email by rememberSaveable(id) { mutableStateOf("") }
 var remove by remember {mutableStateOf<UserProfile?>(null)}
 var delete by remember {mutableStateOf(false)}
 LaunchedEffect(id) {model.open(id)}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item {GroupStatus(model) {model.open(id)}}
  detail?.let { value ->
   val total=value.expenses.fold(BigDecimal.ZERO) {sum,e->sum+e.amount.toBigDecimal()}
   val contribution=value.expenses.filter {it.payerId==me}.fold(BigDecimal.ZERO){sum,e->sum+e.amount.toBigDecimal()}
   val share=value.expenses.fold(BigDecimal.ZERO){sum,e->sum+(e.splits[me]?.amount?.toBigDecimal() ?: BigDecimal.ZERO)}
   item {Text(value.group.name,style=MaterialTheme.typography.headlineMedium); Text(value.group.description)}
   item {Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("Group spending"); Text(cash(total.toPlainString()),style=MaterialTheme.typography.headlineLarge)
     Text("You paid "+cash(contribution.toPlainString())+" · Your share "+cash(share.toPlainString()))
     Text("Your net balance "+cash(value.balances[me] ?: "0"))
    }
   }}
   item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {Button(onClick=add,enabled=!busy) {Text("+ Expense")}; OutlinedButton(onClick=simplify) {Text("Simplify debts")}}}
   item {ScrollableTabRow(selectedTabIndex=tab) {listOf("Expenses","Balances","Members","Activity","Analytics","Settlements").forEachIndexed {index,title->Tab(selected=tab==index,onClick={tab=index},text={Text(title)})}}}
   when(tab) {
    0 -> {
     item {OutlinedTextField(search,{search=it},label={Text("Search description, category or payer")},modifier=Modifier.fillMaxWidth())}
     item {DateRangeFields(fromDate,toDate,{fromDate=it},{toDate=it})}
     val filtered=value.expenses.filter{entry->(entry.description+" "+entry.category+" "+value.members.find{it.id==entry.payerId}?.name).contains(search,true)&&(fromDate.isBlank()||entry.date>=fromDate)&&(toDate.isBlank()||entry.date<=toDate)}
     if(value.expenses.isEmpty()) item {EmptyState("Nothing shared yet","Add the first expense to calculate everyone's share.")}
     if(filtered.isEmpty()&&value.expenses.isNotEmpty())item{EmptyState("No matching expenses","Change the search or date range.")}
     items(filtered,key={it.id}) {entry->Card(onClick={expense(entry.id)},modifier=Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
       Text(entry.description+" · "+cash(entry.amount),fontWeight=FontWeight.Bold)
       Text(entry.category+" · "+entry.date+" · "+entry.splitType,style=MaterialTheme.typography.bodySmall)
       Text("Paid by "+(value.members.find {it.id==entry.payerId}?.name ?: "Member"))
      }
     }}
    }
    1 -> items(value.members,key={it.id}) {member->Card(Modifier.fillMaxWidth()) {
     Column(Modifier.padding(16.dp)) {
      Text(member.name,style=MaterialTheme.typography.titleMedium)
      val balance=(value.balances[member.id] ?: "0").toBigDecimal()
      Text(if(balance.signum()>0) "Should receive "+cash(balance.toPlainString()) else if(balance.signum()<0) "Owes "+cash(balance.abs().toPlainString()) else "All settled")
     }
    }}
    2 -> {
     item {Text("Members must register before being added.",style=MaterialTheme.typography.bodySmall)}
     if(value.group.createdBy==me) item {
      OutlinedTextField(email,{email=it},label={Text("Registered email")},modifier=Modifier.fillMaxWidth(),singleLine=true)
      Button(onClick={model.addMember(id,email){email=""}},enabled=!busy && email.isNotBlank()) {Text("Add member")}
     }
     items(value.members,key={it.id}) {member->Card(Modifier.fillMaxWidth()) {
      Row(Modifier.padding(16.dp)) {Column(Modifier.weight(1f)){Text(member.name);Text(member.email,style=MaterialTheme.typography.bodySmall)}
       if(value.group.createdBy==me && member.id!=me) TextButton(onClick={remove=member},enabled=!busy){Text("Remove")}
      }
     }}
     if(value.group.createdBy==me) item {Row {OutlinedButton(onClick=edit,enabled=!busy){Text("Edit group")};TextButton(onClick={delete=true},enabled=!busy){Text("Delete empty group")}}}
    }
    3 -> {
     if(value.activity.isEmpty()) item {EmptyState("No activity yet","Group changes will appear here.")}
     items(value.activity,key={it.id}) {activity->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)){Text(activity.description);Text(activity.createdAt.take(16).replace('T',' '),style=MaterialTheme.typography.bodySmall)}}}
    }
    4 -> {
     item {Text("Category breakdown",style=MaterialTheme.typography.titleLarge)}
     value.expenses.groupBy{it.category}.forEach{(category,entries)->item{ChartRow(category,entries.fold(BigDecimal.ZERO){sum,e->sum+e.amount.toBigDecimal()}.toPlainString(),total.toPlainString())}}
     val paid=value.members.associate{member->member.id to value.expenses.filter{it.payerId==member.id}.fold(BigDecimal.ZERO){sum,e->sum+e.amount.toBigDecimal()}}
     val highest=paid.maxByOrNull{it.value}
     item{Text("Highest payer: "+(value.members.find{it.id==highest?.key}?.name ?: "None"))}
     items(value.members,key={it.id}){member->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
      Text(member.name,style=MaterialTheme.typography.titleMedium)
      Text("Paid "+cash(paid.getValue(member.id).toPlainString()))
      Text("Share "+cash(value.expenses.fold(BigDecimal.ZERO){sum,e->sum+(e.splits[member.id]?.amount?.toBigDecimal() ?: BigDecimal.ZERO)}.toPlainString()))
     }}}
    }
    5 -> {
     if(value.settlements.isNullOrEmpty())item{EmptyState("No settlements yet","Completed and pending settlements will appear here.")}
     items(value.settlements.orEmpty(),key={it.id}){settlement->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
      Text((value.members.find{it.id==settlement.payerUserId}?.name ?: "Member")+" → "+(value.members.find{it.id==settlement.receiverUserId}?.name ?: "Member"))
      Text(cash(settlement.amount)+" · "+settlement.status)
      if(settlement.method=="PAYPAL_SANDBOX"&&settlement.payerUserId==me&&settlement.status in listOf("CREATED","PENDING"))OutlinedButton(onClick={resumePayment(settlement)}){Text("Resume PayPal Sandbox checkout")}
     }}}
    }
   }
  }
 }
 remove?.let {member->AlertDialog(onDismissRequest={remove=null},title={Text("Remove "+member.name+"?")},text={Text("Members with financial history must remain in the group.")},confirmButton={TextButton(onClick={model.removeMember(id,member.id);remove=null}){Text("Remove")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}})}
 if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("Delete this group?")},text={Text("Only an empty group can be deleted.")},confirmButton={TextButton(onClick={delete=false;model.deleteGroup(id,deleted)}){Text("Delete")}},dismissButton={TextButton(onClick={delete=false}){Text("Cancel")}})
}
@Composable fun SimplifiedDebtsScreen(id:String,model:GroupViewModel,me:String,pay:(Transfer)->Unit) {
 val loaded by model.detail.collectAsStateWithLifecycle()
 val detail=loaded?.takeIf {it.group.id==id}
 LaunchedEffect(id){model.open(id)}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item {Text("Simplified debts",style=MaterialTheme.typography.headlineMedium);Text("Suggestions preserve every member's net balance. Original expenses stay unchanged.")}
  item {GroupStatus(model){model.open(id)}}
  detail?.let {value->
   value.direct?.let{original->
    item {Text("Before: "+original.size+" bilateral obligations",style=MaterialTheme.typography.titleMedium)}
    items(original,key={"before-"+it.fromUser+it.toUser}){transfer->Text((value.members.find{it.id==transfer.fromUser}?.name ?: "Member")+" → "+(value.members.find{it.id==transfer.toUser}?.name ?: "Member")+" · "+cash(transfer.amount))}
    item {Text("After: "+value.simplified.size+" suggested transfers",style=MaterialTheme.typography.titleMedium)}
   }
   if(value.simplified.isEmpty()) item {EmptyState("All settled","No transfers are needed.")}
   items(value.simplified,key={it.fromUser+it.toUser}) {transfer->Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     Text((value.members.find{it.id==transfer.fromUser}?.name ?: "Member")+" owes "+(value.members.find{it.id==transfer.toUser}?.name ?: "Member"))
     Text(cash(transfer.amount),style=MaterialTheme.typography.headlineSmall)
     if(transfer.fromUser==me) Button(onClick={pay(transfer)}){Text("Settle balance")} else Text("The payer can initiate this settlement.",style=MaterialTheme.typography.bodySmall)
    }
   }}
  }
 }
}
@Composable fun SharedExpenseForm(groupId:String,expenseId:String?,model:GroupViewModel,me:String,done:()->Unit,receiptDescription:String="",receiptAmount:String="",receiptDate:String=LocalDate.now().toString(),receiptCategory:String="Other") {
 val loaded by model.detail.collectAsStateWithLifecycle()
 val detail=loaded?.takeIf {it.group.id==groupId}
 val busy by model.busy.collectAsStateWithLifecycle()
 LaunchedEffect(groupId){model.open(groupId)}
 if(detail==null) {Column(Modifier.padding(20.dp)){GroupStatus(model){model.open(groupId)}};return}
 val existing=detail.expenses.find {it.id==expenseId}
 if(expenseId!=null && existing==null) {EmptyState("Expense unavailable","Refresh the group and try again.");return}
 var description by rememberSaveable(expenseId,groupId) {mutableStateOf(existing?.description ?: receiptDescription)}
 var amount by rememberSaveable(expenseId,groupId) {mutableStateOf(existing?.amount ?: receiptAmount)}
 var date by rememberSaveable(expenseId,groupId) {mutableStateOf(existing?.date ?: receiptDate)}
 var category by rememberSaveable(expenseId,groupId) {mutableStateOf(existing?.category ?: receiptCategory)}
 var notes by rememberSaveable(expenseId,groupId) {mutableStateOf(existing?.notes ?: "")}
 var payer by rememberSaveable(expenseId,groupId) {mutableStateOf(existing?.payerId ?: me)}
 var type by rememberSaveable(expenseId,groupId) {mutableStateOf(existing?.splitType ?: "EQUAL")}
 val selected=remember(expenseId,groupId) {mutableStateMapOf<String,Boolean>().apply {detail.members.forEach {put(it.id,existing?.splits?.containsKey(it.id) ?: true)}}}
 val weights=remember(expenseId,groupId) {mutableStateMapOf<String,String>().apply {detail.members.forEach {put(it.id,existing?.splits?.get(it.id)?.weight ?: "1")}}}
 var deletion by remember {mutableStateOf(false)}
 val canEdit=existing==null||existing.createdBy==me||existing.payerId==me||detail.group.createdBy==me
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.imePadding()) {
  item {Text(if(existing==null) "Add shared expense" else "Expense details",style=MaterialTheme.typography.headlineMedium)}
  item {GroupStatus(model){model.open(groupId)}}
  item {OutlinedTextField(description,{description=it},label={Text("Description")},modifier=Modifier.fillMaxWidth(),enabled=canEdit)}
  item {OutlinedTextField(amount,{amount=it},label={Text("Amount (INR)")},modifier=Modifier.fillMaxWidth(),enabled=canEdit)}
  item {ChoiceField("Paid by",payer,detail.members.associate {it.id to it.name},canEdit){payer=it}}
  item {ChoiceField("Category",category,categories.associateWith{it},canEdit){category=it}}
  item {OutlinedTextField(date,{date=it},label={Text("Date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth(),enabled=canEdit)}
  item {OutlinedTextField(notes,{notes=it},label={Text("Notes")},modifier=Modifier.fillMaxWidth(),enabled=canEdit)}
  item {ChoiceField("Split",type,listOf("EQUAL","EXACT","PERCENTAGE","SHARES").associateWith{it},canEdit){type=it}}
  item {Text(when(type){"EXACT"->"Amounts must add up to the expense.";"PERCENTAGE"->"Percentages must total exactly 100.";"SHARES"->"Enter positive relative weights.";else->"Paise are distributed deterministically."})}
  items(detail.members,key={it.id}) {member->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   Checkbox(checked=selected[member.id]==true,onCheckedChange={selected[member.id]=it},enabled=canEdit)
   Text(member.name,modifier=Modifier.weight(1f).padding(top=14.dp))
   if(type!="EQUAL" && selected[member.id]==true) OutlinedTextField(weights[member.id] ?: "",{weights[member.id]=it},modifier=Modifier.width(110.dp),singleLine=true,enabled=canEdit,label={Text(if(type=="PERCENTAGE") "%" else if(type=="EXACT") "INR" else "Shares")})
  }}
  item {
   if(canEdit) Button(onClick={
    val participants=detail.members.filter{selected[it.id]==true}.associate {it.id to (if(type=="EQUAL") "1" else weights[it.id].orEmpty())}
    model.save(groupId,expenseId,SharedExpenseInput(description,amount,payer,category,date,notes,type,participants,existing?.version),done)
   },enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Save expense")}
   if(existing!=null && canEdit) TextButton(onClick={deletion=true},enabled=!busy){Text("Delete expense")}
  }
  existing?.let {entry->item {Text("Saved shares",style=MaterialTheme.typography.titleMedium)}
   items(detail.members.filter{entry.splits.containsKey(it.id)}) {member->Text(member.name+": "+cash(entry.splits.getValue(member.id).amount))}
  }
 }
 if(deletion) AlertDialog(onDismissRequest={deletion=false},title={Text("Delete expense?")},text={Text("Everyone's balance will be recalculated.")},confirmButton={TextButton(onClick={deletion=false;model.deleteExpense(groupId,expenseId!!,done)}){Text("Delete")}},dismissButton={TextButton(onClick={deletion=false}){Text("Cancel")}})
}
@Composable fun ChoiceField(label:String,value:String,choices:Map<String,String>,enabled:Boolean=true,change:(String)->Unit) {
 var expanded by remember {mutableStateOf(false)}
 Box {OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text(label+": "+(choices[value] ?: value))}
  DropdownMenu(expanded,{expanded=false}) {choices.forEach{(key,text)->DropdownMenuItem(text={Text(text)},onClick={change(key);expanded=false})}}
 }
}
