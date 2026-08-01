package com.qiyue.cashflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM AccountEntity ORDER BY isActive DESC, name")
    fun observeAccounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM PlannedTransactionEntity ORDER BY isActive DESC, startDateEpochDay, name")
    fun observePlans(): Flow<List<PlannedTransactionEntity>>
    @Query("SELECT * FROM BudgetEntity ORDER BY isActive DESC, name")
    fun observeBudgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM AdjustmentEntity ORDER BY dateEpochDay DESC, id DESC")
    fun observeAdjustments(): Flow<List<AdjustmentEntity>>
    @Query("SELECT * FROM AppSettingsEntity WHERE id=1")
    fun observeSettings(): Flow<AppSettingsEntity?>
    @Query("SELECT * FROM CryptoHoldingEntity ORDER BY CASE symbol WHEN 'USDT' THEN 1 WHEN 'BTC' THEN 2 ELSE 3 END")
    fun observeCryptoHoldings(): Flow<List<CryptoHoldingEntity>>

    @Query("SELECT * FROM TransactionEntity ORDER BY occurredAtMillis DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM PlannedTransactionEntity WHERE isActive = 1 ORDER BY startDateEpochDay")
    fun observePlannedTransactions(): Flow<List<PlannedTransactionEntity>>

    @Upsert suspend fun upsertAccount(item: AccountEntity): Long
    @Upsert suspend fun upsertPlan(item: PlannedTransactionEntity): Long
    @Upsert suspend fun upsertBudget(item: BudgetEntity): Long
    @Upsert suspend fun upsertAdjustment(item: AdjustmentEntity): Long
    @Upsert suspend fun upsertSettings(item: AppSettingsEntity)
    @Upsert suspend fun upsertCryptoHolding(item: CryptoHoldingEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCryptoHoldings(items: List<CryptoHoldingEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAccounts(items: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransactions(items: List<TransactionEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransaction(item: TransactionEntity): Long
    @Query("SELECT COUNT(*) FROM AccountEntity") suspend fun accountCount(): Int
    @Query("SELECT * FROM AccountEntity") suspend fun getAccounts(): List<AccountEntity>
    @Query("SELECT * FROM AccountEntity WHERE id=:id LIMIT 1") suspend fun getAccount(id: Long): AccountEntity?
    @Query("SELECT COUNT(*) FROM AccountEntity WHERE name=:name AND id!=:id")
    suspend fun countOtherAccountsWithName(id: Long, name: String): Int
    @Query("SELECT * FROM PlannedTransactionEntity") suspend fun getPlans(): List<PlannedTransactionEntity>
    @Query("SELECT * FROM BudgetEntity") suspend fun getBudgets(): List<BudgetEntity>
    @Query("SELECT * FROM BudgetEntity WHERE name=:name LIMIT 1") suspend fun getBudgetByName(name: String): BudgetEntity?
    @Query("SELECT * FROM AdjustmentEntity") suspend fun getAdjustments(): List<AdjustmentEntity>
    @Query("SELECT * FROM AppSettingsEntity WHERE id=1") suspend fun getSettings(): AppSettingsEntity?
    @Query("SELECT * FROM CryptoHoldingEntity") suspend fun getCryptoHoldings(): List<CryptoHoldingEntity>
    @Query("DELETE FROM AccountEntity WHERE id=:id") suspend fun deleteAccount(id: Long)
    @Query("DELETE FROM PlannedTransactionEntity WHERE id=:id") suspend fun deletePlan(id: Long)
    @Query("DELETE FROM BudgetEntity WHERE id=:id") suspend fun deleteBudget(id: Long)
    @Query("DELETE FROM AdjustmentEntity WHERE id=:id") suspend fun deleteAdjustment(id: Long)
    @Query("DELETE FROM TransactionEntity") suspend fun clearTransactions()
    @Query("DELETE FROM AccountEntity") suspend fun clearAccounts()
    @Query("DELETE FROM PlannedTransactionEntity") suspend fun clearPlans()
    @Query("DELETE FROM BudgetEntity") suspend fun clearBudgets()
    @Query("DELETE FROM AdjustmentEntity") suspend fun clearAdjustments()
    @Query("DELETE FROM CryptoHoldingEntity") suspend fun clearCryptoHoldings()
    @Query("UPDATE PlannedTransactionEntity SET accountName='数字货币' WHERE accountName=:oldName")
    suspend fun movePlansToCryptoAccount(oldName: String)
    @Query("UPDATE BudgetEntity SET accountName='数字货币' WHERE accountName=:oldName")
    suspend fun moveBudgetsToCryptoAccount(oldName: String)
    @Query("UPDATE AdjustmentEntity SET accountName='数字货币' WHERE accountName=:oldName")
    suspend fun moveAdjustmentsToCryptoAccount(oldName: String)
    @Query("UPDATE PlannedTransactionEntity SET accountName=:newName WHERE accountName=:oldName")
    suspend fun renamePlanAccount(oldName: String, newName: String)
    @Query("UPDATE PlannedTransactionEntity SET toAccountName=:newName WHERE toAccountName=:oldName")
    suspend fun renamePlanTargetAccount(oldName: String, newName: String)
    @Query("UPDATE AdjustmentEntity SET accountName=:newName WHERE accountName=:oldName")
    suspend fun renameAdjustmentAccount(oldName: String, newName: String)
    @Query("UPDATE BudgetEntity SET accountName=:newName WHERE accountName=:oldName")
    suspend fun renameBudgetAccount(oldName: String, newName: String)
    @Query("UPDATE AccountEntity SET name=:newName WHERE name=:oldName")
    suspend fun renameAccount(oldName: String, newName: String)
    @Query("UPDATE TransactionEntity SET accountName=:newName WHERE accountName=:oldName")
    suspend fun renameTransactionAccount(oldName: String, newName: String)
    @Query("UPDATE TransactionEntity SET toAccountName=:newName WHERE toAccountName=:oldName")
    suspend fun renameTransactionTargetAccount(oldName: String, newName: String)

    @Transaction
    suspend fun renameAccountAndReferences(id: Long, newName: String): Boolean {
        val account = getAccount(id) ?: return false
        if (account.name == newName) return true
        if (countOtherAccountsWithName(id, newName) > 0) return false
        renamePlanAccount(account.name, newName)
        renamePlanTargetAccount(account.name, newName)
        renameBudgetAccount(account.name, newName)
        renameAdjustmentAccount(account.name, newName)
        renameTransactionAccount(account.name, newName)
        renameTransactionTargetAccount(account.name, newName)
        renameAccount(account.name, newName)
        return true
    }

    @Transaction
    suspend fun transferAccountBalance(fromId: Long, toId: Long, amount: Long): Boolean {
        val from = getAccount(fromId) ?: return false
        val to = getAccount(toId) ?: return false
        if (from.id == to.id || from.currencyCode != to.currencyCode || amount <= 0L) return false
        if (from.currentBalanceCents < amount) return false
        val newTargetBalance = runCatching { Math.addExact(to.currentBalanceCents, amount) }.getOrNull() ?: return false
        upsertAccount(from.copy(currentBalanceCents = from.currentBalanceCents - amount))
        upsertAccount(to.copy(currentBalanceCents = newTargetBalance))
        return true
    }

    @Transaction
    suspend fun undoAccountBalanceTransfer(fromId: Long, toId: Long, amount: Long): Boolean {
        val from = getAccount(fromId) ?: return false
        val to = getAccount(toId) ?: return false
        if (from.id == to.id || from.currencyCode != to.currencyCode || amount <= 0L) return false
        val restoredFrom = runCatching { Math.addExact(from.currentBalanceCents, amount) }.getOrNull() ?: return false
        val restoredTo = runCatching { Math.subtractExact(to.currentBalanceCents, amount) }.getOrNull() ?: return false
        upsertAccount(from.copy(currentBalanceCents = restoredFrom))
        upsertAccount(to.copy(currentBalanceCents = restoredTo))
        return true
    }

    @Transaction
    suspend fun replaceForecastData(
        accounts: List<AccountEntity>,
        plans: List<PlannedTransactionEntity>,
        budgets: List<BudgetEntity>,
        adjustments: List<AdjustmentEntity>,
        settings: AppSettingsEntity,
        holdings: List<CryptoHoldingEntity> = emptyList()
    ) {
        clearAdjustments(); clearBudgets(); clearPlans(); clearAccounts(); clearCryptoHoldings()
        accounts.forEach { upsertAccount(it) }
        plans.forEach { upsertPlan(it) }
        budgets.forEach { upsertBudget(it) }
        adjustments.forEach { upsertAdjustment(it) }
        holdings.forEach { upsertCryptoHolding(it) }
        upsertSettings(settings)
    }
}
