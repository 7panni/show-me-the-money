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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiyue.cashflow.cashflow.CashFlowDay
import com.qiyue.cashflow.data.*
import com.qiyue.cashflow.shared.AppMode
import com.qiyue.cashflow.shared.AppProfile
import com.qiyue.cashflow.shared.FeatureModule
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
    val profile = remember(state.settings.appMode, state.settings.customModulesCsv) {
        AppProfile.parse(state.settings.appMode, state.settings.customModulesCsv)
    }
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
                Tab.PLAN -> PlanPage(state, profile, pad, vm)
                Tab.ACCOUNT -> AccountPage(state, profile, pad, vm)
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
    complete: (AppMode, Set<FeatureModule>, Set<AccountType>, Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(AppMode.SIMPLE) }
    var modules by remember { mutableStateOf(AppProfile.SIMPLE_MODULES) }
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
                Text("先选一种用法，以后可在设置中更改；切换不会删除数据。", color = Color.Gray)
                Text("使用模式", fontWeight = FontWeight.Bold)
                ChoiceRow(AppMode.entries, mode, { selected ->
                    mode = selected
                    when (selected) {
                        AppMode.SIMPLE -> {
                            modules = AppProfile.SIMPLE_MODULES
                            accounts = setOf(AccountType.CMB, AccountType.WECHAT, AccountType.ALIPAY, AccountType.CASH)
                        }
                        AppMode.CUSTOM -> modules = AppProfile.SIMPLE_MODULES
                        AppMode.FULL -> {
                            modules = FeatureModule.entries.toSet()
                            accounts = setOf(
                                AccountType.CMB, AccountType.WECHAT, AccountType.ALIPAY,
                                AccountType.CASH, AccountType.PAYPAL, AccountType.CRYPTO,
                            )
                        }
                    }
                }, ::modeLabel)
                Text(modeDescription(mode), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                if (mode == AppMode.CUSTOM) {
                    Text("需要的功能", fontWeight = FontWeight.Bold)
                    FeatureModule.entries.forEach { module ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(module in modules, { checked ->
                                modules = if (checked) modules + module else modules - module
                            })
                            Text(featureLabel(module))
                        }
                    }
                }
                Text("初始账户", fontWeight = FontWeight.Bold)
                accountOptions.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(type in accounts, { checked ->
                            accounts = if (checked) accounts + type else accounts - type
                            if (checked && type == AccountType.PAYPAL) modules = modules + FeatureModule.PAYPAL_USD
                            if (checked && type == AccountType.CRYPTO) modules = modules + FeatureModule.CRYPTO
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
                    onClick = { complete(mode, modules, accounts, examples) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = accounts.isNotEmpty(),
                ) { Text("开始使用") }
            }
        }
    }
}

