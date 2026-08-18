package com.qiyue.cashflow.cashflow

import com.qiyue.cashflow.data.AccountEntity
import com.qiyue.cashflow.data.EntryType
import com.qiyue.cashflow.data.PlannedTransactionEntity
import java.time.LocalDate

/** 新建计划不追溯改变过去的真实余额；若今天恰逢扣款日，则允许今天入账。 */
fun initialPlanPostingCursor(startDate: LocalDate, today: LocalDate = LocalDate.now()): Long =
    maxOf(today.minusDays(1), startDate.minusDays(1)).toEpochDay()

data class AutomaticPostingResult(
    val accounts: List<AccountEntity>,
    val plans: List<PlannedTransactionEntity>,
    val postedCount: Int,
    val missingAccount: Boolean,
)

/** 固定收支和分期的真实余额入账规则；临时收支不属于计划，因此不会进入这里。 */
class AutomaticPlanPoster(
    private val calculator: CashFlowCalculator = CashFlowCalculator(),
) {
    /** 升级时只推进历史期次，不追溯修改真实余额，避免与用户已手动校准的余额重复。 */
    fun initializeExistingPlans(
        plans: List<PlannedTransactionEntity>,
        today: LocalDate,
    ): List<PlannedTransactionEntity> = plans.map { plan ->
        val normalizedRemaining = if (
            plan.installmentTotal > 1 && plan.installmentRemaining <= 0
        ) plan.installmentTotal else plan.installmentRemaining
        val normalized = plan.copy(
            installmentRemaining = normalizedRemaining,
            balanceAppliedThroughEpochDay = null,
        )
        val elapsed = calculator.dueDates(normalized, today).size
        val newRemaining = if (plan.installmentTotal > 1) {
            (normalizedRemaining - elapsed).coerceAtLeast(0)
        } else normalizedRemaining
        plan.copy(
            installmentRemaining = newRemaining,
            balanceAppliedThroughEpochDay = today.toEpochDay(),
            isActive = plan.isActive && !(plan.installmentTotal > 1 && newRemaining == 0),
        )
    }

    fun postDuePlans(
        accounts: List<AccountEntity>,
        plans: List<PlannedTransactionEntity>,
        today: LocalDate,
    ): AutomaticPostingResult {
        val todayEpochDay = today.toEpochDay()
        val accountsByName = accounts.associateBy { it.name }.toMutableMap()
        val updatedPlans = mutableListOf<PlannedTransactionEntity>()
        var postedCount = 0
        var missingAccount = false

        plans.filter { it.isActive }.forEach { original ->
            val remaining = if (
                original.installmentTotal > 1 && original.balanceAppliedThroughEpochDay == null &&
                original.installmentRemaining <= 0
            ) original.installmentTotal else original.installmentRemaining
            val plan = original.copy(installmentRemaining = remaining)
            val dueDates = calculator.dueDates(plan, today)
            if (dueDates.isEmpty()) {
                if (plan.balanceAppliedThroughEpochDay != todayEpochDay) {
                    updatedPlans += plan.copy(balanceAppliedThroughEpochDay = todayEpochDay)
                }
                return@forEach
            }

            val deltas = mutableMapOf<String, Long>()
            var valid = true
            dueDates.forEach { date ->
                val amount = calculator.amountOn(plan, date)
                fun addDelta(accountName: String?, delta: Long) {
                    if (accountName == null || accountName !in accountsByName) {
                        valid = false
                        missingAccount = true
                        return
                    }
                    deltas[accountName] = runCatching {
                        Math.addExact(deltas[accountName] ?: 0L, delta)
                    }.getOrElse { valid = false; 0L }
                }
                when (plan.type) {
                    EntryType.INCOME, EntryType.REFUND -> addDelta(plan.accountName, amount)
                    EntryType.EXPENSE, EntryType.DEBT -> addDelta(plan.accountName, -amount)
                    EntryType.TRANSFER -> {
                        addDelta(plan.accountName, -amount)
                        addDelta(plan.toAccountName, amount)
                    }
                }
            }
            if (!valid) return@forEach

            val changedAccounts = mutableMapOf<String, AccountEntity>()
            deltas.forEach { (name, delta) ->
                val account = accountsByName.getValue(name)
                val newBalance = runCatching {
                    Math.addExact(account.currentBalanceCents, delta)
                }.getOrElse { valid = false; account.currentBalanceCents }
                changedAccounts[name] = account.copy(currentBalanceCents = newBalance)
            }
            if (!valid) return@forEach
            changedAccounts.forEach { (name, account) -> accountsByName[name] = account }

            val newRemaining = if (plan.installmentTotal > 1) {
                (remaining - dueDates.size).coerceAtLeast(0)
            } else remaining
            updatedPlans += plan.copy(
                installmentRemaining = newRemaining,
                balanceAppliedThroughEpochDay = todayEpochDay,
                isActive = plan.isActive && !(plan.installmentTotal > 1 && newRemaining == 0),
            )
            postedCount += dueDates.size
        }

        return AutomaticPostingResult(
            accounts = accountsByName.values.toList(),
            plans = updatedPlans,
            postedCount = postedCount,
            missingAccount = missingAccount,
        )
    }
}
