package com.izavo.app.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

@Database(
    entities = [ExpenseEntity::class, ImportBatchEntity::class, ImportedBankTransactionEntity::class, MerchantRuleEntity::class],
    version = 4,
    exportSchema = true
)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun importDao(): ImportDao

    companion object {

        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        fun getDatabase(context: Context): ExpenseDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_tracker_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        internal val MIGRATION_1_2 = Migration(1, 2) { connection ->
            val alreadyHasCurrencyCode =
                connection.prepare("PRAGMA table_info(expenses)").use { statement ->
                    var found = false
                    while (statement.step()) {
                        // PRAGMA table_info column 1 is the column name.
                        if (statement.getText(1) == "currencyCode") {
                            found = true
                            break
                        }
                    }
                    found
                }

            if (!alreadyHasCurrencyCode) {
                connection.execSQL(
                    "ALTER TABLE expenses ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'MVR'"
                )
            }
        }

        internal val MIGRATION_2_3 = Migration(2, 3) { connection ->
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS import_batches (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceBank TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    importedAt INTEGER NOT NULL,
                    totalRows INTEGER NOT NULL,
                    invalidRows INTEGER NOT NULL
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS imported_bank_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceBank TEXT NOT NULL,
                    primaryReference TEXT,
                    secondaryReference TEXT,
                    postedAt INTEGER NOT NULL,
                    valueAt INTEGER,
                    transactionAt INTEGER,
                    rawType TEXT NOT NULL,
                    rawDescription TEXT NOT NULL,
                    rawDetail TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    amountMinor INTEGER NOT NULL,
                    currencyCode TEXT NOT NULL,
                    runningBalanceMinor INTEGER,
                    normalizedType TEXT NOT NULL,
                    reviewStatus TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    importBatchId INTEGER NOT NULL,
                    importedAt INTEGER NOT NULL,
                    linkedExpenseId INTEGER
                )
                """.trimIndent()
            )
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_imported_bank_transactions_fingerprint ON imported_bank_transactions(fingerprint)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_imported_bank_transactions_importBatchId ON imported_bank_transactions(importBatchId)")
        }

        internal val MIGRATION_3_4 = Migration(3, 4) { connection ->
            connection.execSQL("ALTER TABLE imported_bank_transactions ADD COLUMN normalizedIdentity TEXT")
            connection.execSQL("ALTER TABLE imported_bank_transactions ADD COLUMN classificationConfidence TEXT NOT NULL DEFAULT 'LOW'")
            connection.execSQL("ALTER TABLE imported_bank_transactions ADD COLUMN categoryConfidence TEXT")
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS merchant_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceBank TEXT NOT NULL,
                    normalizedIdentity TEXT NOT NULL,
                    bankRail TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    classification TEXT NOT NULL,
                    category TEXT,
                    source TEXT NOT NULL,
                    confidence TEXT NOT NULL,
                    userConfirmed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchant_rules_sourceBank_normalizedIdentity_bankRail_direction ON merchant_rules(sourceBank, normalizedIdentity, bankRail, direction)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS index_merchant_rules_normalizedIdentity ON merchant_rules(normalizedIdentity)")
        }
    }
}
