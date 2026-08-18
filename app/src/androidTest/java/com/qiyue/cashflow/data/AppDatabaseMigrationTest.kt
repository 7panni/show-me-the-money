package com.qiyue.cashflow.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-5-10-test.db"

    @After fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test fun migration5To10PreservesSettingsAndAddsAutomaticPostingCursor() {
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE AppSettingsEntity (" +
                                "id INTEGER NOT NULL PRIMARY KEY, usdRateMicros INTEGER NOT NULL, " +
                                "defaultHorizonDays INTEGER NOT NULL, budgetMonthStartDay INTEGER NOT NULL, " +
                                "showUsdOnHome INTEGER NOT NULL, showCryptoOnHome INTEGER NOT NULL, " +
                                "showPaypalOnAccounts INTEGER NOT NULL, showAlipayOnAccounts INTEGER NOT NULL, " +
                                "showCryptoOnAccounts INTEGER NOT NULL, seeded INTEGER NOT NULL)"
                        )
                        db.execSQL("CREATE TABLE PlannedTransactionEntity (id INTEGER NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO AppSettingsEntity VALUES (1,7200000,365,21,0,1,0,1,0,1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { db ->
            AppDatabase.MIGRATION_5_6.migrate(db)
            AppDatabase.MIGRATION_6_7.migrate(db)
            AppDatabase.MIGRATION_7_8.migrate(db)
            AppDatabase.MIGRATION_8_9.migrate(db)
            AppDatabase.MIGRATION_9_10.migrate(db)
            db.query("SELECT defaultHorizonDays, appMode, customModulesCsv, onboardingCompleted, hiddenAccountIdsCsv, hiddenHomeCurrenciesCsv, showFinanceTips FROM AppSettingsEntity WHERE id=1").use { cursor ->
                cursor.moveToFirst()
                assertEquals(365, cursor.getInt(0))
                assertEquals("FULL", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals("", cursor.getString(4))
                assertEquals("USD", cursor.getString(5))
                assertEquals(1, cursor.getInt(6))
            }
            db.query("SELECT installmentTotalAmountCents, balanceAppliedThroughEpochDay FROM PlannedTransactionEntity").use { cursor ->
                assertEquals(2,cursor.columnCount)
            }
        }
        helper.close()
    }
}
