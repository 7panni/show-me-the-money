package com.qiyue.cashflow.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BudgetCyclesTest {
    @Test fun defaultSettingsUseCalendarMonthAndStableRiskTip() {
        val settings = AppSettingsEntity()
        assertEquals(1, settings.budgetMonthStartDay)
        assertEquals(false, settings.financeTipChangesDaily)
        assertEquals(
            LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31),
            BudgetCycles.containing(LocalDate.of(2026, 8, 18), settings.budgetMonthStartDay)
        )
    }

    @Test fun day21StartsCycleEndingOnNextMonthDay20() {
        assertEquals(
            LocalDate.of(2026, 7, 21) to LocalDate.of(2026, 8, 20),
            BudgetCycles.containing(LocalDate.of(2026, 7, 29), 21)
        )
        assertEquals(
            LocalDate.of(2026, 6, 21) to LocalDate.of(2026, 7, 20),
            BudgetCycles.containing(LocalDate.of(2026, 7, 5), 21)
        )
    }
}
