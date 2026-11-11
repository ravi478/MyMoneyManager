package com.example.mymoneymanager

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Green = Color(0xFF18D889)
private val Teal = Color(0xFF22C7C9)
private val Rose = Color(0xFFFF829B)
private val Amber = Color(0xFFFFC66B)
private val Blue = Color(0xFF5798FF)
private val Violet = Color(0xFF9B83FF)
private val DarkBg = Color(0xFF080E1B)
private val DarkCard = Color(0xFF141E30)
private val LightBg = Color(0xFFF3F7F8)
private val LightCard = Color.White

private data class Tx(val id: Long, val desc: String, val amount: Double, val type: String, val category: String, val date: String)
private data class Goal(val id: Long, val name: String, val target: Double, val saved: Double, val date: String)
private data class Recurring(val id: Long, val desc: String, val amount: Double, val type: String, val category: String, val day: Int, val appliedMonth: String)
private data class Profile(val name: String = "User", val email: String = "user@example.com", val holder: String = "Money Manager User")
private data class AppData(val tx: List<Tx>, val goals: List<Goal>, val recurring: List<Recurring>, val profile: Profile, val budget: Double, val dark: Boolean, val pinHash: String)

private fun money(v: Double): String = "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(v.roundToInt())
private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun monthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
private fun hashPin(pin: String): String = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MoneyApp(this) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneyApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("mmm_native", Context.MODE_PRIVATE) }
    val transactions = remember { mutableStateListOf<Tx>().apply { addAll(loadData(prefs).tx) } }
    val goals = remember { mutableStateListOf<Goal>().apply { addAll(loadData(prefs).goals) } }
    val recurring = remember { mutableStateListOf<Recurring>().apply { addAll(loadData(prefs).recurring) } }
    var profile by remember { mutableStateOf(loadData(prefs).profile) }
    var budget by remember { mutableDoubleStateOf(loadData(prefs).budget) }
    var dark by remember { mutableStateOf(loadData(prefs).dark) }
    var pinHash by remember { mutableStateOf(loadData(prefs).pinHash) }
    var started by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showPinError by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Home") }
    var showTxDialog by remember { mutableStateOf(false) }
    var editTx by remember { mutableStateOf<Tx?>(null) }
    var txType by remember { mutableStateOf("expense") }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var showBalance by remember { mutableStateOf(true) }
    var reportAll by remember { mutableStateOf(false) }

    fun persist() {
        val json = JSONObject().apply {
            put("transactions", JSONArray().also { a -> transactions.forEach { a.put(JSONObject().apply { put("id",it.id);put("desc",it.desc);put("amount",it.amount);put("type",it.type);put("category",it.category);put("date",it.date) }) } })
            put("goals", JSONArray().also { a -> goals.forEach { a.put(JSONObject().apply { put("id",it.id);put("name",it.name);put("target",it.target);put("saved",it.saved);put("date",it.date) }) } })
            put("recurring", JSONArray().also { a -> recurring.forEach { a.put(JSONObject().apply { put("id",it.id);put("desc",it.desc);put("amount",it.amount);put("type",it.type);put("category",it.category);put("day",it.day);put("appliedMonth",it.appliedMonth) }) } })
            put("name",profile.name);put("email",profile.email);put("holder",profile.holder);put("budget",budget);put("dark",dark);put("pinHash",pinHash)
        }
        prefs.edit().putString("state",json.toString()).apply()
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(prefs.getString("state", "{}") ?: "{}") } }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Cannot read file")
            val restored = parseData(raw)
            transactions.clear(); transactions.addAll(restored.tx)
            goals.clear(); goals.addAll(restored.goals)
            recurring.clear(); recurring.addAll(restored.recurring)
            profile = restored.profile; budget = restored.budget; dark = restored.dark; pinHash = restored.pinHash
            persist()
        }.onFailure { message = "Backup restore failed: ${it.message ?: "Invalid file"}" }
    }
    LaunchedEffect(Unit) {
        recurring.forEach { r -> if (r.appliedMonth != monthKey() && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) >= r.day) {
            transactions.add(Tx(System.currentTimeMillis()+r.id,r.desc,r.amount,r.type,r.category,"${monthKey()}-${r.day.toString().padStart(2,'0')}"))
            val i=recurring.indexOf(r); if(i>=0) recurring[i]=r.copy(appliedMonth=monthKey())
        } }
        persist()
    }
    val income = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    val balance = income-expense
    val bg = if(dark) DarkBg else LightBg
    val card = if(dark) DarkCard else LightCard
    val text = if(dark) Color(0xFFF4F7FB) else Color(0xFF172033)
    val muted = if(dark) Color(0xFF9AA8BC) else Color(0xFF64748B)

    MaterialTheme(colorScheme = if(dark) darkColorScheme(primary=Green,background=bg,surface=card,onSurface=text) else lightColorScheme(primary=Color(0xFF008A57),background=bg,surface=card,onSurface=text)) {
        Surface(Modifier.fillMaxSize(), color=bg) {
            if (!started) {
                Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.SpaceBetween) {
                    Spacer(Modifier.height(12.dp))
                    Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center, modifier=Modifier.weight(1f)) {
                        Box(Modifier.size(112.dp).clip(RoundedCornerShape(30.dp)).background(Green), contentAlignment=Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet,null,tint=DarkBg,modifier=Modifier.size(58.dp)) }
                        Spacer(Modifier.height(22.dp)); Text("My Money Manager",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=text)
                        Text("TRACK • PLAN • SAVE",color=Green,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                        Spacer(Modifier.height(12.dp)); Text("Take control of your personal finances with smart tracking, reports and savings goals.",color=muted)
                    }
                    Button(onClick={started=true;locked=pinHash.isNotBlank()},modifier=Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=Green,contentColor=DarkBg)) { Text("Get Started  →",fontWeight=FontWeight.Bold) }
                }
            } else if (locked) {
                Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(Icons.Default.Lock,null,tint=Green,modifier=Modifier.size(52.dp)); Text("App Locked",fontSize=24.sp,fontWeight=FontWeight.Bold,color=text); Spacer(Modifier.height(18.dp))
                    OutlinedTextField(pinInput,{pinInput=it.filter(Char::isDigit).take(12)},label={Text("Enter PIN")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                    if(showPinError) Text("Incorrect PIN",color=Rose)
                    Button(onClick={if(pinInput.length in 4..12 && hashPin(pinInput)==pinHash){locked=false;pinInput="";showPinError=false}else showPinError=true},modifier=Modifier.fillMaxWidth().padding(top=14.dp)) { Text("Unlock") }
                }
            } else {
                Scaffold(containerColor=bg, bottomBar={ NavigationBar(containerColor=card) {
                    listOf("Home" to Icons.Default.Home,"Transactions" to Icons.Default.ReceiptLong,"Reports" to Icons.Default.PieChart,"Goals" to Icons.Default.Flag,"Settings" to Icons.Default.Settings).forEach { (label,icon) ->
                        NavigationBarItem(selected=tab==label,onClick={tab=label},icon={Icon(icon,null)},label={Text(label,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Green,selectedTextColor=Green,indicatorColor=Green.copy(.12f),unselectedIconColor=muted,unselectedTextColor=muted))
                    }
                }}) { pad ->
                    AnimatedContent(tab,modifier=Modifier.padding(pad).fillMaxSize(),transitionSpec={ (fadeIn()+slideInVertically { it/12 }) togetherWith (fadeOut()+slideOutVertically { -it/12 }) },label="screen") { page ->
                        when(page) {
                            "Home" -> HomeScreen(text,muted,card,transactions,goals,income,expense,balance,budget,showBalance,{showBalance=!showBalance},{txType=it;editTx=null;showTxDialog=true},{tab="Transactions"},{tab="Goals"},profile.holder)
                            "Transactions" -> HistoryScreen(text,muted,card,transactions,query,{query=it},filter,{filter=it},{t->{editTx=t;txType=t.type;showTxDialog=true}},{t->{transactions.remove(t);persist()}},{txType=it;editTx=null;showTxDialog=true})
                            "Reports" -> ReportsScreen(text,muted,card,transactions,reportAll,{reportAll=it})
                            "Goals" -> GoalsScreen(text,muted,card,goals,{showGoalDialog=true},{g->{val i=goals.indexOf(g);if(i>=0)goals.removeAt(i);persist()}},{g,amount->val i=goals.indexOf(g);if(i>=0){goals[i]=g.copy(saved=g.saved+amount);persist()}})
                            else -> SettingsScreen(text,muted,card,profile,dark,budget,pinHash.isNotBlank(),recurring,{showProfileDialog=true},{dark=!dark;persist()},{showBudgetDialog=true},{showPinDialog=true},{locked=pinHash.isNotBlank()},{showRecurringDialog=true},{pinHash="";persist();message="PIN lock disabled"},{backupLauncher.launch("My_Money_Manager_Backup_${today()}.json")},{restoreLauncher.launch(arrayOf("application/json"))},{transactions.clear();goals.clear();recurring.clear();profile=Profile();budget=0.0;pinHash="";persist();message="All app data cleared"})
                        }
                    }
                }
            }
        }
    }

    if(showTxDialog) TransactionDialog(editTx,txType,transactions,{showTxDialog=false},{txType=it},{ tx -> if(editTx==null) transactions.add(tx) else {val i=transactions.indexOfFirst{it.id==editTx!!.id};if(i>=0)transactions[i]=tx}; persist();showTxDialog=false;message="Transaction saved" })
    if(showGoalDialog) GoalDialog({showGoalDialog=false},{g->goals.add(g);persist();showGoalDialog=false})
    if(showProfileDialog) ProfileDialog(profile,{showProfileDialog=false},{profile=it;persist();showProfileDialog=false})
    if(showBudgetDialog) AmountDialog("Monthly Budget",budget,{showBudgetDialog=false},{budget=it;persist();showBudgetDialog=false})
    if(showPinDialog) PinDialog(pinHash,{showPinDialog=false},{pinHash=it;persist();showPinDialog=false;message="PIN updated"})
    if(showRecurringDialog) RecurringDialog(recurring,{showRecurringDialog=false},{recurring.add(it);persist();showRecurringDialog=false},{r->recurring.remove(r);persist()})
    message?.let { msg -> AlertDialog(onDismissRequest={message=null},title={Text("My Money Manager")},text={Text(msg)},confirmButton={TextButton(onClick={message=null}){Text("OK")}}) }
}

