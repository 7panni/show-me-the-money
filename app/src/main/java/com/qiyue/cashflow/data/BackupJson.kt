package com.qiyue.cashflow.data

import org.json.JSONArray
import org.json.JSONObject

data class ForecastBackup(
    val accounts: List<AccountEntity>,
    val plans: List<PlannedTransactionEntity>,
    val budgets: List<BudgetEntity>,
    val adjustments: List<AdjustmentEntity>,
    val settings: AppSettingsEntity,
    val holdings: List<CryptoHoldingEntity> = emptyList(),
    val hasAccountVisibilitySettings: Boolean = true,
    val hasHomeCurrencySettings: Boolean = true,
)

object BackupJson {
    fun encode(data: ForecastBackup): String = JSONObject().apply {
        put("format", "cashflow-forecast")
        put("version", 4)
        put("accounts", JSONArray(data.accounts.map { a -> JSONObject().apply {
            put("id", a.id); put("name", a.name); put("initialBalance", a.initialBalanceCents); put("balance", a.currentBalanceCents)
            put("currency", a.currencyCode); put("rate", a.exchangeRateToCnyMicros)
            put("included", a.includeInForecast); put("type", a.accountType.name); put("active", a.isActive)
        }}))
        put("plans", JSONArray(data.plans.map { p -> JSONObject().apply {
            put("id", p.id); put("name", p.name); put("type", p.type.name); put("amount", p.amountCents)
            put("currency", p.currencyCode); put("rate", p.exchangeRateToCnyMicros)
            put("account", p.accountName); put("toAccount", p.toAccountName)
            put("start", p.startDateEpochDay); put("repeat", p.repeatRule.name); put("end", p.endDateEpochDay)
            put("installmentTotal", p.installmentTotal); put("installmentRemaining", p.installmentRemaining)
            put("installmentTotalAmount", p.installmentTotalAmountCents); put("active", p.isActive)
        }}))
        put("budgets", JSONArray(data.budgets.map { b -> JSONObject().apply {
            put("id", b.id); put("name", b.name); put("total", b.totalCents); put("remaining", b.remainingCents)
            put("period", b.period.name); put("start", b.startDateEpochDay); put("end", b.endDateEpochDay)
            put("account", b.accountName); put("method", b.method.name); put("chargeDay", b.chargeDay)
            put("carryOver", b.carryOver); put("active", b.isActive)
        }}))
        put("adjustments", JSONArray(data.adjustments.map { a -> JSONObject().apply {
            put("id", a.id); put("name", a.name); put("type", a.type.name); put("amount", a.amountCents)
            put("account", a.accountName); put("date", a.dateEpochDay); put("income", a.isIncome); put("usesBudget", a.usesBudget)
            put("appliedToBalance", a.appliedToBalance)
        }}))
        put("cryptoHoldings", JSONArray(data.holdings.map { h -> JSONObject().apply {
            put("symbol", h.symbol); put("amountUnits", h.amountUnits)
        }}))
        put("settings", JSONObject().apply {
            put("usdRate", data.settings.usdRateMicros); put("horizon", data.settings.defaultHorizonDays)
            put("budgetMonthStartDay", data.settings.budgetMonthStartDay)
            put("showUsdOnHome", data.settings.showUsdOnHome)
            put("showCryptoOnHome", data.settings.showCryptoOnHome)
            put("showPaypalOnAccounts", data.settings.showPaypalOnAccounts)
            put("showAlipayOnAccounts", data.settings.showAlipayOnAccounts)
            put("showCryptoOnAccounts", data.settings.showCryptoOnAccounts)
            put("appMode", data.settings.appMode)
            put("customModules", data.settings.customModulesCsv)
            put("hiddenAccountIds", data.settings.hiddenAccountIdsCsv)
            put("hiddenHomeCurrencies", data.settings.hiddenHomeCurrenciesCsv)
            put("showFinanceTips", data.settings.showFinanceTips)
            put("onboardingCompleted", data.settings.onboardingCompleted)
            put("seeded", data.settings.seeded)
        })
    }.toString(2)

