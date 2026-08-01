package com.qiyue.cashflow.ui

import com.qiyue.cashflow.BuildConfig
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiyue.cashflow.cashflow.CashFlowDay
import com.qiyue.cashflow.data.*
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val Green = Color(0xFF147D64)
private val Red = Color(0xFFB3261E)
private val Paper = Color(0xFFF5F6F2)
private enum class Tab(val title: String, val icon: String) {
    HOME("首页", "⌂"), PLAN("计划", "▤"), ACCOUNT("账户", "¥"), SETTINGS("设置", "⚙")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowApp(vm: CashFlowViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, error = Red, surface = Color.White)) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(tab.title, fontWeight = FontWeight.Bold) }) },
            bottomBar = { NavigationBar {
                Tab.entries.forEach { item -> NavigationBarItem(
                    selected = tab == item,
                    onClick = { tab = item },
                    icon = { Text(item.icon) },
                    label = { Text(item.title) }
                ) }
            }},
            containerColor = Paper,
            snackbarHost = { state.message?.let { msg ->
                Snackbar(modifier=Modifier.padding(12.dp), action={ TextButton(onClick=vm::dismissMessage){Text("知道了")} }) { Text(msg) }
            }}
        ) { pad ->
            when (tab) {
                Tab.HOME -> HomePage(state, pad, vm::setHorizon)
                Tab.PLAN -> PlanPage(state, pad, vm)
                Tab.ACCOUNT -> AccountPage(state, pad, vm)
                Tab.SETTINGS -> SettingsPage(state, pad, vm)
            }
        }
        if (state.isReady && !state.settings.onboardingCompleted) {
            OnboardingDialog(vm::completeOnboarding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OnboardingDialog(
    complete: (Set<AccountType>, Boolean) -> Unit,
) {
    var accounts by remember {
        mutableStateOf(setOf(AccountType.CMB, AccountType.WECHAT, AccountType.ALIPAY, AccountType.CASH))
    }
    var examples by remember { mutableStateOf(true) }
    val accountOptions = listOf(
        AccountType.CMB, AccountType.WECHAT, AccountType.ALIPAY,
        AccountType.CASH, AccountType.PAYPAL, AccountType.CRYPTO,
    )
    BasicAlertDialog(onDismissRequest = {}) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(
                Modifier.padding(18.dp).heightIn(max = 700.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("欢迎使用", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("选择要建立的账户。以后仍可随时增加或隐藏。", color = Color.Gray)
                Text("初始账户", fontWeight = FontWeight.Bold)
                accountOptions.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(type in accounts, { checked ->
                            accounts = if (checked) accounts + type else accounts - type
                        })
                        Text(accountTypeLabel(type))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(examples, { examples = it })
                    Text("加入虚构示例，方便立即查看预测")
                }
                Text("示例会标明“示例”，不含任何人的真实资料，并可在设置中一键清空。", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { complete(accounts, examples) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = accounts.isNotEmpty(),
                ) { Text("开始使用") }
            }
        }
    }
}

@Composable private fun HomePage(state: CashFlowUiState, pad: PaddingValues, onHorizon: (Int)->Unit) {
    val s = state.summary
    val hiddenHomeCurrencies = remember(state.settings.hiddenHomeCurrenciesCsv) {
        parseCurrencyCodeCsv(state.settings.hiddenHomeCurrenciesCsv)
    }
    val otherCurrencyBalances = state.accounts
        .filter {
            it.isActive && it.includeInForecast &&
                it.currencyCode !in setOf("CNY", "USD", "CRYPTO") &&
                it.currencyCode !in hiddenHomeCurrencies
        }
        .groupBy { it.currencyCode }
        .mapValues { (_, accounts) -> accounts.sumOf { it.currentBalanceCents } }
        .toSortedMap()
    val monthEnd = s.days.firstOrNull { it.date == LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()) }?.balanceCents
        ?: s.endBalanceCents
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors=CardDefaults.cardColors(containerColor=if(s.firstShortageDate!=null) Color(0xFFFFEDEA) else Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("真实余额", fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleMedium)
                    Text("人民币", color=Color.Gray)
                    Text(money(s.currentBalanceCents), style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
                    if("USD" !in hiddenHomeCurrencies&&state.accounts.any{it.currencyCode=="USD"}) Metric("美元", usdMoney(s.currentUsdCents))
                    otherCurrencyBalances.forEach { (currency, amount) ->
                        Metric(currencyLabel(currency), currencyMoney(amount, currency))
                    }
                    if("CRYPTO" !in hiddenHomeCurrencies&&state.accounts.any{it.currencyCode=="CRYPTO"}) {
                        Text("数字货币",color=Color.Gray)
                        Text(cryptoSummary(state.cryptoHoldings),fontWeight=FontWeight.SemiBold)
                    }
                    HorizontalDivider(Modifier.padding(vertical=4.dp))
                    Text("预计余额", fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleMedium)
                    Metric("月底", money(monthEnd))
                    Metric("谷底", "${money(s.lowestBalanceCents)} · ${s.lowestDate ?: "—"}", s.lowestBalanceCents < 0)
                    Metric("高峰", "${money(s.highestBalanceCents)} · ${s.highestDate ?: "—"}")
                    Metric("预算剩余", money(s.budgetRemainingCents))
                    Metric(
                        "首次现金为负",
                        s.firstShortageDate?.toString() ?: "无",
                        s.firstShortageDate != null
                    )
                }
            }
        }
        item { ChoiceRow(listOf(30,90,365), state.horizonDays, onHorizon) { "${it}天" } }
        item {
            Card { Column(Modifier.padding(12.dp)) {
                Text("人民币未来余额", fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                ForecastChart(s.days, Modifier.fillMaxWidth().height(260.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("今天 ${money(s.currentBalanceCents)}", color=Color.Gray)
                    Text("${state.horizonDays}天后 ${money(s.endBalanceCents)}", fontWeight=FontWeight.SemiBold)
                }
            }}
        }
        item { Text("即将发生", fontWeight=FontWeight.Bold) }
        val upcoming = s.days.flatMap { d -> d.events.filterNot { it.kind == "预算" }.map { d to it } }.take(20)
        if (upcoming.isEmpty()) item { EmptyCard("暂无未来款项，请到“计划”添加固定收支。") }
        else items(upcoming) { (day,event) ->
            Card { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(event.name, fontWeight=FontWeight.SemiBold)
                    Text("${day.date} · ${event.kind} · ${event.accountName}", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
                }
                Text(if(event.amountCents==0L) "转账" else signedCurrencyMoney(event.amountCents,event.currencyCode),
                    color=if(event.amountCents<0) Red else Green, fontWeight=FontWeight.Bold)
            }}
        }
    }
}

