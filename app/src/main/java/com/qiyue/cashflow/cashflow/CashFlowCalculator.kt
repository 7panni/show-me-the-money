package com.qiyue.cashflow.cashflow

import com.qiyue.cashflow.data.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ForecastEvent(
    val name: String,
    val amountCents: Long,
    val currencyCode: String,
    val accountName: String,
    val kind: String
)

data class CashFlowDay(
    val date: LocalDate,
    val balanceCents: Long,
    val usdBalanceCents: Long,
    val accountBalancesCents: Map<String, Long>,
    val events: List<ForecastEvent>
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
    val days: List<CashFlowDay>
)

class CashFlowCalculator {
    fun forecast(
        accounts: List<AccountEntity>,
        plans: List<PlannedTransactionEntity>,
        budgets: List<BudgetEntity>,
        adjustments: List<AdjustmentEntity>,
        today: LocalDate = LocalDate.now(),
        horizonDays: Int = 90
    ): CashFlowSummary {
        val activeAccounts = accounts.filter { it.isActive }
        val balances = activeAccounts.associate { it.name to it.currentBalanceCents }.toMutableMap()
        val included = activeAccounts.filter { it.includeInForecast }.map { it.name }.toSet()
        val currencies = activeAccounts.associate { it.name to it.currencyCode }
        var unallocatedBudgetSpentCents = 0L
        fun total(currency: String) = balances.filterKeys { it in included }.entries
            .filter { (name, _) -> currencies[name] == currency }
            .sumOf { it.value } - if (currency == "CNY") unallocatedBudgetSpentCents else 0L
        val current = total("CNY")
        val currentUsd = total("USD")
        val days = mutableListOf<CashFlowDay>()
        val budgetReductions = mutableMapOf<Pair<Long, Long>, Long>()
        adjustments.filter { it.type == AdjustmentType.TEMPORARY && !it.isIncome && it.usesBudget }
            .forEach { adjustment ->
                budgets.firstOrNull { budget ->
                    budget.isActive && budgetCycleContaining(
                        budget,
                        LocalDate.ofEpochDay(adjustment.dateEpochDay)
                    ) != null
                }?.let { budget ->
                    val cycleStart = budgetCycleContaining(
                        budget,
                        LocalDate.ofEpochDay(adjustment.dateEpochDay)
                    )!!.first.toEpochDay()
                    val key = budget.id to cycleStart
                    budgetReductions[key] = (budgetReductions[key] ?: 0L) + adjustment.amountCents
                }
            }

        repeat(horizonDays) { offset ->
            val date = today.plusDays(offset.toLong())
            val events = mutableListOf<ForecastEvent>()

            plans.filter { it.isActive && occursOn(it, date) }.forEach { plan ->
                val nativeAmount = plan.amountCents
                when (plan.type) {
                    EntryType.INCOME, EntryType.REFUND -> {
                        balances[plan.accountName] = (balances[plan.accountName] ?: 0) + nativeAmount
                        events += ForecastEvent(plan.name, nativeAmount, plan.currencyCode, plan.accountName, "固定收入")
                    }
                    EntryType.EXPENSE, EntryType.DEBT -> {
                        balances[plan.accountName] = (balances[plan.accountName] ?: 0) - nativeAmount
                        events += ForecastEvent(plan.name, -nativeAmount, plan.currencyCode, plan.accountName, if (plan.installmentTotal > 1) "分期付款" else "固定支出")
                    }
                    EntryType.TRANSFER -> {
                        balances[plan.accountName] = (balances[plan.accountName] ?: 0) - nativeAmount
                        plan.toAccountName?.let { target ->
                            balances[target] = (balances[target] ?: 0) + nativeAmount
                        }
                        events += ForecastEvent(plan.name, 0, plan.currencyCode, plan.accountName, "账户转账")
                    }
                }
            }

            budgets.filter { it.isActive }
                .forEach { budget ->
                    val cycleStart = budgetCycleContaining(budget, date)?.first?.toEpochDay()
                    val reduction = cycleStart?.let { budgetReductions[budget.id to it] } ?: 0L
                    val amount = budgetAmountOn(budget, date, today, reduction)
                    if (amount > 0) {
                        unallocatedBudgetSpentCents += amount
                        events += ForecastEvent(
                            budget.name,
                            -amount,
                            "CNY",
                            "",
                            "预算"
                        )
                    }
                }

            adjustments.filter {
                it.type == AdjustmentType.TEMPORARY && !it.appliedToBalance &&
                    it.dateEpochDay == date.toEpochDay()
            }.forEach { item ->
                val delta = if (item.isIncome) item.amountCents else -item.amountCents
                balances[item.accountName] = (balances[item.accountName] ?: 0) + delta
                events += ForecastEvent(item.name, delta, currencies[item.accountName] ?: "CNY", item.accountName, "临时收支")
            }

            days += CashFlowDay(date, total("CNY"), total("USD"), balances.toMap(), events)
        }
        val lowest = days.minByOrNull { it.balanceCents }
        val highest = days.maxByOrNull { it.balanceCents }
        return CashFlowSummary(
            currentBalanceCents = current,
            currentUsdCents = currentUsd,
            endBalanceCents = days.lastOrNull()?.balanceCents ?: current,
            endUsdCents = days.lastOrNull()?.usdBalanceCents ?: currentUsd,
            lowestBalanceCents = lowest?.balanceCents ?: current,
            lowestDate = lowest?.date,
            highestBalanceCents = highest?.balanceCents ?: current,
            highestDate = highest?.date,
            firstShortageDate = days.firstOrNull { it.balanceCents < 0 }?.date,
            budgetRemainingCents = budgets.filter { it.isActive }.sumOf { it.remainingCents },
            budgetRemainingUsdCents = 0,
            days = days
        )
    }

