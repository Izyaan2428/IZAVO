package com.izavo.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izavo.app.data.ExpenseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.izavo.app.ui.format.TransactionTimeFormatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

fun amountNumber(amountMinor: Long): String = String.format(Locale.US, "%,.2f", amountMinor / 100.0)

@Composable
fun MoneyDisplay(
    amountMinor: Long,
    currencyCode: String,
    hero: Boolean = false,
    color: Color = ExpenseColors.Ink,
    horizontalAlignment: Alignment.Horizontal = Alignment.End
) {
    val number = amountNumber(amountMinor)
    val size = if (!hero) 16.sp else when {
        number.length <= 12 -> 50.sp
        number.length <= 15 -> 42.sp
        else -> 34.sp
    }
    AnimatedContent(amountMinor to currencyCode, transitionSpec = {
        (fadeIn(tween(IzavoMotion.Control)) + slideInVertically(tween(IzavoMotion.Control)) { 6 }) togetherWith
            (fadeOut(tween(IzavoMotion.Control)) + slideOutVertically(tween(IzavoMotion.Control)) { -6 })
    }, label = "financialValue") { (amount, currency) ->
        Column(horizontalAlignment = horizontalAlignment) {
            if (hero) Text(currency, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
            BoxWithConstraints {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val value = amountNumber(amount)
                val base = TextStyle(fontSize = size, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr)
                val measured = measurer.measure(value, base, softWrap = false, constraints = Constraints()).size.width
                val available = with(density) { maxWidth.toPx() }
                val fit = if (measured > available && available > 0f) size * (available / measured) else size
                Text(value, color = color, style = base.copy(fontSize = fit, lineHeight = fit * 1.12f), maxLines = 1, softWrap = false)
            }
            if (!hero) Text(currency, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Shared Home/History transaction hierarchy with a stable financial column. */
@Composable
fun ExpenseTransactionRow(
    expense: ExpenseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    rowHeight: androidx.compose.ui.unit.Dp = 70.dp,
    showSeconds: Boolean = false
) {
    val category = expense.category?.takeIf { it.isNotBlank() } ?: "Other"
    val categoryVisual = ExpenseCategoryVisuals.forKey(category)
    val title = expense.note.takeIf { it.isNotBlank() } ?: category
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = rowHeight).premiumClick(pressedScale = 1f, onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.width(34.dp).height(34.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = categoryVisual.color.copy(alpha = .14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(categoryVisual.icon, contentDescription = category, tint = categoryVisual.color,
                        modifier = Modifier.width(17.dp).height(17.dp))
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = ExpenseColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
                Spacer(Modifier.height(3.dp))
                Text(
                    if (expense.note.isNotBlank()) "$category · ${expenseTimeLabel(expense, showSeconds)}" else expenseTimeLabel(expense, showSeconds),
                    color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
                Text(amountNumber(expense.amountMinor), color = ExpenseColors.Ink,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr),
                    maxLines = 1, softWrap = false)
                Spacer(Modifier.height(2.dp))
                Text(expense.currencyCode, color = ExpenseColors.InkSecondary,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        if (showDivider) HairlineDivider()
    }
}

@Composable
fun ExpensePrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (enabled) ExpenseColors.Action else ExpenseColors.SurfaceQuiet,
        tween(ExpenseMotion.Fast), label = "primaryButton"
    )
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = ExpenseColors.OnAction,
            disabledContainerColor = color, disabledContentColor = ExpenseColors.InkTertiary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun QuietChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) ExpenseColors.CyanSoft.copy(alpha = .94f) else ExpenseColors.SurfaceQuiet.copy(alpha = .88f),
        tween(ExpenseMotion.Fast), label = "choiceBackground"
    )
    val foreground by animateColorAsState(
        if (selected) ExpenseColors.CyanSelected else ExpenseColors.InkSecondary,
        tween(ExpenseMotion.Fast), label = "choiceText"
    )
    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(15.dp),
        border = if (selected) BorderStroke(1.dp, Color(0x576ED6E5)) else null,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.selected = selected }.premiumClick(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            if (label in com.izavo.app.data.ExpenseCategories.all) {
                Icon(ExpenseCategoryVisuals.forKey(label).icon, null, modifier = Modifier.width(14.dp).height(14.dp), tint = foreground)
                Spacer(Modifier.width(5.dp))
            }
            Text(label, color = foreground, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String? = null, trailing: String? = null, onClick: (() -> Unit)? = null) {
    val rowModifier = if (onClick != null) Modifier.premiumClick(pressedScale = 1f, onClick = onClick) else Modifier
    Row(rowModifier.fillMaxWidth().defaultMinSize(minHeight = 68.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ExpenseColors.Ink, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (!trailing.isNullOrBlank()) {
            Text(trailing, color = ExpenseColors.InkSecondary, modifier = Modifier.fillMaxWidth(.45f), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr),
                textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(7.dp))
        }
        if (onClick != null) Text("›", color = ExpenseColors.InkTertiary, fontSize = 24.sp)
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, 1.dp, ExpenseColors.Divider)

@Composable
fun QuietDivider() = HairlineDivider()

@Composable
fun ExpenseEmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(Icons.Outlined.Search, null, tint = ExpenseColors.InkTertiary)
        Spacer(Modifier.height(14.dp))
        Text(title, color = ExpenseColors.Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(message, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

private fun expenseTimeLabel(expense: ExpenseEntity, showSeconds: Boolean): String {
    val dateTime = Instant.ofEpochMilli(expense.occurredAt).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (dateTime.toLocalDate()) {
        today -> TransactionTimeFormatter.time(expense.occurredAt, showSeconds)
        today.minusDays(1) -> "Yesterday"
        else -> dateTime.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
}
