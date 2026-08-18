package com.qiyue.cashflow.ui

import com.qiyue.cashflow.BuildConfig
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiyue.cashflow.cashflow.CashFlowCalculator
import com.qiyue.cashflow.cashflow.CashFlowDay
import com.qiyue.cashflow.cashflow.initialPlanPostingCursor
import com.qiyue.cashflow.data.*
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val Green = Color(0xFF173F34)
private val Red = Color(0xFFA64C41)
private val Paper = Color(0xFFF4F0E8)
private val Panel = Color(0xFFFAF8F3)
private val SoftGreen = Color(0xFFD2E2DC)
private val Ink = Color(0xFF202521)
private val Muted = Color(0xFF77756F)
private val Line = Color(0xFFCfd1CA)
private enum class Tab(val title: String, val icon: String) {
    HOME("首页", "⌂"), PLAN("计划", "▤"), ACCOUNT("账户", "¥"), SETTINGS("设置", "⚙")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowApp(vm: CashFlowViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Green,
        onPrimary = Color.White,
        primaryContainer = SoftGreen,
        onPrimaryContainer = Green,
        secondary = Green,
        onSecondary = Color.White,
        secondaryContainer = SoftGreen,
        onSecondaryContainer = Green,
        tertiary = Green,
        onTertiary = Color.White,
        error = Red,
        onError = Color.White,
        errorContainer = Color(0xFFF0DDD7),
        onErrorContainer = Color(0xFF6F2D27),
        background = Paper,
        onBackground = Ink,
        surface = Panel,
        onSurface = Ink,
        surfaceVariant = Color(0xFFE9E6DE),
        onSurfaceVariant = Muted,
        outline = Color(0xFFBFC5BE),
        outlineVariant = Line,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Panel,
        surfaceContainer = Panel,
        surfaceContainerHigh = Color(0xFFF0EDE6),
        surfaceContainerHighest = Color(0xFFE9E6DE),
    ),shapes=Shapes(
        extraSmall=RoundedCornerShape(3.dp),
        small=RoundedCornerShape(3.dp),
        medium=RoundedCornerShape(3.dp),
        large=RoundedCornerShape(18.dp),
        extraLarge=RoundedCornerShape(24.dp),
    )) {
        Scaffold(
            topBar = { CashTopBar(tab) },
            bottomBar = { CashBottomBar(tab) { tab = it } },
            containerColor = Paper,
            snackbarHost = { state.message?.let { msg ->
                Snackbar(
                    modifier=Modifier.padding(12.dp),
                    containerColor=Green,
                    contentColor=Color.White,
                    actionContentColor=Color.White,
                    action={
                    TextButton(
                        onClick=if(state.pendingTransferUndo!=null) vm::undoLastTransfer else vm::dismissMessage,
                        colors=ButtonDefaults.textButtonColors(contentColor=Color.White),
                    ) {
                        Text(if(state.pendingTransferUndo!=null) "撤销" else "知道了")
                    }
                }) { Text(msg) }
            }}
        ) { pad ->
            when (tab) {
                Tab.HOME -> HomePage(state, pad, vm) { tab = Tab.PLAN }
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

@Composable private fun CashTopBar(tab: Tab) = Row(
    Modifier.fillMaxWidth().background(Paper).statusBarsPadding().height(62.dp).padding(horizontal=14.dp,vertical=8.dp),
    horizontalArrangement=Arrangement.SpaceBetween,
    verticalAlignment=Alignment.CenterVertically,
) {
    Column {
        Text(if(tab==Tab.HOME) "啥时候有钱" else tab.title,fontWeight=FontWeight.ExtraBold,fontSize=20.sp,letterSpacing=(-.5).sp)
        if(tab!=Tab.HOME) Text(when(tab){
            Tab.PLAN->"固定、分期与未来临时收支"
            Tab.ACCOUNT->"点击名称或余额直接修改"
            Tab.SETTINGS->"显示、预测与本地数据"
            else->""
        },color=Muted,fontSize=10.sp)
    }
    if(tab==Tab.HOME) Text(homeDateLabel(LocalDate.now()),color=Muted,fontSize=10.sp)
}

@Composable private fun CashBottomBar(selected:Tab,onSelect:(Tab)->Unit) = Row(
    Modifier.fillMaxWidth().background(Panel).border(1.dp,Line).navigationBarsPadding().height(58.dp).padding(top=4.dp),
    horizontalArrangement=Arrangement.SpaceAround,
) {
    Tab.entries.forEach { item ->
        Column(
            Modifier.weight(1f).fillMaxHeight().clickable{onSelect(item)},
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center,
        ) {
            Text(item.icon,color=if(item==selected)Green else Muted,fontSize=17.sp)
            Text(item.title,color=if(item==selected)Green else Muted,fontSize=10.sp,fontWeight=if(item==selected)FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OnboardingDialog(
    complete: (Set<AccountType>, Boolean) -> Unit,
) {
    var accounts by remember {
        mutableStateOf(setOf(
            AccountType.CMB, AccountType.WECHAT, AccountType.ALIPAY,
            AccountType.CASH, AccountType.PAYPAL, AccountType.CRYPTO,
        ))
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

@Composable private fun HomePage(
    state: CashFlowUiState,
    pad: PaddingValues,
    vm: CashFlowViewModel,
    openPlans: () -> Unit,
) {
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
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(start=14.dp,end=14.dp,bottom=14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                modifier=Modifier.fillMaxWidth().heightIn(min=116.dp),
                shape=RoundedCornerShape(22.dp),
                colors=CardDefaults.cardColors(containerColor=Green),
            ) { Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=15.dp),verticalAlignment=Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("真实余额", color=Color.White.copy(alpha=.72f), fontSize=11.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(money(s.currentBalanceCents), color=Color.White, fontSize=32.sp, fontWeight=FontWeight.Bold, maxLines=1)
                    Text("人民币", color=Color.White.copy(alpha=.65f), fontSize=10.sp)
                }
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    if("USD" !in hiddenHomeCurrencies&&state.accounts.any{it.currencyCode=="USD"}) CompactCurrencyLine("美元",usdMoney(s.currentUsdCents))
                    otherCurrencyBalances.forEach { (currency, amount) -> CompactCurrencyLine(currencyLabel(currency),currencyMoney(amount,currency)) }
                    if("CRYPTO" !in hiddenHomeCurrencies&&state.accounts.any{it.currencyCode=="CRYPTO"}) CompactCurrencyLine("数字货币",cryptoSummary(state.cryptoHoldings))
                }
            }}
        }
        item { PageSectionHeader("余额走势") { PeriodSelector(state.horizonDays,vm::setHorizon) } }
        item {
            Card(Modifier.fillMaxWidth().border(1.dp,Line),shape=RoundedCornerShape(0.dp),colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.padding(horizontal=7.dp,vertical=7.dp)) {
                ForecastChart(s.days, Modifier.fillMaxWidth().height(212.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("点按曲线查看当天明细",color=Muted,fontSize=9.sp)
                    Text("月底预计 ${money(monthEnd)}",color=Muted,fontSize=9.sp)
                }
            }}
        }
        item { PageSectionHeader("预计余额") { Text("根据当前计划计算",color=Muted,fontSize=9.sp) } }
        item {
            ForecastMetricsGrid(
                monthEnd=money(monthEnd),
                lowest="${money(s.lowestBalanceCents)}\n${s.lowestDate ?: "—"}",
                highest="${money(s.highestBalanceCents)}\n${s.highestDate ?: "—"}",
                budget=money(s.budgetRemainingCents),
                warn=s.lowestBalanceCents<0,
            )
        }
        item { ShortageCard(
            s.firstShortageDate,
            s.days.firstOrNull{it.date==s.firstShortageDate}?.balanceCents,
            state.settings.showFinanceTips,
            state.settings.financeTipChangesDaily,
        ) }
        item { PageSectionHeader("接下来会发生什么") {
            Text("查看全部计划 ›",Modifier.clickable(onClick=openPlans),color=Muted,fontSize=9.sp)
        } }
        val upcoming = s.days.flatMap { d -> d.events.filterNot { it.kind == "预算" }.map { d to it } }.take(20)
        if (upcoming.isEmpty()) item { EmptyCard("暂无未来款项，请到“计划”添加固定收支。") }
        else item { UpcomingEventsCard(upcoming) }
    }
}

@Composable private fun UpcomingEventsCard(upcoming:List<Pair<CashFlowDay,com.qiyue.cashflow.cashflow.ForecastEvent>>) {
    Column(Modifier.fillMaxWidth().background(Panel).border(1.dp,Line)) {
        upcoming.forEachIndexed { index,(day,event) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min=66.dp).padding(end=14.dp),
                verticalAlignment=Alignment.CenterVertically,
            ) {
                Box(Modifier.width(92.dp).height(66.dp)) {
                    if(index<upcoming.lastIndex) Box(
                        Modifier.align(Alignment.BottomStart).offset(x=18.dp).width(1.dp).height(34.dp).background(Line)
                    )
                    if(index>0) Box(
                        Modifier.align(Alignment.TopStart).offset(x=18.dp).width(1.dp).height(28.dp).background(Line)
                    )
                    Box(
                        Modifier.align(Alignment.CenterStart).offset(x=13.dp).size(11.dp)
                            .background(if(event.amountCents<0)Red else Green,CircleShape)
                            .border(2.dp,Panel,CircleShape)
                    )
                    Row(
                        Modifier.align(Alignment.CenterStart).padding(start=34.dp),
                        verticalAlignment=Alignment.Bottom,
                    ) {
                        Text(day.date.dayOfMonth.toString(),fontSize=18.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.width(3.dp))
                        Text("${day.date.monthValue}月",color=Muted,fontSize=9.sp,modifier=Modifier.padding(bottom=3.dp))
                    }
                }
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                    Text(event.name,fontWeight=FontWeight.SemiBold,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(event.accountName.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(event.accountName,color=Muted,fontSize=9.sp,maxLines=1)
                    }
                }
                Text(
                    if(event.amountCents==0L)"转账" else signedCurrencyMoney(event.amountCents,event.currencyCode),
                    color=if(event.amountCents<0)Red else Green,
                    fontWeight=FontWeight.Bold,
                    fontSize=12.sp,
                )
            }
            if(index<upcoming.lastIndex) HorizontalDivider(Modifier.padding(start=36.dp),color=Line)
        }
    }
}

@Composable private fun CompactCurrencyLine(label:String,value:String) = Text("$label  $value",color=Color.White.copy(alpha=.76f),fontSize=9.sp,maxLines=1)

@Composable private fun PageSectionHeader(title:String,trailing:@Composable ()->Unit={}) = Row(
    Modifier.fillMaxWidth().heightIn(min=28.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom
) { Text(title,fontWeight=FontWeight.Bold,fontSize=13.sp);trailing() }

@Composable private fun PeriodSelector(selected:Int,onSelect:(Int)->Unit) = Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
    listOf(30,90,365).forEach { value ->
        Column(Modifier.clickable{onSelect(value)},horizontalAlignment=Alignment.CenterHorizontally) {
            Text("${value}天",color=if(value==selected)Green else Muted,fontSize=10.sp,fontWeight=if(value==selected)FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.width(24.dp).height(2.dp).background(if(value==selected)Green else Color.Transparent))
        }
    }
}

@Composable private fun ForecastMetricsGrid(monthEnd:String,lowest:String,highest:String,budget:String,warn:Boolean) {
    Column(Modifier.fillMaxWidth().background(Panel).border(1.dp,Line)) {
        Row(Modifier.fillMaxWidth()) {
            ForecastMetricCell("月底",monthEnd,Modifier.weight(1f),false)
            ForecastMetricCell("谷底",lowest,Modifier.weight(1f),warn)
        }
        Row(Modifier.fillMaxWidth()) {
            ForecastMetricCell("高峰",highest,Modifier.weight(1f),false)
            ForecastMetricCell("预算剩余",budget,Modifier.weight(1f),false)
        }
    }
}

@Composable private fun ForecastMetricCell(label:String,value:String,modifier:Modifier,warn:Boolean) = Column(
    modifier.height(80.dp).background(Panel).border(.5.dp,Line).padding(horizontal=12.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(4.dp)
) {
    Text(label,color=Muted,fontSize=9.sp)
    Text(value,color=if(warn)Red else Ink,fontWeight=FontWeight.Bold,fontSize=12.sp,lineHeight=15.sp)
}

private val shortageMessages = listOf(
    "现金如同氧气，拥有时容易忽视，缺少时只会想到它。",
    "无法预知明天，就要为明天做好准备。",
    "先留有余地，才能从容选择。",
    "安全边际，是自由选择的起点。",
    "先保证走完全程，才有机会赢得比赛。",
    "避免陷入困境，比走出困境更容易。",
    "留住现金，也是在留住选择。",
)

@Composable private fun ShortageCard(date:LocalDate?,balanceOnDate:Long?,showTip:Boolean,changesDaily:Boolean) {
    val message=remember(date,changesDaily){date?.let{
        val seed=if(changesDaily)LocalDate.now().toEpochDay() else it.toEpochDay()
        shortageMessages[(seed.absoluteValue%shortageMessages.size).toInt()]
    }}
    Card(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(2.dp),
        colors=CardDefaults.cardColors(containerColor=if(date==null)SoftGreen else Color(0xFF91483E)),
    ) { Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        if(date==null) Text("长期预测检查：现金为负日期无",fontWeight=FontWeight.SemiBold,fontSize=11.sp,color=Green)
        else {
            Text("${monthDay(date)} 起现金预计为负",fontWeight=FontWeight.Bold,fontSize=16.sp,color=Color.White)
            balanceOnDate?.let{Text("预计缺口 ${money(it.absoluteValue)}",color=Color(0xFFF0D1CB),fontSize=10.sp)}
            if(showTip&&message!=null) Text(message,fontWeight=FontWeight.SemiBold,color=Color.White,fontSize=10.sp)
            Text("行动提醒：提前检查日期，推迟非必要支出或补充确定收入。",color=Color(0xFFF0D1CB),fontSize=9.sp)
        }
    }}
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
                Modifier.width(47.dp).fillMaxHeight(),
                verticalArrangement=Arrangement.SpaceBetween
            ) {
                yTicks.forEach { tick ->
                    Text(axisMoney(tick.toLong()), color=Muted, fontSize=8.sp, maxLines=1)
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
                Spacer(Modifier.width(47.dp))
                listOf(0,days.lastIndex/4,days.lastIndex/2,days.lastIndex*3/4,days.lastIndex).forEach { index ->
                    Text(monthDay(days[index].date),Modifier.weight(1f),color=Color.Gray,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=1)
                }
            }
        }
        selectedIndex?.let { index -> Text(
            "${days[index].date} · ${money(days[index].balanceCents)}",
            Modifier.fillMaxWidth(),color=Green,fontWeight=FontWeight.SemiBold,fontSize=10.sp
        ) }
    }
}

