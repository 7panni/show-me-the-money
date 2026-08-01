package com.qiyue.cashflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiyue.cashflow.cashflow.CashFlowCalculator
import com.qiyue.cashflow.cashflow.CashFlowSummary
import com.qiyue.cashflow.data.*
import com.qiyue.cashflow.shared.AppMode
import com.qiyue.cashflow.shared.AppProfile
import com.qiyue.cashflow.shared.FeatureModule
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CashFlowUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val plans: List<PlannedTransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val adjustments: List<AdjustmentEntity> = emptyList(),
    val cryptoHoldings: List<CryptoHoldingEntity> = emptyList(),
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val horizonDays: Int = 90,
    val summary: CashFlowSummary = CashFlowCalculator().forecast(emptyList(), emptyList(), emptyList(), emptyList()),
    val message: String? = null,
    val isReady: Boolean = false,
)

class CashFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).transactionDao()
    private val setupPrefs = application.getSharedPreferences("forecast_setup", 0)
    private val calculator = CashFlowCalculator()
    private val horizon = MutableStateFlow(90)
    private val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            seedIfEmpty()
            if (dao.getSettings()?.onboardingCompleted == true) ensureDefaultStructure()
        }
    }

    val uiState = combine(
        dao.observeAccounts(), dao.observePlans(), dao.observeBudgets(),
        dao.observeAdjustments(), dao.observeSettings(), dao.observeCryptoHoldings(), horizon, message
    ) { values ->
        val accounts = values[0] as List<AccountEntity>
        val plans = values[1] as List<PlannedTransactionEntity>
        val budgets = values[2] as List<BudgetEntity>
        val adjustments = values[3] as List<AdjustmentEntity>
        val settings = (values[4] as AppSettingsEntity?) ?: AppSettingsEntity()
        val holdings = values[5] as List<CryptoHoldingEntity>
        val days = values[6] as Int
        val sortedAccounts = accounts.sortedBy { accountOrder(it) }
        val visibleSummary = calculator.forecast(
            sortedAccounts, plans, budgets, adjustments, horizonDays = days
        )
        val longTermShortage = if (days >= LONG_TERM_RISK_DAYS) {
            visibleSummary.firstShortageDate
        } else {
            calculator.forecast(
                sortedAccounts, plans, budgets, adjustments,
                horizonDays = LONG_TERM_RISK_DAYS
            ).firstShortageDate
        }
        CashFlowUiState(
            accounts = sortedAccounts,
            plans = plans,
            budgets = budgets,
            adjustments = adjustments,
            cryptoHoldings = holdings,
            settings = settings,
            horizonDays = days,
            summary = visibleSummary.copy(firstShortageDate = longTermShortage),
            message = values[7] as String?,
            isReady = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashFlowUiState())

    fun setHorizon(days: Int) { horizon.value = days }
    fun dismissMessage() { message.value = null }
    fun saveAccount(item: AccountEntity) = launch { dao.upsertAccount(item) }
    fun savePlan(item: PlannedTransactionEntity) = launch { dao.upsertPlan(item) }
    fun saveBudget(item: BudgetEntity) = launch { dao.upsertBudget(item) }
    fun saveAdjustment(item: AdjustmentEntity) = launch { dao.upsertAdjustment(item) }
    fun saveCryptoHoldings(items: List<CryptoHoldingEntity>) = launch {
        items.forEach { dao.upsertCryptoHolding(it) }
    }
    fun deletePlan(id: Long) = launch { dao.deletePlan(id) }
    fun deleteBudget(id: Long) = launch { dao.deleteBudget(id) }
    fun deleteAdjustment(id: Long) = launch { dao.deleteAdjustment(id) }

    fun applyAdjustmentToBalance(item: AdjustmentEntity) = launch {
        if (item.type != AdjustmentType.TEMPORARY || item.appliedToBalance) return@launch
        if (item.dateEpochDay > LocalDate.now().toEpochDay()) {
            message.value = "发生日期到来后才能计入真实余额"
            return@launch
        }
        val account = dao.getAccounts().firstOrNull { it.name == item.accountName }
        if (account == null) {
            message.value = "找不到对应账户，请先编辑这项临时收支"
            return@launch
        }
        val delta = if (item.isIncome) item.amountCents else -item.amountCents
        dao.upsertAccount(account.copy(currentBalanceCents = account.currentBalanceCents + delta))
        dao.upsertAdjustment(item.copy(appliedToBalance = true))
        message.value = "已计入真实余额，预测不会重复计算"
    }

    fun calibrate(account: AccountEntity, newBalanceCents: Long) = launch {
        dao.upsertAccount(account.copy(currentBalanceCents = newBalanceCents))
        message.value = "真实余额已更新"
    }

    fun updateSettings(
        defaultDays: Int,
        budgetStartDay: Int,
        showUsd: Boolean,
        showCrypto: Boolean,
        hiddenAccountIds: Set<Long>,
        appMode: AppMode,
        customModules: Set<FeatureModule>,
    ) = launch {
        val profile = AppProfile(appMode, customModules)
        val accounts = dao.getAccounts()
        fun shows(type: AccountType) = accounts.none { it.accountType == type } ||
            accounts.any { it.accountType == type && it.id !in hiddenAccountIds }
        dao.upsertSettings(uiState.value.settings.copy(
            defaultHorizonDays = defaultDays,
            budgetMonthStartDay = budgetStartDay.coerceIn(1, 28),
            showUsdOnHome = showUsd,
            showCryptoOnHome = showCrypto,
            showPaypalOnAccounts = shows(AccountType.PAYPAL),
            showAlipayOnAccounts = shows(AccountType.ALIPAY),
            showCryptoOnAccounts = shows(AccountType.CRYPTO),
            appMode = appMode.name,
            customModulesCsv = profile.modulesCsv(),
            hiddenAccountIdsCsv = hiddenAccountIds.intersect(accounts.map { it.id }.toSet())
                .sorted().joinToString(","),
            onboardingCompleted = true,
            seeded = true
        ))
        horizon.value = defaultDays
        message.value = "设置已保存；切换模式不会删除现有数据"
    }

    fun completeOnboarding(
        mode: AppMode,
        customModules: Set<FeatureModule>,
        accountTypes: Set<AccountType>,
        addExamples: Boolean,
    ) = launch {
        val profile = AppProfile(mode, customModules)
        val chosenTypes = accountTypes.ifEmpty { setOf(AccountType.CASH) }
        if (dao.accountCount() == 0) {
            val examples = if (addExamples) exampleBalances else emptyMap()
            dao.insertAccounts(chosenTypes.map { type -> defaultAccount(type, examples[type] ?: 0L) })
            if (AccountType.CRYPTO in chosenTypes) {
                dao.insertCryptoHoldings(listOf(
                    CryptoHoldingEntity("USDT", if (addExamples) 100_000_000L else 0L),
                    CryptoHoldingEntity("BTC"),
                    CryptoHoldingEntity("LTC"),
                ))
            }
            if (addExamples) addExampleForecastData(profile)
        }
        val createdAccounts = dao.getAccounts()
        val hiddenAccountIds = defaultHiddenAccountIds(createdAccounts)
        dao.upsertSettings((dao.getSettings() ?: AppSettingsEntity()).copy(
            showUsdOnHome = AccountType.PAYPAL in chosenTypes,
            showCryptoOnHome = AccountType.CRYPTO in chosenTypes,
            showPaypalOnAccounts = AccountType.PAYPAL in chosenTypes,
            showAlipayOnAccounts = AccountType.ALIPAY in chosenTypes,
            showCryptoOnAccounts = AccountType.CRYPTO in chosenTypes,
            appMode = mode.name,
            customModulesCsv = profile.modulesCsv(),
            hiddenAccountIdsCsv = hiddenAccountIds.sorted().joinToString(","),
            onboardingCompleted = true,
            seeded = true,
        ))
        markStructureCurrent()
        message.value = "设置完成，示例数据可在“设置”中一键清空"
    }

    fun clearAll() = launch {
        dao.replaceForecastData(emptyList(), emptyList(), emptyList(), emptyList(),
            uiState.value.settings.copy(
                hiddenAccountIdsCsv = "",
                seeded = true,
                onboardingCompleted = true,
            ))
        message.value = "账户和计划数据已清空，可从“账户”重新开始"
    }

    suspend fun exportJson(): String = BackupJson.encode(ForecastBackup(
        dao.getAccounts(), dao.getPlans(), dao.getBudgets(), dao.getAdjustments(),
        dao.getSettings() ?: AppSettingsEntity(seeded = true),
        dao.getCryptoHoldings()
    ))

    fun restoreJson(text: String) = launch {
        runCatching { BackupJson.decode(text) }.onSuccess {
            fun renamed(name: String): String = if (name == "招商银行") "银行" else name
            val restoredAccounts = it.accounts.map { account -> account.copy(name = renamed(account.name)) }
            val restoredHiddenIds = if (it.hasAccountVisibilitySettings) {
                parseAccountIds(it.settings.hiddenAccountIdsCsv)
            } else {
                defaultHiddenAccountIds(restoredAccounts)
            }
            dao.replaceForecastData(
                restoredAccounts,
                it.plans.map { plan -> plan.copy(
                    accountName = renamed(plan.accountName),
                    toAccountName = plan.toAccountName?.let(::renamed)
                ) },
                it.budgets.map { budget -> budget.copy(
                    accountName = "",
                    method = BudgetMethod.DAILY_AVERAGE,
                    chargeDay = null,
                    carryOver = false
                ) },
                it.adjustments.map { adjustment ->
                    adjustment.copy(accountName = renamed(adjustment.accountName))
                },
                it.settings.copy(hiddenAccountIdsCsv = restoredHiddenIds.sorted().joinToString(",")),
                it.holdings
            )
            horizon.value = it.settings.defaultHorizonDays
            message.value = "备份恢复完成"
        }.onFailure { message.value = it.message ?: "恢复失败" }
    }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    private suspend fun seedIfEmpty() {
        if (dao.getSettings() != null) return
        dao.upsertSettings(AppSettingsEntity(
            defaultHorizonDays = 90,
            budgetMonthStartDay = 21,
            appMode = AppMode.SIMPLE.name,
            onboardingCompleted = false,
            seeded = true,
        ))
    }

    private suspend fun addExampleForecastData(profile: AppProfile) {
        val accounts = dao.getAccounts()
        val cnyAccount = accounts.firstOrNull { it.currencyCode == "CNY" } ?: return
        val today = LocalDate.now()
        dao.upsertPlan(PlannedTransactionEntity(
            name = "示例工资", type = EntryType.INCOME, amountCents = 600_000,
            accountName = cnyAccount.name, startDateEpochDay = today.plusDays(5).toEpochDay(),
            repeatRule = RepeatRule.MONTHLY,
        ))
        dao.upsertPlan(PlannedTransactionEntity(
            name = "示例房租", type = EntryType.EXPENSE, amountCents = 200_000,
            accountName = cnyAccount.name, startDateEpochDay = today.plusDays(10).toEpochDay(),
            repeatRule = RepeatRule.MONTHLY,
        ))
        if (profile.isEnabled(FeatureModule.INSTALLMENT)) {
            dao.upsertPlan(PlannedTransactionEntity(
                name = "示例分期", type = EntryType.EXPENSE, amountCents = 30_000,
                accountName = cnyAccount.name, startDateEpochDay = today.plusDays(7).toEpochDay(),
                repeatRule = RepeatRule.MONTHLY, installmentTotal = 3, installmentRemaining = 3,
            ))
        }
        if (profile.isEnabled(FeatureModule.BUDGET)) {
            val (cycleStart, cycleEnd) = BudgetCycles.containing(today, 21)
            dao.upsertBudget(BudgetEntity(
                name = "预算", totalCents = 200_000, remainingCents = 150_000,
                period = BudgetPeriod.MONTHLY, startDateEpochDay = cycleStart.toEpochDay(),
                endDateEpochDay = cycleEnd.toEpochDay(), accountName = "",
            ))
        }
        if (profile.isEnabled(FeatureModule.TEMPORARY_CASHFLOW)) {
            dao.upsertAdjustment(AdjustmentEntity(
                name = "示例临时收入", type = AdjustmentType.TEMPORARY, amountCents = 50_000,
                accountName = cnyAccount.name, dateEpochDay = today.plusDays(14).toEpochDay(),
                isIncome = true,
            ))
        }
    }

    private fun defaultAccount(type: AccountType, balance: Long): AccountEntity {
        val name = when (type) {
            AccountType.CMB -> "银行"
            AccountType.WECHAT -> "微信"
            AccountType.ALIPAY -> "支付宝"
            AccountType.CASH -> "现金"
            AccountType.PAYPAL -> "PayPal"
            AccountType.CRYPTO -> "数字货币"
            AccountType.CUSTOM -> "自定义账户"
        }
        val currency = when (type) {
            AccountType.PAYPAL -> "USD"
            AccountType.CRYPTO -> "CRYPTO"
            else -> "CNY"
        }
        return AccountEntity(
            name = name,
            initialBalanceCents = if (currency == "CRYPTO") 0L else balance,
            currentBalanceCents = if (currency == "CRYPTO") 0L else balance,
            currencyCode = currency,
            exchangeRateToCnyMicros = if (currency == "CNY") 1_000_000 else 0,
            accountType = type,
        )
    }

    private fun markStructureCurrent() {
        setupPrefs.edit()
            .putBoolean("v4_currency_groups_applied", true)
            .putBoolean("v5_single_budget_applied", true)
            .putBoolean("v6_account_free_budget_applied", true)
            .putBoolean("v063_bank_rename_applied", true)
            .putBoolean("v064_all_accounts_enabled", true)
            .putBoolean("v065_cash_account_applied", true)
            .putBoolean("v09_dynamic_visibility_applied", true)
            .apply()
    }

    private val exampleBalances = mapOf(
        AccountType.CMB to 800_000L,
        AccountType.WECHAT to 120_000L,
        AccountType.ALIPAY to 60_000L,
        AccountType.CASH to 20_000L,
        AccountType.PAYPAL to 20_000L,
    )

    private suspend fun ensureDefaultStructure() {
        if (!setupPrefs.getBoolean("v4_currency_groups_applied", false)) {
            val settings = dao.getSettings() ?: AppSettingsEntity(seeded = true)
            dao.insertAccounts(listOf(
                AccountEntity(name="支付宝", accountType=AccountType.ALIPAY),
                AccountEntity(name="数字货币", currencyCode="CRYPTO", exchangeRateToCnyMicros=0, includeInForecast=false, accountType=AccountType.CRYPTO)
            ))
            val holdingAmounts = dao.getCryptoHoldings().associate { it.symbol to it.amountUnits }.toMutableMap()
            dao.getAccounts().filter { it.name in listOf("USDT","BTC","莱特币","LTC") }.forEach { legacy ->
                val symbol = if (legacy.name in listOf("莱特币","LTC")) "LTC" else legacy.name
                holdingAmounts[symbol] = (holdingAmounts[symbol] ?: 0) + legacy.currentBalanceCents * 1_000_000L
                dao.movePlansToCryptoAccount(legacy.name)
                dao.moveBudgetsToCryptoAccount(legacy.name)
                dao.moveAdjustmentsToCryptoAccount(legacy.name)
                dao.deleteAccount(legacy.id)
            }
            listOf("USDT","BTC","LTC").forEach {
                dao.upsertCryptoHolding(CryptoHoldingEntity(it, holdingAmounts[it] ?: 0))
            }
            val (cycleStart, cycleEnd) = BudgetCycles.containing(LocalDate.now(), settings.budgetMonthStartDay)
            dao.getBudgetByName("本月日常预算")?.let { daily ->
                dao.upsertBudget(daily.copy(
                    startDateEpochDay = cycleStart.toEpochDay(),
                    endDateEpochDay = cycleEnd.toEpochDay()
                ))
            }
            setupPrefs.edit().putBoolean("v4_currency_groups_applied", true).apply()
        }

        if (!setupPrefs.getBoolean("v5_single_budget_applied", false)) {
            val budgets = dao.getBudgets()
            if (budgets.isNotEmpty() && (budgets.size > 1 || budgets.first().name != "预算")) {
                val preferred = budgets.firstOrNull { it.name == "本月日常预算" } ?: budgets.first()
                val merged = BudgetEntity(
                    name = "预算",
                    totalCents = budgets.sumOf { it.totalCents },
                    remainingCents = budgets.sumOf { it.remainingCents },
                    period = BudgetPeriod.MONTHLY,
                    startDateEpochDay = budgets.minOf { it.startDateEpochDay },
                    endDateEpochDay = budgets.maxOf { it.endDateEpochDay },
                    accountName = preferred.accountName,
                    method = BudgetMethod.DAILY_AVERAGE,
                    carryOver = budgets.any { it.carryOver },
                    isActive = budgets.any { it.isActive }
                )
                budgets.forEach { dao.deleteBudget(it.id) }
                dao.upsertBudget(merged)
            }
            setupPrefs.edit().putBoolean("v5_single_budget_applied", true).apply()
        }

        if (!setupPrefs.getBoolean("v6_account_free_budget_applied", false)) {
            dao.getBudgets().forEach { budget ->
                dao.upsertBudget(budget.copy(
                    accountName = "",
                    method = BudgetMethod.DAILY_AVERAGE,
                    chargeDay = null,
                    carryOver = false
                ))
            }
            setupPrefs.edit().putBoolean("v6_account_free_budget_applied", true).apply()
        }

        if (!setupPrefs.getBoolean("v063_bank_rename_applied", false)) {
            val accounts = dao.getAccounts()
            val oldBank = accounts.firstOrNull { it.name == "招商银行" }
            if (oldBank != null && accounts.none { it.name == "银行" }) {
                dao.renamePlanAccount("招商银行", "银行")
                dao.renamePlanTargetAccount("招商银行", "银行")
                dao.renameAdjustmentAccount("招商银行", "银行")
                dao.renameTransactionAccount("招商银行", "银行")
                dao.renameTransactionTargetAccount("招商银行", "银行")
                dao.renameAccount("招商银行", "银行")
            }
            setupPrefs.edit().putBoolean("v063_bank_rename_applied", true).apply()
        }

        if (!setupPrefs.getBoolean("v064_all_accounts_enabled", false)) {
            dao.getAccounts().forEach { account ->
                if (!account.includeInForecast || !account.isActive) {
                    dao.upsertAccount(account.copy(includeInForecast = true, isActive = true))
                }
            }
            setupPrefs.edit().putBoolean("v064_all_accounts_enabled", true).apply()
        }

        if (!setupPrefs.getBoolean("v065_cash_account_applied", false)) {
            if (dao.getAccounts().none { it.name == "现金" || it.accountType == AccountType.CASH }) {
                dao.upsertAccount(AccountEntity(name = "现金", accountType = AccountType.CASH))
            }
            setupPrefs.edit().putBoolean("v065_cash_account_applied", true).apply()
        }

        if (!setupPrefs.getBoolean("v09_dynamic_visibility_applied", false)) {
            val settings = dao.getSettings() ?: AppSettingsEntity(seeded = true)
            if (settings.hiddenAccountIdsCsv.isBlank()) {
                val accounts = dao.getAccounts()
                val hiddenIds = defaultHiddenAccountIds(accounts)
                dao.upsertSettings(settings.copy(
                    hiddenAccountIdsCsv = hiddenIds.sorted().joinToString(","),
                    showPaypalOnAccounts = accounts.none { it.accountType == AccountType.PAYPAL },
                    showAlipayOnAccounts = accounts.none { it.accountType == AccountType.ALIPAY } ||
                        accounts.any { it.accountType == AccountType.ALIPAY && it.id !in hiddenIds },
                    showCryptoOnAccounts = accounts.none { it.accountType == AccountType.CRYPTO },
                ))
            }
            setupPrefs.edit().putBoolean("v09_dynamic_visibility_applied", true).apply()
        }
    }

    private fun accountOrder(account: AccountEntity): Int = when (account.name) {
        "银行", "招商银行" -> 1
        "微信" -> 2
        "支付宝" -> 3
        "现金" -> 4
        "PayPal" -> 5
        "数字货币" -> 6
        else -> 100
    }

    companion object {
        private const val LONG_TERM_RISK_DAYS = 3650
        private val DEFAULT_VISIBLE_ACCOUNT_TYPES = setOf(
            AccountType.CMB, AccountType.WECHAT, AccountType.ALIPAY, AccountType.CASH,
        )

        private fun parseAccountIds(csv: String): Set<Long> = csv.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()

        private fun defaultHiddenAccountIds(accounts: List<AccountEntity>): Set<Long> {
            val defaultVisible = accounts.filter { it.accountType in DEFAULT_VISIBLE_ACCOUNT_TYPES }
            if (defaultVisible.isEmpty()) return emptySet()
            return accounts.filterNot { it.accountType in DEFAULT_VISIBLE_ACCOUNT_TYPES }.map { it.id }.toSet()
        }
    }
}