@Composable private fun ForecastChart(days: List<CashFlowDay>, modifier: Modifier) {
    val values = days.map { it.balanceCents.toFloat() }
    var selectedIndex by remember(days) { mutableStateOf<Int?>(null) }
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 1f
    val yTicks = listOf(1f,.75f,.5f,.25f,0f).map { fraction -> min + (max-min)*fraction }
    val range = (max-min).coerceAtLeast(1f)
    Column(modifier, verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.width(62.dp).fillMaxHeight(),
                verticalArrangement=Arrangement.SpaceBetween
            ) {
                yTicks.forEach { tick ->
                    Text(money(tick.toLong()), color=Color.Gray, style=MaterialTheme.typography.labelSmall, maxLines=1)
                }
            }
            Canvas(
                Modifier.weight(1f).fillMaxHeight().pointerInput(days) {
                    detectTapGestures { position ->
                        if (days.isNotEmpty()) {
                            selectedIndex = (
                                position.x / size.width * days.lastIndex.coerceAtLeast(1)
                            ).roundToInt().coerceIn(0, days.lastIndex)
                        }
                    }
                }
            ) {
                if(values.size < 2) return@Canvas
                listOf(0f,.25f,.5f,.75f,1f).forEach { fraction ->
                    drawLine(
                        Color.Gray.copy(alpha=.18f),
                        start=androidx.compose.ui.geometry.Offset(0f,size.height*fraction),
                        end=androidx.compose.ui.geometry.Offset(size.width,size.height*fraction),
                        strokeWidth=1f
                    )
                    drawLine(
                        Color.Gray.copy(alpha=.12f),
                        start=androidx.compose.ui.geometry.Offset(size.width*fraction,0f),
                        end=androidx.compose.ui.geometry.Offset(size.width*fraction,size.height),
                        strokeWidth=1f
                    )
                }
                if (min < 0 && max > 0) {
                    val zeroY = size.height - (0-min)/range*size.height
                    drawLine(Red.copy(alpha=.38f),androidx.compose.ui.geometry.Offset(0f,zeroY),androidx.compose.ui.geometry.Offset(size.width,zeroY),strokeWidth=2f)
                }
                val p = Path()
                values.forEachIndexed { i,v ->
                    val x = size.width*i/(values.lastIndex.coerceAtLeast(1))
                    val y = size.height-(v-min)/range*size.height
                    if(i==0) p.moveTo(x,y) else p.lineTo(x,y)
                }
                drawPath(p, color=if(values.any{it<0}) Red else Green, style=androidx.compose.ui.graphics.drawscope.Stroke(3f))
                selectedIndex?.let { index ->
                    val x = size.width*index/(values.lastIndex.coerceAtLeast(1))
                    val y = size.height-(values[index]-min)/range*size.height
                    val point = androidx.compose.ui.geometry.Offset(x,y)
                    drawLine(
                        Color.Gray.copy(alpha=.45f),
                        start=androidx.compose.ui.geometry.Offset(x,0f),
                        end=androidx.compose.ui.geometry.Offset(x,size.height),
                        strokeWidth=2f
                    )
                    drawCircle(Color.White,radius=8f,center=point)
                    drawCircle(Green,radius=5f,center=point)
                }
            }
        }
        if(days.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(62.dp))
                listOf(0,days.lastIndex/4,days.lastIndex/2,days.lastIndex*3/4,days.lastIndex).forEach { index ->
                    Text(monthDay(days[index].date),Modifier.weight(1f),color=Color.Gray,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=1)
                }
            }
        }
        selectedIndex?.let { index ->
            Text(
                "${days[index].date} · ${money(days[index].balanceCents)}",
                Modifier.fillMaxWidth(),
                color=Green,
                fontWeight=FontWeight.SemiBold
            )
        } ?: Text("点按曲线查看某一天", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun PlanPage(state: CashFlowUiState, pad: PaddingValues, vm: CashFlowViewModel) {
    var planEdit by remember { mutableStateOf<PlannedTransactionEntity?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    var budgetEdit by remember { mutableStateOf<BudgetEntity?>(null) }
    var showBudget by remember { mutableStateOf(false) }
    var adjustmentEdit by remember { mutableStateOf<AdjustmentEntity?>(null) }
    var showAdjustment by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    val temporaryAdjustments = state.adjustments.filter { it.type == AdjustmentType.TEMPORARY }
    val visiblePlans = state.plans.filter { it.type != EntryType.TRANSFER }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({planEdit=null;showPlan=true}){Text("＋ 固定款项")}
            OutlinedButton({adjustmentEdit=null;showAdjustment=true}){Text("＋ 未来临时收支")}
        }}
        item { Section("固定收入、支出与分期") }
        if(visiblePlans.isEmpty()) item { EmptyCard("还没有固定款项。先添加工资或房租。") }
        items(visiblePlans, key={"plan-${it.id}"}) { p -> DataCard(
            title=p.name,
            subtitle=buildString {
                append("${entryLabel(p.type)} · ${repeatLabel(p.repeatRule)} · ${p.accountName}")
                if(p.installmentTotal>1) append(" · 分期 · 剩余${p.installmentRemaining}期")
            },
            amount=if(p.installmentTotal>1) "每期约 ${currencyMoney(p.amountCents,p.currencyCode)}" else currencyMoney(p.amountCents,p.currencyCode), active=p.isActive,
            onToggle={vm.savePlan(p.copy(isActive=!p.isActive))}, onEdit={planEdit=p;showPlan=true},
            onDelete={delete="“${p.name}”" to {vm.deletePlan(p.id)}}
        )}
        item { Section("总预算") }
        if(state.budgets.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmptyCard("暂无预算。预算会作为未来支出进入预测。")
                OutlinedButton({budgetEdit=null;showBudget=true}, Modifier.fillMaxWidth()){Text("设置预算")}
            }
        }
        items(state.budgets.take(1), key={"budget-${it.id}"}) { b -> DataCard(
            b.name,
            "${LocalDate.ofEpochDay(b.startDateEpochDay)} 至 ${LocalDate.ofEpochDay(b.endDateEpochDay)} · 用于未来现金预测",
            "剩余 ${money(b.remainingCents)}",
            b.isActive, {vm.saveBudget(b.copy(isActive=!b.isActive))}, {budgetEdit=b;showBudget=true},
            {delete="“${b.name}”" to {vm.deleteBudget(b.id)}})
        }
        item { Section("未来临时收支") }
        if(temporaryAdjustments.isEmpty()) item { EmptyCard("暂无未来临时收支。实际余额有偏差时，直接修改账户真实余额即可。") }
        items(temporaryAdjustments, key={"adjustment-${it.id}"}) { a -> DataCard(
            a.name, buildString {
                append(if(a.isIncome)"临时收入" else "临时支出")
                append(" · ${LocalDate.ofEpochDay(a.dateEpochDay)}")
                if(a.appliedToBalance) append(" · 已计入真实余额")
            },
            currencyMoney(
                a.amountCents,
                state.accounts.firstOrNull { it.name == a.accountName }?.currencyCode ?: "CNY"
            ), true, null,
            if(a.type==AdjustmentType.TEMPORARY && !a.appliedToBalance) {{adjustmentEdit=a;showAdjustment=true}} else null,
            {delete="“${a.name}”" to {vm.deleteAdjustment(a.id)}},
            extraAction = if(
                a.type==AdjustmentType.TEMPORARY && !a.appliedToBalance &&
                a.dateEpochDay<=LocalDate.now().toEpochDay()
            ) "计入真实余额" to {vm.applyAdjustmentToBalance(a)} else null)
        }
    }
    if(showPlan) PlanDialog(planEdit, state, {showPlan=false}) {vm.savePlan(it);showPlan=false}
    if(showBudget) BudgetDialog(budgetEdit, state, {showBudget=false}) {vm.saveBudget(it);showBudget=false}
    if(showAdjustment) AdjustmentDialog(adjustmentEdit, state, {showAdjustment=false}) {vm.saveAdjustment(it);showAdjustment=false}
    delete?.let { (name, action) -> ConfirmDialog("删除$name？","删除后无法恢复。",{delete=null},{action();delete=null}) }
}