@Composable private fun PlanPage(state: CashFlowUiState, pad: PaddingValues, vm: CashFlowViewModel) {
    var planEdit by remember { mutableStateOf<PlannedTransactionEntity?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    var newPlanIsInstallment by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var budgetEdit by remember { mutableStateOf<BudgetEntity?>(null) }
    var showBudget by remember { mutableStateOf(false) }
    var adjustmentEdit by remember { mutableStateOf<AdjustmentEntity?>(null) }
    var showAdjustment by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    val temporaryAdjustments = state.adjustments.filter { it.type == AdjustmentType.TEMPORARY }
    val visiblePlans = state.plans.filter { it.type != EntryType.TRANSFER }
    val occurrenceCalculator = remember { CashFlowCalculator() }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(start=14.dp,end=14.dp,bottom=14.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { Box {
            Button({showAddMenu=true},Modifier.height(36.dp),shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=0.dp)){Text("新增",fontSize=11.sp)}
            DropdownMenu(showAddMenu,{showAddMenu=false}) {
                DropdownMenuItem({Column{Text("固定款项");Text("工资、房租等周期款项",style=MaterialTheme.typography.bodySmall,color=Color.Gray)}},{planEdit=null;newPlanIsInstallment=false;showPlan=true;showAddMenu=false})
                DropdownMenuItem({Column{Text("分期");Text("按每期金额或总额计算",style=MaterialTheme.typography.bodySmall,color=Color.Gray)}},{planEdit=null;newPlanIsInstallment=true;showPlan=true;showAddMenu=false})
                DropdownMenuItem({Column{Text("未来临时收支");Text("一次性影响未来预测",style=MaterialTheme.typography.bodySmall,color=Color.Gray)}},{adjustmentEdit=null;showAdjustment=true;showAddMenu=false})
            }
        }}}
        item { PageSectionHeader("本期预算") }
        if(state.budgets.isEmpty()) item {
            EmptyCard("暂无预算。点击这里设置每月总预算。",onClick={budgetEdit=null;showBudget=true})
        }
        items(state.budgets.take(1), key={"budget-${it.id}"}) { b -> BudgetSummaryCard(b) { budgetEdit=b;showBudget=true } }
        item { PageSectionHeader("固定款项与分期") }
        if(visiblePlans.isEmpty()) item { EmptyCard("还没有固定款项。先添加工资或房租。") }
        items(visiblePlans, key={"plan-${it.id}"}) { p ->
            val installment=isInstallmentPlan(p)
            val next=remember(p,today){nextPlanDate(p,today,occurrenceCalculator)}
            DataCard(
                title=p.name,
                subtitle=buildString {
                    append(if(installment)"分期" else if(p.type==EntryType.INCOME)"固定收入" else "固定支出")
                    append(" · ${repeatLabel(p.repeatRule)} · ${p.accountName}")
                    append(" · 下次 ${next ?: "无"}")
                    if(installment) append(" · 剩余${remainingInstallmentPeriods(p)}期")
                },
                amount=if(installment) "剩余 ${currencyMoney(installmentRemainingAmount(p),p.currencyCode)}" else "每次 ${currencyMoney(p.amountCents,p.currencyCode)}",
                active=p.isActive,
                onToggle={vm.savePlan(p.copy(isActive=!p.isActive))}, onEdit={planEdit=p;showPlan=true},
                onDelete={delete="“${p.name}”" to {vm.deletePlan(p.id)}}
            )
        }
        item { PageSectionHeader("未来临时收支") }
        if(temporaryAdjustments.isEmpty()) item { EmptyCard("暂无未来临时收支。实际余额有偏差时，直接修改账户真实余额即可。") }
        items(temporaryAdjustments, key={"adjustment-${it.id}"}) { a -> DataCard(
            a.name, buildString {
                append(if(a.isIncome)"临时收入" else "临时支出")
                append(" · ${LocalDate.ofEpochDay(a.dateEpochDay)}")
            if(a.appliedToBalance) append(" · 历史记录")
            },
            currencyMoney(
                a.amountCents,
                state.accounts.firstOrNull { it.name == a.accountName }?.currencyCode ?: "CNY"
            ), true, null,
            if(a.type==AdjustmentType.TEMPORARY) {{adjustmentEdit=a;showAdjustment=true}} else null,
            {delete="“${a.name}”" to {vm.deleteAdjustment(a.id)}})
        }
    }
    if(showPlan) PlanDialog(planEdit,newPlanIsInstallment,state,{showPlan=false}) {vm.savePlan(it);showPlan=false}
    if(showBudget) BudgetDialog(budgetEdit, state, {showBudget=false}) {vm.saveBudget(it);showBudget=false}
    if(showAdjustment) AdjustmentDialog(adjustmentEdit, state, {showAdjustment=false}) {vm.saveAdjustment(it);showAdjustment=false}
    delete?.let { (name, action) -> ConfirmDialog("删除$name？","删除后无法恢复。",{delete=null},{action();delete=null}) }
}

