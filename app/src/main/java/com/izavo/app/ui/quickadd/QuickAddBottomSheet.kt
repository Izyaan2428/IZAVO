package com.izavo.app.ui.quickadd

import androidx.compose.runtime.Composable
import java.time.LocalDateTime

@Composable
fun QuickAddBottomSheet(
    defaultCurrency: String,
    quickCurrencies: List<String>,
    showSeconds: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, LocalDateTime) -> Unit
) {
    ExpenseFormSheet(
        title = "Add Expense",
        actionLabel = "Add Expense",
        currencyChoices = (listOf(defaultCurrency) + quickCurrencies).distinct(),
        initialAmount = "",
        initialCurrency = defaultCurrency,
        initialCategory = null,
        initialNote = "",
        initialDateTime = LocalDateTime.now(),
        showSeconds = showSeconds,
        onDismiss = onDismiss,
        onSave = onSave
    )
}