@Composable private fun AccountPage(state: CashFlowUiState, pad: PaddingValues, vm: CashFlowViewModel) {
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showCryptoEditor by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val hiddenAccountIds = remember(state.settings.hiddenAccountIdsCsv) {
        parseAccountIdCsv(state.settings.hiddenAccountIdsCsv)
    }
    val visibleAccounts = state.accounts.filter { it.id !in hiddenAccountIds }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({editing=null;showEditor=true},Modifier.weight(1f)){Text("＋ 增加账户")}
            OutlinedButton(
                {showTransfer=true},
                Modifier.weight(1f),
                enabled=state.accounts.filter{it.currencyCode!="CRYPTO"}.groupBy{it.currencyCode}.any{it.value.size>=2}
            ){Text("账户间转账")}
        }}
        if(visibleAccounts.isEmpty()) item { EmptyCard("没有需要显示的账户，可到“设置”恢复隐藏账户。") }
        items(visibleAccounts, key={it.id}) { a ->
            val endNative = state.summary.days.lastOrNull()?.accountBalancesCents?.get(a.name) ?: a.currentBalanceCents
            var balanceText by remember(a.id,a.currentBalanceCents) { mutableStateOf(currencyDecimal(a.currentBalanceCents,a.currencyCode)) }
            var showCalculator by remember(a.id) { mutableStateOf(false) }
            var wasFocused by remember(a.id) { mutableStateOf(false) }
            fun saveBalance() {
                parseCurrencyExpression(balanceText,a.currencyCode)?.let { balance ->
                    if(balance!=a.currentBalanceCents) vm.calibrate(a,balance)
                }
            }
            Card { Column(Modifier.padding(15.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text("${currencyLabel(a.currencyCode)} · ${accountTypeLabel(a.accountType)}", color=Color.Gray)
                    }
                    if(a.currencyCode=="CRYPTO") Column(horizontalAlignment=Alignment.End) {
                        Text(
                            cryptoSummary(state.cryptoHoldings),
                            fontWeight=FontWeight.Bold
                        )
                    }
                }
                if(a.currencyCode!="CRYPTO") {
                    OutlinedTextField(
                        value=balanceText,
                        onValueChange={balanceText=it},
                        modifier=Modifier.fillMaxWidth().onFocusChanged { focusState ->
                            if(wasFocused && !focusState.isFocused) saveBalance()
                            wasFocused=focusState.isFocused
                        },
                        label={Text("真实余额")},
                        singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={saveBalance();focusManager.clearFocus()})
                    )
                    OutlinedButton({showCalculator=true},Modifier.fillMaxWidth()){Text("打开计算器")}
                    Text("直接修改后按键盘“完成”即可保存。", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
                    Text("未来${state.horizonDays}天预计余额 ${nativeMoney(endNative,a.currencyCode)}", color=Green)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(a.currencyCode=="CRYPTO") AssistChip({showCryptoEditor=true},{Text("修改持仓")})
                    AssistChip({editing=a;showEditor=true},{Text("编辑")})
                }
            }}
            if(showCalculator) BalanceCalculatorDialog(
                balanceText,a.currencyCode,{showCalculator=false}
            ){result->
                balanceText=result
                parseCurrencyExpression(result,a.currencyCode)?.let{vm.calibrate(a,it)}
                showCalculator=false
            }
        }
    }
    if(showEditor) AccountDialog(editing,{showEditor=false}){vm.saveAccount(it);showEditor=false}
    if(showTransfer) AccountTransferDialog(state.accounts,{showTransfer=false}){fromId,toId,amount->
        vm.transferBalance(fromId,toId,amount);showTransfer=false
    }
    if(showCryptoEditor) CryptoHoldingsDialog(state.cryptoHoldings,{showCryptoEditor=false}) {
        vm.saveCryptoHoldings(it);showCryptoEditor=false
    }
}