@Composable private fun HomePage(state: CashFlowUiState, pad: PaddingValues, onHorizon: (Int)->Unit) {
    val s = state.summary
    val monthEnd = s.days.firstOrNull { it.date == LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()) }?.balanceCents
        ?: s.endBalanceCents
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors=CardDefaults.cardColors(containerColor=if(s.firstShortageDate!=null) Color(0xFFFFEDEA) else Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("真实余额", fontWeight=FontWeight.Bold, style=MaterialTheme.typography.titleMedium)
                    Text("人民币", color=Color.Gray)
                    Text(money(s.currentBalanceCents), style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
                    if(state.settings.showUsdOnHome) Metric("美元", usdMoney(s.currentUsdCents))
                    if(state.settings.showCryptoOnHome) {
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

@Composable private fun PlanPage(state: CashFlowUiState, profile: AppProfile, pad: PaddingValues, vm: CashFlowViewModel) {
    var planEdit by remember { mutableStateOf<PlannedTransactionEntity?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    var budgetEdit by remember { mutableStateOf<BudgetEntity?>(null) }
    var showBudget by remember { mutableStateOf(false) }
    var adjustmentEdit by remember { mutableStateOf<AdjustmentEntity?>(null) }
    var showAdjustment by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    val temporaryAdjustments = state.adjustments.filter { it.type == AdjustmentType.TEMPORARY }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({planEdit=null;showPlan=true}){Text("＋ 固定款项")}
            if (profile.isEnabled(FeatureModule.TEMPORARY_CASHFLOW)) {
                OutlinedButton({adjustmentEdit=null;showAdjustment=true}){Text("＋ 未来临时收支")}
            }
        }}
        item { Section(if (profile.mode == AppMode.SIMPLE) "固定收入与支出" else "固定收入、支出、转账与分期") }
        if(state.plans.isEmpty()) item { EmptyCard("还没有固定款项。先添加工资或房租。") }
        items(state.plans, key={"plan-${it.id}"}) { p -> DataCard(
            title=p.name, subtitle="${entryLabel(p.type)} · ${repeatLabel(p.repeatRule)} · ${p.accountName}${p.toAccountName?.let{" → $it"}.orEmpty()}",
            amount=currencyMoney(p.amountCents,p.currencyCode), active=p.isActive,
            onToggle={vm.savePlan(p.copy(isActive=!p.isActive))}, onEdit={planEdit=p;showPlan=true},
            onDelete={delete="“${p.name}”" to {vm.deletePlan(p.id)}}
        )}
        if (profile.isEnabled(FeatureModule.BUDGET)) item { Section("预算") }
        if(profile.isEnabled(FeatureModule.BUDGET) && state.budgets.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmptyCard("暂无预算。预算会作为未来支出进入预测。")
                OutlinedButton({budgetEdit=null;showBudget=true}, Modifier.fillMaxWidth()){Text("设置预算")}
            }
        }
        if (profile.isEnabled(FeatureModule.BUDGET)) items(state.budgets.take(1), key={"budget-${it.id}"}) { b -> DataCard(
            b.name,
            "${LocalDate.ofEpochDay(b.startDateEpochDay)} 至 ${LocalDate.ofEpochDay(b.endDateEpochDay)} · 用于未来现金预测",
            "剩余 ${money(b.remainingCents)}",
            b.isActive, {vm.saveBudget(b.copy(isActive=!b.isActive))}, {budgetEdit=b;showBudget=true},
            {delete="“${b.name}”" to {vm.deleteBudget(b.id)}})
        }
        if (profile.isEnabled(FeatureModule.TEMPORARY_CASHFLOW)) item { Section("未来临时收支") }
        if(profile.isEnabled(FeatureModule.TEMPORARY_CASHFLOW) && temporaryAdjustments.isEmpty()) item { EmptyCard("暂无未来临时收支。实际余额有偏差时，到“账户”校准即可。") }
        if (profile.isEnabled(FeatureModule.TEMPORARY_CASHFLOW)) items(temporaryAdjustments, key={"adjustment-${it.id}"}) { a -> DataCard(
            a.name, buildString {
                append(if(a.isIncome)"临时收入" else "临时支出")
                append(" · ${LocalDate.ofEpochDay(a.dateEpochDay)}")
                if(a.appliedToBalance) append(" · 已计入真实余额")
            },
            money(a.amountCents), true, null,
            if(a.type==AdjustmentType.TEMPORARY && !a.appliedToBalance) {{adjustmentEdit=a;showAdjustment=true}} else null,
            {delete="“${a.name}”" to {vm.deleteAdjustment(a.id)}},
            extraAction = if(
                a.type==AdjustmentType.TEMPORARY && !a.appliedToBalance &&
                a.dateEpochDay<=LocalDate.now().toEpochDay()
            ) "计入真实余额" to {vm.applyAdjustmentToBalance(a)} else null)
        }
    }
    if(showPlan) PlanDialog(planEdit, state, profile, {showPlan=false}) {vm.savePlan(it);showPlan=false}
    if(showBudget) BudgetDialog(budgetEdit, state, {showBudget=false}) {vm.saveBudget(it);showBudget=false}
    if(showAdjustment) AdjustmentDialog(adjustmentEdit, state, {showAdjustment=false}) {vm.saveAdjustment(it);showAdjustment=false}
    delete?.let { (name, action) -> ConfirmDialog("删除$name？","删除后无法恢复。",{delete=null},{action();delete=null}) }
}