@Composable private fun BudgetSummaryCard(budget:BudgetEntity,onEdit:()->Unit) {
    val used=(budget.totalCents-budget.remainingCents).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().background(Panel).border(1.dp,Line).clickable(onClick=onEdit)) {
        Row(Modifier.fillMaxWidth()) {
            BudgetCell("总预算",money(budget.totalCents),"点击金额可修改",Modifier.weight(1f))
            BudgetCell("预算剩余",money(budget.remainingCents),"点击金额可修改",Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            BudgetCell("已经使用",money(used),"按当前剩余计算",Modifier.weight(1f))
            BudgetCell("预算周期",if(budget.period==BudgetPeriod.MONTHLY)"每月" else if(budget.period==BudgetPeriod.WEEKLY)"每周" else "自定义","点击此卡片编辑",Modifier.weight(1f))
        }
    }
}

@Composable private fun BudgetCell(label:String,value:String,hint:String,modifier:Modifier) = Column(
    modifier.height(74.dp).border(.5.dp,Line).padding(horizontal=12.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(3.dp)
) {
    Text(label,color=Muted,fontSize=9.sp)
    Text(value,color=Ink,fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=1)
    Text(hint,color=Muted,fontSize=8.sp,maxLines=1)
}

@Composable private fun AccountPage(state: CashFlowUiState, pad: PaddingValues, vm: CashFlowViewModel) {
    var showEditor by remember { mutableStateOf(false) }
    var showCryptoEditor by remember { mutableStateOf(false) }
    var transferSource by remember { mutableStateOf<AccountEntity?>(null) }
    val focusManager = LocalFocusManager.current
    val hiddenAccountIds = remember(state.settings.hiddenAccountIdsCsv) {
        parseAccountIdCsv(state.settings.hiddenAccountIdsCsv)
    }
    val visibleAccounts = state.accounts.filter { it.id !in hiddenAccountIds }
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding=PaddingValues(start=14.dp,end=14.dp,bottom=14.dp), verticalArrangement=Arrangement.spacedBy(0.dp)) {
        item { PageSectionHeader("我的账户") { Text("按设置中的显示项目",color=Muted,fontSize=9.sp) } }
        if(visibleAccounts.isEmpty()) item { EmptyCard("没有需要显示的账户，可到“设置”恢复隐藏账户。") }
        items(visibleAccounts, key={it.id}) { a ->
            val transferTargets = state.accounts.filter {
                it.id!=a.id && it.currencyCode==a.currencyCode && it.currencyCode!="CRYPTO"
            }
            var nameText by remember(a.id,a.name) { mutableStateOf(a.name) }
            var balanceText by remember(a.id,a.currentBalanceCents) { mutableStateOf(currencyDecimal(a.currentBalanceCents,a.currencyCode)) }
            var showCalculator by remember(a.id) { mutableStateOf(false) }
            var nameWasFocused by remember(a.id) { mutableStateOf(false) }
            var balanceWasFocused by remember(a.id) { mutableStateOf(false) }
            fun saveName() {
                val normalized=nameText.trim()
                if(normalized.isBlank()||state.accounts.any{it.id!=a.id&&it.name==normalized}) nameText=a.name
                vm.renameAccount(a,normalized)
            }
            fun saveBalance() {
                parseCurrencyExpression(balanceText,a.currencyCode)?.let { balance ->
                    if(balance!=a.currentBalanceCents) vm.calibrate(a,balance)
                }
            }
            Card(Modifier.fillMaxWidth().border(1.dp,Line),shape=RoundedCornerShape(0.dp),colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.padding(horizontal=11.dp,vertical=10.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                BasicTextField(
                    value=nameText,
                    onValueChange={nameText=it},
                    modifier=Modifier.weight(1f).onFocusChanged { focusState ->
                        if(nameWasFocused&&!focusState.isFocused) saveName()
                        nameWasFocused=focusState.isFocused
                    },
                    singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(color=Ink,fontWeight=FontWeight.Bold,fontSize=12.sp),
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                    keyboardActions=KeyboardActions(onDone={saveName();focusManager.clearFocus()})
                )
                Text("点击修改",color=Muted,fontSize=8.sp)
                Spacer(Modifier.width(8.dp))
                Text(currencyLabel(a.currencyCode), color=Muted,fontSize=8.sp)
                }
                if(a.currencyCode=="CRYPTO") {
                    Text(cryptoSummary(state.cryptoHoldings),color=Muted,fontSize=9.sp)
                }
                if(a.currencyCode!="CRYPTO") {
                    Row(
                        Modifier.fillMaxWidth().height(46.dp).border(1.dp,Color(0xFFBFC5BE),RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal=10.dp),
                        verticalAlignment=Alignment.CenterVertically,
                    ) {
                    Text(when(a.currencyCode){"CNY"->"¥";"USD"->"$";else->a.currencyCode},color=Muted,fontSize=13.sp)
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value=balanceText,
                        onValueChange={balanceText=it},
                        modifier=Modifier.weight(1f).onFocusChanged { focusState ->
                            if(balanceWasFocused && !focusState.isFocused) saveBalance()
                            balanceWasFocused=focusState.isFocused
                        },
                        textStyle=MaterialTheme.typography.bodyMedium.copy(color=Ink,fontWeight=FontWeight.Bold,fontSize=14.sp),
                        singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={saveBalance();focusManager.clearFocus()})
                    )
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            {showCalculator=true},Modifier.weight(1f).height(36.dp),
                            shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=9.dp)
                        ){Text("打开计算器",fontSize=9.sp)}
                        OutlinedButton(
                            {transferSource=a},Modifier.weight(1f).height(36.dp),
                            shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=9.dp),
                            enabled=transferTargets.isNotEmpty()
                        ){Text("转账",fontSize=9.sp)}
                    }
                }
                if(a.currencyCode=="CRYPTO") OutlinedButton({showCryptoEditor=true},shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=5.dp)){Text("修改持仓",fontSize=9.sp)}
            }}
            if(showCalculator) BalanceCalculatorDialog(
                balanceText,a.currencyCode,{showCalculator=false}
            ){result->
                balanceText=result
                parseCurrencyExpression(result,a.currencyCode)?.let{vm.calibrate(a,it)}
                showCalculator=false
            }
        }
        item { OutlinedButton({showEditor=true},Modifier.fillMaxWidth().padding(top=14.dp),shape=RoundedCornerShape(0.dp)){Text("增加账户",fontSize=11.sp)} }
    }
    if(showEditor) AccountDialog({showEditor=false}){vm.saveAccount(it);showEditor=false}
    transferSource?.let { source -> AccountTransferDialog(
        source,state.accounts,state.settings.showFinanceTips,{transferSource=null},{vm.setFinanceTips(false)}
    ){fromId,toId,amount->
        vm.transferBalance(fromId,toId,amount);transferSource=null
    }
    }
    if(showCryptoEditor) CryptoHoldingsDialog(state.cryptoHoldings,{showCryptoEditor=false}) {
        vm.saveCryptoHoldings(it);showCryptoEditor=false
    }
}

