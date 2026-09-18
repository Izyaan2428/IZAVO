package com.izavo.app.ui.importing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseCategories
import com.izavo.app.importing.*
import com.izavo.app.ui.design.*
import com.izavo.app.ui.format.TransactionTimeFormatter
import java.math.BigDecimal

private enum class ReviewStage { SUMMARY, GUIDED }
private sealed interface ReviewPage {
    data object Summary : ReviewPage
    data object Ready : ReviewPage
    data class Guided(val group: ReviewGroup) : ReviewPage
}
private data class ReviewChoice(val decision: ImportDecision, val title: String, val explanation: String)

@Composable
fun SmartImportFlowScreen(
    state: ImportUiState,
    showSeconds: Boolean,
    onBack: () -> Unit,
    onResolveGroup: (String, ImportDecision, String?, Boolean) -> Unit,
    onRemember: (String, Boolean) -> Unit,
    onReopen: (String) -> Unit,
    onSkipRemaining: () -> Unit,
    onConfirm: () -> Unit
) {
    when (state) {
        ImportUiState.Idle -> Unit
        ImportUiState.Loading -> ImportMessage("Reading your statement…", true, onBack)
        is ImportUiState.Error -> ImportMessage(state.message, false, onBack)
        is ImportUiState.Complete -> ImportComplete(state.summary, onBack)
        is ImportUiState.Review -> GuidedReview(state.prepared, showSeconds, onBack, onResolveGroup, onSkipRemaining, onConfirm, false)
        is ImportUiState.ApplyingDecision -> GuidedReview(state.prepared, showSeconds, onBack, onResolveGroup, onSkipRemaining, onConfirm, true)
    }
}

@Composable
private fun GuidedReview(
    prepared: PreparedImport,
    showSeconds: Boolean,
    onBack: () -> Unit,
    onResolve: (String, ImportDecision, String?, Boolean) -> Unit,
    onSkipRemaining: () -> Unit,
    onConfirm: () -> Unit,
    decisionInFlight: Boolean
) {
    var stageName by rememberSaveable(prepared.fileName) { mutableStateOf(ReviewStage.SUMMARY.name) }
    val unresolved = prepared.reviewGroups.filterNot { it.resolved }
    val completedDecisions = prepared.reviewProgress.completedHumanDecisions
    val progressLabel = "Review ${completedDecisions + 1} · ${unresolved.size} remaining"
    val page = when {
        unresolved.isEmpty() -> ReviewPage.Ready
        stageName == ReviewStage.GUIDED.name || unresolved.size <= 3 -> ReviewPage.Guided(unresolved.first())
        else -> ReviewPage.Summary
    }
    Box(Modifier.fillMaxSize().background(ExpenseColors.Background)) {
        IzavoAtmosphericBackground(Modifier.fillMaxWidth().height(300.dp), AtmosphereIntensity.SUBTLE)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ExpenseColors.InkSecondary)
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                modifier = Modifier.fillMaxWidth(),
                targetState = page,
                transitionSpec = { androidx.compose.animation.fadeIn(tween(200)) togetherWith androidx.compose.animation.fadeOut(tween(160)) },
                label = "importReviewStage"
            ) { page ->
                when (page) {
                    ReviewPage.Ready -> FinalConfirmation(prepared, onConfirm)
                    is ReviewPage.Guided -> ReviewDecision(page.group, showSeconds, progressLabel, decisionInFlight, onResolve, onSkipRemaining)
                    ReviewPage.Summary -> ReviewWelcome(prepared, unresolved, onStart = { stageName = ReviewStage.GUIDED.name }, onSkipRemaining)
                }
            }
        }
    }
}

