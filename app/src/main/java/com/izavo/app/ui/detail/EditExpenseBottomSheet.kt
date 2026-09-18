package com.izavo.app.ui.detail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.HOME_CURRENCY_CODE
import com.izavo.app.data.SUPPORTED_CURRENCY_CODES
import com.izavo.app.ui.quickadd.DateTimeEditor
import com.izavo.app.ui.quickadd.QUICK_ADD_CATEGORIES
import com.izavo.app.ui.quickadd.QuickChoiceRow
import com.izavo.app.ui.quickadd.formatNowLabel
import com.izavo.app.ui.quickadd.isAllowedAmountInput
import com.izavo.app.ui.quickadd.isValidAmount
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyEditExpenseBottomSheet(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onSave: (
        amount: String,
        currencyCode: String,
        category: String?,
        note: String,
        dateTime: LocalDateTime
    ) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val maximumSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.60f
    val focusRequester = remember { FocusRequester() }

    var amount by remember(expense.id) { mutableStateOf(amountForInput(expense.amountMinor)) }
    var selectedCurrency by remember(expense.id) {
        mutableStateOf(expense.currencyCode.ifBlank { HOME_CURRENCY_CODE })
    }
    var selectedCategory by remember(expense.id) { mutableStateOf(expense.category) }
    var note by remember(expense.id) { mutableStateOf(expense.note) }
    var showNote by remember(expense.id) { mutableStateOf(expense.note.isNotBlank()) }
    var expenseDateTime by remember(expense.id) {
        mutableStateOf(
            Instant.ofEpochMilli(expense.occurredAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        )
    }
    var showDateTimeControls by remember { mutableStateOf(false) }
    val amountIsValid = isValidAmount(amount)

    LaunchedEffect(expense.id) {
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ExpenseColors.Surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f),
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = maximumSheetHeight)
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Edit Expense",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text("Amount", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(selectedCurrency, color = ExpenseColors.Cyan, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 7.dp, end = 12.dp))
                BasicTextField(
                value = amount,
                onValueChange = { if (isAllowedAmountInput(it)) amount = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.displayLarge.copy(color = ExpenseColors.Ink, fontSize = 40.sp,
                    fontFeatureSettings = "tnum"), cursorBrush = SolidColor(ExpenseColors.Cyan),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            QuickChoiceRow(
                choices = SUPPORTED_CURRENCY_CODES,
                selectedChoice = selectedCurrency,
                allowNoSelection = false,
                onChoiceSelected = { selectedCurrency = it ?: HOME_CURRENCY_CODE }
            )
            Spacer(modifier = Modifier.height(12.dp))

            QuickChoiceRow(
                choices = QUICK_ADD_CATEGORIES,
                selectedChoice = selectedCategory,
                allowNoSelection = true,
                onChoiceSelected = { selectedCategory = it }
            )
            Spacer(modifier = Modifier.height(10.dp))

            TextButton(onClick = { showDateTimeControls = !showDateTimeControls }) {
                Text(formatNowLabel(expenseDateTime, showDateTimeControls))
            }

            if (showDateTimeControls) {
                DateTimeEditor(
                    dateTime = expenseDateTime,
                    onDateClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                expenseDateTime = expenseDateTime.withYear(year)
                                    .withMonth(month + 1).withDayOfMonth(day)
                            },
                            expenseDateTime.year,
                            expenseDateTime.monthValue - 1,
                            expenseDateTime.dayOfMonth
                        ).show()
                    },
                    onTimeClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                expenseDateTime = expenseDateTime.withHour(hour).withMinute(minute)
                            },
                            expenseDateTime.hour,
                            expenseDateTime.minute,
                            false
                        ).show()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            TextButton(onClick = { showNote = !showNote }) {
                Text(if (showNote) "Hide note" else "Add a note", color = ExpenseColors.InkSecondary)
            }
            AnimatedVisibility(showNote) {
                OutlinedTextField(value = note, onValueChange = { note = it }, placeholder = { Text("Optional note") },
                    minLines = 2, maxLines = 3, shape = RoundedCornerShape(ExpenseShapes.Control),
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(14.dp))

            ExpensePrimaryButton(
                text = "Save Changes",
                enabled = amountIsValid,
                onClick = {
                    keyboardController?.hide()
                    onSave(
                        amount,
                        selectedCurrency,
                        selectedCategory,
                        note.trim(),
                        expenseDateTime
                    )
                }
            )
        }
    }
}

private fun amountForInput(amountMinor: Long): String {
    return BigDecimal.valueOf(amountMinor, 2).toPlainString()
}