@Composable private fun SettingsPage(state: CashFlowUiState, pad: PaddingValues, vm: CashFlowViewModel) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var days by remember(state.settings.defaultHorizonDays) { mutableIntStateOf(state.settings.defaultHorizonDays) }
    var budgetStartDay by remember(state.settings.budgetMonthStartDay) { mutableStateOf(state.settings.budgetMonthStartDay.toString()) }
    var hiddenHomeCurrencies by remember(state.settings.hiddenHomeCurrenciesCsv,state.accounts.map{it.currencyCode}) {
        mutableStateOf(parseCurrencyCodeCsv(state.settings.hiddenHomeCurrenciesCsv))
    }
    var hiddenAccountIds by remember(state.settings.hiddenAccountIdsCsv, state.accounts.map { it.id }) {
        mutableStateOf(parseAccountIdCsv(state.settings.hiddenAccountIdsCsv))
    }
    var showFinanceTips by remember(state.settings.showFinanceTips) { mutableStateOf(state.settings.showFinanceTips) }
    var financeTipChangesDaily by remember(state.settings.financeTipChangesDaily) { mutableStateOf(state.settings.financeTipChangesDaily) }
    var showAccountVisibility by remember { mutableStateOf(false) }
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
    LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(start=14.dp,end=14.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        item { SettingsGroup("显示") {
            homeCurrencies.forEach { currency -> SettingToggleRow(
                "首页显示${currencyLabel(currency)}",currency !in hiddenHomeCurrencies
            ){visible->hiddenHomeCurrencies=if(visible)hiddenHomeCurrencies-currency else hiddenHomeCurrencies+currency} }
            if(homeCurrencies.isEmpty()) SettingTextRow("首页显示币种","目前只有人民币账户")
            SettingActionRow(
                "账户页显示项目",
                state.accounts.filter{it.id !in hiddenAccountIds}.joinToString("、"){it.name}.ifBlank{"未选择"}
            ){showAccountVisibility=true}
        } }
        item { SettingsGroup("预测") {
            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=9.dp)) {
                Text("默认预测周期",fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp));PeriodSelector(days){days=it}
            }
            HorizontalDivider(color=Line)
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text("每月预算起始日",fontSize=11.sp,fontWeight=FontWeight.SemiBold);Text("1—28 日",fontSize=8.sp,color=Muted)}
                Row(
                    Modifier.width(108.dp).height(42.dp).border(1.dp,Color(0xFFBFC5BE),RoundedCornerShape(3.dp)).background(Color.White).padding(horizontal=10.dp),
                    verticalAlignment=Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        budgetStartDay,
                        {budgetStartDay=it.filter(Char::isDigit).take(2)},
                        Modifier.weight(1f),
                        singleLine=true,
                        textStyle=MaterialTheme.typography.bodyMedium.copy(color=Ink,fontSize=13.sp,textAlign=TextAlign.Center),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),
                    )
                    Text("日",color=Muted,fontSize=10.sp)
                }
            }
            HorizontalDivider(color=Line)
            SettingToggleRow(
                "现金预警提示语",showFinanceTips,
                if(!showFinanceTips)"不显示" else if(financeTipChangesDaily)"风险期间每天更换" else "风险期间保持不变"
            ){showFinanceTips=it}
            if(showFinanceTips) {
                Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("预警期间提示语",fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                    ChoiceRow(listOf(false,true),financeTipChangesDaily,{financeTipChangesDaily=it}){
                        if(it)"每天更换" else "保持不变"
                    }
                }
            }
            Button({vm.updateSettings(days,budgetStartDay.toIntOrNull()?:1,hiddenHomeCurrencies,hiddenAccountIds,showFinanceTips,financeTipChangesDaily)},Modifier.fillMaxWidth().padding(10.dp),shape=RoundedCornerShape(0.dp)){Text("保存设置",fontSize=11.sp)}
        } }
        item { SettingsGroup("数据") {
            SettingActionRow("导出完整备份","JSON 文件保存到本机"){create.launch("现金流预测备份-${LocalDate.now()}.json")}
            SettingActionRow("从备份恢复","恢复会替换当前数据"){open.launch(arrayOf("application/json","text/*"))}
        } }
        item { SettingsGroup("危险操作") { SettingActionRow("清空所有测试数据","需要再次确认",Red){confirmClear=true} } }
        item { SettingsGroup("关于") {
            Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("啥时候有钱 ${BuildConfig.VERSION_NAME}",fontSize=11.sp,fontWeight=FontWeight.Bold)
                Text("该版本为简易版，仅供简单计算现金状况，暂未包含理财统计功能。如有存款、基金等投资，建议用“未来临时收支”功能替代。",color=Muted,fontSize=9.sp,lineHeight=14.sp)
                Text("开发者：尚宸鸣 | 使用 Codex 协助开发",fontSize=9.sp)
                Text("项目地址：github.com/7panni/show-me-the-money ›",Modifier.clickable{uriHandler.openUri("https://github.com/7panni/show-me-the-money")}.padding(vertical=5.dp),color=Green,fontSize=9.sp)
                Text("Blog：7panni.com ›",Modifier.clickable{uriHandler.openUri("https://7panni.com")}.padding(vertical=5.dp),color=Green,fontSize=9.sp)
                Text("独立安装 · 数据仅保存在本机",color=Muted,fontSize=8.sp)
            }
        } }
    }
    if(showAccountVisibility) EditDialog("账户页显示",{showAccountVisibility=false},{
        vm.updateSettings(days,budgetStartDay.toIntOrNull()?:1,hiddenHomeCurrencies,hiddenAccountIds,showFinanceTips,financeTipChangesDaily)
        showAccountVisibility=false
    }) {
        Text("选择要在账户页显示的项目",color=Muted,fontSize=10.sp)
        state.accounts.forEach { account -> SettingToggleRow(
            account.name,account.id !in hiddenAccountIds,currencyLabel(account.currencyCode)
        ){visible->hiddenAccountIds=if(visible)hiddenAccountIds-account.id else hiddenAccountIds+account.id} }
    }
    if(confirmClear) ConfirmDialog("清空全部测试数据？","账户、计划、预算和修正都会删除。",{confirmClear=false},{vm.clearAll();confirmClear=false})
}

