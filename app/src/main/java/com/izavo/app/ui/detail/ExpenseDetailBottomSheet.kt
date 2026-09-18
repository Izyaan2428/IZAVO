package com.izavo.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.formatCurrency
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import com.izavo.app.ui.design.amountNumber
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyExpenseDetailBottomSheet(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val category = expense.category?.takeIf { it.isNotBlank() } ?: "Other"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ExpenseColors.Surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = amountNumber(expense.amountMinor),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, fontFeatureSettings = "tnum"),
                color = ExpenseColors.Ink,
                fontWeight = FontWeight.Medium
            )
            Text(expense.currencyCode, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(28.dp))
            DetailLine("Category", category)
            DetailLine("Date & time", formatExpenseDate(expense))
            if (expense.note.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("Note", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(5.dp))
                Text(expense.note, color = ExpenseColors.Ink,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr))
            }
            Spacer(modifier = Modifier.height(28.dp))
            FilledTonalButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = ExpenseColors.SurfaceQuiet,
                    contentColor = ExpenseColors.Ink)
            ) {
                Text("Edit", fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { showDeleteConfirmation = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Expense", fontWeight = FontWeight.Normal)
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete expense?") },
            text = {
                Text(
                    "${formatCurrency(expense.amountMinor, expense.currencyCode)} will be " +
                        "permanently removed. This cannot be undone."
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = ExpenseColors.Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatExpenseDate(expense: ExpenseEntity): String =
    Instant.ofEpochMilli(expense.occurredAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))
