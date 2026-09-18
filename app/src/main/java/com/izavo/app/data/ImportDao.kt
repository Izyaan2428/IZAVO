package com.izavo.app.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

data class ImportPersistenceRow(
    val transaction: ImportedBankTransactionEntity,
    val expense: ExpenseEntity?
)

data class ImportPersistenceResult(val expenseCount: Int, val storedTransactionCount: Int)

@Dao
interface ImportDao {
    @Query(
        """
        SELECT linkedExpenseId AS expenseId,
               normalizedIdentity,
               rawDescription
        FROM imported_bank_transactions
        WHERE linkedExpenseId IS NOT NULL
        """
    )
    fun observeExpenseMerchantMetadata(): Flow<List<ExpenseMerchantMetadata>>
    @Query("SELECT fingerprint FROM imported_bank_transactions WHERE fingerprint IN (:fingerprints)")
    suspend fun findExistingFingerprints(fingerprints: List<String>): List<String>

    @Query("SELECT * FROM merchant_rules WHERE sourceBank = :sourceBank AND normalizedIdentity IN (:identities)")
    suspend fun findMerchantRules(sourceBank: String, identities: List<String>): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantRules(rules: List<MerchantRuleEntity>)

    @Insert
    suspend fun insertBatch(batch: ImportBatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImportedTransactions(transactions: List<ImportedBankTransactionEntity>): List<Long>

    @Insert
    suspend fun insertExpenses(expenses: List<ExpenseEntity>): List<Long>

    @Transaction
    suspend fun persistBatch(batch: ImportBatchEntity, rows: List<ImportPersistenceRow>, rules: List<MerchantRuleEntity>): ImportPersistenceResult {
        if (rules.isNotEmpty()) upsertMerchantRules(rules)
        val batchId = insertBatch(batch)
        val expenseRows = rows.filter { it.expense != null }
        val expenseIds = if (expenseRows.isEmpty()) emptyList() else insertExpenses(expenseRows.map { it.expense!! })
        var expenseIndex = 0
        val ledgers = rows.map { row ->
            val linkedId = if (row.expense != null) expenseIds[expenseIndex++] else null
            row.transaction.copy(importBatchId = batchId, linkedExpenseId = linkedId)
        }
        val ids = if (ledgers.isEmpty()) emptyList() else insertImportedTransactions(ledgers)
        // Preparation normally removes duplicates. If a concurrent/repeated import still
        // reaches this point, fail the Room transaction so its batch, rules and expenses
        // are rolled back together instead of committing an unlinked duplicate expense.
        check(ids.none { it == -1L }) { "An imported transaction already exists" }
        return ImportPersistenceResult(expenseIds.size, ids.count { it != -1L })
    }
}
