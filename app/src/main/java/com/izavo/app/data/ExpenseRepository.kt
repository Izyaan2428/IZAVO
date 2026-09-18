package com.izavo.app.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao
) {

    fun observeAllExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.observeAllExpenses()
    }

    fun observeTotalBetween(
        start: Long,
        end: Long
    ): Flow<List<CurrencyTotal>> {
        return expenseDao.observeTotalBetween(start, end)
    }

    fun observeCategoryTotalsBetween(
        start: Long,
        end: Long
    ): Flow<List<CategoryTotal>> {
        return expenseDao.observeCategoryTotalsBetween(start, end)
    }

    suspend fun addExpense(expense: ExpenseEntity): ExpenseEntity =
        expense.copy(id = expenseDao.insertExpense(expense))

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
    }

    fun observeExpensesForStatistics(currencyCode: String): Flow<List<ExpenseEntity>> =
        expenseDao.observeExpensesForStatistics(currencyCode, 0L, Long.MAX_VALUE)

    fun observeCurrencyCoverage(): Flow<List<CurrencyCoverage>> =
        expenseDao.observeCurrencyCoverage()

    suspend fun updateExpense(expense: ExpenseEntity) {
        expenseDao.updateExpense(expense)
    }
}