@Composable private fun SettingsPage(state: CashFlowUiState, pad: PaddingValues, vm: CashFlowViewModel) {
    val scope = rememberCoroutineScope()
    var days by remember(state.settings.defaultHorizonDays) { mutableIntStateOf(state.settings.defaultHorizonDays) }
    var budgetStartDay by remember(state.settings.budgetMonthStartDay) { mutableStateOf(state.settings.budgetMonthStartDay.toString()) }
    var hiddenHomeCurrencies by remember(state.settings.hiddenHomeCurrenciesCsv,state.accounts.map{it.currencyCode}) {
        mutableStateOf(parseCurrencyCodeCsv(state.settings.hiddenHomeCurrenciesCsv))
    }
    var hiddenAccountIds by remember(state.settings.hiddenAccountIdsCsv, state.accounts.map { it.id }) {
        mutableStateOf(parseAccountIdCsv(state.settings.hiddenAccountIdsCsv))
    }
    val homeCurrencies=state.accounts.map{it.currencyCode}.filter{it!="CNY"}.distinct().sortedWith(
        compareBy<String>{when(it){"USD"->1;"CRYPTO"->99;else->10}}.thenBy{it}
    )
    var confirmClear by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { scope.launch { runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { w -> w.write(vm.exportJson()) } } } }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }.getOrNull()?.let(vm::restoreJson) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Section("预测设置")
            Text("默认预测周期")
            ChoiceRow(listOf(30,90,365),days,{days=it}){"${it}天"}
            TextFieldSimple(budgetStartDay,{budgetStartDay=it},"每月预算从几号开始（1—28）",KeyboardType.Number)
            Text("当前按每月 ${budgetStartDay.toIntOrNull()?.coerceIn(1,28) ?: 21} 日至次月前一天计算。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Section("首页显示币种")
            Text("人民币固定显示",color=Color.Gray)
            homeCurrencies.forEach { currency ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(currency !in hiddenHomeCurrencies,{visible->
                        hiddenHomeCurrencies=if(visible)hiddenHomeCurrencies-currency else hiddenHomeCurrencies+currency
                    })
                    Text(currencyLabel(currency))
                }
            }
            if(homeCurrencies.isEmpty()) Text("目前只有人民币账户。",color=Color.Gray)
            Text("不同币种分别显示，不做汇率换算。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Section("账户页显示")
            if (state.accounts.isEmpty()) Text("还没有账户。",color=Color.Gray)
            state.accounts.forEach { account ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(account.id !in hiddenAccountIds,{ visible ->
                        hiddenAccountIds = if (visible) hiddenAccountIds - account.id else hiddenAccountIds + account.id
                    })
                    Column {
                        Text(account.name)
                        Text(currencyLabel(account.currencyCode),color=Color.Gray,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("这里只控制账户页是否显示，不影响余额和预测。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            Button({vm.updateSettings(
                days,budgetStartDay.toIntOrNull()?:21,hiddenHomeCurrencies,hiddenAccountIds
            )},Modifier.fillMaxWidth()){Text("保存全部设置")}
        }}}
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Section("本地数据")
            Button({create.launch("现金流预测备份-${LocalDate.now()}.json")},Modifier.fillMaxWidth()){Text("导出 JSON 完整备份")}
            OutlinedButton({open.launch(arrayOf("application/json","text/*"))},Modifier.fillMaxWidth()){Text("从 JSON 恢复")}
            OutlinedButton({confirmClear=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=Red)){Text("清空测试数据")}
            Text("所有数据只保存在本机；恢复会替换测试版当前数据。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
        }}}
        item { Card { Column(Modifier.padding(16.dp)) {
            Section("版本信息"); Text("简易现金流计算-测试版 ${BuildConfig.VERSION_NAME.removeSuffix("-test")}")
            Text("开发者：7panni",color=Color.Gray)
            Text("独立安装 · 本地 Room 数据库",color=Color.Gray)
        }}}
    }
    if(confirmClear) ConfirmDialog("清空全部测试数据？","账户、计划、预算和修正都会删除。",{confirmClear=false},{vm.clearAll();confirmClear=false})
}

