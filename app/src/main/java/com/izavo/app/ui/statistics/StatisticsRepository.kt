package com.izavo.app.ui.statistics

import com.izavo.app.data.ExpenseRepository
import com.izavo.app.data.ImportDao

class StatisticsRepository(
    private val expenses: ExpenseRepository,
    private val importDao: ImportDao
) {
    fun observeExpenses(currencyCode: String) = expenses.observeExpensesForStatistics(currencyCode)
    fun observeCurrencyCoverage() = expenses.observeCurrencyCoverage()
    fun observeMerchantMetadata() = importDao.observeExpenseMerchantMetadata()
}
