package com.qiyue.cashflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class BackupJsonTest {
    @Test fun completeBackupRoundTripPreservesForecastData() {
        val today = LocalDate.of(2026, 7, 28).toEpochDay()
        val source = ForecastBackup(
            accounts = listOf(AccountEntity(
                id = 1, name = "美元账户", currentBalanceCents = 12_345,
                currencyCode = "USD", exchangeRateToCnyMicros = 7_180_000,
                accountType = AccountType.PAYPAL
            )),
            plans = listOf(PlannedTransactionEntity(
                id = 2, name = "年费", type = EntryType.EXPENSE, amountCents = 9_900,
                accountName = "美元账户", startDateEpochDay = today,
                repeatRule = RepeatRule.YEARLY, endDateEpochDay = today + 730,
                installmentTotal = 2, installmentRemaining = 1,
                installmentTotalAmountCents = 19_801,
                balanceAppliedThroughEpochDay = today - 1,
            )),
            budgets = listOf(BudgetEntity(
                id = 3, name = "旅行", totalCents = 50_000, remainingCents = 32_000,
                period = BudgetPeriod.CUSTOM, startDateEpochDay = today,
                endDateEpochDay = today + 30, accountName = "美元账户",
                method = BudgetMethod.SPECIFIC_DAY, chargeDay = 15, carryOver = true
            )),
            adjustments = listOf(AdjustmentEntity(
                id = 4, name = "奖金", type = AdjustmentType.TEMPORARY,
                amountCents = 20_000, accountName = "美元账户",
                dateEpochDay = today + 2, isIncome = true, appliedToBalance = true
            )),
            settings = AppSettingsEntity(
                usdRateMicros = 7_180_000,
                defaultHorizonDays = 365,
                showPaypalOnAccounts = false,
                showAlipayOnAccounts = false,
                showCryptoOnAccounts = false,
                appMode = "CUSTOM",
                customModulesCsv = "BUDGET,TRANSFER",
                hiddenAccountIdsCsv = "1,8,21",
                hiddenHomeCurrenciesCsv = "CRYPTO,USD",
                showFinanceTips = false,
                onboardingCompleted = true,
                seeded = true
            ),
            holdings = listOf(
                CryptoHoldingEntity("USDT", 125_000_000),
                CryptoHoldingEntity("BTC", 12_345_678),
                CryptoHoldingEntity("LTC", 300_000_000)
            )
        )

        val json = BackupJson.encode(source)
        val restored = BackupJson.decode(json)

        assertTrue(json.contains("\"format\": \"cashflow-forecast\""))
        assertEquals(source.accounts, restored.accounts)
        assertEquals(source.plans, restored.plans)
        assertEquals(source.budgets, restored.budgets)
        assertEquals(source.adjustments, restored.adjustments)
        assertEquals(source.settings, restored.settings)
        assertEquals(source.holdings, restored.holdings)
    }

    @Test fun oldBackupWithoutModeStillRestores() {
        val source = ForecastBackup(
            accounts = emptyList(), plans = emptyList(), budgets = emptyList(), adjustments = emptyList(),
            settings = AppSettingsEntity(onboardingCompleted = true, seeded = true),
        )
        val legacy = JSONObject(BackupJson.encode(source)).apply {
            getJSONObject("settings").apply {
                remove("appMode")
                remove("customModules")
                remove("onboardingCompleted")
                remove("hiddenAccountIds")
                remove("hiddenHomeCurrencies")
                remove("showFinanceTips")
            }
        }.toString()
        val restored = BackupJson.decode(legacy)
        assertEquals("SIMPLE", restored.settings.appMode)
        assertTrue(restored.settings.onboardingCompleted)
        assertTrue(!restored.hasAccountVisibilitySettings)
        assertTrue(!restored.hasHomeCurrencySettings)
        assertTrue(restored.settings.showFinanceTips)
    }
}