    fun occursOn(plan: PlannedTransactionEntity, date: LocalDate): Boolean {
        val start = LocalDate.ofEpochDay(plan.startDateEpochDay)
        val end = plan.endDateEpochDay?.let(LocalDate::ofEpochDay)
        if (date < start || (end != null && date > end)) return false
        val occurrenceIndex = when (plan.repeatRule) {
            RepeatRule.NONE -> if (date == start) 0 else -1
            RepeatRule.DAILY -> ChronoUnit.DAYS.between(start, date).toInt()
            RepeatRule.WEEKLY -> if (date.dayOfWeek == start.dayOfWeek) ChronoUnit.WEEKS.between(start, date).toInt() else -1
            RepeatRule.MONTHLY -> periodicMonthIndex(start, date, 1)
            RepeatRule.QUARTERLY -> periodicMonthIndex(start, date, 3)
            RepeatRule.YEARLY -> periodicMonthIndex(start, date, 12)
        }
        if (occurrenceIndex < 0) return false
        return plan.installmentRemaining <= 0 || occurrenceIndex < plan.installmentRemaining
    }

    private fun periodicMonthIndex(start: LocalDate, date: LocalDate, step: Int): Int {
        val months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), date.withDayOfMonth(1)).toInt()
        if (months < 0 || months % step != 0) return -1
        val expected = start.plusMonths(months.toLong())
        val expectedDay = minOf(start.dayOfMonth, expected.lengthOfMonth())
        return if (date.dayOfMonth == expectedDay) months / step else -1
    }

    private fun budgetAmountOn(
        budget: BudgetEntity,
        date: LocalDate,
        today: LocalDate,
        reductionCents: Long
    ): Long {
        val cycle = budgetCycleContaining(budget, date) ?: return 0
        val currentCycle = budgetCycleContaining(budget, today)
        val isCurrentCycle = currentCycle?.first == cycle.first ||
            (today < LocalDate.ofEpochDay(budget.startDateEpochDay) &&
                cycle.first.toEpochDay() == budget.startDateEpochDay)
        val cycleBudget = (if (isCurrentCycle) budget.remainingCents else budget.totalCents)
            .minus(reductionCents)
            .coerceAtLeast(0)
        if (cycleBudget <= 0) return 0
        if (budget.method == BudgetMethod.SPECIFIC_DAY) {
            val targetDay = minOf(budget.chargeDay ?: cycle.second.dayOfMonth, date.lengthOfMonth())
            return if (date.dayOfMonth == targetDay) cycleBudget else 0
        }
        val effectiveStart = if (isCurrentCycle) maxOf(today.toEpochDay(), cycle.first.toEpochDay())
            else cycle.first.toEpochDay()
        val remainingDays = (cycle.second.toEpochDay() - effectiveStart + 1).coerceAtLeast(1)
        val dayIndex = date.toEpochDay() - effectiveStart
        if (dayIndex !in 0 until remainingDays) return 0
        val base = cycleBudget / remainingDays
        val remainder = cycleBudget % remainingDays
        return base + if (dayIndex < remainder) 1 else 0
    }

    private fun budgetCycleContaining(
        budget: BudgetEntity,
        date: LocalDate
    ): Pair<LocalDate, LocalDate>? {
        val baseStart = LocalDate.ofEpochDay(budget.startDateEpochDay)
        if (date < baseStart) return null
        return when (budget.period) {
            BudgetPeriod.CUSTOM -> {
                val end = LocalDate.ofEpochDay(budget.endDateEpochDay)
                if (date <= end) baseStart to end else null
            }
            BudgetPeriod.WEEKLY -> {
                val weeks = ChronoUnit.DAYS.between(baseStart, date) / 7
                val start = baseStart.plusWeeks(weeks)
                start to start.plusDays(6)
            }
            BudgetPeriod.MONTHLY -> {
                var months = ChronoUnit.MONTHS.between(
                    baseStart.withDayOfMonth(1),
                    date.withDayOfMonth(1)
                )
                var start = baseStart.plusMonths(months)
                if (date < start) {
                    months -= 1
                    start = baseStart.plusMonths(months)
                }
                start to start.plusMonths(1).minusDays(1)
            }
        }
    }
}
