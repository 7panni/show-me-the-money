package com.qiyue.cashflow.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EntryType { EXPENSE, INCOME, DEBT, TRANSFER, REFUND }
enum class AccountType { CASH, WECHAT, ALIPAY, CMB, PAYPAL, CRYPTO, CUSTOM }
enum class RepeatRule { NONE, DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY }
enum class BudgetPeriod { WEEKLY, MONTHLY, CUSTOM }
enum class BudgetMethod { DAILY_AVERAGE, SPECIFIC_DAY }
enum class AdjustmentType { BALANCE_CALIBRATION, TEMPORARY }

@Entity(indices = [Index(value = ["name"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val initialBalanceCents: Long = 0,
    val currentBalanceCents: Long = initialBalanceCents,
    val currencyCode: String = "CNY",
    val exchangeRateToCnyMicros: Long = 1_000_000,
    val includeInForecast: Boolean = true,
    val accountType: AccountType = AccountType.CUSTOM,
    val isActive: Boolean = true
)

@Entity(indices = [Index(value = ["name", "parentName"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentName: String? = null,
    val type: EntryType
)

@Entity(indices = [Index(value = ["sourceRowHash"], unique = true)])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtMillis: Long,
    val type: EntryType,
    val amountCents: Long,
    val currencyCode: String = "CNY",
    val exchangeRateToCnyMicros: Long = 1_000_000,
    val accountName: String,
    val toAccountName: String? = null,
    val category: String,
    val subcategory: String? = null,
    val note: String? = null,
    val tags: String? = null,
    val counterparty: String? = null,
    val feeCents: Long = 0,
    val refundAmountCents: Long = 0,
    val refundAtMillis: Long? = null,
    val installmentTotal: Int = 1,
    val installmentIndex: Int = 1,
    val liabilityHidden: Boolean = false,
    val excludeFromBudget: Boolean = false,
    val excludeFromCashflow: Boolean = false,
    val sourceApp: String = "好好记账",
    val sourceRowHash: String
)

@Entity
data class PlannedTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: EntryType,
    val amountCents: Long,
    val currencyCode: String = "CNY",
    val exchangeRateToCnyMicros: Long = 1_000_000,
    val accountName: String,
    val toAccountName: String? = null,
    val startDateEpochDay: Long,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val endDateEpochDay: Long? = null,
    val installmentTotal: Int = 1,
    val installmentRemaining: Int = 0,
    val installmentTotalAmountCents: Long = 0,
    val isActive: Boolean = true
)

@Entity
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val totalCents: Long,
    val remainingCents: Long,
    val period: BudgetPeriod,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val accountName: String,
    val method: BudgetMethod = BudgetMethod.DAILY_AVERAGE,
    val chargeDay: Int? = null,
    val carryOver: Boolean = false,
    val isActive: Boolean = true
)

@Entity
data class AdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AdjustmentType,
    val amountCents: Long,
    val accountName: String,
    val dateEpochDay: Long,
    val isIncome: Boolean = false,
    val usesBudget: Boolean = false,
    val appliedToBalance: Boolean = false
)

@Entity
data class CryptoHoldingEntity(
    @PrimaryKey val symbol: String,
    val amountUnits: Long = 0
)

@Entity
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val usdRateMicros: Long = 7_200_000,
    val defaultHorizonDays: Int = 90,
    val budgetMonthStartDay: Int = 21,
    val showUsdOnHome: Boolean = true,
    val showCryptoOnHome: Boolean = true,
    val showPaypalOnAccounts: Boolean = true,
    val showAlipayOnAccounts: Boolean = true,
    val showCryptoOnAccounts: Boolean = true,
    val appMode: String = "SIMPLE",
    val customModulesCsv: String = "",
    val hiddenAccountIdsCsv: String = "",
    val hiddenHomeCurrenciesCsv: String = "",
    val onboardingCompleted: Boolean = false,
    val seeded: Boolean = false
)
