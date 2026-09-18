package com.example.splitpay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.example.splitpay.data.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

fun money(minor: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(BigDecimal.valueOf(minor, 2))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPayApp(model: MainViewModel, auth: AuthViewModel, groups: GroupViewModel,receipt:com.example.splitpay.receipt.ReceiptViewModel,payment:com.example.splitpay.payment.PaymentViewModel,notificationGroup:String?=null,consumeNotification:()->Unit={}) {
    val preference by model.darkTheme.collectAsStateWithLifecycle()
    val dark = preference ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF75DFCD)) else lightColorScheme(primary = Color(0xFF006B5D), background = Color(0xFFF5F8F6))) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "home"
        val rawState by model.state.collectAsStateWithLifecycle()
        val account by auth.account.collectAsStateWithLifecycle()
        val profile by auth.profile.collectAsStateWithLifecycle()
        val state = rawState.copy(expenses = rawState.expenses.filter { it.ownerId == account })
        val groupDetail by groups.detail.collectAsStateWithLifecycle()
        val analytics by model.analytics.collectAsStateWithLifecycle()
        val draft by receipt.draft.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(notificationGroup,account) {if(account.isNotEmpty()&&notificationGroup!=null&&runCatching{java.util.UUID.fromString(notificationGroup)}.isSuccess){nav.navigate("group/"+notificationGroup){launchSingleTop=true};consumeNotification()}}
        androidx.lifecycle.compose.LifecycleStartEffect(account) {
            groups.connect()
            onStopOrDispose { groups.disconnect() }
        }
        LaunchedEffect(groups) {groups.liveChanges.collect {model.refresh();model.refreshNotifications()}}
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            TopAppBar(title = { Text(if (route == "add") "Add personal expense" else "SplitPay", fontWeight = FontWeight.Bold) }, actions = { IconButton(onClick = { nav.navigate("profile") }) { Icon(Icons.Default.Person, "Profile") }; IconButton(onClick = { model.setDarkTheme(!dark) }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme") } }, navigationIcon = {
                if (route !in listOf("home","groups","analytics","expenses")) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        }, bottomBar = { if (route != "add") NavigationBar {
            listOf(Triple("home", "Home", Icons.Default.Home), Triple("groups", "Groups", Icons.Default.Groups), Triple("add-menu", "Add", Icons.Default.AddCircle), Triple("analytics", "Analytics", Icons.Default.BarChart), Triple("profile", "Profile", Icons.Default.Person)).forEach { (path, title, icon) ->
                NavigationBarItem(selected = route == path, onClick = { nav.navigate(path) { launchSingleTop = true; if (path != "add") { popUpTo("home") { saveState = true }; restoreState = true } } }, icon = { Icon(icon, title) }, label = { Text(title) })
            }
        } }) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("profile") { ProfileScreen(auth) }
                composable("notifications") {NotificationsScreen(model){nav.navigate("group/"+it)}}
                composable("budget") {BudgetScreen(model)}
                composable("scanner") {com.example.splitpay.receipt.ReceiptScannerScreen {text,image->receipt.scanned(text,image);nav.navigate("receipt-review")}}
                composable("receipt-review") {com.example.splitpay.receipt.ReceiptReviewScreen(receipt,model,{nav.popBackStack("home",false)},{nav.navigate("receipt-groups")})}
                composable("receipt-groups") {GroupsScreen(groups,{nav.navigate("shared-receipt/"+it)},{nav.navigate("create-group")})}
                composable("shared-receipt/{id}") {entry->SharedExpenseForm(entry.arguments?.getString("id")!!,null,groups,profile?.id.orEmpty(),{nav.popBackStack("home",false)},draft.merchant,draft.amount,draft.date,draft.category)}
                composable("personal/{id}") {entry->state.expenses.find{it.id==entry.arguments?.getString("id")}?.let{expense->AddExpense(model,expense){nav.popBackStack()}} ?: EmptyState("Expense unavailable","Go back and refresh your expenses.")}
                composable("add-menu") {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Text("What would you like to add?",style=MaterialTheme.typography.headlineMedium);Button(onClick={nav.navigate("add")},modifier=Modifier.fillMaxWidth()){Text("Personal expense")};Button(onClick={nav.navigate("choose-group")},modifier=Modifier.fillMaxWidth()){Text("Shared expense")};Button(onClick={nav.navigate("scanner")},modifier=Modifier.fillMaxWidth()){Text("Scan receipt")}}}
                composable("choose-group") {GroupsScreen(groups,{nav.navigate("shared/"+it+"/new")},{nav.navigate("create-group")})}
                composable("groups") { GroupsScreen(groups, { nav.navigate("group/"+it) }, { nav.navigate("create-group") }) }
                composable("create-group") { GroupForm(groups) { nav.popBackStack(); nav.navigate("group/"+it) } }
                composable("group/{id}") { entry -> val id=entry.arguments?.getString("id")!!
                    GroupDetailsScreen(id,groups,profile?.id.orEmpty(),{nav.navigate("edit-group/"+id)}, {nav.navigate("shared/"+id+"/new")}, {nav.navigate("shared/"+id+"/"+it)}, {nav.navigate("debts/"+id)}, {nav.popBackStack()},{settlement->nav.navigate("payment/"+id+"/"+settlement.receiverUserId+"/"+settlement.amount+"/"+settlement.id)})
                }
                composable("edit-group/{id}") { entry -> groupDetail?.takeIf {it.group.id==entry.arguments?.getString("id")}?.let { GroupForm(groups,it.group){nav.popBackStack()} } }
                composable("shared/{groupId}/{expenseId}") { entry -> SharedExpenseForm(entry.arguments?.getString("groupId")!!,entry.arguments?.getString("expenseId")?.takeUnless{it=="new"},groups,profile?.id.orEmpty(),{nav.popBackStack()}) }
                composable("debts/{id}") { entry -> val id=entry.arguments?.getString("id")!!; SimplifiedDebtsScreen(id,groups,profile?.id.orEmpty()) { nav.navigate("settle/"+id+"/"+it.toUser+"/"+it.amount) } }
                composable("settle/{id}/{receiver}/{amount}") { entry -> val id=entry.arguments?.getString("id")!!;val receiver=entry.arguments?.getString("receiver")!!;SettlementScreen(id,receiver,entry.arguments?.getString("amount")!!,groups,{amount,request->nav.navigate("payment/"+id+"/"+receiver+"/"+amount+"/"+request)}) { nav.popBackStack();nav.navigate("settlement-success") } }
                composable("payment/{id}/{receiver}/{amount}/{request}") {entry->val id=entry.arguments?.getString("id")!!;val receiver=entry.arguments?.getString("receiver")!!;com.example.splitpay.payment.PaymentScreen(id,receiver,groupDetail?.members?.find{it.id==receiver}?.name ?: "member",entry.arguments?.getString("amount")!!,entry.arguments?.getString("request")!!,payment){groups.open(id);model.refresh();nav.popBackStack("group/"+id,false)}}
                composable("settlement-success") { Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {Text("Settlement saved",style=MaterialTheme.typography.headlineLarge);Text("The group balance and activity feed have been updated.");Button(onClick={nav.popBackStack()}){Text("Return to balances")}} }
                composable("home") {
                    LaunchedEffect(Unit){model.refresh()}
                    val total = state.expenses.filter { it.date.startsWith(YearMonth.now().toString()) }.sumOf { it.amountMinor }
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Text("A little clarity. Every day.", style = MaterialTheme.typography.headlineSmall) }
                        item { OutlinedButton(onClick={nav.navigate("notifications")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Notifications,null);Spacer(Modifier.width(8.dp));Text("Notifications")} }
                        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Hello, "+(profile?.name ?: "there"))
                                Text(analytics?.let{cash(it.total)} ?: money(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text("This month · Personal + your shared portion")
                                analytics?.let{Text("You owe "+cash(it.youOwe)+" · You are owed "+cash(it.youAreOwed));Text("Net "+cash((it.youAreOwed.toBigDecimal()-it.youOwe.toBigDecimal()).toPlainString()))}
                            }
                        } }
                        item { Button(onClick = { nav.navigate("add") }, modifier = Modifier.fillMaxWidth()) { Text("+ Add expense") } }
                        item{Button(onClick={nav.navigate("scanner")},modifier=Modifier.fillMaxWidth()){Text("Scan receipt")}}
                        item { OutlinedButton(onClick = { nav.navigate("expenses") }, modifier = Modifier.fillMaxWidth()) { Text("All personal expenses") } }
                        item{OutlinedButton(onClick={nav.navigate("budget")},modifier=Modifier.fillMaxWidth()){Text("Manage monthly budgets")}}
                        analytics?.budgets?.firstOrNull{it.category=="ALL"}?.let{item{BudgetCard(it)}}
                        item { Text("Recent transactions", style = MaterialTheme.typography.titleLarge) }
                        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        if (!state.loading && state.expenses.isEmpty()) item { EmptyState("Your spending story starts here", "Add your first expense. It will stay available offline.") }
                        items(state.expenses.take(5), key = { it.id }) { ExpenseCard(it) }
                        item { OutlinedButton(onClick = model::checkBackend, modifier = Modifier.fillMaxWidth()) { Text("Check backend connection") } }
                    }
                }
                composable("expenses") {
                    var query by rememberSaveable { mutableStateOf("") }
                    var from by rememberSaveable {mutableStateOf("")}
                    var to by rememberSaveable {mutableStateOf("")}
                    var minimum by rememberSaveable {mutableStateOf("")}
                    var maximum by rememberSaveable {mutableStateOf("")}
                    var filters by rememberSaveable {mutableStateOf(false)}
                    var pendingDelete by remember { mutableStateOf<PersonalExpense?>(null) }
                    val filtered = state.expenses.filter { "${it.merchant} ${it.category} ${it.date}".contains(query, true)&&(from.isBlank()||it.date>=from)&&(to.isBlank()||it.date<=to)&&(minimum.toBigDecimalOrNull()?.let{min->BigDecimal.valueOf(it.amountMinor,2)>=min} ?: true)&&(maximum.toBigDecimalOrNull()?.let{max->BigDecimal.valueOf(it.amountMinor,2)<=max} ?: true) }
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { OutlinedTextField(query, { query = it }, label = { Text("Search merchant, category or date") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                        item {Row {TextButton(onClick={filters=!filters}){Text("Date & amount filters")};TextButton(onClick={query="";from="";to="";minimum="";maximum=""}){Text("Clear")}}}
                        if(filters){
                            item{DateRangeFields(from,to,{from=it},{to=it})}
                            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(minimum,{minimum=it},label={Text("Min INR")},modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));OutlinedTextField(maximum,{maximum=it},label={Text("Max INR")},modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))}}
                        }
                        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        if (!state.loading && filtered.isEmpty()) item { EmptyState("No expenses found", "Add an expense or change your search.") }
                        items(filtered, key = { it.id }) { expense -> ExpenseCard(expense,onDelete={ pendingDelete = expense },onEdit={nav.navigate("personal/"+expense.id)}) }
                    }
                    pendingDelete?.let { expense -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete expense?") }, text = { Text("${expense.merchant} · ${money(expense.amountMinor)}") }, confirmButton = { TextButton(onClick = { model.delete(expense.id); pendingDelete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }) }
                }
                composable("add") { AddExpense(model) { nav.popBackStack() } }
                composable("analytics") {
                    AnalyticsScreen(model,state.expenses){nav.navigate("budget")}
                }
            }
        }
    }
}

