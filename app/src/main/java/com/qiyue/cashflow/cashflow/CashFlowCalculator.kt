package com.qiyue.cashflow.cashflow

import com.qiyue.cashflow.data.*
import com.qiyue.cashflow.shared.*
import java.time.LocalDate

data class ForecastEvent(
    val name: String,
    val amountCents: Long,
    val currencyCode: String,
    val accountName: String,
    val kind: String,
)

data class CashFlowDay(
    val date: LocalDate,
    val balanceCents: Long,
    val usdBalanceCents: Long,
    val accountBalancesCents: Map<String, Long>,
    val events: List<ForecastEvent>,
)

data class CashFlowSummary(
    val currentBalanceCents: Long,
    val currentUsdCents: Long,
    val endBalanceCents: Long,
    val endUsdCents: Long,
    val lowestBalanceCents: Long,
    val lowestDate: LocalDate?,
    val highestBalanceCents: Long,
    val highestDate: LocalDate?,
    val firstShortageDate: LocalDate?,
    val budgetRemainingCents: Long,
    val budgetRemainingUsdCents: Long,
    val days: List<CashFlowDay>,
)

/** Android/Room 数据到共享预测核心的薄适配层。 */
class CashFlowCalculator(private val engine: CoreForecastEngine = CoreForecastEngine()) {
    fun forecast(
        accounts: List<AccountEntity>,
        plans: List<PlannedTransactionEntity>,
        budgets: List<BudgetEntity>,
        adjustments: List<AdjustmentEntity>,
        today: LocalDate = LocalDate.now(),
        horizonDays: Int = 90,
    ): CashFlowSummary {
        val result = engine.forecast(
            accounts = accounts.map { it.toCore() },
            plans = plans.map { it.toCore() },
            budgets = budgets.map { it.toCore() },
            adjustments = adjustments.filter { it.type == AdjustmentType.TEMPORARY }.map { it.toCore() },
            todayEpochDay = today.toEpochDay(),
            horizonDays = horizonDays,
        )
        fun date(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
        return CashFlowSummary(
            currentBalanceCents = result.currentCnyMinor,
            currentUsdCents = result.currentUsdMinor,
            endBalanceCents = result.endCnyMinor,
            endUsdCents = result.endUsdMinor,
            lowestBalanceCents = result.lowestCnyMinor,
            lowestDate = date(result.lowestEpochDay),
            highestBalanceCents = result.highestCnyMinor,
            highestDate = date(result.highestEpochDay),
            firstShortageDate = date(result.firstShortageEpochDay),
            budgetRemainingCents = result.budgetRemainingMinor,
            budgetRemainingUsdCents = 0L,
            days = result.days.map { day ->
                CashFlowDay(
                    date = LocalDate.ofEpochDay(day.epochDay),
                    balanceCents = day.cnyBalanceMinor,
                    usdBalanceCents = day.usdBalanceMinor,
                    accountBalancesCents = day.accountBalancesMinor,
                    events = day.events.map { event ->
                        ForecastEvent(event.name, event.amountMinor, event.currencyCode, event.accountName, event.kind)
                    },
                )
            },
        )
    }

    fun occursOn(plan: PlannedTransactionEntity, date: LocalDate): Boolean =
        engine.occursOn(plan.toCore(), date.toEpochDay())
}

private fun AccountEntity.toCore() = CoreAccount(
    name = name,
    balanceMinor = currentBalanceCents,
    currencyCode = currencyCode,
    included = includeInForecast,
    active = isActive,
)

private fun PlannedTransactionEntity.toCore() = CorePlan(
    name = name,
    type = CoreEntryType.valueOf(type.name),
    amountMinor = amountCents,
    currencyCode = currencyCode,
    accountName = accountName,
    targetAccountName = toAccountName,
    startEpochDay = startDateEpochDay,
    repeatRule = CoreRepeatRule.valueOf(repeatRule.name),
    endEpochDay = endDateEpochDay,
    installmentTotal = installmentTotal,
    installmentRemaining = installmentRemaining,
    installmentTotalAmountMinor = installmentTotalAmountCents,
    active = isActive,
)

private fun BudgetEntity.toCore() = CoreBudget(
    id = id,
    name = name,
    totalMinor = totalCents,
    remainingMinor = remainingCents,
    period = CoreBudgetPeriod.valueOf(period.name),
    startEpochDay = startDateEpochDay,
    endEpochDay = endDateEpochDay,
    method = CoreBudgetMethod.valueOf(method.name),
    chargeDay = chargeDay,
    active = isActive,
)

private fun AdjustmentEntity.toCore() = CoreAdjustment(
    name = name,
    amountMinor = amountCents,
    accountName = accountName,
    dateEpochDay = dateEpochDay,
    income = isIncome,
    usesBudget = usesBudget,
    appliedToBalance = appliedToBalance,
)
