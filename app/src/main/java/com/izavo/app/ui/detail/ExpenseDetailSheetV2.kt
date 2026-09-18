package com.izavo.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.formatCurrency
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import com.izavo.app.ui.design.HairlineDivider
import com.izavo.app.ui.design.amountNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.izavo.app.ui.format.TransactionTimeFormatter
import com.izavo.app.ui.design.ExpenseCategoryVisuals
import com.izavo.app.ui.design.MoneyDisplay
import com.izavo.app.ui.design.premiumClick
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailBottomSheet(
    expense: ExpenseEntity,
    showSeconds: Boolean = false,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val category = expense.category?.takeIf(String::isNotBlank) ?: "Other"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ExpenseColors.Glass,
        scrimColor = ExpenseColors.Scrim,
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet),
        dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = 520.dp).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(.105f).height(4.dp)
                    .background(ExpenseColors.InkSecondary.copy(alpha = .75f), RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(32.dp))
            MoneyDisplay(expense.amountMinor, expense.currencyCode, hero = true, horizontalAlignment = Alignment.CenterHorizontally)
            Spacer(Modifier.height(31.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val visual = ExpenseCategoryVisuals.forKey(category)
                Icon(visual.icon, null, Modifier.size(18.dp), tint = visual.color)
                Column(Modifier.padding(start = 12.dp)) { DetailBlock("Category", category) }
            }
            HairlineDivider()
            DetailBlock("Date", detailDate(expense, showSeconds))
            HairlineDivider()
            if (expense.note.isNotBlank()) {
                DetailBlock("Note", expense.note, contentDirection = true)
            } else {
                DetailBlock("Note", "No note")
            }
            Spacer(Modifier.weight(1f))
            Surface(modifier = Modifier.fillMaxWidth().height(50.dp).premiumClick(onClick = onEdit),
                shape = RoundedCornerShape(18.dp), color = ExpenseColors.SurfaceQuiet.copy(alpha = .85f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Edit", color = ExpenseColors.Ink, style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = ExpenseColors.Danger)) {
                Text("Delete Expense", fontWeight = FontWeight.Normal)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete expense?") },
            text = { Text("${formatCurrency(expense.amountMinor, expense.currencyCode)} will be permanently removed. This cannot be undone.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = ExpenseColors.Danger)) {
                Text("Delete", fontWeight = FontWeight.Bold)
            } }
        )
    }
}

@Composable
private fun DetailBlock(label: String, value: String, contentDirection: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(3.dp))
        Text(value, color = ExpenseColors.Ink,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDirection = if (contentDirection) TextDirection.ContentOrLtr else TextDirection.Ltr
            ), textAlign = TextAlign.Start)
    }
}

private fun detailDate(expense: ExpenseEntity, showSeconds: Boolean): String =
    TransactionTimeFormatter.dateTime(expense.occurredAt, showSeconds)
