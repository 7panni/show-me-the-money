package com.qiyue.cashflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