private fun loadData(p: android.content.SharedPreferences): AppData = parseData(p.getString("state", "{}") ?: "{}")
private fun parseData(raw: String): AppData {
    return try {
        val j=JSONObject(raw)
        val tx=mutableListOf<Tx>(); val ta=j.optJSONArray("transactions")?:JSONArray(); for(i in 0 until ta.length()){val x=ta.getJSONObject(i);tx.add(Tx(x.optLong("id"),x.optString("desc"),x.optDouble("amount"),x.optString("type"),x.optString("category"),x.optString("date")))}
        val gs=mutableListOf<Goal>();val ga=j.optJSONArray("goals")?:JSONArray();for(i in 0 until ga.length()){val x=ga.getJSONObject(i);gs.add(Goal(x.optLong("id"),x.optString("name"),x.optDouble("target"),x.optDouble("saved"),x.optString("date")))}
        val rs=mutableListOf<Recurring>();val ra=j.optJSONArray("recurring")?:JSONArray();for(i in 0 until ra.length()){val x=ra.getJSONObject(i);rs.add(Recurring(x.optLong("id"),x.optString("desc"),x.optDouble("amount"),x.optString("type"),x.optString("category"),x.optInt("day",1),x.optString("appliedMonth")))}
        AppData(tx,gs,rs,Profile(j.optString("name","User"),j.optString("email","user@example.com"),j.optString("holder","Money Manager User")),j.optDouble("budget",0.0),j.optBoolean("dark",true),j.optString("pinHash"))
    } catch (_:Exception) { AppData(emptyList(),emptyList(),emptyList(),Profile(),0.0,true,"") }
}
@Composable private fun AppCard(modifier: Modifier=Modifier,card:Color,content:@Composable ColumnScope.()->Unit){Card(modifier,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=card),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(16.dp),content=content)}}
@Composable private fun HomeScreen(text:Color,muted:Color,card:Color,tx:List<Tx>,goals:List<Goal>,income:Double,expense:Double,balance:Double,budget:Double,show:Boolean,onEye:()->Unit,onAdd:(String)->Unit,onHistory:()->Unit,onGoals:()->Unit,holder:String){
    val spent=tx.filter{it.type=="expense"&&it.date.startsWith(monthKey())}.sumOf{it.amount}; val pct=if(budget>0)(spent/budget).coerceAtMost(1.0).toFloat() else 0f
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Column { Text("Welcome back 👋",color=muted,fontSize=12.sp);Text("Good ${if(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)<12)"Morning" else if(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)<17)"Afternoon" else "Evening"}",fontSize=23.sp,fontWeight=FontWeight.Bold,color=text) } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFD5DDE2)),shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("TOTAL BALANCE",color=Color(0xFF45515D),fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp);IconButton(onClick=onEye){Icon(if(show)Icons.Default.Visibility else Icons.Default.VisibilityOff,null,tint=Color.DarkGray)}};Text(if(show)money(balance) else "₹ ••••••",fontSize=32.sp,fontWeight=FontWeight.ExtraBold,color=Color(0xFF26323D));Spacer(Modifier.height(14.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("CARD HOLDER",color=Color(0xFF46535E),fontSize=10.sp);Text(holder.uppercase(),color=Color(0xFF26323D),fontWeight=FontWeight.Bold,fontSize=11.sp)} } } }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){SummaryCard("Income",money(income),Green,Icons.Default.South,card,text,Modifier.weight(1f));SummaryCard("Expenses",money(expense),Rose,Icons.Default.North,card,text,Modifier.weight(1f))} }
        item { AppCard(card=card){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Monthly Budget",color=text,fontWeight=FontWeight.SemiBold);Text(if(budget>0)"${(pct*100).roundToInt()}% used" else "Not set",color=Green,fontSize=12.sp)};Text("${money(spent)} spent of ${money(budget)}",color=muted,fontSize=12.sp);LinearProgressIndicator(progress={pct},modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),color=if(spent>budget&&budget>0)Rose else Green,trackColor=muted.copy(.18f));Text(if(budget<=0)"Set a monthly limit in Settings." else if(spent>budget)"${money(spent-budget)} over budget" else "${money(budget-spent)} remaining this month",color=muted,fontSize=11.sp)} }
        item { AppCard(card=card){Row(Modifier.fillMaxWidth().clickable(onClick=onGoals),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Savings Target Progress",color=muted,fontSize=12.sp);val g=goals.firstOrNull();Text(if(g!=null)"${money(g.saved)} / ${money(g.target)}" else "Create your first savings goal",color=text,fontWeight=FontWeight.Bold)};Icon(Icons.Default.Savings,null,tint=Green,modifier=Modifier.size(30.dp))};val g=goals.firstOrNull();if(g!=null){LinearProgressIndicator(progress={(g.saved/g.target).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth().padding(top=10.dp),color=Green);Text("${(g.saved/g.target*100).roundToInt().coerceAtMost(100)}% completed",color=muted,fontSize=11.sp,modifier=Modifier.align(Alignment.End))}} }
        item { Text("Quick Actions",color=text,fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={onAdd("income")},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Green,contentColor=DarkBg)){Icon(Icons.Default.Add,null);Text("Income")};Button(onClick={onAdd("expense")},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Rose,contentColor=DarkBg)){Icon(Icons.Default.Add,null);Text("Expense")}} }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Recent Transactions",color=text,fontWeight=FontWeight.Bold);TextButton(onClick=onHistory){Text("See All")}} }
        if(tx.isEmpty()) item { Text("No transactions recorded yet.",color=muted,modifier=Modifier.fillMaxWidth().padding(20.dp)) } else items(tx.sortedByDescending{it.id}.take(5)){ TransactionRow(it,text,muted,card,{}, {},false) }
    }
}
@Composable private fun SummaryCard(title:String,value:String,color:Color,icon:androidx.compose.ui.graphics.vector.ImageVector,card:Color,text:Color,modifier:Modifier){AppCard(modifier,card){Icon(icon,null,tint=color);Text(title,color=Color.Gray,fontSize=11.sp);Text(value,color=color,fontWeight=FontWeight.Bold,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}

@Composable private fun CategoryIcon(category:String, selected:Boolean=false){
    val (icon,color)=when(category){"Food & Dining"->Icons.Default.Restaurant to Amber;"Shopping"->Icons.Default.ShoppingCart to Rose;"Bills"->Icons.Default.Bolt to Amber;"Transport"->Icons.Default.DirectionsCar to Teal;"Salary"->Icons.Default.Work to Blue;"Freelance"->Icons.Default.Laptop to Violet;"Investments"->Icons.Default.TrendingUp to Green;else->Icons.Default.Wallet to Green}
    val scale by animateFloatAsState(if(selected)1.17f else 1f, spring(dampingRatio=.42f),label="iconScale")
    val rotation by animateFloatAsState(if(selected)-8f else 0f,spring(dampingRatio=.35f),label="iconRotation")
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(.14f)),contentAlignment=Alignment.Center){Icon(icon,null,tint=color,modifier=Modifier.size(23.dp).scale(scale).rotate(rotation))}
}
@Composable private fun TransactionRow(t:Tx,text:Color,muted:Color,card:Color,onEdit:()->Unit,onDelete:()->Unit,actions:Boolean=true){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(card).clickable(enabled=actions,onClick=onEdit).padding(12.dp),verticalAlignment=Alignment.CenterVertically){CategoryIcon(t.category);Column(Modifier.weight(1f).padding(horizontal=11.dp)){Text(t.desc,color=text,fontWeight=FontWeight.Bold,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${t.category} • ${t.date}",color=muted,fontSize=10.sp)};Column(horizontalAlignment=Alignment.End){Text((if(t.type=="income")"+" else "−")+money(t.amount),color=if(t.type=="income")Green else Rose,fontWeight=FontWeight.Bold,fontSize=13.sp);if(actions)Row{IconButton(onClick=onEdit,modifier=Modifier.size(28.dp)){Icon(Icons.Default.Edit,null,tint=muted,modifier=Modifier.size(15.dp))};IconButton(onClick=onDelete,modifier=Modifier.size(28.dp)){Icon(Icons.Default.Delete,null,tint=Rose,modifier=Modifier.size(15.dp))}}}}}

@Composable private fun HistoryScreen(text:Color,muted:Color,card:Color,tx:List<Tx>,query:String,onQuery:(String)->Unit,filter:String,onFilter:(String)->Unit,onEdit:(Tx)->Unit,onDelete:(Tx)->Unit,onAdd:(String)->Unit){
    val shown=tx.filter{(filter=="All"||it.type==filter.lowercase())&&(it.desc.contains(query,true)||it.category.contains(query,true))}.sortedByDescending{it.id}
    Column(Modifier.fillMaxSize().padding(18.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Transactions",color=text,fontSize=23.sp,fontWeight=FontWeight.Bold);Text("${shown.size} items",color=muted,fontSize=12.sp)};Spacer(Modifier.height(12.dp));OutlinedTextField(query,onQuery,modifier=Modifier.fillMaxWidth(),placeholder={Text("Search transactions...")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true);Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","Income","Expense").forEach{FilterChip(selected=filter==it,onClick={onFilter(it)},label={Text(it)})}};Spacer(Modifier.height(10.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.weight(1f)){if(shown.isEmpty())item{Text("No matching transactions.",color=muted,modifier=Modifier.padding(20.dp))};items(shown,key={it.id}){t->TransactionRow(t,text,muted,card,{onEdit(t)},{onDelete(t)})}};Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={onAdd("income")},modifier=Modifier.weight(1f)){Text("+ Income")};Button(onClick={onAdd("expense")},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Rose)){Text("+ Expense")}}}
}