@Composable private fun PlanDialog(old: PlannedTransactionEntity?, state: CashFlowUiState, close:()->Unit, save:(PlannedTransactionEntity)->Unit) {
    var name by remember {mutableStateOf(old?.name?:"")}
    var amount by remember {mutableStateOf(old?.let{currencyDecimal(it.amountCents,it.currencyCode)}?:"")}
    var type by remember {mutableStateOf(old?.type?.takeIf{it in listOf(EntryType.INCOME,EntryType.EXPENSE)}?:EntryType.EXPENSE)}
    var repeat by remember {mutableStateOf(old?.repeatRule?:RepeatRule.MONTHLY)}
    var account by remember {mutableStateOf(old?.accountName?:state.accounts.firstOrNull()?.name.orEmpty())}
    var cryptoSymbol by remember {mutableStateOf(old?.currencyCode?.takeIf{it in listOf("USDT","BTC","LTC")} ?: "USDT")}
    var date by remember {mutableStateOf(old?.startDateEpochDay?.let(LocalDate::ofEpochDay)?:LocalDate.now().plusDays(1))}
    var hasEnd by remember {mutableStateOf(old?.endDateEpochDay != null)}
    var endDate by remember {mutableStateOf(old?.endDateEpochDay?.let(LocalDate::ofEpochDay)?:date.plusYears(1))}
    var isInstallment by remember {mutableStateOf((old?.installmentTotal?:1)>1)}
    var installmentTotal by remember {mutableStateOf((old?.installmentTotal?:1).toString())}
    var installments by remember {mutableStateOf((old?.installmentRemaining?.takeIf{it>0}?:old?.installmentTotal?:1).toString())}
    val initialInstallmentTotal=old?.let{
        if(it.installmentTotalAmountCents>0)it.installmentTotalAmountCents else it.amountCents*it.installmentTotal.coerceAtLeast(1)
    }
    var installmentAmount by remember {mutableStateOf(initialInstallmentTotal?.let{currencyDecimal(it,old?.currencyCode?:"CNY")}?:"")}
    EditDialog(if(old==null)"新增固定款项" else "编辑固定款项",close,{
        val sourceAccount = state.accounts.firstOrNull { it.name == account }
        val currency = if(sourceAccount?.currencyCode=="CRYPTO")cryptoSymbol else sourceAccount?.currencyCode?:"CNY"
        val effectiveInstallment=type==EntryType.EXPENSE&&isInstallment
        val periods=(installmentTotal.toIntOrNull()?:0)
        val remaining=(installments.toIntOrNull()?:0)
        val totalAmount=if(effectiveInstallment)parseCurrencyAmount(installmentAmount,currency) else null
        val installmentValues=if(effectiveInstallment)installmentBreakdown(totalAmount?:return@EditDialog,periods) else null
        if(effectiveInstallment&&(remaining !in 1..periods)) return@EditDialog
        val cents=installmentValues?.first?:parseCurrencyAmount(amount,currency)?:return@EditDialog
        save(PlannedTransactionEntity(old?.id?:0,name.ifBlank{if(effectiveInstallment)"分期" else if(type==EntryType.INCOME)"工资" else "房租"},type,cents,
            currencyCode=currency,exchangeRateToCnyMicros=0,
            accountName=account,toAccountName=null,startDateEpochDay=date.toEpochDay(),
            repeatRule=repeat,endDateEpochDay=endDate.toEpochDay().takeIf { hasEnd&&!effectiveInstallment },
            installmentTotal=if(effectiveInstallment)periods else 1,
            installmentRemaining=if(effectiveInstallment)remaining else 0,
            installmentTotalAmountCents=if(effectiveInstallment)totalAmount?:0 else 0,
            isActive=old?.isActive?:true))
    }) {
        TextFieldSimple(name,{name=it},"名称")
        Text("类型"); ChoiceRow(listOf(EntryType.INCOME,EntryType.EXPENSE),type,{type=it}){entryLabel(it)}
        Text("频率"); ChoiceRow(listOf(RepeatRule.NONE,RepeatRule.WEEKLY,RepeatRule.MONTHLY,RepeatRule.QUARTERLY,RepeatRule.YEARLY),repeat,{repeat=it}){repeatLabel(it)}
        AccountChoice("账户",state.accounts,account){account=it}
        val selectedAccount=state.accounts.firstOrNull{it.name==account}
        if(selectedAccount?.currencyCode=="CRYPTO") {
            Text("数字货币种类")
            ChoiceRow(listOf("USDT","BTC","LTC"),cryptoSymbol,{cryptoSymbol=it}){if(it=="LTC")"ltc" else it}
        }
        val displayCurrency=if(selectedAccount?.currencyCode=="CRYPTO")cryptoSymbol else selectedAccount?.currencyCode?:"CNY"
        if(type==EntryType.EXPENSE) Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(isInstallment,{isInstallment=it;if(it)repeat=RepeatRule.MONTHLY})
            Text("这是分期支出")
        }
        if(type==EntryType.EXPENSE&&isInstallment) {
            MoneyField(installmentAmount,{installmentAmount=it},"分期总还款金额（${currencyLabel(displayCurrency)}）")
            TextFieldSimple(installmentTotal,{installmentTotal=it},"总期数",KeyboardType.Number)
            TextFieldSimple(installments,{installments=it},"剩余期数",KeyboardType.Number)
            val previewTotal=parseCurrencyAmount(installmentAmount,displayCurrency)
            val preview=previewTotal?.let{installmentBreakdown(it,installmentTotal.toIntOrNull()?:0)}
            if(preview!=null) Text(
                "每期 ${currencyMoney(preview.first,displayCurrency)}；最后一期 ${currencyMoney(preview.second,displayCurrency)}",
                color=Green,style=MaterialTheme.typography.bodySmall
            )
            Text("默认无利息；如有利息或手续费，请填写实际总还款金额。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
        } else {
            MoneyField(amount,{amount=it},"金额（${currencyLabel(displayCurrency)}）")
        }
        DateChoice("开始日期",date){date=it}
        if(!isInstallment) {
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(hasEnd,{hasEnd=it});Text("设置结束日期")}
            if(hasEnd) DateChoice("结束日期",endDate){endDate=it}
        }
    }
}

