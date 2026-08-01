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
}
