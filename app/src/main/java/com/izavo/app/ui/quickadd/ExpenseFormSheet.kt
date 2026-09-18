package com.izavo.app.ui.quickadd

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import java.time.LocalDateTime
import com.izavo.app.ui.design.premiumClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormSheet(
    title: String,
    actionLabel: String,
    currencyChoices: List<String>,
    initialAmount: String,
    initialCurrency: String,
    initialCategory: String?,
    initialNote: String,
    initialDateTime: LocalDateTime,
    showSeconds: Boolean = false,
    topActionLabel: String? = null,
    onTopAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, LocalDateTime) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val noteFocusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = minOf(if (imeVisible) 444.dp else 508.dp, screenHeight * if (imeVisible) .74f else .60f)

    var amount by remember(initialAmount) { mutableStateOf(initialAmount) }
    var currency by remember(initialCurrency) { mutableStateOf(initialCurrency) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var noteExpanded by remember(initialNote) { mutableStateOf(initialNote.isNotBlank()) }
    var noteFocusRequest by remember { mutableStateOf(0) }
    var dateTime by remember(initialDateTime) { mutableStateOf(initialDateTime) }
    var dateExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val amountValid = isValidAmount(amount)
    val dismissTextInput = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        Unit
    }

    val save = {
        if (amountValid && !submitting) {
            submitting = true
            keyboard?.hide()
            onSave(amount, currency, category, note.trim(), dateTime)
        }
    }

    LaunchedEffect(noteFocusRequest) {
        if (noteFocusRequest > 0) {
            noteFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    ModalBottomSheet(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ExpenseColors.Glass,
        contentColor = ExpenseColors.Ink,
        scrimColor = ExpenseColors.Scrim,
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet),
        dragHandle = null
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismissTextInput
                )
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(11.dp))
            Box(Modifier.width(38.dp).height(4.dp).background(ExpenseColors.InkSecondary.copy(alpha = .75f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth()) {
                Text(title, color = ExpenseColors.Ink, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.CenterStart))
                if (topActionLabel != null && onTopAction != null) {
                    TextButton(onClick = onTopAction, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(topActionLabel, color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box {
                Row(
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                        dismissTextInput()
                        currencyExpanded = true
                    }
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currency, color = ExpenseColors.InkSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Outlined.ExpandMore, null, tint = ExpenseColors.InkSecondary, modifier = Modifier.width(17.dp))
                }
                DropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = ExpenseColors.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, ExpenseColors.Divider)
                ) {
                    currencyChoices.distinct().forEach { code ->
                        val selected = code == currency
                        DropdownMenuItem(
                            text = {
                                Text(
                                    code,
                                    color = if (selected) ExpenseColors.CyanSelected else ExpenseColors.Ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (selected) Text("✓", color = ExpenseColors.CyanSelected)
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.height(44.dp),
                            onClick = { currency = code; currencyExpanded = false }
                        )
                    }
                }
            }
            BasicTextField(
                value = amount,
                onValueChange = { if (isAllowedAmountInput(it)) amount = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                singleLine = true,
                textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp, lineHeight = 54.sp,
                    color = ExpenseColors.Ink, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                cursorBrush = SolidColor(ExpenseColors.Cyan),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (amount.isEmpty()) Text("0.00", color = ExpenseColors.InkTertiary,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp, textAlign = TextAlign.Center))
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Tap currency to change", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(if (imeVisible) 12.dp else 22.dp))
            Text("Category", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            QuickChoiceRow(QUICK_ADD_CATEGORIES, category, true) {
                dismissTextInput()
                if (it != category) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                category = it
            }
            Spacer(Modifier.height(if (imeVisible) 10.dp else 18.dp))
            MetadataRow(formatNowLabel(dateTime, dateExpanded, showSeconds)) {
                dismissTextInput()
                dateExpanded = !dateExpanded
            }
            AnimatedVisibility(dateExpanded) {
                DateTimeEditor(
                    dateTime = dateTime,
                    showSeconds = showSeconds,
                    onDateClick = {
                        DatePickerDialog(context, { _, year, month, day ->
                            dateTime = dateTime.withYear(year).withMonth(month + 1).withDayOfMonth(day)
                        }, dateTime.year, dateTime.monthValue - 1, dateTime.dayOfMonth).show()
                    },
                    onTimeClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            dateTime = dateTime.withHour(hour).withMinute(minute)
                        }, dateTime.hour, dateTime.minute, false).show()
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            MetadataRow(if (noteExpanded) "Hide note" else "Add a note") {
                if (noteExpanded) {
                    noteExpanded = false
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                } else {
                    noteExpanded = true
                    noteFocusRequest += 1
                }
            }
            AnimatedVisibility(noteExpanded) {
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, placeholder = { Text("Optional note") },
                    minLines = 2, maxLines = 3, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocusRequester)
                )
            }
            Spacer(Modifier.height(if (imeVisible) 10.dp else 18.dp))
            ExpensePrimaryButton(actionLabel, amountValid && !submitting) { save() }
        }
    }
}

@Composable
private fun MetadataRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .background(ExpenseColors.Surface.copy(alpha = .32f), RoundedCornerShape(16.dp))
            .premiumClick(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text("›", color = ExpenseColors.InkTertiary, fontSize = 21.sp)
    }
}
