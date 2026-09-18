package com.izavo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.izavo.app.data.CategoryTotal
import com.izavo.app.data.CurrencyTotal
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.ExpenseRepository
import com.izavo.app.data.HOME_CURRENCY_CODE
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ExpenseViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val zoneId = ZoneId.systemDefault()

    private val today = LocalDate.now()

    private val startOfToday =
        today
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    private val startOfTomorrow =
        today
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    private val firstDayOfMonth =
        today.withDayOfMonth(1)

    private val startOfMonth =
        firstDayOfMonth
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    private val startOfNextMonth =
        firstDayOfMonth
            .plusMonths(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    val todayTotals =
        repository
            .observeTotalBetween(
                startOfToday,
                startOfTomorrow
            )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList<CurrencyTotal>()
            )

    val monthTotals =
        repository
            .observeTotalBetween(
                startOfMonth,
                startOfNextMonth
            )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList<CurrencyTotal>()
            )

    val monthCategoryTotals =
        repository
            .observeCategoryTotalsBetween(
                startOfMonth,
                startOfNextMonth
            )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList<CategoryTotal>()
            )

    val expenses =
        repository
            .observeAllExpenses()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun addExpense(
        amount: String,
        currencyCode: String,
        category: String?,
        note: String,
        dateTime: LocalDateTime,
        onInserted: (ExpenseEntity) -> Unit = { }
    ) {
        val amountMinor =
            amountToMinorUnits(amount) ?: return

        val occurredAt =
            dateTime
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        val expense =
            ExpenseEntity(
                amountMinor = amountMinor,
                currencyCode = currencyCode.ifBlank { HOME_CURRENCY_CODE },
                category = category,
                note = note,
                occurredAt = occurredAt
            )

        viewModelScope.launch { onInserted(repository.addExpense(expense)) }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun updateExpense(
        originalExpense: ExpenseEntity,
        amount: String,
        currencyCode: String,
        category: String?,
        note: String,
        dateTime: LocalDateTime
    ) {
        val amountMinor = amountToMinorUnits(amount) ?: return
        val occurredAt = dateTime.atZone(zoneId).toInstant().toEpochMilli()

        val updatedExpense = originalExpense.copy(
            amountMinor = amountMinor,
            currencyCode = currencyCode.ifBlank { HOME_CURRENCY_CODE },
            category = category,
            note = note,
            occurredAt = occurredAt
        )

        viewModelScope.launch {
            repository.updateExpense(updatedExpense)
        }
    }

    private fun amountToMinorUnits(
        amount: String
    ): Long? {
        return try {
            BigDecimal(amount)
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (exception: Exception) {
            null
        }
    }
}