@Composable
private fun AddExpense(model: MainViewModel, existing:PersonalExpense?=null, onSaved: () -> Unit) {
    var merchant by rememberSaveable(existing?.id) { mutableStateOf(existing?.merchant ?: "") }
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.let{BigDecimal.valueOf(it.amountMinor,2).toPlainString()} ?: "") }
    var category by rememberSaveable(existing?.id) { mutableStateOf(existing?.category ?: "Other") }
    var date by rememberSaveable(existing?.id) { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    var paymentMethod by rememberSaveable(existing?.id) {mutableStateOf(existing?.paymentMethod ?: "OTHER")}
    var expanded by remember { mutableStateOf(false) }
    val saving by model.saving.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.imePadding()) {
        item { OutlinedTextField(amount, { amount = it }, label = { Text("Amount (INR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / description") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item{TextButton(onClick={model.classify(merchant){category=it}},enabled=merchant.isNotBlank()){Text("Suggest category")}}
        item { Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: $category") }; DropdownMenu(expanded, { expanded = false }) { categories.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { category = value; expanded = false }) } } } }
        item { OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth()) }
        item{ChoiceField("Payment method",paymentMethod,listOf("CASH","UPI","CARD","BANK","OTHER").associateWith{it}){paymentMethod=it}}
        item { Button(onClick = { model.save(merchant, amount, category, date, notes, onSaved,id=existing?.id,paymentMethod=paymentMethod) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save expense") } }
    }
}

@Composable
fun ExpenseCard(expense: PersonalExpense, onDelete: (() -> Unit)? = null, onEdit:(()->Unit)?=null) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(expense.merchant, fontWeight = FontWeight.SemiBold); Text("${expense.category} · ${expense.date}", style = MaterialTheme.typography.bodySmall); if (expense.notes.isNotEmpty()) Text(expense.notes, style = MaterialTheme.typography.bodySmall) }
            Text(money(expense.amountMinor), fontWeight = FontWeight.Bold)
            if(onEdit!=null) IconButton(onClick=onEdit,modifier=Modifier.size(24.dp)){Icon(Icons.Default.Edit,"Edit expense")}
            if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.DeleteOutline, "Delete ${expense.merchant}") }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description) } }
}
