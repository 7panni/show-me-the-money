package com.qiyue.cashflow.ui

import com.qiyue.cashflow.data.EntryType
import com.qiyue.cashflow.data.PlannedTransactionEntity
import com.qiyue.cashflow.data.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BalanceExpressionTest {
    @Test fun additionAndSubtractionUseExactMinorUnits() {
        assertEquals(103_055L, parseCentsExpression("1000+50.55-20"))
        assertEquals(-12_345L, parseCentsExpression("-123.45"))
        assertEquals(10L, parseCentsExpression("0.1"))
    }

    @Test fun malformedExpressionIsRejected() {
        assertNull(parseCentsExpression("100+"))
        assertNull(parseCentsExpression("1.2.3"))
        assertNull(parseCentsExpression("100*2"))
    }

    @Test fun calculatorButtonsAddAndSubtractWithoutFloatingPointError() {
        assertEquals("1030.55", adjustBalanceText("1000.00", "30.55", "CNY", true))
        assertEquals("969.45", adjustBalanceText("1000.00", "30.55", "CNY", false))
        assertNull(adjustBalanceText("1000.00", "", "CNY", true))
    }

    @Test fun currenciesUseTheirOwnMinorUnits() {
        assertEquals(1235L, parseCurrencyAmount("12.345", "USD"))
        assertEquals(12L, parseCurrencyAmount("12.4", "JPY"))
        assertEquals(12_346L, parseCurrencyAmount("12.3456", "KWD"))
        assertEquals("1234", currencyDecimal(1234L, "JPY"))
        assertEquals("12.346", currencyDecimal(12_346L, "KWD"))
    }

    @Test fun accountVisibilityCsvIgnoresInvalidValues() {
        assertEquals(setOf(1L, 8L, 21L), parseAccountIdCsv("1, 8,bad,21,8"))
    }

    @Test fun installmentBreakdownKeepsExactTotal() {
        assertEquals(3_333L to 3_335L,installmentBreakdown(10_001L,3))
        assertNull(installmentBreakdown(100L,1))
        assertEquals(setOf("USD","CRYPTO"),parseCurrencyCodeCsv("usd, CRYPTO,usd"))
    }

    @Test fun installmentCardShowsExactRemainingAmountIncludingLastPeriodTail() {
        val plan=PlannedTransactionEntity(
            name="分期",type=EntryType.EXPENSE,amountCents=3_333L,
            accountName="银行",
            startDateEpochDay=LocalDate.of(2026,8,10).toEpochDay(),
            repeatRule=RepeatRule.MONTHLY,installmentTotal=3,installmentRemaining=2,
            installmentTotalAmountCents=10_001L
        )
        assertEquals(2,remainingInstallmentPeriods(plan))
        assertEquals(6_668L,installmentRemainingAmount(plan))
        assertEquals(LocalDate.of(2026,8,10),nextPlanDate(plan,LocalDate.of(2026,8,1)))
        assertNull(nextPlanDate(plan.copy(isActive=false),LocalDate.of(2026,8,1)))
    }

    @Test fun recurringRentIsNotMistakenForInstallment() {
        val rent=PlannedTransactionEntity(
            name="房租",type=EntryType.EXPENSE,amountCents=2_000_00L,accountName="银行",
            startDateEpochDay=LocalDate.of(2026,8,15).toEpochDay(),repeatRule=RepeatRule.QUARTERLY,
            installmentTotal=40,installmentRemaining=40
        )
        assertFalse(isInstallmentPlan(rent))
        assertFalse(isInstallmentPlan(rent.copy(repeatRule=RepeatRule.MONTHLY)))
        assertTrue(isInstallmentPlan(rent.copy(name="分期",repeatRule=RepeatRule.MONTHLY)))
    }
}
