package com.qiyue.cashflow.shared

enum class CoreEntryType { EXPENSE, INCOME, DEBT, TRANSFER, REFUND }
enum class CoreRepeatRule { NONE, DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY }
enum class CoreBudgetPeriod { WEEKLY, MONTHLY, CUSTOM }
enum class CoreBudgetMethod { DAILY_AVERAGE, SPECIFIC_DAY }

data class CoreAccount(
    val name: String,
    val balanceMinor: Long,
    val currencyCode: String,
    val included: Boolean = true,
    val active: Boolean = true,
)

data class CorePlan(
    val name: String,
    val type: CoreEntryType,
    val amountMinor: Long,
    val currencyCode: String,
    val accountName: String,
    val targetAccountName: String? = null,
    val startEpochDay: Long,
    val repeatRule: CoreRepeatRule,
    val endEpochDay: Long? = null,
    val installmentTotal: Int = 1,
    val installmentRemaining: Int = 0,
    val installmentTotalAmountMinor: Long = 0,
    val active: Boolean = true,
)

data class CoreBudget(
    val id: Long,
    val name: String,
    val totalMinor: Long,
    val remainingMinor: Long,
    val period: CoreBudgetPeriod,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val method: CoreBudgetMethod = CoreBudgetMethod.DAILY_AVERAGE,
    val chargeDay: Int? = null,
    val active: Boolean = true,
)

data class CoreAdjustment(
    val name: String,
    val amountMinor: Long,
    val accountName: String,
    val dateEpochDay: Long,
    val income: Boolean,
    val usesBudget: Boolean = false,
    val appliedToBalance: Boolean = false,
)

data class CoreForecastEvent(
    val name: String,
    val amountMinor: Long,
    val currencyCode: String,
    val accountName: String,
    val kind: String,
)

data class CoreForecastDay(
    val epochDay: Long,
    val cnyBalanceMinor: Long,
    val usdBalanceMinor: Long,
    val accountBalancesMinor: Map<String, Long>,
    val events: List<CoreForecastEvent>,
)

data class CoreForecastSummary(
    val currentCnyMinor: Long,
    val currentUsdMinor: Long,
    val endCnyMinor: Long,
    val endUsdMinor: Long,
    val lowestCnyMinor: Long,
    val lowestEpochDay: Long?,
    val highestCnyMinor: Long,
    val highestEpochDay: Long?,
    val firstShortageEpochDay: Long?,
    val budgetRemainingMinor: Long,
    val days: List<CoreForecastDay>,
)

