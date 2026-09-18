package com.izavo.app.ui.importing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.izavo.app.importing.*
import com.izavo.app.ui.design.*
import com.izavo.app.ui.format.TransactionTimeFormatter
import java.math.BigDecimal

private val smartCategories = com.izavo.app.data.ExpenseCategories.all

@Composable
fun LegacySmartImportFlowScreen(
    state: ImportUiState,
    showSeconds: Boolean,
    onBack: () -> Unit,
    onResolveGroup: (String, ImportDecision, String?, Boolean) -> Unit,
    onRemember: (String, Boolean) -> Unit,
    onReopen: (String) -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        ImportUiState.Idle -> Unit
        ImportUiState.Loading -> SmartImportMessage("Scanning statement…", true, onBack)
        is ImportUiState.Error -> SmartImportMessage(state.message, false, onBack)
        is ImportUiState.Complete -> SmartImportComplete(state.summary, onBack)
        is ImportUiState.Review -> SmartReview(state.prepared, showSeconds, onBack, onResolveGroup, onRemember, onReopen, onConfirm)
        is ImportUiState.ApplyingDecision -> SmartReview(state.prepared, showSeconds, onBack, onResolveGroup, onRemember, onReopen, onConfirm)
    }
}

@Composable
private fun SmartReview(prepared: PreparedImport, showSeconds: Boolean, onBack: () -> Unit,
    onResolve: (String, ImportDecision, String?, Boolean) -> Unit, onRemember: (String, Boolean) -> Unit,
    onReopen: (String) -> Unit, onConfirm: () -> Unit) {
    val unresolved = prepared.reviewGroups.filterNot(ReviewGroup::resolved)
    val automatic = prepared.reviewGroups.filter(ReviewGroup::resolved)
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var automaticExpanded by rememberSaveable { mutableStateOf(false) }
    val active = unresolved.firstOrNull { it.id == activeId }

    LazyColumn(Modifier.fillMaxSize().background(ExpenseColors.Background).padding(horizontal = 24.dp)) {
        item {
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ExpenseColors.InkSecondary) }
                Text("Review import", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(15.dp))
            Text("${prepared.items.size} transactions", style = MaterialTheme.typography.titleLarge)
            Text("${automatic.sumOf(ReviewGroup::transactionCount)} classified automatically", color = ExpenseColors.InkSecondary)
            Text("${unresolved.size} need your input", color = if (unresolved.isEmpty()) ExpenseColors.CyanSelected else ExpenseColors.Ink)
            if (prepared.invalidRowCount > 0) Text("${prepared.invalidRowCount} rows could not be read", color = ExpenseColors.Danger)
            Spacer(Modifier.height(13.dp))
            Text("Processed on-device. IZAVO does not connect to BML, request bank credentials, or upload this CSV.", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            if (unresolved.isNotEmpty()) ExpensePrimaryButton("Review ${unresolved.size}") { activeId = unresolved.first().id }
            else ExpensePrimaryButton("Import ${prepared.items.count { it.decision == ImportDecision.EXPENSE && it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }} expenses", onClick = onConfirm)
            Spacer(Modifier.height(24.dp))
        }
        if (active != null) item(key = "active-${active.id}") { SmartGroupEditor(active, showSeconds, onResolve, onRemember) { activeId = null }; Spacer(Modifier.height(18.dp)) }
        if (unresolved.isNotEmpty()) {
            item { SmartSection("Needs review", unresolved.size) }
            items(unresolved, key = ReviewGroup::id) { group ->
                SmartGroupSummary(group, showSeconds, if (group.confidence == ConfidenceLevel.MEDIUM) "Suggested: ${decisionLabel(group.suggestedDecision)}" else "What are these?") { activeId = group.id }
                QuietDivider()
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clickable { automaticExpanded = !automaticExpanded }.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Automatically classified", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${automatic.sumOf(ReviewGroup::transactionCount)} ${if (automaticExpanded) "▴" else "▾"}", color = ExpenseColors.InkSecondary)
            }
        }
        if (automaticExpanded) items(automatic, key = { "auto-${it.id}" }) { group ->
            SmartGroupSummary(group, showSeconds, "${group.suggestedCategory ?: decisionLabel(group.suggestedDecision)} · ${decisionLabel(group.suggestedDecision)}") {
                onReopen(group.id); activeId = group.id
            }
            QuietDivider()
        }
        if (prepared.duplicateCount > 0) item { SmartSection("Duplicates skipped", prepared.duplicateCount) }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun SmartGroupEditor(group: ReviewGroup, showSeconds: Boolean,
    onResolve: (String, ImportDecision, String?, Boolean) -> Unit, onRemember: (String, Boolean) -> Unit, onClose: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var decision by remember(group.id) { mutableStateOf(group.suggestedDecision) }
    var category by remember(group.id) { mutableStateOf(group.suggestedCategory ?: "Other") }
    var rememberRule by remember(group.id) { mutableStateOf(group.rememberEligible) }
    var details by remember(group.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = ExpenseColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(group.identity, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${group.transactionCount} transaction${if (group.transactionCount == 1) "" else "s"} · ${group.currencyCode} ${smartMoney(group.totalMinor)}", color = ExpenseColors.InkSecondary)
            Text("${group.bankRail.lowercase().replaceFirstChar(Char::uppercase)} ${group.direction.name.lowercase()}", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp)); Text("What are these?", style = MaterialTheme.typography.labelMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ImportDecision.entries.forEach { value -> QuietChoice(decisionLabel(value), decision == value) { decision = value } }
            }
            AnimatedVisibility(decision == ImportDecision.EXPENSE) { Column {
                Spacer(Modifier.height(13.dp)); Text("Category", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    smartCategories.forEach { value -> QuietChoice(value, category == value) { category = value } }
                }
            } }
            if (group.rememberEligible) Row(Modifier.fillMaxWidth().clickable { rememberRule = !rememberRule; onRemember(group.id, rememberRule) }.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rememberRule, onCheckedChange = { rememberRule = it; onRemember(group.id, it) }); Text("Remember for ${group.identity}", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (details) "Hide transactions" else "View transactions", color = ExpenseColors.Cyan, modifier = Modifier.clickable { details = !details }.padding(vertical = 10.dp))
            AnimatedVisibility(details) { Column { group.items.take(50).forEach { item ->
                Text("${TransactionTimeFormatter.dateTime(item.transaction.expenseTimestamp, showSeconds)} · ${item.transaction.currencyCode} ${smartMoney(item.transaction.amountMinor)}", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
            } } }
            Spacer(Modifier.height(8.dp)); ExpensePrimaryButton(if (decision == group.suggestedDecision) "Looks right" else "Apply to ${group.transactionCount}") {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onResolve(group.id, decision, if (decision == ImportDecision.EXPENSE) category else null, rememberRule); onClose()
            }
        }
    }
}

