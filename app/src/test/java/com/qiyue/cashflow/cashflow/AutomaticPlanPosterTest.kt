package com.qiyue.cashflow.cashflow

import com.qiyue.cashflow.data.AccountEntity
import com.qiyue.cashflow.data.EntryType
import com.qiyue.cashflow.data.PlannedTransactionEntity
import com.qiyue.cashflow.data.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AutomaticPlanPosterTest {
    private val poster = AutomaticPlanPoster()
    private val dueDate = LocalDate.of(2026, 2, 28)

    private fun plan(
        name: String,
        type: EntryType,
        amount: Long,
        installmentTotal: Int = 1,
        installmentRemaining: Int = 0,
    ) = PlannedTransactionEntity(
        name = name,
        type = type,
        amountCents = amount,
        accountName = "银行",
        startDateEpochDay = dueDate.toEpochDay(),
        repeatRule = RepeatRule.MONTHLY,
        installmentTotal = installmentTotal,
        installmentRemaining = installmentRemaining,
        installmentTotalAmountCents = if (installmentTotal > 1) amount * installmentTotal else 0,
        balanceAppliedThroughEpochDay = dueDate.minusDays(1).toEpochDay(),
    )

    @Test fun salaryRentAndInstallmentPostOnceAndInstallmentDecreases() {
        val account = AccountEntity(name = "银行", currentBalanceCents = 100_000)
        val plans = listOf(
            plan("工资", EntryType.INCOME, 60_000),
            plan("房租", EntryType.EXPENSE, 20_000),
            plan("分期", EntryType.EXPENSE, 10_000, installmentTotal = 6, installmentRemaining = 6),
        )

        val first = poster.postDuePlans(listOf(account), plans, dueDate)
        assertEquals(130_000, first.accounts.single().currentBalanceCents)
        assertEquals(3, first.postedCount)
        assertEquals(5, first.plans.first { it.name == "分期" }.installmentRemaining)

        val second = poster.postDuePlans(first.accounts, first.plans, dueDate)
        assertEquals(130_000, second.accounts.single().currentBalanceCents)
        assertEquals(0, second.postedCount)
    }

    @Test fun upgradeAdvancesOldInstallmentWithoutChangingAccounts() {
        val old = plan("分期", EntryType.EXPENSE, 10_000, installmentTotal = 6, installmentRemaining = 6)
            .copy(balanceAppliedThroughEpochDay = null)
        val initialized = poster.initializeExistingPlans(listOf(old), dueDate)
        assertEquals(5, initialized.single().installmentRemaining)
        assertEquals(dueDate.toEpochDay(), initialized.single().balanceAppliedThroughEpochDay)
        assertFalse(initialized.single().isActive.not())
    }

    @Test fun newlyCreatedPastPlanDoesNotBackPostEarlierMonths() {
        val today = LocalDate.of(2026, 3, 31)
        val pastPlan = plan("房租", EntryType.EXPENSE, 10_000).copy(
            startDateEpochDay = LocalDate.of(2026, 1, 31).toEpochDay(),
            balanceAppliedThroughEpochDay = initialPlanPostingCursor(
                LocalDate.of(2026, 1, 31), today
            ),
        )

        val result = poster.postDuePlans(
            listOf(AccountEntity(name = "银行", currentBalanceCents = 100_000)),
            listOf(pastPlan),
            today,
        )

        assertEquals(90_000, result.accounts.single().currentBalanceCents)
        assertEquals(1, result.postedCount)
    }

    @Test fun finalInstallmentUsesRemainderAndThenStops() {
        val firstDate = LocalDate.of(2026, 1, 31)
        val installment = plan(
            "分期", EntryType.EXPENSE, 3_333,
            installmentTotal = 3, installmentRemaining = 1,
        ).copy(
            startDateEpochDay = firstDate.toEpochDay(),
            installmentTotalAmountCents = 10_001,
            balanceAppliedThroughEpochDay = LocalDate.of(2026, 2, 28).toEpochDay(),
        )
        val result = poster.postDuePlans(
            listOf(AccountEntity(name = "银行", currentBalanceCents = 20_000)),
            listOf(installment),
            LocalDate.of(2026, 3, 31),
        )

        assertEquals(16_665, result.accounts.single().currentBalanceCents)
        assertEquals(0, result.plans.single().installmentRemaining)
        assertFalse(result.plans.single().isActive)
        assertTrue(result.postedCount == 1)
    }
}