@Composable private fun AccountPage(state: CashFlowUiState, profile: AppProfile, pad: PaddingValues, vm: CashFlowViewModel) {
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showCryptoEditor by remember { mutableStateOf(false) }
    val visibleAccounts = state.accounts.filter { account ->
        when (account.accountType) {
            AccountType.PAYPAL -> state.settings.showPaypalOnAccounts
            AccountType.ALIPAY -> state.settings.showAlipayOnAccounts
            AccountType.CRYPTO -> state.settings.showCryptoOnAccounts
            else -> true
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Button({editing=null;showEditor=true}, Modifier.fillMaxWidth()){Text("＋ 增加账户")} }
        if(visibleAccounts.isEmpty()) item { EmptyCard("没有需要显示的账户，可到“设置”恢复隐藏账户。") }
        items(visibleAccounts, key={it.id}) { a ->
            val endNative = state.summary.days.lastOrNull()?.accountBalancesCents?.get(a.name) ?: a.currentBalanceCents
            var balanceText by remember(a.id,a.currentBalanceCents) { mutableStateOf(decimal(a.currentBalanceCents)) }
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
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value=balanceText,
                            onValueChange={balanceText=it},
                            modifier=Modifier.weight(1f),
                            label={Text("真实余额（可加减）")},
                            singleLine=true,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal)
                        )
                        Button(
                            onClick={parseCentsExpression(balanceText)?.let{vm.calibrate(a,it)}},
                            enabled=parseCentsExpression(balanceText)!=null
                        ){Text("校准")}
                    }
                    Text("可输入新余额，或像 1000+50-20 这样快速计算。", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
                    Text("未来${state.horizonDays}天预计余额 ${nativeMoney(endNative,a.currencyCode)}", color=Green)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(a.currencyCode=="CRYPTO") AssistChip({showCryptoEditor=true},{Text("修改持仓")})
                    AssistChip({editing=a;showEditor=true},{Text("编辑")})
                }
            }}
        }
    }
    if(showEditor) AccountDialog(editing,profile,{showEditor=false}){vm.saveAccount(it);showEditor=false}
    if(showCryptoEditor) CryptoHoldingsDialog(state.cryptoHoldings,{showCryptoEditor=false}) {
        vm.saveCryptoHoldings(it);showCryptoEditor=false
    }
}