@Composable private fun ReportsScreen(text:Color,muted:Color,card:Color,tx:List<Tx>,all:Boolean,onAll:(Boolean)->Unit){
    val filtered=if(all)tx else tx.filter{it.date.startsWith(monthKey())};val inc=filtered.filter{it.type=="income"}.sumOf{it.amount};val exp=filtered.filter{it.type=="expense"}.sumOf{it.amount};val cats=filtered.filter{it.type=="expense"}.groupBy{it.category}.mapValues{e->e.value.sumOf{it.amount}}.toList().sortedByDescending{it.second};val colors=listOf(Amber,Blue,Rose,Violet,Teal,Green)
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Reports",fontSize=23.sp,fontWeight=FontWeight.Bold,color=text);Row(verticalAlignment=Alignment.CenterVertically){Text("All time",color=muted,fontSize=11.sp);Switch(all,onAll)}}};item{AppCard(card=card){Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Canvas(Modifier.size(190.dp)){val total=cats.sumOf{it.second}.coerceAtLeast(1.0);var start=-90f;cats.forEachIndexed{index,e->val sweep=(e.second/total*360).toFloat();drawArc(colors[index%colors.size],start,sweep,false,style=Stroke(22.dp.toPx(),cap=StrokeCap.Butt));start+=sweep}};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("TOTAL SPENT",color=muted,fontSize=10.sp);Text(money(exp),color=text,fontWeight=FontWeight.ExtraBold,fontSize=20.sp)}}}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){SummaryCard("Total Income",money(inc),Green,Icons.Default.ArrowDownward,card,text,Modifier.weight(1f));SummaryCard("Net Balance",money(inc-exp),Blue,Icons.Default.AccountBalance,card,text,Modifier.weight(1f))}};item{Text("Expenses by Category",color=text,fontWeight=FontWeight.Bold)};items(cats){(cat,amount)->AppCard(card=card){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Row(verticalAlignment=Alignment.CenterVertically){CategoryIcon(cat);Text(cat,color=text,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=9.dp))};Text("${money(amount)} (${(amount/exp.coerceAtLeast(1.0)*100).roundToInt()}%)",color=muted,fontSize=12.sp)};LinearProgressIndicator(progress={(amount/exp.coerceAtLeast(1.0)).toFloat()},modifier=Modifier.fillMaxWidth().padding(top=9.dp),color=Green)}};if(cats.isEmpty())item{Text("No expense breakdown recorded.",color=muted)} }
}