@Composable
private fun ReviewWelcome(prepared: PreparedImport, unresolved: List<ReviewGroup>, onStart: () -> Unit, onSkipRemaining: () -> Unit) {
    val ready = prepared.items.count { it.resolved && it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }
    Column(Modifier.fillMaxWidth()) {
    Text("Almost done", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(14.dp))
    Text("We found ${prepared.items.size - prepared.duplicateCount} transactions.", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(6.dp))
    Text("IZAVO sorted most of them for you.", color = ExpenseColors.InkSecondary)
    Spacer(Modifier.height(30.dp))
    ImportCount("$ready ready", "✓")
    ImportCount("${unresolved.sumOf { it.transactionCount }} need a quick check", null)
    if (prepared.duplicateCount > 0) ImportCount("${prepared.duplicateCount} duplicates safely skipped", null)
    if (prepared.invalidRowCount > 0) ImportCount("${prepared.invalidRowCount} rows could not be read", null)
    Spacer(Modifier.height(34.dp))
    ExpensePrimaryButton("Review ${unresolved.size} decisions", onClick = onStart)
    TextButton(onClick = onSkipRemaining, modifier = Modifier.fillMaxWidth()) {
        Text("Skip for now", color = ExpenseColors.InkSecondary)
    }
    Spacer(Modifier.height(18.dp))
    Text("Everything stays on your device.", color = ExpenseColors.InkSecondary,
        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportCount(label: String, suffix: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (suffix != null) Text(suffix, color = ExpenseColors.CyanSelected, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReviewDecision(
    group: ReviewGroup,
    showSeconds: Boolean,
    progressLabel: String,
    decisionInFlight: Boolean,
    onResolve: (String, ImportDecision, String?, Boolean) -> Unit,
    onSkipRemaining: () -> Unit
) {
    var decision by remember(group.id) { mutableStateOf<ImportDecision?>(
        group.suggestedDecision.takeIf { group.confidence == ConfidenceLevel.MEDIUM }
    ) }
    var category by remember(group.id) { mutableStateOf(group.suggestedCategory ?: "Other") }
    var rememberRule by remember(group.id) { mutableStateOf(group.rememberEligible) }
    var details by remember(group.id) { mutableStateOf(false) }
    val outgoing = group.direction == TransactionDirection.DEBIT
    val first = group.items.first().transaction

    Column(Modifier.fillMaxWidth()) {
    Text(progressLabel, color = ExpenseColors.InkTertiary, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(18.dp))
    Text(if (outgoing) "What was this money for?" else "Why did you receive this money?",
        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(32.dp))
    Text(group.identity, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(5.dp))
    Text("${group.currencyCode} ${money(group.totalMinor)}", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(
        if (group.transactionCount > 1) "${group.transactionCount} similar transactions"
        else TransactionTimeFormatter.dateTime(first.expenseTimestamp, showSeconds),
        color = ExpenseColors.InkSecondary
    )
    Spacer(Modifier.height(34.dp))
    val choices = if (outgoing) listOf(
        ReviewChoice(ImportDecision.EXPENSE, "Paid for something", ""),
        ReviewChoice(ImportDecision.MONEY_MOVEMENT, "Moved / returned money", ""),
        ReviewChoice(ImportDecision.UNKNOWN, "Don't remember", ""),
        ReviewChoice(ImportDecision.IGNORE, "Ignore", "")
    ) else listOf(
        ReviewChoice(ImportDecision.INCOME, "Income", "Money you earned or received as income"),
        ReviewChoice(ImportDecision.MONEY_MOVEMENT, "Money moved to me", "Doesn't count as spending"),
        ReviewChoice(ImportDecision.REFUND, "Refund", "Money returned to you"),
        ReviewChoice(ImportDecision.UNKNOWN, "I don't remember", "Keep it without guessing"),
        ReviewChoice(ImportDecision.IGNORE, "Ignore this transaction", "Leave it out of IZAVO")
    )
    choices.forEach { choice -> DecisionRow(choice.title, choice.explanation, decision == choice.decision) { decision = choice.decision } }

    AnimatedVisibility(decision == ImportDecision.EXPENSE) {
        Column {
            Spacer(Modifier.height(20.dp))
            Text("Category", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { ExpenseCategories.all.forEach { QuietChoice(it, category == it) { category = it } } }
        }
    }
    if (group.rememberEligible) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clickable { rememberRule = !rememberRule }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberRule, onCheckedChange = { rememberRule = it })
            Text("Remember for next time", color = ExpenseColors.InkSecondary)
        }
    }
    Text(if (details) "Hide bank details" else "Details", color = ExpenseColors.Cyan,
        modifier = Modifier.clickable { details = !details }.padding(vertical = 12.dp))
    AnimatedVisibility(details) {
        Column(Modifier.fillMaxWidth().background(ExpenseColors.palette.controlSurface, RoundedCornerShape(16.dp)).padding(16.dp)) {
            DetailLine("Original description", first.rawDescription)
            DetailLine("Transaction type", first.rawType)
            DetailLine("Reference", listOfNotNull(first.primaryReference, first.secondaryReference).joinToString(" · ").ifBlank { "—" })
            DetailLine("Posted", TransactionTimeFormatter.dateTime(first.postedAt, showSeconds))
            if (group.transactionCount > 1) group.items.drop(1).take(49).forEach {
                Text("${TransactionTimeFormatter.dateTime(it.transaction.expenseTimestamp, showSeconds)} · ${it.transaction.currencyCode} ${money(it.transaction.amountMinor)}",
                    color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }
    Spacer(Modifier.height(26.dp))
    ExpensePrimaryButton(if (group.transactionCount > 1) "Apply to all ${group.transactionCount}" else "Continue",
        enabled = decision != null && !decisionInFlight) {
        val selected = decision ?: return@ExpensePrimaryButton
        onResolve(group.id, selected, category.takeIf { selected == ImportDecision.EXPENSE }, rememberRule)
    }
    TextButton(onClick = onSkipRemaining, enabled = !decisionInFlight, modifier = Modifier.fillMaxWidth()) {
        Text("Skip for now", color = ExpenseColors.InkSecondary)
    }
    }
}

@Composable
private fun DecisionRow(label: String, explanation: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) ExpenseColors.CyanSelected else ExpenseColors.Ink
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        color = if (selected) ExpenseColors.CyanSoft else ExpenseColors.palette.surfaceGlass,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (selected) ExpenseColors.Cyan.copy(alpha = .28f) else ExpenseColors.GlassBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = color, style = MaterialTheme.typography.titleMedium)
                if (explanation.isNotBlank()) Text(explanation, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Text("✓", color = ExpenseColors.CyanSelected)
        }
    }
}

@Composable private fun DetailLine(label: String, value: String) {
    Text(label, color = ExpenseColors.InkTertiary, style = MaterialTheme.typography.labelSmall)
    Text(value.ifBlank { "—" }, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FinalConfirmation(prepared: PreparedImport, onConfirm: () -> Unit) {
    val summary = prepared.decisionSummary()
    Column(Modifier.fillMaxWidth()) {
    Text("Ready to import", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Text("Ready when you are.", color = ExpenseColors.InkSecondary)
    Spacer(Modifier.height(28.dp))
    ImportCount("${summary.expenses} expenses", null)
    val other = summary.income + summary.moneyMovements + summary.refunds + summary.unknown + summary.ignored
    if (other > 0) ImportCount("$other other transactions saved", null)
    Spacer(Modifier.height(30.dp))
    ExpensePrimaryButton("Import transactions", onClick = onConfirm)
    Spacer(Modifier.height(16.dp))
    Text("Only expenses are added to your spending totals.", color = ExpenseColors.InkSecondary,
        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun ImportMessage(message: String, loading: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ExpenseColors.Background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) { CircularProgressIndicator(color = ExpenseColors.Cyan); Spacer(Modifier.height(18.dp)) }
        Text(message, style = MaterialTheme.typography.titleLarge)
        if (!loading) { Spacer(Modifier.height(20.dp)); ExpensePrimaryButton("Back to Settings", onClick = onBack) }
    }
}

@Composable private fun ImportComplete(summary: ImportResultSummary, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ExpenseColors.Background).navigationBarsPadding().padding(24.dp)) {
        Spacer(Modifier.weight(.7f))
        Surface(Modifier.size(52.dp).align(Alignment.CenterHorizontally), shape = androidx.compose.foundation.shape.CircleShape,
            color = ExpenseColors.CyanSoft) {
            Box(contentAlignment = Alignment.Center) { Text("✓", color = ExpenseColors.CyanSelected, style = MaterialTheme.typography.headlineSmall) }
        }
        Spacer(Modifier.height(22.dp))
        Text("Import complete", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("${summary.expenseCount} expenses added", style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (summary.excludedCount > 0) Text("${summary.excludedCount} other transactions saved", color = ExpenseColors.InkSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        if (summary.duplicateCount > 0) Text("${summary.duplicateCount} duplicates skipped", color = ExpenseColors.InkTertiary,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        ExpensePrimaryButton("Done", onClick = onBack)
        Spacer(Modifier.height(8.dp))
    }
}

private fun money(minor: Long) = BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