@Composable private fun SettingsPage(state: CashFlowUiState, pad: PaddingValues, vm: CashFlowViewModel) {
    val scope = rememberCoroutineScope()
    var days by remember(state.settings.defaultHorizonDays) { mutableIntStateOf(state.settings.defaultHorizonDays) }
    var budgetStartDay by remember(state.settings.budgetMonthStartDay) { mutableStateOf(state.settings.budgetMonthStartDay.toString()) }
    var showUsd by remember(state.settings.showUsdOnHome) { mutableStateOf(state.settings.showUsdOnHome) }
    var showCrypto by remember(state.settings.showCryptoOnHome) { mutableStateOf(state.settings.showCryptoOnHome) }
    var showPaypalAccount by remember(state.settings.showPaypalOnAccounts) { mutableStateOf(state.settings.showPaypalOnAccounts) }
    var showAlipayAccount by remember(state.settings.showAlipayOnAccounts) { mutableStateOf(state.settings.showAlipayOnAccounts) }
    var showCryptoAccount by remember(state.settings.showCryptoOnAccounts) { mutableStateOf(state.settings.showCryptoOnAccounts) }
    var mode by remember(state.settings.appMode) {
        mutableStateOf(AppProfile.parse(state.settings.appMode, state.settings.customModulesCsv).mode)
    }
    var customModules by remember(state.settings.appMode, state.settings.customModulesCsv) {
        mutableStateOf(AppProfile.parse(state.settings.appMode, state.settings.customModulesCsv).customModules)
    }
    var confirmClear by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { scope.launch { runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { w -> w.write(vm.exportJson()) } } } }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }.getOrNull()?.let(vm::restoreJson) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Section("使用模式")
            ChoiceRow(AppMode.entries, mode, { mode = it }, ::modeLabel)
            Text(modeDescription(mode), color=Color.Gray, style=MaterialTheme.typography.bodySmall)
            if (mode == AppMode.CUSTOM) {
                FeatureModule.entries.forEach { module ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(module in customModules, { checked ->
                            customModules = if (checked) customModules + module else customModules - module
                        })
                        Text(featureLabel(module))
                    }
                }
            }
            Text("切换模式只调整界面，不会删除已有账户或计划。", color=Color.Gray, style=MaterialTheme.typography.bodySmall)
        }}}
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Section("预测设置")
            Text("默认预测周期")
            ChoiceRow(listOf(30,90,365),days,{days=it}){"${it}天"}
            TextFieldSimple(budgetStartDay,{budgetStartDay=it},"每月预算从几号开始（1—28）",KeyboardType.Number)
            Text("当前按每月 ${budgetStartDay.toIntOrNull()?.coerceIn(1,28) ?: 21} 日至次月前一天计算。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(showUsd,{showUsd=it});Text("首页显示美元余额")}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(showCrypto,{showCrypto=it});Text("首页显示数字货币")}
            Text("人民币、美元和数字货币分别显示，不做汇率换算。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Section("账户页显示")
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(showPaypalAccount,{showPaypalAccount=it});Text("显示 PayPal")}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(showAlipayAccount,{showAlipayAccount=it});Text("显示支付宝")}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(showCryptoAccount,{showCryptoAccount=it});Text("显示数字货币")}
            Text("这里只控制账户页是否显示，不影响余额和预测。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            Button({vm.updateSettings(
                days,budgetStartDay.toIntOrNull()?:21,showUsd,showCrypto,
                showPaypalAccount,showAlipayAccount,showCryptoAccount,mode,customModules
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

@Composable private fun PlanDialog(old: PlannedTransactionEntity?, state: CashFlowUiState, profile: AppProfile, close:()->Unit, save:(PlannedTransactionEntity)->Unit) {
    var name by remember {mutableStateOf(old?.name?:"")}
    var amount by remember {mutableStateOf(old?.let{currencyDecimal(it.amountCents,it.currencyCode)}?:"")}
    var type by remember {mutableStateOf(old?.type?:EntryType.EXPENSE)}
    var repeat by remember {mutableStateOf(old?.repeatRule?:RepeatRule.MONTHLY)}
    var account by remember {mutableStateOf(old?.accountName?:state.accounts.firstOrNull()?.name.orEmpty())}
    var cryptoSymbol by remember {mutableStateOf(old?.currencyCode?.takeIf{it in listOf("USDT","BTC","LTC")} ?: "USDT")}
    var toAccount by remember {mutableStateOf(old?.toAccountName?:state.accounts.drop(1).firstOrNull()?.name.orEmpty())}
    var date by remember {mutableStateOf(old?.startDateEpochDay?.let(LocalDate::ofEpochDay)?:LocalDate.now().plusDays(1))}
    var hasEnd by remember {mutableStateOf(old?.endDateEpochDay != null)}
    var endDate by remember {mutableStateOf(old?.endDateEpochDay?.let(LocalDate::ofEpochDay)?:date.plusYears(1))}
    var installmentTotal by remember {mutableStateOf((old?.installmentTotal?:1).toString())}
    var installments by remember {mutableStateOf((old?.installmentRemaining?:0).toString())}
    EditDialog(if(old==null)"新增固定款项" else "编辑固定款项",close,{
        val sourceAccount = state.accounts.firstOrNull { it.name == account }
        val currency = if(sourceAccount?.currencyCode=="CRYPTO")cryptoSymbol else sourceAccount?.currencyCode?:"CNY"
        val cents=parseCurrencyAmount(amount,currency) ?: return@EditDialog
        save(PlannedTransactionEntity(old?.id?:0,name.ifBlank{"未命名款项"},type,cents,
            currencyCode=currency,exchangeRateToCnyMicros=0,
            accountName=account,toAccountName=toAccount.takeIf{type==EntryType.TRANSFER},startDateEpochDay=date.toEpochDay(),
            repeatRule=repeat,endDateEpochDay=endDate.toEpochDay().takeIf { hasEnd },
            installmentTotal=(installmentTotal.toIntOrNull()?:1).coerceAtLeast(1),
            installmentRemaining=(installments.toIntOrNull()?:0).coerceAtLeast(0),isActive=old?.isActive?:true))
    }) {
        TextFieldSimple(name,{name=it},"名称")
        val typeOptions = buildList {
            add(EntryType.INCOME); add(EntryType.EXPENSE)
            if (profile.isEnabled(FeatureModule.TRANSFER) || old?.type == EntryType.TRANSFER) add(EntryType.TRANSFER)
        }
        Text("类型"); ChoiceRow(typeOptions,type,{type=it}){entryLabel(it)}
        Text("频率"); ChoiceRow(listOf(RepeatRule.NONE,RepeatRule.WEEKLY,RepeatRule.MONTHLY,RepeatRule.QUARTERLY,RepeatRule.YEARLY),repeat,{repeat=it}){repeatLabel(it)}
        AccountChoice("账户",state.accounts,account){account=it}
        val selectedAccount=state.accounts.firstOrNull{it.name==account}
        if(selectedAccount?.currencyCode=="CRYPTO") {
            Text("数字货币种类")
            ChoiceRow(listOf("USDT","BTC","LTC"),cryptoSymbol,{cryptoSymbol=it}){if(it=="LTC")"ltc" else it}
        }
        val displayCurrency=if(selectedAccount?.currencyCode=="CRYPTO")cryptoSymbol else selectedAccount?.currencyCode?:"CNY"
        MoneyField(amount,{amount=it},"金额（${currencyLabel(displayCurrency)}）")
        if(type==EntryType.TRANSFER) AccountChoice("转入账户",state.accounts.filter{it.name!=account},toAccount){toAccount=it}
        DateChoice("开始日期",date){date=it}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(hasEnd,{hasEnd=it});Text("设置结束日期")}
        if(hasEnd) DateChoice("结束日期",endDate){endDate=it}
        if(type==EntryType.EXPENSE && (profile.isEnabled(FeatureModule.INSTALLMENT) || (old?.installmentTotal ?: 1) > 1)) {
            TextFieldSimple(installmentTotal,{installmentTotal=it},"总期数（1 表示非分期）",KeyboardType.Number)
            TextFieldSimple(installments,{installments=it},"剩余期数（0 表示按结束日期）",KeyboardType.Number)
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
    var amount by remember{mutableStateOf(old?.amountCents?.let(::decimal)?:"")}
    var income by remember{mutableStateOf(old?.isIncome?:false)}
    var account by remember{mutableStateOf(old?.accountName?:state.accounts.firstOrNull()?.name.orEmpty())}
    var date by remember{mutableStateOf(old?.dateEpochDay?.let(LocalDate::ofEpochDay)?:LocalDate.now().plusDays(1))}
    var budget by remember{mutableStateOf(old?.usesBudget?:false)}
    EditDialog(if(old==null)"新增未来临时收支" else "编辑未来临时收支",close,{
        parseCents(amount)?.let{save(AdjustmentEntity(old?.id?:0,name.ifBlank{if(income)"临时收入" else "临时支出"},AdjustmentType.TEMPORARY,it,account,date.toEpochDay(),income,budget,old?.appliedToBalance?:false))}
    }) {
        TextFieldSimple(name,{name=it},"名称");MoneyField(amount,{amount=it},"金额")
        ChoiceRow(listOf(false,true),income,{income=it}){if(it)"收入" else "支出"}
        AccountChoice("账户",state.accounts,account){account=it};DateChoice("发生日期",date){date=it}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(budget,{budget=it});Text("占用预算")}
    }
}

@Composable private fun AccountDialog(old:AccountEntity?,profile:AppProfile,close:()->Unit,save:(AccountEntity)->Unit) {
    var name by remember{mutableStateOf(old?.name?:"")}
    var balance by remember{mutableStateOf("")}
    var currency by remember{mutableStateOf(old?.currencyCode?:"CNY")}
    var type by remember{mutableStateOf(old?.accountType?:AccountType.CASH)}
    EditDialog(if(old==null)"增加账户" else "编辑账户",close,{
        if(old==null) {
            val actualCurrency = when(type) {
                AccountType.PAYPAL -> "USD"
                AccountType.CRYPTO -> "CRYPTO"
                AccountType.CUSTOM -> currency
                else -> "CNY"
            }
            val initial = if(actualCurrency=="CRYPTO") 0 else parseCents(balance) ?: return@EditDialog
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
            val allowedTypes = buildList {
                add(AccountType.CASH); add(AccountType.WECHAT); add(AccountType.ALIPAY); add(AccountType.CMB)
                if (profile.isEnabled(FeatureModule.PAYPAL_USD)) add(AccountType.PAYPAL)
                if (profile.isEnabled(FeatureModule.CRYPTO)) add(AccountType.CRYPTO)
                add(AccountType.CUSTOM)
            }
            ChoiceRow(allowedTypes,type,{type=it}){accountTypeLabel(it)}
            if(type==AccountType.CUSTOM) {
                Text("币种")
                val currencies = if (profile.isEnabled(FeatureModule.PAYPAL_USD)) listOf("CNY","USD") else listOf("CNY")
                ChoiceRow(currencies,currency,{currency=it}){currencyLabel(it)}
            }
            if(type!=AccountType.CRYPTO) {
                val unit = if(type==AccountType.PAYPAL || (type==AccountType.CUSTOM && currency=="USD")) "美元" else "人民币"
                MoneyField(balance,{balance=it},"初始真实余额（$unit）")
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

private fun parseCents(text:String):Long?=text.trim().toBigDecimalOrNull()?.movePointRight(2)?.setScale(0,RoundingMode.HALF_UP)?.longValueExact()
internal fun parseCentsExpression(text:String):Long? {
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
        total.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}
private fun parseCryptoUnits(text:String):Long?=text.trim().toBigDecimalOrNull()?.movePointRight(8)?.setScale(0,RoundingMode.HALF_UP)?.longValueExact()
private fun parseCurrencyAmount(text:String,currency:String):Long?=if(currency in listOf("USDT","BTC","LTC"))parseCryptoUnits(text) else parseCents(text)
private fun decimal(cents:Long)="${cents/100}.${(cents.absoluteValue%100).toString().padStart(2,'0')}"
private fun cryptoDecimal(units:Long)=java.math.BigDecimal.valueOf(units,8).stripTrailingZeros().toPlainString()
private fun currencyDecimal(amount:Long,currency:String)=if(currency in listOf("USDT","BTC","LTC"))cryptoDecimal(amount) else decimal(amount)
private fun money(cents:Long)="${if(cents<0)"-" else ""}¥${cents.absoluteValue/100}.${(cents.absoluteValue%100).toString().padStart(2,'0')}"
private fun usdMoney(cents:Long)="${if(cents<0)"-" else ""}$${cents.absoluteValue/100}.${(cents.absoluteValue%100).toString().padStart(2,'0')}"
private fun nativeMoney(cents:Long,currency:String)=when(currency){"USD"->"$${decimal(cents)}";"CNY"->"¥${decimal(cents)}";else->"$currency ${decimal(cents)}"}
private fun currencyMoney(amount:Long,currency:String)=when(currency){"CNY"->money(amount);"USD"->usdMoney(amount);"LTC"->"ltc ${cryptoDecimal(amount)}";else->"$currency ${if(currency in listOf("USDT","BTC"))cryptoDecimal(amount) else decimal(amount)}"}
private fun signedCurrencyMoney(amount:Long,currency:String)="${if(amount>=0)"+" else "-"}${currencyMoney(amount.absoluteValue,currency)}"
private fun cryptoSummary(holdings:List<CryptoHoldingEntity>)=listOf("USDT","BTC","LTC").joinToString(" · "){symbol->
    val value=holdings.firstOrNull{it.symbol==symbol}?.amountUnits?:0
    "${if(symbol=="LTC")"ltc" else symbol} ${cryptoDecimal(value)}"
}
private fun monthDay(date:LocalDate)="${date.monthValue}/${date.dayOfMonth}"
private fun entryLabel(t:EntryType)=when(t){EntryType.INCOME->"收入";EntryType.EXPENSE->"支出";EntryType.TRANSFER->"转账";EntryType.DEBT->"负债";EntryType.REFUND->"退款"}
private fun repeatLabel(r:RepeatRule)=when(r){RepeatRule.NONE->"一次";RepeatRule.DAILY->"每天";RepeatRule.WEEKLY->"每周";RepeatRule.MONTHLY->"每月";RepeatRule.QUARTERLY->"每季度";RepeatRule.YEARLY->"每年"}
private fun accountTypeLabel(t:AccountType)=when(t){AccountType.CASH->"现金";AccountType.WECHAT->"微信";AccountType.ALIPAY->"支付宝";AccountType.CMB->"银行";AccountType.PAYPAL->"PayPal";AccountType.CRYPTO->"数字货币";AccountType.CUSTOM->"自定义"}
private fun currencyLabel(code:String)=when(code){"CNY"->"人民币";"USD"->"美元";"LTC"->"ltc";"CRYPTO"->"数字货币";else->code}
private fun modeLabel(mode:AppMode)=when(mode){AppMode.SIMPLE->"极简";AppMode.CUSTOM->"定制";AppMode.FULL->"完整"}
private fun modeDescription(mode:AppMode)=when(mode){
    AppMode.SIMPLE->"账户、固定收支、统一预算和预测，适合只想看现金流。"
    AppMode.CUSTOM->"只显示你选择的功能；账户仍可随时增减。"
    AppMode.FULL->"开放预算、临时收支、转账、分期、美元和数字货币。"
}
private fun featureLabel(module:FeatureModule)=when(module){
    FeatureModule.BUDGET->"预算"
    FeatureModule.TEMPORARY_CASHFLOW->"未来临时收支"
    FeatureModule.TRANSFER->"账户间转账"
    FeatureModule.INSTALLMENT->"分期付款"
    FeatureModule.PAYPAL_USD->"PayPal 与美元账户"
    FeatureModule.CRYPTO->"数字货币"
}