/** 不依赖 Android 或 Apple API 的现金流预测核心。 */
class CoreForecastEngine {
    fun forecast(
        accounts: List<CoreAccount>,
        plans: List<CorePlan>,
        budgets: List<CoreBudget>,
        adjustments: List<CoreAdjustment>,
        todayEpochDay: Long,
        horizonDays: Int,
    ): CoreForecastSummary {
        val activeAccounts = accounts.filter { it.active }
        val balances = activeAccounts.associate { it.name to it.balanceMinor }.toMutableMap()
        val included = activeAccounts.filter { it.included }.map { it.name }.toSet()
        val currencies = activeAccounts.associate { it.name to it.currencyCode }
        var unallocatedBudgetSpent = 0L
        fun total(currency: String): Long = balances.entries
            .filter { (name, _) -> name in included && currencies[name] == currency }
            .sumOf { it.value } - if (currency == "CNY") unallocatedBudgetSpent else 0L

        val currentCny = total("CNY")
        val currentUsd = total("USD")
        val budgetReductions = mutableMapOf<Pair<Long, Long>, Long>()
        adjustments.filter { !it.income && it.usesBudget }.forEach { adjustment ->
            budgets.firstOrNull { budget -> budget.active && budgetCycle(budget, adjustment.dateEpochDay) != null }
                ?.let { budget ->
                    val cycleStart = budgetCycle(budget, adjustment.dateEpochDay)!!.first
                    val key = budget.id to cycleStart
                    budgetReductions[key] = (budgetReductions[key] ?: 0L) + adjustment.amountMinor
                }
        }

        val days = buildList {
            repeat(horizonDays.coerceAtLeast(0)) { offset ->
                val epochDay = todayEpochDay + offset
                val events = mutableListOf<CoreForecastEvent>()
                plans.filter { it.active && occursOn(it, epochDay) }.forEach { plan ->
                    val occurrenceAmount = installmentAmountOn(plan, epochDay)
                    when (plan.type) {
                        CoreEntryType.INCOME, CoreEntryType.REFUND -> {
                            balances[plan.accountName] = (balances[plan.accountName] ?: 0L) + occurrenceAmount
                            events += CoreForecastEvent(plan.name, occurrenceAmount, plan.currencyCode, plan.accountName, "固定收入")
                        }
                        CoreEntryType.EXPENSE, CoreEntryType.DEBT -> {
                            balances[plan.accountName] = (balances[plan.accountName] ?: 0L) - occurrenceAmount
                            val kind = if (plan.installmentTotal > 1) "分期付款" else "固定支出"
                            events += CoreForecastEvent(plan.name, -occurrenceAmount, plan.currencyCode, plan.accountName, kind)
                        }
                        CoreEntryType.TRANSFER -> {
                            balances[plan.accountName] = (balances[plan.accountName] ?: 0L) - occurrenceAmount
                            plan.targetAccountName?.let { target ->
                                balances[target] = (balances[target] ?: 0L) + occurrenceAmount
                            }
                            events += CoreForecastEvent(plan.name, 0L, plan.currencyCode, plan.accountName, "账户转账")
                        }
                    }
                }

                budgets.filter { it.active }.forEach { budget ->
                    val cycleStart = budgetCycle(budget, epochDay)?.first
                    val reduction = cycleStart?.let { budgetReductions[budget.id to it] } ?: 0L
                    val amount = budgetAmountOn(budget, epochDay, todayEpochDay, reduction)
                    if (amount > 0L) {
                        unallocatedBudgetSpent += amount
                        events += CoreForecastEvent(budget.name, -amount, "CNY", "", "预算")
                    }
                }

                adjustments.filter { !it.appliedToBalance && it.dateEpochDay == epochDay }.forEach { item ->
                    val delta = if (item.income) item.amountMinor else -item.amountMinor
                    balances[item.accountName] = (balances[item.accountName] ?: 0L) + delta
                    events += CoreForecastEvent(
                        item.name,
                        delta,
                        currencies[item.accountName] ?: "CNY",
                        item.accountName,
                        "临时收支",
                    )
                }
                add(CoreForecastDay(epochDay, total("CNY"), total("USD"), balances.toMap(), events))
            }
        }
        val lowest = days.minByOrNull { it.cnyBalanceMinor }
        val highest = days.maxByOrNull { it.cnyBalanceMinor }
        return CoreForecastSummary(
            currentCnyMinor = currentCny,
            currentUsdMinor = currentUsd,
            endCnyMinor = days.lastOrNull()?.cnyBalanceMinor ?: currentCny,
            endUsdMinor = days.lastOrNull()?.usdBalanceMinor ?: currentUsd,
            lowestCnyMinor = lowest?.cnyBalanceMinor ?: currentCny,
            lowestEpochDay = lowest?.epochDay,
            highestCnyMinor = highest?.cnyBalanceMinor ?: currentCny,
            highestEpochDay = highest?.epochDay,
            firstShortageEpochDay = days.firstOrNull { it.cnyBalanceMinor < 0L }?.epochDay,
            budgetRemainingMinor = budgets.filter { it.active }.sumOf { it.remainingMinor },
            days = days,
        )
    }

    fun occursOn(plan: CorePlan, epochDay: Long): Boolean {
        if (epochDay < plan.startEpochDay || plan.endEpochDay?.let { epochDay > it } == true) return false
        val occurrenceIndex = occurrenceIndex(plan, epochDay)
        return occurrenceIndex >= 0 && (plan.installmentRemaining <= 0 || occurrenceIndex < plan.installmentRemaining)
    }

    fun installmentAmountOn(plan: CorePlan, epochDay: Long): Long {
        val occurrenceIndex = occurrenceIndex(plan, epochDay)
        if (occurrenceIndex < 0 || plan.installmentTotal <= 1 || plan.installmentTotalAmountMinor <= 0L) {
            return plan.amountMinor
        }
        val totalPeriods = plan.installmentTotal.coerceAtLeast(1)
        val remainingPeriods = plan.installmentRemaining.takeIf { it > 0 }?.coerceAtMost(totalPeriods) ?: totalPeriods
        val paidPeriods = totalPeriods - remainingPeriods
        val originalIndex = paidPeriods + occurrenceIndex
        val regularAmount = plan.installmentTotalAmountMinor / totalPeriods
        return if (originalIndex >= totalPeriods - 1) {
            plan.installmentTotalAmountMinor - regularAmount * (totalPeriods - 1L)
        } else {
            regularAmount
        }
    }

    private fun occurrenceIndex(plan: CorePlan, epochDay: Long): Int {
        if (epochDay < plan.startEpochDay || plan.endEpochDay?.let { epochDay > it } == true) return -1
        return when (plan.repeatRule) {
            CoreRepeatRule.NONE -> if (epochDay == plan.startEpochDay) 0 else -1
            CoreRepeatRule.DAILY -> (epochDay - plan.startEpochDay).toInt()
            CoreRepeatRule.WEEKLY -> if ((epochDay - plan.startEpochDay) % 7L == 0L) ((epochDay - plan.startEpochDay) / 7L).toInt() else -1
            CoreRepeatRule.MONTHLY -> periodicMonthIndex(plan.startEpochDay, epochDay, 1)
            CoreRepeatRule.QUARTERLY -> periodicMonthIndex(plan.startEpochDay, epochDay, 3)
            CoreRepeatRule.YEARLY -> periodicMonthIndex(plan.startEpochDay, epochDay, 12)
        }
    }