@Composable private fun SettingsGroup(title:String,content:@Composable ColumnScope.()->Unit) = Column(Modifier.fillMaxWidth()) {
    Text(title,Modifier.padding(start=2.dp,top=8.dp,bottom=5.dp),color=Muted,fontSize=9.sp)
    Column(Modifier.fillMaxWidth().background(Panel).border(1.dp,Line),content=content)
}

@Composable private fun SettingToggleRow(title:String,checked:Boolean,subtitle:String?=null,onChecked:(Boolean)->Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min=44.dp).padding(horizontal=12.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically
) {
    Column(Modifier.weight(1f)){Text(title,fontSize=11.sp,fontWeight=FontWeight.SemiBold);subtitle?.let{Text(it,color=Muted,fontSize=8.sp)}}
    Box(
        Modifier.width(36.dp).height(22.dp).background(if(checked)Green else Color(0xFFC4C7C1),RoundedCornerShape(12.dp)).clickable{onChecked(!checked)}.padding(3.dp)
    ) { Box(Modifier.size(16.dp).align(if(checked)Alignment.CenterEnd else Alignment.CenterStart).background(Color.White,CircleShape)) }
}

@Composable private fun SettingTextRow(title:String,value:String) = Row(Modifier.fillMaxWidth().padding(12.dp)) {
    Text(title,Modifier.weight(1f),fontSize=11.sp,fontWeight=FontWeight.SemiBold);Text(value,color=Muted,fontSize=9.sp)
}

