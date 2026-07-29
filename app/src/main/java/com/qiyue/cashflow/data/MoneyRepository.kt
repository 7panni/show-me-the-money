package com.qiyue.cashflow.data

import com.qiyue.cashflow.importer.ImportResult
import com.qiyue.cashflow.importer.ImportedBook
import kotlinx.coroutines.flow.Flow

/** 保留给旧 CSV 导入代码使用；预测测试版界面不展示导入入口。 */
class MoneyRepository(private val dao: TransactionDao) {
    fun observeTransactions(): Flow<List<TransactionEntity>> = dao.observeTransactions()
    fun observePlannedTransactions(): Flow<List<PlannedTransactionEntity>> = dao.observePlannedTransactions()
    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()
    suspend fun ensureDefaultAccounts() = Unit
    suspend fun importBook(book: ImportedBook): ImportResult {
        dao.insertAccounts(book.accounts)
        dao.insertCategories(book.categories)
        val inserted = dao.insertTransactions(book.transactions).count { it != -1L }
        return ImportResult(book.sourceRowCount, inserted, book.transactions.size - inserted)
    }
    suspend fun addPlanned(item: PlannedTransactionEntity) { dao.upsertPlan(item) }
    suspend fun addTransaction(item: TransactionEntity) { dao.insertTransaction(item) }
}