@Composable private fun GoalsScreen(text:Color,muted:Color,card:Color,goals:List<Goal>,onAdd:()->Unit,onDelete:(Goal)->Unit,onAddSaved:(Goal,Double)->Unit){var selected by remember{mutableStateOf<Goal?>(null)};var amount by remember{mutableStateOf("")};LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Savings Goals",fontSize=23.sp,fontWeight=FontWeight.Bold,color=text);Icon(Icons.Default.Savings,null,tint=Green)}};if(goals.isEmpty())item{Text("No savings goals created yet.",color=muted)};items(goals){g->AppCard(card=card){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.EmojiEvents,null,tint=Amber,modifier=Modifier.size(34.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text(g.name,color=text,fontWeight=FontWeight.Bold);Text("Target: ${money(g.target)} • ${g.date}",color=muted,fontSize=11.sp)};IconButton(onClick={onDelete(g)}){Icon(Icons.Default.Delete,null,tint=Rose)}};LinearProgressIndicator(progress={(g.saved/g.target).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth().padding(vertical=9.dp),color=Green);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Saved: ${money(g.saved)}",color=muted,fontSize=11.sp);Text("${(g.saved/g.target*100).roundToInt().coerceAtMost(100)}%",color=Green,fontWeight=FontWeight.Bold)};TextButton(onClick={selected=g}){Text("+ Add Saved")}}};item{Button(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text("+ Create New Goal")}}};selected?.let{g->AmountDialog("Add savings to ${g.name}",0.0,{selected=null},{onAddSaved(g,it);selected=null})}}