@Composable private fun BudgetDialog(old:BudgetEntity?,state:CashFlowUiState,close:()->Unit,save:(BudgetEntity)->Unit) {
    var total by remember{mutableStateOf(old?.totalCents?.let(::decimal)?:"")}
    var remain by remember{mutableStateOf(old?.remainingCents?.let(::decimal)?:"")}
    var period by remember{mutableStateOf(old?.period?:BudgetPeriod.MONTHLY)}
    val defaultCycle = remember(state.settings.budgetMonthStartDay) { BudgetCycles.containing(LocalDate.now(),state.settings.budgetMonthStartDay) }
    var start by remember{mutableStateOf(old?.startDateEpochDay?.let(LocalDate::ofEpochDay)?:defaultCycle.first)}
    var end by remember{mutableStateOf(old?.endDateEpochDay?.let(LocalDate::ofEpochDay)?:defaultCycle.second)}
    EditDialog(if(old==null)"新增预算" else "编辑预算",close,{
        val t=parseCents(total)?:return@EditDialog; val r=parseCents(remain.ifBlank{total})?:return@EditDialog
        val dates = when(period) {
            BudgetPeriod.MONTHLY -> BudgetCycles.containing(LocalDate.now(),state.settings.budgetMonthStartDay)
            BudgetPeriod.WEEKLY -> start to start.plusDays(6)
            BudgetPeriod.CUSTOM -> start to end
        }
        save(BudgetEntity(old?.id?:0,"预算",t,r,period,dates.first.toEpochDay(),dates.second.toEpochDay(),
            "",BudgetMethod.DAILY_AVERAGE,null,false,old?.isActive?:true))
    }) {
        Text("统一预算",fontWeight=FontWeight.Bold)
        MoneyField(total,{total=it},"总预算"); MoneyField(remain,{remain=it},"剩余预算")
        Text("周期"); ChoiceRow(BudgetPeriod.entries,period,{period=it}){when(it){BudgetPeriod.WEEKLY->"每周";BudgetPeriod.MONTHLY->"每月";else->"自定义"}}
        when(period) {
            BudgetPeriod.MONTHLY -> Text("默认每月自动续期：${state.settings.budgetMonthStartDay} 日至次月前一天。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            BudgetPeriod.WEEKLY -> {
                DateChoice("首个周期开始日期",start){start=it}
                Text("之后每7天自动续期。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            }
            BudgetPeriod.CUSTOM -> {
                DateChoice("预算开始日期",start){start=it}
                DateChoice("预算结束日期",end){end=it}
            }
        }
        Text("预算只用于估算未来现金，不改变任何账户的真实余额。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun AdjustmentDialog(old:AdjustmentEntity?,state:CashFlowUiState,close:()->Unit,save:(AdjustmentEntity)->Unit) {
    var name by remember{mutableStateOf(old?.name?:"")}
    var income by remember{mutableStateOf(old?.isIncome?:false)}
    var account by remember{mutableStateOf(old?.accountName?:state.accounts.firstOrNull()?.name.orEmpty())}
    val initialCurrency=state.accounts.firstOrNull{it.name==account}?.currencyCode?:"CNY"
    var amount by remember(old?.id,initialCurrency){mutableStateOf(old?.amountCents?.let{currencyDecimal(it,initialCurrency)}?:"")}
    var date by remember{mutableStateOf(old?.dateEpochDay?.let(LocalDate::ofEpochDay)?:LocalDate.now().plusDays(1))}
    var budget by remember{mutableStateOf(old?.usesBudget?:false)}
    EditDialog(if(old==null)"新增未来临时收支" else "编辑未来临时收支",close,{
        val currency=state.accounts.firstOrNull{it.name==account}?.currencyCode?:"CNY"
        parseCurrencyAmount(amount,currency)?.let{save(AdjustmentEntity(old?.id?:0,name.ifBlank{if(income)"临时收入" else "临时支出"},AdjustmentType.TEMPORARY,it,account,date.toEpochDay(),income,budget,old?.appliedToBalance?:false))}
    }) {
        val currency=state.accounts.firstOrNull{it.name==account}?.currencyCode?:"CNY"
        TextFieldSimple(name,{name=it},"名称");MoneyField(amount,{amount=it},"金额（${currencyLabel(currency)}）")
        ChoiceRow(listOf(false,true),income,{income=it}){if(it)"收入" else "支出"}
        AccountChoice("账户",state.accounts,account){account=it};DateChoice("发生日期",date){date=it}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(budget,{budget=it});Text("占用预算")}
    }
}

@Composable private fun AccountDialog(old:AccountEntity?,close:()->Unit,save:(AccountEntity)->Unit) {
    var name by remember{mutableStateOf(old?.name?:"")}
    var balance by remember{mutableStateOf("")}
    var type by remember{mutableStateOf(old?.accountType?:AccountType.CASH)}
    val commonCurrencies=listOf("CNY","USD","EUR","GBP","JPY","HKD")
    var currencyChoice by remember{mutableStateOf(old?.currencyCode?.takeIf{it in commonCurrencies}?:"CNY")}
    var customCurrency by remember{mutableStateOf(old?.currencyCode?.takeIf{it !in commonCurrencies}.orEmpty())}
    var currencyError by remember{mutableStateOf(false)}
    EditDialog(if(old==null)"增加账户" else "编辑账户",close,{
        if(old==null) {
            val actualCurrency = when(type) {
                AccountType.PAYPAL -> "USD"
                AccountType.CRYPTO -> "CRYPTO"
                AccountType.CUSTOM -> if(currencyChoice=="OTHER") customCurrency.trim().uppercase() else currencyChoice
                else -> "CNY"
            }
            if(actualCurrency!="CRYPTO" && !actualCurrency.matches(Regex("[A-Z]{2,8}"))) {
                currencyError=true
                return@EditDialog
            }
            val initial = if(actualCurrency=="CRYPTO") 0 else parseCurrencyAmount(balance,actualCurrency) ?: return@EditDialog
            save(AccountEntity(0,name.ifBlank{"未命名账户"},initial,initial,actualCurrency,
                if(actualCurrency=="CNY")1_000_000 else 0,true,type,true))
        } else {
            save(old.copy(
                name=name.ifBlank{"未命名账户"},
                includeInForecast=true,
                isActive=true
            ))
        }
    }) {
        TextFieldSimple(name,{name=it},"账户名称")
        if(old==null) {
            Text("账户类型")
            val allowedTypes = listOf(
                AccountType.CASH,AccountType.WECHAT,AccountType.ALIPAY,AccountType.CMB,
                AccountType.PAYPAL,AccountType.CRYPTO,AccountType.CUSTOM
            )
            ChoiceRow(allowedTypes,type,{type=it}){accountTypeLabel(it)}
            if(type==AccountType.CUSTOM) {
                Text("币种")
                ChoiceRow(commonCurrencies+"OTHER",currencyChoice,{currencyChoice=it;currencyError=false}){if(it=="OTHER")"其他" else currencyLabel(it)}
                if(currencyChoice=="OTHER") {
                    TextFieldSimple(customCurrency,{customCurrency=it.uppercase();currencyError=false},"币种代码，例如 SGD")
                    if(currencyError) Text("请输入 2—8 位英文字母币种代码",color=Red,style=MaterialTheme.typography.bodySmall)
                }
            }
            if(type!=AccountType.CRYPTO) {
                val actualCurrency=when(type){AccountType.PAYPAL->"USD";AccountType.CUSTOM->if(currencyChoice=="OTHER")customCurrency.uppercase() else currencyChoice;else->"CNY"}
                MoneyField(balance,{balance=it},"初始真实余额（${currencyLabel(actualCurrency)}）")
            }
        } else {
            Text("${accountTypeLabel(old.accountType)} · ${currencyLabel(old.currencyCode)}",color=Color.Gray)
        }
    }
}

@Composable private fun CryptoHoldingsDialog(
    holdings:List<CryptoHoldingEntity>,
    close:()->Unit,
    save:(List<CryptoHoldingEntity>)->Unit
) {
    fun initial(symbol:String)=holdings.firstOrNull{it.symbol==symbol}?.amountUnits?.let(::cryptoDecimal)?:"0"
    var usdt by remember{mutableStateOf(initial("USDT"))}
    var btc by remember{mutableStateOf(initial("BTC"))}
    var ltc by remember{mutableStateOf(initial("LTC"))}
    EditDialog("修改数字货币持仓",close,{
        val values=listOf(
            CryptoHoldingEntity("USDT",parseCryptoUnits(usdt)?:return@EditDialog),
            CryptoHoldingEntity("BTC",parseCryptoUnits(btc)?:return@EditDialog),
            CryptoHoldingEntity("LTC",parseCryptoUnits(ltc)?:return@EditDialog)
        )
        save(values)
    }){
        Text("不设置汇率，不折算成人民币或美元。",color=Color.Gray)
        MoneyField(usdt,{usdt=it},"USDT 数量")
        MoneyField(btc,{btc=it},"BTC 数量")
        MoneyField(ltc,{ltc=it},"ltc 数量")
    }
}

@Composable private fun AccountTransferDialog(
    accounts:List<AccountEntity>,
    close:()->Unit,
    transfer:(Long,Long,Long)->Unit,
) {
    val sources=accounts.filter { source ->
        source.currencyCode!="CRYPTO"&&accounts.any{it.id!=source.id&&it.currencyCode==source.currencyCode}
    }
    var fromName by remember{mutableStateOf(sources.firstOrNull()?.name.orEmpty())}
    val source=sources.firstOrNull{it.name==fromName}?:sources.firstOrNull()
    val targets=accounts.filter{it.id!=source?.id&&it.currencyCode==source?.currencyCode}
    var toName by remember{mutableStateOf(targets.firstOrNull()?.name.orEmpty())}
    val selectedTarget=toName.takeIf{name->targets.any{it.name==name}}?:targets.firstOrNull()?.name.orEmpty()
    var amount by remember{mutableStateOf("")}
    val currency=source?.currencyCode?:"CNY"
    EditDialog("账户间转账",close,{
        val target=targets.firstOrNull{it.name==selectedTarget}?:return@EditDialog
        val minor=parseCurrencyAmount(amount,currency)?:return@EditDialog
        transfer(source?.id?:return@EditDialog,target.id,minor)
    }) {
        AccountChoice("转出账户",sources,source?.name.orEmpty()){fromName=it;toName="";amount=""}
        source?.let{Text("当前余额 ${currencyMoney(it.currentBalanceCents,it.currencyCode)}",color=Color.Gray)}
        AccountChoice("转入账户",targets,selectedTarget){toName=it}
        MoneyField(amount,{amount=it},"转账金额（${currencyLabel(currency)}）")
        Text("确认后立即改变两个账户的真实余额；同币种总余额不变。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BalanceCalculatorDialog(
    initialBalance:String,
    currency:String,
    close:()->Unit,
    useResult:(String)->Unit,
) {
    var expression by remember(initialBalance,currency){mutableStateOf(initialBalance)}
    var started by remember(initialBalance,currency){mutableStateOf(false)}
    val result=parseCurrencyExpression(expression,currency)
    fun input(key:String) {
        when(key) {
            "清空"->{expression=initialBalance;started=false}
            "⌫"->{
                expression=expression.dropLast(1).ifBlank{"0"}
                started=true
            }
            "+","-"->{
                expression=if(expression.lastOrNull() in listOf('+','-')) expression.dropLast(1)+key else expression+key
                started=true
            }
            "="->result?.let{expression=currencyDecimal(it,currency);started=false}
            else->{
                expression=if(!started){if(key==".")"0." else key}else expression+key
                started=true
            }
        }
    }
    BasicAlertDialog(onDismissRequest=close) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${currencyLabel(currency)} · 起始 $initialBalance",color=Color.Gray)
                Surface(color=Paper,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth()) {
                    Text(expression,Modifier.padding(16.dp),style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.End,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                listOf(
                    listOf("7","8","9","+"),
                    listOf("4","5","6","-"),
                    listOf("1","2","3","⌫"),
                    listOf("0",".","清空","="),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        row.forEach { key ->
                            OutlinedButton(
                                onClick={input(key)},
                                modifier=Modifier.weight(1f),
                                enabled=key!="." || currencyFractionDigits(currency)>0,
                                contentPadding=PaddingValues(horizontal=4.dp),
                            ){Text(key,maxLines=1)}
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(close){Text("取消")}
                    Button(
                        onClick={result?.let{useResult(currencyDecimal(it,currency))}},
                        enabled=result!=null,
                    ){Text("校准")}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditDialog(title:String,close:()->Unit,save:()->Unit,content:@Composable ColumnScope.()->Unit) {
    BasicAlertDialog(onDismissRequest=close) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(18.dp).heightIn(max=650.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);content()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(close){Text("取消")};Button(save){Text("保存")}}
            }
        }
    }
}
@Composable private fun ConfirmDialog(title:String,text:String,cancel:()->Unit,confirm:()->Unit)=AlertDialog(
    onDismissRequest=cancel,
    confirmButton={Button(confirm,colors=ButtonDefaults.buttonColors(containerColor=Red)){Text("确认")}},
    dismissButton={TextButton(cancel){Text("取消")}},
    title={Text(title)},
    text={Text(text)}
)

@Composable private fun DataCard(title:String,subtitle:String,amount:String,active:Boolean,onToggle:(()->Unit)?,onEdit:(()->Unit)?,onDelete:()->Unit,extraAction:Pair<String,()->Unit>?=null) {
    Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(subtitle,color=Color.Gray,style=MaterialTheme.typography.bodySmall)};Text(amount,fontWeight=FontWeight.Bold)}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){extraAction?.let{(label,action)->AssistChip(action,{Text(label)})};onToggle?.let{AssistChip(it,{Text(if(active)"停用" else "恢复")})};onEdit?.let{AssistChip(it,{Text("编辑")})};AssistChip(onDelete,{Text("删除",color=Red)})}
    }}
}
@Composable private fun <T> ChoiceRow(options:List<T>,selected:T,onSelect:(T)->Unit,label:(T)->String) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
        options.forEach{FilterChip(it==selected,{onSelect(it)},{Text(label(it),maxLines=1)})}
    }
}
@Composable private fun AccountChoice(title:String,accounts:List<AccountEntity>,selected:String,onSelect:(String)->Unit){Text(title);ChoiceRow(accounts.map{it.name},selected,onSelect){it}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateChoice(title:String,date:LocalDate,onChange:(LocalDate)->Unit){
    var showCalendar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text("$title：$date",fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedButton({onChange(date.minusDays(1))},Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){Text("前一天",maxLines=1)}
            Button({showCalendar=true},Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){Text("日历",maxLines=1)}
            OutlinedButton({onChange(date.plusDays(1))},Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp)){Text("后一天",maxLines=1)}
        }
    }
    if(showCalendar){
        val pickerState=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest={showCalendar=false},
            confirmButton={TextButton({
                pickerState.selectedDateMillis?.let{onChange(LocalDate.ofEpochDay(it/86_400_000L))}
                showCalendar=false
            }){Text("确定")}},
            dismissButton={TextButton({showCalendar=false}){Text("取消")}}
        ){DatePicker(state=pickerState)}
    }
}
@Composable private fun MoneyField(value:String,onChange:(String)->Unit,label:String)=OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
@Composable private fun TextFieldSimple(value:String,onChange:(String)->Unit,label:String,type:KeyboardType=KeyboardType.Text)=OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=type))
@Composable private fun Metric(label:String,value:String,warn:Boolean=false)=Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Color.Gray);Text(value,fontWeight=FontWeight.SemiBold,color=if(warn)Red else Color.Unspecified)}
@Composable private fun Section(text:String)=Text(text,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
@Composable private fun EmptyCard(text:String)=Card{Text(text,Modifier.padding(16.dp),color=Color.Gray)}

private fun parseCents(text:String):Long?=parseCurrencyAmount(text,"CNY")
internal fun currencyFractionDigits(currency:String):Int=when(currency.uppercase()){
    "USDT","BTC","LTC","CRYPTO"->8
    "JPY","KRW","VND"->0
    "KWD","BHD","JOD","OMR","TND"->3
    else->2
}
internal fun parseCurrencyAmount(text:String,currency:String):Long?=runCatching {
    text.trim().toBigDecimalOrNull()
        ?.movePointRight(currencyFractionDigits(currency))
        ?.setScale(0,RoundingMode.HALF_UP)
        ?.longValueExact()
}.getOrNull()
internal fun parseCentsExpression(text:String):Long?=parseCurrencyExpression(text,"CNY")
internal fun parseCurrencyExpression(text:String,currency:String):Long? {
    val input = text.filterNot(Char::isWhitespace)
    if (input.isEmpty()) return null
    var index = 0
    var total = java.math.BigDecimal.ZERO
    while (index < input.length) {
        var sign = 1
        if (input[index] == '+' || input[index] == '-') {
            if (input[index] == '-') sign = -1
            index++
        } else if (index > 0) return null
        val start = index
        while (index < input.length && (input[index].isDigit() || input[index] == '.')) index++
        if (start == index) return null
        val number = input.substring(start, index).toBigDecimalOrNull() ?: return null
        total = if (sign > 0) total + number else total - number
    }
    return runCatching {
        total.movePointRight(currencyFractionDigits(currency)).setScale(0,RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}
internal fun adjustBalanceText(current:String,adjustment:String,currency:String,add:Boolean):String? {
    val currentMinor=parseCurrencyExpression(current,currency)?:return null
    val adjustmentMinor=parseCurrencyAmount(adjustment,currency)?:return null
    val result=runCatching{if(add)Math.addExact(currentMinor,adjustmentMinor) else Math.subtractExact(currentMinor,adjustmentMinor)}.getOrNull()?:return null
    return currencyDecimal(result,currency)
}
internal fun installmentBreakdown(totalAmount:Long,periods:Int):Pair<Long,Long>? {
    if(totalAmount<=0L||periods<2||totalAmount<periods) return null
    val regular=totalAmount/periods
    val last=totalAmount-regular*(periods-1L)
    return regular to last
}
private fun parseCryptoUnits(text:String):Long?=parseCurrencyAmount(text,"BTC")
private fun decimal(cents:Long)=currencyDecimal(cents,"CNY")
private fun cryptoDecimal(units:Long)=java.math.BigDecimal.valueOf(units,8).stripTrailingZeros().toPlainString()
internal fun currencyDecimal(amount:Long,currency:String):String {
    val digits=currencyFractionDigits(currency)
    return if(digits==8) java.math.BigDecimal.valueOf(amount,digits).stripTrailingZeros().toPlainString()
    else java.math.BigDecimal.valueOf(amount,digits).setScale(digits).toPlainString()
}
private fun money(cents:Long)="${if(cents<0)"-" else ""}¥${cents.absoluteValue/100}.${(cents.absoluteValue%100).toString().padStart(2,'0')}"
private fun usdMoney(cents:Long)="${if(cents<0)"-" else ""}$${cents.absoluteValue/100}.${(cents.absoluteValue%100).toString().padStart(2,'0')}"
private fun nativeMoney(amount:Long,currency:String)=currencyMoney(amount,currency)
private fun currencyMoney(amount:Long,currency:String):String {
    val sign=if(amount<0)"-" else ""
    val number=currencyDecimal(amount.absoluteValue,currency)
    return when(currency.uppercase()){
        "CNY"->"${sign}¥$number"
        "USD"->"${sign}$$number"
        "EUR"->"${sign}€$number"
        "GBP"->"${sign}£$number"
        "JPY"->"${sign}JP¥$number"
        "HKD"->"${sign}HK$$number"
        "LTC"->"${sign}ltc $number"
        else->"$sign${currency.uppercase()} $number"
    }
}
private fun signedCurrencyMoney(amount:Long,currency:String)="${if(amount>=0)"+" else "-"}${currencyMoney(amount.absoluteValue,currency)}"
private fun cryptoSummary(holdings:List<CryptoHoldingEntity>)=listOf("USDT","BTC","LTC").joinToString(" · "){symbol->
    val value=holdings.firstOrNull{it.symbol==symbol}?.amountUnits?:0
    "${if(symbol=="LTC")"ltc" else symbol} ${cryptoDecimal(value)}"
}
internal fun parseAccountIdCsv(csv:String):Set<Long> = csv.split(',').mapNotNull{it.trim().toLongOrNull()}.toSet()
internal fun parseCurrencyCodeCsv(csv:String):Set<String> = csv.split(',').map{it.trim().uppercase()}.filter{it.isNotBlank()}.toSet()
private fun monthDay(date:LocalDate)="${date.monthValue}/${date.dayOfMonth}"
private fun entryLabel(t:EntryType)=when(t){EntryType.INCOME->"收入";EntryType.EXPENSE->"支出";EntryType.TRANSFER->"转账";EntryType.DEBT->"负债";EntryType.REFUND->"退款"}
private fun repeatLabel(r:RepeatRule)=when(r){RepeatRule.NONE->"一次";RepeatRule.DAILY->"每天";RepeatRule.WEEKLY->"每周";RepeatRule.MONTHLY->"每月";RepeatRule.QUARTERLY->"每季度";RepeatRule.YEARLY->"每年"}
private fun accountTypeLabel(t:AccountType)=when(t){AccountType.CASH->"现金";AccountType.WECHAT->"微信";AccountType.ALIPAY->"支付宝";AccountType.CMB->"银行";AccountType.PAYPAL->"PayPal";AccountType.CRYPTO->"数字货币";AccountType.CUSTOM->"自定义"}
private fun currencyLabel(code:String)=when(code.uppercase()){
    "CNY"->"人民币";"USD"->"美元";"EUR"->"欧元";"GBP"->"英镑";"JPY"->"日元";"HKD"->"港币"
    "LTC"->"ltc";"CRYPTO"->"数字货币";"OTHER"->"其他";else->code.uppercase()
}
