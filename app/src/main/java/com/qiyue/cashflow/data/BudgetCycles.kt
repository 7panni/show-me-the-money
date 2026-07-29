package com.qiyue.cashflow.data

import java.time.LocalDate

object BudgetCycles {
    fun containing(date: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
        val day = startDay.coerceIn(1, 28)
        val thisMonthStart = date.withDayOfMonth(day)
        val start = if (date < thisMonthStart) thisMonthStart.minusMonths(1) else thisMonthStart
        return start to start.plusMonths(1).minusDays(1)
    }
}
