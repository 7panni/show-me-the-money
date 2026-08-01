package com.qiyue.cashflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TransactionDao

    @Before fun setUp() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        database=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        dao=database.transactionDao()
    }

    @After fun tearDown()=database.close()

    @Test fun accountRenameUpdatesEveryForecastReferenceAndRejectsDuplicates()=runBlocking {
        dao.upsertAccount(AccountEntity(id=1,name="银行",currentBalanceCents=100_000))
        dao.upsertAccount(AccountEntity(id=2,name="微信"))
        dao.upsertPlan(PlannedTransactionEntity(
            id=1,name="工资",type=EntryType.INCOME,amountCents=10_000,
            accountName="银行",startDateEpochDay=LocalDate.now().toEpochDay(),repeatRule=RepeatRule.MONTHLY
        ))
        dao.upsertBudget(BudgetEntity(
            id=1,name="预算",totalCents=0,remainingCents=0,period=BudgetPeriod.MONTHLY,
            startDateEpochDay=LocalDate.now().toEpochDay(),endDateEpochDay=LocalDate.now().plusMonths(1).minusDays(1).toEpochDay(),
            accountName="银行"
        ))
        dao.upsertAdjustment(AdjustmentEntity(
            id=1,name="奖金",type=AdjustmentType.TEMPORARY,amountCents=1_000,
            accountName="银行",dateEpochDay=LocalDate.now().toEpochDay()
        ))

        assertTrue(dao.renameAccountAndReferences(1,"储蓄账户"))
        assertEquals("储蓄账户",dao.getAccount(1)?.name)
        assertEquals("储蓄账户",dao.getPlans().single().accountName)
        assertEquals("储蓄账户",dao.getBudgets().single().accountName)
        assertEquals("储蓄账户",dao.getAdjustments().single().accountName)
        assertFalse(dao.renameAccountAndReferences(1,"微信"))
    }

    @Test fun transferAndUndoRestoreBothBalancesExactly()=runBlocking {
        dao.upsertAccount(AccountEntity(id=1,name="银行",currentBalanceCents=100_001))
        dao.upsertAccount(AccountEntity(id=2,name="微信",currentBalanceCents=-2_000))

        assertTrue(dao.transferAccountBalance(1,2,30_001))
        assertEquals(70_000,dao.getAccount(1)?.currentBalanceCents)
        assertEquals(28_001,dao.getAccount(2)?.currentBalanceCents)
        assertTrue(dao.undoAccountBalanceTransfer(1,2,30_001))
        assertEquals(100_001,dao.getAccount(1)?.currentBalanceCents)
        assertEquals(-2_000,dao.getAccount(2)?.currentBalanceCents)
    }
}
