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
    version = 5,
    exportSchema = false
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
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
    }
}