@Composable private fun SettingActionRow(title:String,subtitle:String,color:Color=Ink,onClick:()->Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically
) {
    Column(Modifier.weight(1f)){Text(title,color=color,fontSize=11.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,color=Muted,fontSize=8.sp)}
    Text("›",color=Muted,fontSize=16.sp)
}

private enum class InstallmentInputMode { PER_PAYMENT, TOTAL }

@Composable private fun PlanDialog(
    old: PlannedTransactionEntity?,
    startAsInstallment:Boolean,
    state: CashFlowUiState,
    close:()->Unit,
    save:(PlannedTransactionEntity)->Unit,
) {
    val isInstallment=old?.let(::isInstallmentPlan)?:startAsInstallment
    var name by remember {mutableStateOf(old?.name?:"")}
    var amount by remember {mutableStateOf(old?.let{currencyDecimal(it.amountCents,it.currencyCode)}?:"")}
    var type by remember {mutableStateOf(old?.type?.takeIf{it in listOf(EntryType.INCOME,EntryType.EXPENSE)}?:EntryType.EXPENSE)}
    var repeat by remember {mutableStateOf(old?.repeatRule?.takeIf{it in listOf(RepeatRule.WEEKLY,RepeatRule.MONTHLY,RepeatRule.QUARTERLY,RepeatRule.SEMIANNUAL,RepeatRule.YEARLY)}?:RepeatRule.MONTHLY)}
    var account by remember {mutableStateOf(old?.accountName?:state.accounts.firstOrNull()?.name.orEmpty())}
    var cryptoSymbol by remember {mutableStateOf(old?.currencyCode?.takeIf{it in listOf("USDT","BTC","LTC")} ?: "USDT")}
    var date by remember {
        mutableStateOf(
            old?.let { plan ->
                if (isInstallment) nextPlanDate(plan, LocalDate.now())
                    ?: LocalDate.ofEpochDay(plan.startDateEpochDay)
                else LocalDate.ofEpochDay(plan.startDateEpochDay)
            } ?: LocalDate.now().plusDays(1)
        )
    }
    var hasEnd by remember {mutableStateOf(old?.endDateEpochDay != null)}
    var endDate by remember {mutableStateOf(old?.endDateEpochDay?.let(LocalDate::ofEpochDay)?:date.plusYears(1))}
    var installmentMode by remember {mutableStateOf(if(old==null)InstallmentInputMode.PER_PAYMENT else InstallmentInputMode.TOTAL)}
    val oldPeriods=old?.installmentTotal?.coerceAtLeast(2)?:12
    val oldRemaining=old?.let(::remainingInstallmentPeriods)?:oldPeriods
    var totalPeriods by remember {mutableStateOf(oldPeriods.toString())}
    var paidPeriods by remember {mutableStateOf((oldPeriods-oldRemaining).toString())}
    val oldGrandTotal=old?.let{if(it.installmentTotalAmountCents>0)it.installmentTotalAmountCents else it.amountCents*oldPeriods}
    var perPayment by remember {mutableStateOf(old?.let{currencyDecimal(it.amountCents,it.currencyCode)}?:"")}
    var grandTotal by remember {mutableStateOf(oldGrandTotal?.let{currencyDecimal(it,old?.currencyCode?:"CNY")}?:"")}
    EditDialog(
        if(old==null)if(isInstallment)"新增分期" else "新增固定款项" else if(isInstallment)"编辑分期" else "编辑固定款项",
        close,
        {
            val sourceAccount=state.accounts.firstOrNull{it.name==account}?:return@EditDialog
            val currency=if(sourceAccount.currencyCode=="CRYPTO")cryptoSymbol else sourceAccount.currencyCode
            if(isInstallment) {
                val periods=totalPeriods.toIntOrNull()?.takeIf{it>=2}?:return@EditDialog
                val paid=paidPeriods.toIntOrNull()?.takeIf{it in 0 until periods}?:return@EditDialog
                val values=when(installmentMode) {
                    InstallmentInputMode.PER_PAYMENT -> {
                        val each=parseCurrencyAmount(perPayment,currency)?.takeIf{it>0}?:return@EditDialog
                        val total=runCatching{Math.multiplyExact(each,periods.toLong())}.getOrNull()?:return@EditDialog
                        each to total
                    }
                    InstallmentInputMode.TOTAL -> {
                        val total=parseCurrencyAmount(grandTotal,currency)?.takeIf{it>0}?:return@EditDialog
                        val split=installmentBreakdown(total,periods)?:return@EditDialog
                        split.first to total
                    }
                }
                save(PlannedTransactionEntity(
                    id=old?.id?:0,name=name.ifBlank{"分期"},type=EntryType.EXPENSE,
                    amountCents=values.first,currencyCode=currency,exchangeRateToCnyMicros=0,
                    accountName=account,startDateEpochDay=date.toEpochDay(),repeatRule=RepeatRule.MONTHLY,
                    installmentTotal=periods,installmentRemaining=periods-paid,
                    installmentTotalAmountCents=values.second,
                    balanceAppliedThroughEpochDay=old?.balanceAppliedThroughEpochDay
                        ?: initialPlanPostingCursor(date),
                    isActive=old?.isActive?:true,
                ))
            } else {
                val cents=parseCurrencyAmount(amount,currency)?.takeIf{it>0}?:return@EditDialog
                if(hasEnd&&endDate<date)return@EditDialog
                save(PlannedTransactionEntity(
                    id=old?.id?:0,name=name.ifBlank{if(type==EntryType.INCOME)"工资" else "房租"},type=type,
                    amountCents=cents,currencyCode=currency,exchangeRateToCnyMicros=0,
                    accountName=account,startDateEpochDay=date.toEpochDay(),repeatRule=repeat,
                    endDateEpochDay=endDate.toEpochDay().takeIf{hasEnd},
                    balanceAppliedThroughEpochDay=old?.balanceAppliedThroughEpochDay
                        ?: initialPlanPostingCursor(date),
                    isActive=old?.isActive?:true,
                ))
            }
        }
    ) {
        TextFieldSimple(name,{name=it},"名称")
        if(!isInstallment) {
            Text("类型");ChoiceRow(listOf(EntryType.INCOME,EntryType.EXPENSE),type,{type=it}){entryLabel(it)}
        }
        AccountChoice(if(isInstallment)"扣款账户" else "账户",state.accounts,account){account=it}
        val selectedAccount=state.accounts.firstOrNull{it.name==account}
        if(selectedAccount?.currencyCode=="CRYPTO") {
            Text("数字货币种类");ChoiceRow(listOf("USDT","BTC","LTC"),cryptoSymbol,{cryptoSymbol=it}){it}
        }
        val currency=if(selectedAccount?.currencyCode=="CRYPTO")cryptoSymbol else selectedAccount?.currencyCode?:"CNY"
        if(isInstallment) {
            Text("计算方式");ChoiceRow(InstallmentInputMode.entries,installmentMode,{installmentMode=it}){
                if(it==InstallmentInputMode.PER_PAYMENT)"按每期金额" else "按总额"
            }
            if(installmentMode==InstallmentInputMode.PER_PAYMENT) MoneyField(perPayment,{perPayment=it},"每期金额（${currencyLabel(currency)}）")
            else MoneyField(grandTotal,{grandTotal=it},"分期总额（${currencyLabel(currency)}）")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)){TextFieldSimple(totalPeriods,{totalPeriods=it},"总期数",KeyboardType.Number)}
                Box(Modifier.weight(1f)){TextFieldSimple(paidPeriods,{paidPeriods=it},"已支付期数",KeyboardType.Number)}
            }
            val periods=totalPeriods.toIntOrNull()?:0
            val paid=paidPeriods.toIntOrNull()?:0
            val remaining=(periods-paid).coerceAtLeast(0)
            val preview=when(installmentMode) {
                InstallmentInputMode.PER_PAYMENT -> parseCurrencyAmount(perPayment,currency)?.let{it to runCatching{Math.multiplyExact(it,remaining.toLong())}.getOrNull()}
                InstallmentInputMode.TOTAL -> parseCurrencyAmount(grandTotal,currency)?.let{total->installmentBreakdown(total,periods)?.first?.let{each->each to (total-each*paid)}}
            }
            preview?.second?.let { remainingTotal ->
                Text("每期 ${currencyMoney(preview.first,currency)} · 剩余 $remaining 期 · 剩余总额 ${currencyMoney(remainingTotal,currency)}",color=Green,fontWeight=FontWeight.SemiBold)
            }
            Text("当前按无息分期计算。若有利息或手续费，请选择“按总额”并填写实际总还款额。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            DateChoice("下次扣款日期",date){date=it}
        } else {
            MoneyField(amount,{amount=it},"每次金额（${currencyLabel(currency)}）")
            Text("频率");ChoiceRow(listOf(RepeatRule.WEEKLY,RepeatRule.MONTHLY,RepeatRule.QUARTERLY,RepeatRule.SEMIANNUAL,RepeatRule.YEARLY),repeat,{repeat=it}){repeatLabel(it)}
            DateChoice("开始日期",date){date=it}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(hasEnd,{hasEnd=it});Text("设置结束日期")}
            if(hasEnd)DateChoice("结束日期",endDate){endDate=it}
            Text("固定款项只按每次金额影响预测，不计算、也不显示总剩余。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
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
        Text("未来临时收支只影响预测，不会自动修改账户真实余额。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun AccountDialog(close:()->Unit,save:(AccountEntity)->Unit) {
    var name by remember{mutableStateOf("")}
    var balance by remember{mutableStateOf("")}
    var type by remember{mutableStateOf(AccountType.CASH)}
    val commonCurrencies=listOf("CNY","USD","EUR","GBP","JPY","HKD")
    var currencyChoice by remember{mutableStateOf("CNY")}
    var customCurrency by remember{mutableStateOf("")}
    var currencyError by remember{mutableStateOf(false)}
    EditDialog("增加账户",close,{
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
    }) {
        TextFieldSimple(name,{name=it},"账户名称")
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
    source:AccountEntity,
    accounts:List<AccountEntity>,
    showTips:Boolean,
    close:()->Unit,
    dismissTips:()->Unit,
    transfer:(Long,Long,Long)->Unit,
) {
    val targets=accounts.filter{it.id!=source.id&&it.currencyCode==source.currencyCode&&it.currencyCode!="CRYPTO"}
    var toName by remember{mutableStateOf(targets.firstOrNull()?.name.orEmpty())}
    val selectedTarget=toName.takeIf{name->targets.any{it.name==name}}?:targets.firstOrNull()?.name.orEmpty()
    var amount by remember{mutableStateOf("")}
    var amountError by remember{mutableStateOf(false)}
    var confirmation by remember{mutableStateOf<Pair<AccountEntity,Long>?>(null)}
    val currency=source.currencyCode
    val pending=confirmation
    if(pending!=null) {
        val (target,minor)=pending
        val targetAfter=runCatching{Math.addExact(target.currentBalanceCents,minor)}.getOrNull()
        AlertDialog(
            onDismissRequest={confirmation=null},
            confirmButton={Button({transfer(source.id,target.id,minor)}){Text("确认转账")}},
            dismissButton={TextButton({confirmation=null}){Text("返回修改")}},
            title={Text("确认从“${source.name}”转账？")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("${source.name}：${currencyMoney(source.currentBalanceCents,currency)} → ${currencyMoney(source.currentBalanceCents-minor,currency)}")
                if(targetAfter!=null) Text("${target.name}：${currencyMoney(target.currentBalanceCents,currency)} → ${currencyMoney(targetAfter,currency)}")
                Text("同币种总余额不变。",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
            }}
        )
    } else EditDialog("从“${source.name}”转账",close,{
        val target=targets.firstOrNull{it.name==selectedTarget}?:return@EditDialog
        val minor=parseCurrencyAmount(amount,currency)?:return@EditDialog
        if(minor<=0L||minor>source.currentBalanceCents||runCatching{Math.addExact(target.currentBalanceCents,minor)}.isFailure) {
            amountError=true
            return@EditDialog
        }
        confirmation=target to minor
    }) {
        Text("转出账户：${source.name}",fontWeight=FontWeight.SemiBold)
        Text("当前余额 ${currencyMoney(source.currentBalanceCents,currency)}",color=Color.Gray)
        AccountChoice("转入账户",targets,selectedTarget){toName=it}
        MoneyField(amount,{amount=it;amountError=false},"转账金额（${currencyLabel(currency)}）")
        if(amountError) Text("金额必须大于 0，且不能超过转出账户余额。",color=Red,style=MaterialTheme.typography.bodySmall)
        if(showTips) FinanceTip("转账只改变钱所在的账户，不会改变同币种总资产。",dismissTips)
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
        Card(Modifier.fillMaxWidth().padding(8.dp).border(1.dp,Line),shape=RoundedCornerShape(0.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
            Column(Modifier.padding(horizontal=11.dp,vertical=9.dp).heightIn(max=620.dp)) {
                Text(title,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Column(
                    Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(5.dp),
                ){content()}
                HorizontalDivider(Modifier.padding(top=6.dp),color=Line)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    TextButton(close){Text("取消")}
                    Button(save,shape=RoundedCornerShape(0.dp)){Text("保存")}
                }
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
    Card(Modifier.fillMaxWidth().border(1.dp,Line),shape=RoundedCornerShape(0.dp),colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth()){Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(subtitle,color=Muted,fontSize=9.sp,lineHeight=12.sp)};Text(amount,fontWeight=FontWeight.Bold,fontSize=11.sp,color=if(amount.contains("-")||amount.contains("剩余"))Red else Green)}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            extraAction?.let{(label,action)->SmallAction(label,action)}
            onToggle?.let{SmallAction(if(active)"停用" else "恢复",it)}
            onEdit?.let{SmallAction("编辑",it)}
            SmallAction("删除",onDelete,Red)
        }
    }}
}
@Composable private fun SmallAction(label:String,onClick:()->Unit,color:Color=Green)=OutlinedButton(
    onClick,Modifier.height(30.dp),shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=color)
){Text(label,fontSize=9.sp)}
@Composable private fun FinanceTip(text:String,onDismiss:()->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFEAF5F1))) {
        Row(Modifier.fillMaxWidth().padding(start=14.dp,top=10.dp,bottom=10.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
            TextButton(onDismiss){Text("关闭")}
        }
    }
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
            OutlinedButton({onChange(date.minusDays(1))},Modifier.weight(1f).height(36.dp),shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)){Text("前一天",fontSize=10.sp,maxLines=1)}
            Button({showCalendar=true},Modifier.weight(1f).height(36.dp),shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)){Text("日历",fontSize=10.sp,maxLines=1)}
            OutlinedButton({onChange(date.plusDays(1))},Modifier.weight(1f).height(36.dp),shape=RoundedCornerShape(0.dp),contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)){Text("后一天",fontSize=10.sp,maxLines=1)}
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
@Composable private fun EmptyCard(text:String,onClick:(()->Unit)?=null) {
    val modifier=Modifier.fillMaxWidth().border(1.dp,Line).let{base->if(onClick!=null)base.clickable(onClick=onClick) else base}
    Card(modifier,shape=RoundedCornerShape(0.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
        Text(text,Modifier.padding(14.dp),color=Muted,fontSize=10.sp)
    }
}

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
internal fun isInstallmentPlan(plan:PlannedTransactionEntity):Boolean =
    plan.installmentTotal>1 && plan.repeatRule==RepeatRule.MONTHLY && !plan.name.contains("房租")
internal fun remainingInstallmentPeriods(plan:PlannedTransactionEntity):Int =
    if(plan.installmentTotal<=1)0
    else if(plan.installmentRemaining>0)plan.installmentRemaining.coerceAtMost(plan.installmentTotal)
    else if(plan.balanceAppliedThroughEpochDay==null)plan.installmentTotal
    else 0
internal fun installmentRemainingAmount(plan:PlannedTransactionEntity):Long {
    val periods=plan.installmentTotal.coerceAtLeast(1)
    val remaining=remainingInstallmentPeriods(plan)
    if(plan.installmentTotalAmountCents<=0L) return runCatching{
        Math.multiplyExact(plan.amountCents,remaining.toLong())
    }.getOrDefault(Long.MAX_VALUE)
    val regular=plan.installmentTotalAmountCents/periods
    val paid=periods-remaining
    return plan.installmentTotalAmountCents-regular*paid
}
internal fun nextPlanDate(
    plan:PlannedTransactionEntity,
    from:LocalDate,
    calculator:CashFlowCalculator=CashFlowCalculator(),
    maxDays:Int=3650,
):LocalDate? {
    if(!plan.isActive) return null
    for(offset in 0..maxDays) {
        val date=from.plusDays(offset.toLong())
        if(calculator.occursOn(plan,date)) return date
    }
    return null
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
private fun homeDateLabel(date:LocalDate):String {
    val weekday=listOf("一","二","三","四","五","六","日")[date.dayOfWeek.value-1]
    return "${date.monthValue}月${date.dayOfMonth}日 · 周$weekday"
}
private fun axisMoney(amount:Long):String {
    val value=amount/100.0
    return if(value.absoluteValue>=1000) "${if(value<0)"−" else ""}${(value.absoluteValue/1000).roundToInt()}k"
    else "${if(value<0)"−" else ""}${value.absoluteValue.roundToInt()}"
}
private fun entryLabel(t:EntryType)=when(t){EntryType.INCOME->"收入";EntryType.EXPENSE->"支出";EntryType.TRANSFER->"转账";EntryType.DEBT->"负债";EntryType.REFUND->"退款"}
private fun repeatLabel(r:RepeatRule)=when(r){RepeatRule.NONE->"一次";RepeatRule.DAILY->"每天";RepeatRule.WEEKLY->"每周";RepeatRule.MONTHLY->"每月";RepeatRule.QUARTERLY->"每季度";RepeatRule.SEMIANNUAL->"每半年";RepeatRule.YEARLY->"每年"}
private fun accountTypeLabel(t:AccountType)=when(t){AccountType.CASH->"现金";AccountType.WECHAT->"微信";AccountType.ALIPAY->"支付宝";AccountType.CMB->"银行";AccountType.PAYPAL->"PayPal";AccountType.CRYPTO->"数字货币";AccountType.CUSTOM->"自定义"}
private fun currencyLabel(code:String)=when(code.uppercase()){
    "CNY"->"人民币";"USD"->"美元";"EUR"->"欧元";"GBP"->"英镑";"JPY"->"日元";"HKD"->"港币"
    "LTC"->"ltc";"CRYPTO"->"数字货币";"OTHER"->"其他";else->code.uppercase()
}