@Composable private fun SmartGroupSummary(group: ReviewGroup, showSeconds: Boolean, action: String, onClick: () -> Unit) {
    val first = group.items.first().transaction
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(group.identity, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (group.transactionCount > 1) "${group.transactionCount} transactions · ${group.bankRail.lowercase().replaceFirstChar(Char::uppercase)}" else "${group.bankRail.lowercase().replaceFirstChar(Char::uppercase)} · ${TransactionTimeFormatter.dateTime(first.expenseTimestamp, showSeconds)}", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(action, color = ExpenseColors.CyanSelected, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) { Text(smartMoney(group.totalMinor), fontWeight = FontWeight.Medium); Text(group.currencyCode, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable private fun SmartSection(title: String, count: Int) { Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 7.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(count.toString(), color = ExpenseColors.InkSecondary) } }
@Composable private fun SmartImportMessage(message: String, loading: Boolean, onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(ExpenseColors.Background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { if (loading) { CircularProgressIndicator(color = ExpenseColors.Cyan); Spacer(Modifier.height(18.dp)) }; Text(message, style = MaterialTheme.typography.titleLarge); if (!loading) { Spacer(Modifier.height(20.dp)); ExpensePrimaryButton("Back to Settings", onClick = onBack) } } }
@Composable private fun SmartImportComplete(summary: ImportResultSummary, onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(ExpenseColors.Background).padding(24.dp), verticalArrangement = Arrangement.Center) { Text("Import complete", style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(12.dp)); Text("${summary.expenseCount} expenses added", style = MaterialTheme.typography.titleLarge); Text("${summary.excludedCount} non-expense transactions recorded · ${summary.duplicateCount} duplicates skipped", color = ExpenseColors.InkSecondary); Spacer(Modifier.height(24.dp)); ExpensePrimaryButton("Done", onClick = onBack) } }
private fun decisionLabel(value: ImportDecision) = when (value) { ImportDecision.EXPENSE -> "Expense"; ImportDecision.MONEY_MOVEMENT -> "Money movement"; ImportDecision.INCOME -> "Income"; ImportDecision.REFUND -> "Refund"; ImportDecision.UNKNOWN -> "Not sure"; ImportDecision.IGNORE -> "Ignore" }
private fun smartMoney(minor: Long) = BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