    fun decode(text: String): ForecastBackup {
        val root = JSONObject(text)
        require(root.optString("format") == "cashflow-forecast") { "不是现金流预测备份文件" }
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        val accounts = root.getJSONArray("accounts").objects().map { o -> AccountEntity(
            id=o.getLong("id"), name=o.getString("name"), initialBalanceCents=o.optLong("initialBalance", o.getLong("balance")),
            currentBalanceCents=o.getLong("balance"), currencyCode=o.getString("currency"), exchangeRateToCnyMicros=o.getLong("rate"),
            includeInForecast=o.getBoolean("included"), accountType=AccountType.valueOf(o.getString("type")), isActive=o.getBoolean("active")
        )}
        val plans = root.getJSONArray("plans").objects().map { o -> PlannedTransactionEntity(
            id=o.getLong("id"), name=o.getString("name"), type=EntryType.valueOf(o.getString("type")), amountCents=o.getLong("amount"),
            currencyCode=o.getString("currency"), exchangeRateToCnyMicros=o.getLong("rate"), accountName=o.getString("account"),
            toAccountName=o.optString("toAccount").takeIf { it.isNotBlank() && it != "null" }, startDateEpochDay=o.getLong("start"),
            repeatRule=RepeatRule.valueOf(o.getString("repeat")), endDateEpochDay=o.optLong("end").takeIf { !o.isNull("end") },
            installmentTotal=o.getInt("installmentTotal"), installmentRemaining=o.getInt("installmentRemaining"),
            installmentTotalAmountCents=o.optLong("installmentTotalAmount", 0), isActive=o.getBoolean("active")
        )}
        val budgets = root.getJSONArray("budgets").objects().map { o -> BudgetEntity(
            id=o.getLong("id"), name=o.getString("name"), totalCents=o.getLong("total"), remainingCents=o.getLong("remaining"),
            period=BudgetPeriod.valueOf(o.getString("period")), startDateEpochDay=o.getLong("start"), endDateEpochDay=o.getLong("end"),
            accountName=o.getString("account"), method=BudgetMethod.valueOf(o.getString("method")),
            chargeDay=o.optInt("chargeDay").takeIf { !o.isNull("chargeDay") }, carryOver=o.getBoolean("carryOver"), isActive=o.getBoolean("active")
        )}
        val adjustments = root.getJSONArray("adjustments").objects().map { o -> AdjustmentEntity(
            id=o.getLong("id"), name=o.getString("name"), type=AdjustmentType.valueOf(o.getString("type")),
            amountCents=o.getLong("amount"), accountName=o.getString("account"), dateEpochDay=o.getLong("date"),
            isIncome=o.getBoolean("income"), usesBudget=o.getBoolean("usesBudget"),
            appliedToBalance=o.optBoolean("appliedToBalance", false)
        )}
        val holdings = root.optJSONArray("cryptoHoldings")?.objects()?.map { o ->
            CryptoHoldingEntity(o.getString("symbol"), o.getLong("amountUnits"))
        }.orEmpty()
        val s = root.getJSONObject("settings")
        return ForecastBackup(accounts, plans, budgets, adjustments,
            AppSettingsEntity(
                usdRateMicros=s.getLong("usdRate"),
                defaultHorizonDays=s.getInt("horizon"),
                budgetMonthStartDay=s.optInt("budgetMonthStartDay", 21),
                showUsdOnHome=s.optBoolean("showUsdOnHome", true),
                showCryptoOnHome=s.optBoolean("showCryptoOnHome", true),
                showPaypalOnAccounts=s.optBoolean("showPaypalOnAccounts", true),
                showAlipayOnAccounts=s.optBoolean("showAlipayOnAccounts", true),
                showCryptoOnAccounts=s.optBoolean("showCryptoOnAccounts", true),
                appMode=s.optString("appMode", "SIMPLE"),
                customModulesCsv=s.optString("customModules", ""),
                hiddenAccountIdsCsv=s.optString("hiddenAccountIds", ""),
                hiddenHomeCurrenciesCsv=s.optString("hiddenHomeCurrencies", ""),
                showFinanceTips=s.optBoolean("showFinanceTips", true),
                onboardingCompleted=s.optBoolean("onboardingCompleted", true),
                seeded=s.optBoolean("seeded", true)
            ),
            holdings,
            s.has("hiddenAccountIds"),
            s.has("hiddenHomeCurrencies"),
        )
    }
}
