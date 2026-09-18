package com.izavo.app

import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.izavo.app.data.ExpenseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "migration-1-to-4-test"

    @Before fun cleanBefore() { context.deleteDatabase(databaseName) }
    @After fun cleanAfter() { context.deleteDatabase(databaseName) }

    @Test
    fun migration1To4PreservesExpenseAndAddsCurrentSchema() = runBlocking {
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL(
                """CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    amountMinor INTEGER NOT NULL,
                    category TEXT,
                    note TEXT NOT NULL,
                    occurredAt INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )""".trimIndent()
            )
            legacy.execSQL("INSERT INTO expenses(amountMinor, category, note, occurredAt, createdAt) VALUES(1250, 'Food', 'Legacy', 1000, 1000)")
            legacy.version = 1
        }

        val database = Room.databaseBuilder(context, ExpenseDatabase::class.java, databaseName)
            .addMigrations(ExpenseDatabase.MIGRATION_1_2, ExpenseDatabase.MIGRATION_2_3, ExpenseDatabase.MIGRATION_3_4)
            .build()
        val expenses = database.expenseDao().observeAllExpenses().first()
        assertEquals(1, expenses.size)
        assertEquals("MVR", expenses.single().currencyCode)
        assertEquals(1250L, expenses.single().amountMinor)
        assertEquals(emptyList<com.izavo.app.data.MerchantRuleEntity>(), database.importDao().findMerchantRules("BML", listOf("NONE")))
        database.close()
    }
}
