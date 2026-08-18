package com.qiyue.cashflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class,
        PlannedTransactionEntity::class, BudgetEntity::class,
        AdjustmentEntity::class, CryptoHoldingEntity::class, AppSettingsEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "cashflow_forecast.db"
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            ).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN budgetMonthStartDay INTEGER NOT NULL DEFAULT 21")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN showUsdOnHome INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN showCryptoOnHome INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS CryptoHoldingEntity " +
                        "(symbol TEXT NOT NULL, amountUnits INTEGER NOT NULL, PRIMARY KEY(symbol))"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE AdjustmentEntity ADD COLUMN appliedToBalance " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN showPaypalOnAccounts INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN showAlipayOnAccounts INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN showCryptoOnAccounts INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 旧版本已经提供全部功能，因此迁移后保持完整模式，避免界面功能突然消失。
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN appMode TEXT NOT NULL DEFAULT 'FULL'")
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN customModulesCsv TEXT NOT NULL DEFAULT ''")
                // 已有用户直接进入应用；只有全新安装才显示首次启动选择。
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN onboardingCompleted INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN hiddenAccountIdsCsv TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PlannedTransactionEntity ADD COLUMN installmentTotalAmountCents INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN hiddenHomeCurrenciesCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE AppSettingsEntity SET hiddenHomeCurrenciesCsv = CASE " +
                        "WHEN showUsdOnHome = 0 AND showCryptoOnHome = 0 THEN 'USD,CRYPTO' " +
                        "WHEN showUsdOnHome = 0 THEN 'USD' " +
                        "WHEN showCryptoOnHome = 0 THEN 'CRYPTO' ELSE '' END"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE AppSettingsEntity ADD COLUMN showFinanceTips INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PlannedTransactionEntity ADD COLUMN balanceAppliedThroughEpochDay INTEGER DEFAULT NULL")
            }
        }
    }
}