@Composable private fun SettingsScreen(text:Color,muted:Color,card:Color,profile:Profile,dark:Boolean,budget:Double,pin:Boolean,recurring:List<Recurring>,onProfile:()->Unit,onDark:()->Unit,onBudget:()->Unit,onPin:()->Unit,onLock:()->Unit,onRecurring:()->Unit,onRemovePin:()->Unit,onBackup:()->Unit,onRestore:()->Unit,onReset:()->Unit){LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Settings",fontSize=23.sp,fontWeight=FontWeight.Bold,color=text)};item{AppCard(card=card){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AccountCircle,null,tint=Green,modifier=Modifier.size(42.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text(profile.name,color=text,fontWeight=FontWeight.Bold);Text(profile.email,color=muted,fontSize=11.sp)};TextButton(onClick=onProfile){Text("Edit")}}}};item{SettingRow("Card Holder Name",profile.holder,Icons.Default.CreditCard,text,muted,card,onProfile)};item{AppCard(card=card){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Dark Mode",color=text,fontWeight=FontWeight.Medium);Switch(dark,onCheckedChange={onDark()})};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Currency",color=text);Text("Indian Rupee (₹)",color=muted,fontSize=12.sp)}}};item{SettingRow("Monthly Budget",if(budget>0)money(budget) else "Not set",Icons.Default.Wallet,text,muted,card,onBudget)};item{SettingRow("Recurring Transactions","${recurring.size} monthly entries",Icons.Default.Repeat,text,muted,card,onRecurring)};item{AppCard(card=card){Text("App PIN Lock",color=text,fontWeight=FontWeight.Bold);Text(if(pin)"PIN lock is enabled on this device." else "PIN lock is off.",color=muted,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onPin,modifier=Modifier.weight(1f)){Text("Set / Change PIN")};OutlinedButton(onClick=onLock,modifier=Modifier.weight(1f)){Text("Lock Now")}};TextButton(onClick=onRemovePin){Text("Turn off PIN lock",color=Rose)}}};item{AppCard(card=card){Text("Data Management",color=text,fontWeight=FontWeight.Bold);Text("Data is saved automatically on this phone.",color=Green,fontSize=11.sp);Text("Backup and restore are supported through app storage.",color=muted,fontSize=11.sp);Button(onClick=onBackup,modifier=Modifier.fillMaxWidth()){Text("Backup Data (JSON)")};OutlinedButton(onClick=onRestore,modifier=Modifier.fillMaxWidth()){Text("Restore Backup")}}};item{OutlinedButton(onClick=onReset,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=Rose)){Text("Reset Application Data")}}}}
@Composable private fun SettingRow(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,text:Color,muted:Color,card:Color,onClick:()->Unit){AppCard(Modifier.fillMaxWidth().clickable(onClick=onClick),card){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green);Column(Modifier.weight(1f).padding(start=12.dp)){Text(title,color=text,fontWeight=FontWeight.Medium);Text(value,color=muted,fontSize=11.sp)};Icon(Icons.Default.ChevronRight,null,tint=muted)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TransactionDialog(existing:Tx?,initialType:String,all:List<Tx>,onClose:()->Unit,onType:(String)->Unit,onSave:(Tx)->Unit){var desc by remember{mutableStateOf(existing?.desc?:"")};var amount by remember{mutableStateOf(existing?.amount?.toString()?:"")};var category by remember{mutableStateOf(existing?.category?:if(initialType=="income")"Salary" else "Food & Dining")};var date by remember{mutableStateOf(existing?.date?:today())};var recurring by remember{mutableStateOf(false)};var error by remember{mutableStateOf(false)};val type=initialType;val categories=if(type=="income")listOf("Salary","Freelance","Investments","Others") else listOf("Food & Dining","Bills","Shopping","Transport","Others")
    AlertDialog(onDismissRequest=onClose,title={Text(if(existing==null)"Add ${type.replaceFirstChar{it.uppercase()}}" else "Edit Transaction")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(type=="income",{onType("income")},{Text("Income")});FilterChip(type=="expense",{onType("expense")},{Text("Expense")})};OutlinedTextField(desc,{desc=it},label={Text("Description")},singleLine=true);OutlinedTextField(amount,{amount=it},label={Text("Amount (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);Text("Category",fontWeight=FontWeight.SemiBold);categories.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{c->FilterChip(category==c,{category=c},{Row(verticalAlignment=Alignment.CenterVertically){CategoryIcon(c,category==c);Text(c,fontSize=10.sp,modifier=Modifier.padding(start=4.dp))}})}}};TextButton(onClick={date=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())}){Text("Date: $date")};if(error)Text("Enter a description and valid amount greater than zero.",color=Rose,fontSize=11.sp)}},confirmButton={TextButton(onClick={val v=amount.toDoubleOrNull();if(desc.isBlank()||v==null||v<=0)error=true else onSave(Tx(existing?.id?:System.currentTimeMillis(),desc.trim(),v,type,category,date))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun GoalDialog(onClose:()->Unit,onSave:(Goal)->Unit){var name by remember{mutableStateOf("")};var target by remember{mutableStateOf("")};var saved by remember{mutableStateOf("0")};var date by remember{mutableStateOf(today())};var error by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onClose,title={Text("Create Savings Goal")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Goal Name")});OutlinedTextField(target,{target=it},label={Text("Target Amount (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));OutlinedTextField(saved,{saved=it},label={Text("Already Saved (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));TextButton(onClick={date=today()}){Text("Target Date: $date")};if(error)Text("Enter a valid goal and target.",color=Rose)}},confirmButton={TextButton(onClick={val t=target.toDoubleOrNull();val s=saved.toDoubleOrNull();if(name.isBlank()||t==null||t<=0||s==null||s<0)error=true else onSave(Goal(System.currentTimeMillis(),name.trim(),t,s,date))}){Text("Create")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
@Composable private fun AmountDialog(title:String,initial:Double,onClose:()->Unit,onSave:(Double)->Unit) {
    var value by remember { mutableStateOf(if(initial>0) initial.toString() else "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onClose, title={Text(title)}, text={Column {
        OutlinedTextField(value,{value=it},label={Text("Amount (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        if(error) Text("Enter a valid amount greater than zero",color=Rose)
    }}, confirmButton={TextButton(onClick={
        val v=value.toDoubleOrNull()
        if(v==null || v<=0) error=true else onSave(v)
    }) { Text("Save") }}, dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun ProfileDialog(profile:Profile,onClose:()->Unit,onSave:(Profile)->Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var email by remember { mutableStateOf(profile.email) }
    var holder by remember { mutableStateOf(profile.holder) }
    AlertDialog(onDismissRequest=onClose,title={Text("Edit Profile")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name,{name=it},label={Text("Name")})
        OutlinedTextField(email,{email=it},label={Text("Email")})
        OutlinedTextField(holder,{holder=it},label={Text("Card Holder Name")})
    }},confirmButton={TextButton(onClick={onSave(Profile(name.ifBlank{"User"},email.ifBlank{"Not added"},holder.ifBlank{"Money Manager User"}))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun PinDialog(oldHash:String,onClose:()->Unit,onSave:(String)->Unit){var pin by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var error by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onClose,title={Text("Set App PIN")},text={Column{OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(12)},label={Text("4–12 digit PIN")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword));OutlinedTextField(confirm,{confirm=it.filter(Char::isDigit).take(12)},label={Text("Confirm PIN")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword));if(error)Text("PINs must match and contain 4–12 digits.",color=Rose)}},confirmButton={TextButton(onClick={if(pin.length in 4..12&&pin==confirm)onSave(hashPin(pin))else error=true}){Text("Enable")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
@Composable private fun RecurringDialog(list:List<Recurring>,onClose:()->Unit,onAdd:(Recurring)->Unit,onDelete:(Recurring)->Unit){var desc by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var type by remember{mutableStateOf("expense")};var category by remember{mutableStateOf("Bills")};var day by remember{mutableStateOf("1")};var error by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onClose,title={Text("Recurring Transactions")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){if(list.isNotEmpty())list.forEach{r->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r.desc,fontWeight=FontWeight.SemiBold);Text("${money(r.amount)} • day ${r.day}",fontSize=11.sp,color=Color.Gray)};IconButton(onClick={onDelete(r)}){Icon(Icons.Default.Delete,null,tint=Rose)}}};Divider();OutlinedTextField(desc,{desc=it},label={Text("Description")});OutlinedTextField(amount,{amount=it},label={Text("Amount")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));Row{FilterChip(type=="income",{type="income";category="Salary"},{Text("Income")});FilterChip(type=="expense",{type="expense";category="Bills"},{Text("Expense")})};OutlinedTextField(category,{category=it},label={Text("Category")});OutlinedTextField(day,{day=it.filter(Char::isDigit).take(2)},label={Text("Day of month (1–28)")});if(error)Text("Check all recurring fields.",color=Rose)}},confirmButton={TextButton(onClick={val v=amount.toDoubleOrNull();val d=day.toIntOrNull();if(desc.isBlank()||v==null||v<=0||d==null||d !in 1..28)error=true else onAdd(Recurring(System.currentTimeMillis(),desc,v,type,category,d,""))}){Text("Add Monthly")}},dismissButton={TextButton(onClick=onClose){Text("Close")}})}
