package com.izavo.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.SUPPORTED_CURRENCY_CODES
import com.izavo.app.data.formatCurrency
import com.izavo.app.ui.quickadd.QUICK_ADD_CATEGORIES
import com.izavo.app.ui.quickadd.QuickChoiceRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseEmptyState
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import com.izavo.app.ui.design.ExpenseTransactionRow
import com.izavo.app.ui.design.QuietChoice
import com.izavo.app.ui.design.premiumClick
import com.izavo.app.ui.design.IzavoMaterial
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun HistorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filtersActive: Boolean,
    onFilterClick: () -> Unit
) {
    var searchFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).height(48.dp)
                .background(ExpenseColors.SurfaceQuiet.copy(alpha = .9f), RoundedCornerShape(18.dp))
                .border(1.dp, if (searchFocused) ExpenseColors.Cyan.copy(alpha = .45f) else Color.Transparent, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = ExpenseColors.InkSecondary,
                modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ExpenseColors.Ink),
                cursorBrush = SolidColor(ExpenseColors.Cyan),
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search expenses", color = ExpenseColors.InkSecondary,
                            style = MaterialTheme.typography.bodyLarge)
                        field()
                    }
                },
                modifier = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused }
            )
        }

        Surface(
            modifier = Modifier.size(48.dp).premiumClick(onClick = onFilterClick),
            shape = RoundedCornerShape(18.dp),
            color = if (filtersActive) ExpenseColors.CyanSoft else ExpenseColors.SurfaceQuiet,
            contentColor = if (filtersActive) ExpenseColors.Cyan else ExpenseColors.InkSecondary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.FilterList, contentDescription = "Filter expenses")
            }
        }
    }
}

@Composable
fun HistoryTransactionRow(
    expense: ExpenseEntity,
    showSeconds: Boolean = false,
    onClick: () -> Unit
) {
    ExpenseTransactionRow(expense, onClick, showDivider = true, rowHeight = 70.dp, showSeconds = showSeconds)
}

@Composable
fun HistoryEmptyState(title: String, message: String) {
    ExpenseEmptyState(title, message)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HistoryFilterSheet(
    currentFilters: HistoryFilterState,
    onDismiss: () -> Unit,
    onApply: (HistoryFilterState) -> Unit
) {
    var draft by remember(currentFilters) { mutableStateOf(currentFilters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ExpenseColors.Glass,
        scrimColor = ExpenseColors.Scrim,
        shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = HistoryFilterState() }) {
                    Text("Clear", color = ExpenseColors.Cyan)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            FilterLabel("CATEGORY")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietChoice("All", draft.category == null) { draft = draft.copy(category = null) }
                QUICK_ADD_CATEGORIES.forEach { value ->
                    QuietChoice(value, draft.category == value) { draft = draft.copy(category = value) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FilterLabel("CURRENCY")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietChoice("All", draft.currencyCode == null) { draft = draft.copy(currencyCode = null) }
                SUPPORTED_CURRENCY_CODES.forEach { value ->
                    QuietChoice(value, draft.currencyCode == value) { draft = draft.copy(currencyCode = value) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            FilterLabel("PERIOD")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryDateFilter.entries.forEach { dateFilter ->
                    QuietChoice(
                        label = dateFilter.label,
                        selected = draft.dateFilter == dateFilter,
                        onClick = { draft = draft.copy(dateFilter = dateFilter) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            ExpensePrimaryButton("Apply", onClick = { onApply(draft) })
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(text.lowercase().replaceFirstChar { it.uppercase() }, color = ExpenseColors.InkSecondary,
        style = MaterialTheme.typography.bodySmall)
    Spacer(modifier = Modifier.height(7.dp))
}

private fun historyTime(expense: ExpenseEntity): String =
    Instant.ofEpochMilli(expense.occurredAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

private fun historyCategoryIcon(category: String): ImageVector = when (category) {
    "Food" -> Icons.Outlined.Restaurant
    "Transport" -> Icons.Outlined.DirectionsCar
    "Shopping" -> Icons.Outlined.ShoppingBag
    "Bills" -> Icons.Outlined.ReceiptLong
    else -> Icons.Outlined.MoreHoriz
}
