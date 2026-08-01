package com.qiyue.cashflow.cashflow

import com.qiyue.cashflow.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CashFlowCalculatorTest {
    private val today = LocalDate.of(2026, 1, 31)
    private val calculator = CashFlowCalculator()
    private fun account(name:String,balance:Long=100_000,included:Boolean=true)=AccountEntity(
        name=name,currentBalanceCents=balance,initialBalanceCents=balance,includeInForecast=included)
    private fun plan(name:String,type:EntryType,amount:Long,start:LocalDate,repeat:RepeatRule,to:String?=null,remaining:Int=0)=
        PlannedTransactionEntity(name=name,type=type,amountCents=amount,accountName="A",toAccountName=to,
            startDateEpochDay=start.toEpochDay(),repeatRule=repeat,installmentRemaining=remaining)

    @Test fun monthlyQuarterlySemiannualYearlyClampToMonthEnd() {
        val monthly=plan("月",EntryType.EXPENSE,1,today,RepeatRule.MONTHLY)
        assertTrue(calculator.occursOn(monthly,LocalDate.of(2026,2,28)))
        val quarterly=plan("季",EntryType.EXPENSE,1,today,RepeatRule.QUARTERLY)
        assertTrue(calculator.occursOn(quarterly,LocalDate.of(2026,4,30)))
        val semiannual=plan("半年",EntryType.EXPENSE,1,today,RepeatRule.SEMIANNUAL)
        assertTrue(calculator.occursOn(semiannual,LocalDate.of(2026,7,31)))
        assertFalse(calculator.occursOn(semiannual,LocalDate.of(2026,6,30)))
        val yearly=plan("年",EntryType.EXPENSE,1,today,RepeatRule.YEARLY)
        assertTrue(calculator.occursOn(yearly,LocalDate.of(2027,1,31)))
    }

    @Test fun recurringRentUsesPaymentAmountWithoutInstallmentTotal() {
        val rent=plan("房租",EntryType.EXPENSE,200_000,today,RepeatRule.MONTHLY).copy(
            endDateEpochDay=LocalDate.of(2026,3,31).toEpochDay()
        )
        val result=calculator.forecast(listOf(account("A",1_000_000)),listOf(rent),emptyList(),emptyList(),today,120)
        assertEquals(400_000,result.endBalanceCents)
    }

    @Test fun weeklyUsesSameWeekdayAndInstallmentsStop() {
        val weekly=plan("周",EntryType.EXPENSE,100,today,RepeatRule.WEEKLY,remaining=2)
        assertTrue(calculator.occursOn(weekly,today.plusWeeks(1)))
        assertFalse(calculator.occursOn(weekly,today.plusWeeks(2)))
    }

    @Test fun transferDoesNotChangeTotal() {
        val result=calculator.forecast(listOf(account("A"),account("B",50_000)),
            listOf(plan("转账",EntryType.TRANSFER,20_000,today,RepeatRule.NONE,"B")),emptyList(),emptyList(),today,30)
        assertEquals(150_000,result.currentBalanceCents)
        assertEquals(150_000,result.endBalanceCents)
        assertEquals(80_000L,result.days.first().accountBalancesCents["A"])
        assertEquals(70_000L,result.days.first().accountBalancesCents["B"])
    }

    @Test fun budgetAndTemporaryExpenseAffectForecast() {
        val budget=BudgetEntity(name="预算",totalCents=3_000,remainingCents=3_000,period=BudgetPeriod.CUSTOM,
            startDateEpochDay=today.toEpochDay(),endDateEpochDay=today.plusDays(2).toEpochDay(),accountName="A")
        val temp=AdjustmentEntity(name="意外",type=AdjustmentType.TEMPORARY,amountCents=5_000,accountName="A",
            dateEpochDay=today.plusDays(1).toEpochDay(),isIncome=false)
        val result=calculator.forecast(listOf(account("A")),emptyList(),listOf(budget),listOf(temp),today,30)
        assertEquals(92_000,result.endBalanceCents)
    }

    @Test fun temporaryExpenseUsingBudgetIsNotDoubleCounted() {
        val budget=BudgetEntity(name="预算",totalCents=10_000,remainingCents=10_000,period=BudgetPeriod.CUSTOM,
            startDateEpochDay=today.toEpochDay(),endDateEpochDay=today.plusDays(9).toEpochDay(),accountName="A")
        val temp=AdjustmentEntity(name="预算内购物",type=AdjustmentType.TEMPORARY,amountCents=2_000,accountName="A",
            dateEpochDay=today.plusDays(1).toEpochDay(),isIncome=false,usesBudget=true)
        val result=calculator.forecast(listOf(account("A")),emptyList(),listOf(budget),listOf(temp),today,30)
        assertEquals(90_000,result.endBalanceCents)
    }

    @Test fun accountFreeBudgetChangesTotalButNotAccountBalance() {
        val budget=BudgetEntity(name="预算",totalCents=3_000,remainingCents=3_000,period=BudgetPeriod.CUSTOM,
            startDateEpochDay=today.toEpochDay(),endDateEpochDay=today.plusDays(2).toEpochDay(),accountName="")
        val result=calculator.forecast(listOf(account("A")),emptyList(),listOf(budget),emptyList(),today,30)
        assertEquals(97_000,result.endBalanceCents)
        assertEquals(100_000L,result.days.last().accountBalancesCents["A"])
    }

    @Test fun monthlyBudgetUsesRemainingNowAndTotalInFollowingMonths() {
        val budget=BudgetEntity(name="预算",totalCents=3_000,remainingCents=2_000,period=BudgetPeriod.MONTHLY,
            startDateEpochDay=LocalDate.of(2026,1,21).toEpochDay(),
            endDateEpochDay=LocalDate.of(2026,2,20).toEpochDay(),accountName="")
        val result=calculator.forecast(listOf(account("A")),emptyList(),listOf(budget),emptyList(),today,49)
        assertEquals(95_000,result.endBalanceCents)
    }

    @Test fun weeklyBudgetRepeatsEveryWeek() {
        val budget=BudgetEntity(name="周预算",totalCents=700,remainingCents=700,period=BudgetPeriod.WEEKLY,
            startDateEpochDay=today.toEpochDay(),endDateEpochDay=today.plusDays(6).toEpochDay(),accountName="")
        val result=calculator.forecast(listOf(account("A")),emptyList(),listOf(budget),emptyList(),today,14)
        assertEquals(98_600,result.endBalanceCents)
    }

    @Test fun appliedTemporaryAdjustmentIsNotForecastAgain() {
        val applied=AdjustmentEntity(name="已到账奖金",type=AdjustmentType.TEMPORARY,amountCents=5_000,
            accountName="A",dateEpochDay=today.toEpochDay(),isIncome=true,appliedToBalance=true)
        val result=calculator.forecast(listOf(account("A",105_000)),emptyList(),emptyList(),listOf(applied),today,30)
        assertEquals(105_000,result.endBalanceCents)
        assertTrue(result.days.first().events.isEmpty())
    }

    @Test fun calibrationIsNotCountedAgainAndExcludedAccountIsIgnored() {
        val calibration=AdjustmentEntity(name="校准",type=AdjustmentType.BALANCE_CALIBRATION,amountCents=999_999,
            accountName="A",dateEpochDay=today.toEpochDay())
        val result=calculator.forecast(listOf(account("A",80_000),account("隐藏",50_000,false)),
            emptyList(),emptyList(),listOf(calibration),today,365)
        assertEquals(80_000,result.currentBalanceCents)
        assertEquals(80_000,result.endBalanceCents)
        assertEquals(365,result.days.size)
    }

    @Test fun incomeExpenseAndShortageDateAreCorrect() {
        val plans=listOf(
            plan("工资",EntryType.INCOME,20_000,today,RepeatRule.NONE),
            plan("房租",EntryType.EXPENSE,130_000,today.plusDays(1),RepeatRule.NONE)
        )
        val result=calculator.forecast(listOf(account("A")),plans,emptyList(),emptyList(),today,90)
        assertEquals(today.plusDays(1),result.firstShortageDate)
        assertEquals(-10_000,result.endBalanceCents)
    }

    @Test fun cnyAndUsdTotalsStaySeparateWithoutExchangeRate() {
        val cny = account("人民币", 100_000)
        val usd = account("美元", 20_000).copy(
            currencyCode = "USD",
            exchangeRateToCnyMicros = 99_000_000
        )
        val result = calculator.forecast(listOf(cny, usd), emptyList(), emptyList(), emptyList(), today, 30)
        assertEquals(100_000L, result.currentBalanceCents)
        assertEquals(20_000L, result.currentUsdCents)
        assertEquals(100_000L, result.endBalanceCents)
        assertEquals(20_000L, result.endUsdCents)
    }
}
