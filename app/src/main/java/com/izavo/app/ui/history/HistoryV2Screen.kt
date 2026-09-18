package com.izavo.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.QuietDivider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryV2Screen(
    expenses: List<ExpenseEntity>,
    showSeconds: Boolean = false,
    onExpenseClick: (ExpenseEntity) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(HistoryFilterState()) }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(showFilters) {
        if (!showFilters) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    val filteredExpenses = remember(expenses, query, filters) {
        expenses.filter { expense ->
            expenseMatchesQuery(expense, query) && expenseMatchesFilters(expense, filters)
        }
    }
    val groupedExpenses = remember(filteredExpenses) {
        filteredExpenses.groupBy(::expenseLocalDate)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ExpenseColors.Background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "History",
            color = ExpenseColors.Ink,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(18.dp))
        HistorySearchBar(
            query = query,
            onQueryChange = { query = it },
            filtersActive = filters.isActive,
            onFilterClick = {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                showFilters = true
            }
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp)
        ) {
            if (filteredExpenses.isEmpty()) {
                item {
                    val emptyCopy = historyEmptyCopy(
                        hasExpenses = expenses.isNotEmpty(),
                        hasSearch = query.isNotBlank(),
                        hasFilters = filters.isActive
                    )
                    HistoryEmptyState(emptyCopy.first, emptyCopy.second)
                }
            } else {
                groupedExpenses.forEach { (date, dateExpenses) ->
                    item(key = "header-$date") {
                        Text(
                            text = formatHistoryDate(date),
                            color = ExpenseColors.InkSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp)
                        )
                    }
                    items(dateExpenses, key = { it.id }) { expense ->
                        HistoryTransactionRow(
                            expense = expense,
                            showSeconds = showSeconds,
                            onClick = { onExpenseClick(expense) }
                        )
                    }
                    item(key = "space-$date") {
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }
    }

    if (showFilters) {
        HistoryFilterSheet(
            currentFilters = filters,
            onDismiss = {
                showFilters = false
            },
            onApply = {
                filters = it
                showFilters = false
            }
        )
    }
}

private fun expenseMatchesQuery(expense: ExpenseEntity, query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return true

    val category = expense.category?.takeIf { it.isNotBlank() } ?: "Other"
    return expense.note.contains(normalizedQuery, ignoreCase = true) ||
        category.contains(normalizedQuery, ignoreCase = true) ||
        expense.currencyCode.contains(normalizedQuery, ignoreCase = true)
}

private fun expenseMatchesFilters(
    expense: ExpenseEntity,
    filters: HistoryFilterState
): Boolean {
    val category = expense.category?.takeIf { it.isNotBlank() } ?: "Other"
    if (filters.category != null && category != filters.category) return false
    if (filters.currencyCode != null && expense.currencyCode != filters.currencyCode) return false

    val expenseDate = expenseLocalDate(expense)
    val today = LocalDate.now()
    return when (filters.dateFilter) {
        HistoryDateFilter.ALL_TIME -> true
        HistoryDateFilter.TODAY -> expenseDate == today
        HistoryDateFilter.THIS_MONTH ->
            expenseDate.year == today.year && expenseDate.month == today.month
    }
}

private fun expenseLocalDate(expense: ExpenseEntity): LocalDate =
    Instant.ofEpochMilli(expense.occurredAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

private fun formatHistoryDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))
    }
}

private fun historyEmptyCopy(
    hasExpenses: Boolean,
    hasSearch: Boolean,
    hasFilters: Boolean
): Pair<String, String> = when {
    !hasExpenses -> "No expenses yet" to "Add your first expense in seconds."
    hasFilters -> "No expenses match these filters" to "Adjust or clear your current filters."
    hasSearch -> "No matching expenses" to "Try a different note, category, or currency."
    else -> "Nothing to show" to "Your expenses will appear here."
}