    private fun periodicMonthIndex(startEpochDay: Long, epochDay: Long, step: Int): Int {
        val start = CivilDate.fromEpochDay(startEpochDay)
        val date = CivilDate.fromEpochDay(epochDay)
        val months = (date.year - start.year) * 12 + date.month - start.month
        if (months < 0 || months % step != 0) return -1
        val expected = start.plusMonths(months)
        return if (date == expected) months / step else -1
    }

    private fun budgetAmountOn(budget: CoreBudget, epochDay: Long, todayEpochDay: Long, reduction: Long): Long {
        val cycle = budgetCycle(budget, epochDay) ?: return 0L
        val currentCycle = budgetCycle(budget, todayEpochDay)
        val current = currentCycle?.first == cycle.first ||
            (todayEpochDay < budget.startEpochDay && cycle.first == budget.startEpochDay)
        val cycleBudget = (if (current) budget.remainingMinor else budget.totalMinor).minus(reduction).coerceAtLeast(0L)
        if (cycleBudget == 0L) return 0L
        if (budget.method == CoreBudgetMethod.SPECIFIC_DAY) {
            val date = CivilDate.fromEpochDay(epochDay)
            val target = minOf(budget.chargeDay ?: CivilDate.fromEpochDay(cycle.second).day, date.lengthOfMonth())
            return if (date.day == target) cycleBudget else 0L
        }
        val effectiveStart = if (current) maxOf(todayEpochDay, cycle.first) else cycle.first
        val remainingDays = (cycle.second - effectiveStart + 1L).coerceAtLeast(1L)
        val dayIndex = epochDay - effectiveStart
        if (dayIndex !in 0 until remainingDays) return 0L
        return cycleBudget / remainingDays + if (dayIndex < cycleBudget % remainingDays) 1L else 0L
    }

    private fun budgetCycle(budget: CoreBudget, epochDay: Long): Pair<Long, Long>? {
        if (epochDay < budget.startEpochDay) return null
        return when (budget.period) {
            CoreBudgetPeriod.CUSTOM -> if (epochDay <= budget.endEpochDay) budget.startEpochDay to budget.endEpochDay else null
            CoreBudgetPeriod.WEEKLY -> {
                val start = budget.startEpochDay + ((epochDay - budget.startEpochDay) / 7L) * 7L
                start to start + 6L
            }
            CoreBudgetPeriod.MONTHLY -> {
                val base = CivilDate.fromEpochDay(budget.startEpochDay)
                val date = CivilDate.fromEpochDay(epochDay)
                var months = (date.year - base.year) * 12 + date.month - base.month
                var start = base.plusMonths(months).toEpochDay()
                if (epochDay < start) {
                    months -= 1
                    start = base.plusMonths(months).toEpochDay()
                }
                start to CivilDate.fromEpochDay(start).plusMonths(1).toEpochDay() - 1L
            }
        }
    }
}

data class CivilDate(val year: Int, val month: Int, val day: Int) {
    fun lengthOfMonth(): Int = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun plusMonths(months: Int): CivilDate {
        val total = year * 12L + (month - 1) + months
        val newYear = floorDiv(total, 12L).toInt()
        val newMonth = floorMod(total, 12L).toInt() + 1
        return CivilDate(newYear, newMonth, minOf(day, CivilDate(newYear, newMonth, 1).lengthOfMonth()))
    }

    fun toEpochDay(): Long {
        var y = year.toLong()
        val m = month.toLong()
        if (m <= 2L) y -= 1L
        val era = floorDiv(y, 400L)
        val yearOfEra = y - era * 400L
        val shiftedMonth = m + if (m > 2L) -3L else 9L
        val dayOfYear = (153L * shiftedMonth + 2L) / 5L + day - 1L
        val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }

    companion object {
        fun fromEpochDay(epochDay: Long): CivilDate {
            val zeroDay = epochDay + 719_468L
            val era = floorDiv(zeroDay, 146_097L)
            val dayOfEra = zeroDay - era * 146_097L
            val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
            var year = yearOfEra + era * 400L
            val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
            val monthPart = (5L * dayOfYear + 2L) / 153L
            val day = dayOfYear - (153L * monthPart + 2L) / 5L + 1L
            val month = monthPart + if (monthPart < 10L) 3L else -9L
            if (month <= 2L) year += 1L
            return CivilDate(year.toInt(), month.toInt(), day.toInt())
        }

        private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
}

private fun floorDiv(value: Long, divisor: Long): Long {
    var quotient = value / divisor
    if ((value xor divisor) < 0 && quotient * divisor != value) quotient--
    return quotient
}

private fun floorMod(value: Long, divisor: Long): Long = value - floorDiv(value, divisor) * divisor
