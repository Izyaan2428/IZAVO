package com.izavo.app.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query(
        """
        SELECT *
        FROM expenses
        ORDER BY occurredAt DESC
        """
    )
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE currencyCode = :currencyCode
          AND occurredAt >= :start
          AND occurredAt < :end
          AND amountMinor > 0
        ORDER BY occurredAt ASC
        """
    )
    fun observeExpensesForStatistics(
        currencyCode: String,
        start: Long,
        end: Long
    ): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT currencyCode,
               MIN(occurredAt) AS firstOccurredAt,
               MAX(occurredAt) AS lastOccurredAt,
               COUNT(*) AS transactionCount
        FROM expenses
        WHERE amountMinor > 0
        GROUP BY currencyCode
        ORDER BY currencyCode
        """
    )
    fun observeCurrencyCoverage(): Flow<List<CurrencyCoverage>>

    @Query(
        """
        SELECT currencyCode, COALESCE(SUM(amountMinor), 0) AS totalMinor
        FROM expenses
        WHERE occurredAt >= :start
        AND occurredAt < :end
        GROUP BY currencyCode
        """
    )
    fun observeTotalBetween(
        start: Long,
        end: Long
    ): Flow<List<CurrencyTotal>>

    @Query(
        """
        SELECT
            CASE
                WHEN category IS NULL OR TRIM(category) = '' THEN 'Other'
                ELSE category
            END AS category,
            currencyCode,
            COALESCE(SUM(amountMinor), 0) AS totalMinor
        FROM expenses
        WHERE occurredAt >= :start
          AND occurredAt < :end
        GROUP BY
            CASE
                WHEN category IS NULL OR TRIM(category) = '' THEN 'Other'
                ELSE category
            END,
            currencyCode
        """
    )
    fun observeCategoryTotalsBetween(
        start: Long,
        end: Long
    ): Flow<List<CategoryTotal>>
}
