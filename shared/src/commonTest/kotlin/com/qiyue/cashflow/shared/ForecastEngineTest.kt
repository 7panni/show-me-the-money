package com.qiyue.cashflow.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForecastEngineTest {
    private val engine = CoreForecastEngine()
    private val today = CivilDate(2026, 1, 31).toEpochDay()

    @Test fun dateMathRoundTripsAndClampsMonthEnd() {
        assertEquals(CivilDate(2026, 1, 31), CivilDate.fromEpochDay(today))
        assertEquals(CivilDate(2026, 2, 28), CivilDate(2026, 1, 31).plusMonths(1))
        assertEquals(CivilDate(2024, 2, 29), CivilDate.fromEpochDay(CivilDate(2024, 2, 29).toEpochDay()))
    }

    @Test fun monthlyTransferAndShortageMatchCashRules() {
        val monthly = CorePlan("月费", CoreEntryType.EXPENSE, 1L, "CNY", "A", startEpochDay = today, repeatRule = CoreRepeatRule.MONTHLY)
        assertTrue(engine.occursOn(monthly, CivilDate(2026, 2, 28).toEpochDay()))
        assertFalse(engine.occursOn(monthly, CivilDate(2026, 2, 27).toEpochDay()))
        val transfer = CorePlan("转账", CoreEntryType.TRANSFER, 20_000L, "CNY", "A", "B", today, CoreRepeatRule.NONE)
        val result = engine.forecast(
            listOf(CoreAccount("A", 100_000L, "CNY"), CoreAccount("B", 50_000L, "CNY")),
            listOf(transfer), emptyList(), emptyList(), today, 30,
        )
        assertEquals(150_000L, result.endCnyMinor)
    }

    @Test fun weeklyQuarterlyYearlyAndInstallmentsUseCorrectDates() {
        val weekly = CorePlan("周", CoreEntryType.EXPENSE, 1, "CNY", "A", startEpochDay=today, repeatRule=CoreRepeatRule.WEEKLY)
        val quarterly = CorePlan("季", CoreEntryType.EXPENSE, 1, "CNY", "A", startEpochDay=today, repeatRule=CoreRepeatRule.QUARTERLY)
        val yearly = CorePlan("年", CoreEntryType.EXPENSE, 1, "CNY", "A", startEpochDay=today, repeatRule=CoreRepeatRule.YEARLY)
        val installment = CorePlan("分期", CoreEntryType.EXPENSE, 1, "CNY", "A", startEpochDay=today, repeatRule=CoreRepeatRule.MONTHLY, installmentTotal=2, installmentRemaining=2)
        assertTrue(engine.occursOn(weekly, CivilDate(2026, 2, 7).toEpochDay()))
        assertTrue(engine.occursOn(quarterly, CivilDate(2026, 4, 30).toEpochDay()))
        assertTrue(engine.occursOn(yearly, CivilDate(2027, 1, 31).toEpochDay()))
        assertTrue(engine.occursOn(installment, CivilDate(2026, 2, 28).toEpochDay()))
        assertFalse(engine.occursOn(installment, CivilDate(2026, 3, 31).toEpochDay()))
    }

    @Test fun repeatingBudgetAdjustmentAndExcludedAccountMatchCashRules() {
        val result = engine.forecast(
            accounts = listOf(
                CoreAccount("A", 100_000, "CNY"),
                CoreAccount("不计入", 50_000, "CNY", included=false),
            ),
            plans = emptyList(),
            budgets = listOf(CoreBudget(
                id=1, name="预算", totalMinor=700, remainingMinor=700,
                period=CoreBudgetPeriod.WEEKLY, startEpochDay=today, endEpochDay=today+6,
            )),
            adjustments = listOf(CoreAdjustment(
                name="预算内支出", amountMinor=200, accountName="A", dateEpochDay=today+1,
                income=false, usesBudget=true,
            )),
            todayEpochDay = today,
            horizonDays = 14,
        )
        assertEquals(98_600L, result.endCnyMinor)
        assertEquals(99_800L, result.days.last().accountBalancesMinor["A"])
    }
}
