package com.izavo.app.ui.detail

import androidx.compose.runtime.Composable
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.HOME_CURRENCY_CODE
import com.izavo.app.data.SUPPORTED_CURRENCY_CODES
import com.izavo.app.ui.quickadd.ExpenseFormSheet
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun EditExpenseBottomSheet(
    expense: ExpenseEntity,
    showSeconds: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, LocalDateTime) -> Unit
) {
    ExpenseFormSheet(
        title = "Edit Expense",
        actionLabel = "Save Changes",
        currencyChoices = SUPPORTED_CURRENCY_CODES,
        initialAmount = BigDecimal.valueOf(expense.amountMinor, 2).toPlainString(),
        initialCurrency = expense.currencyCode.ifBlank { HOME_CURRENCY_CODE },
        initialCategory = expense.category,
        initialNote = expense.note,
        initialDateTime = Instant.ofEpochMilli(expense.occurredAt)
            .atZone(ZoneId.systemDefault()).toLocalDateTime(),
        showSeconds = showSeconds,
        topActionLabel = "Cancel",
        onTopAction = onDismiss,
        onDismiss = onDismiss,
        onSave = onSave
    )
}
