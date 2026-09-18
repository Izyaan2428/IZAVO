package com.izavo.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import com.izavo.app.data.CategoryTotal
import com.izavo.app.data.CurrencyTotal
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.currencyDisplayOrder
import com.izavo.app.data.formatCurrency
import com.izavo.app.preferences.homeGreeting
import com.izavo.app.ui.design.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeGreetingPose(val topLeft: Offset, val size: IntSize)

@Composable
fun HomeV22Screen(
    monthTotals: List<CurrencyTotal>, monthCategoryTotals: List<CategoryTotal>,
    expenses: List<ExpenseEntity>, homeCurrency: String, showSeconds: Boolean = false,
    onStatisticsClick: () -> Unit, onViewAllClick: () -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit, displayName: String = "",
    greetingOverride: String? = null, greetingVisible: Boolean = true,
    onGreetingPositioned: (HomeGreetingPose) -> Unit = {},
    todayTotals: List<CurrencyTotal> = emptyList(), darkTheme: Boolean = false,
    onThemeToggle: (Offset) -> Unit = {}, onTrendClick: () -> Unit = onStatisticsClick
) {
    val clock = rememberHomeTime()
    val today = clock.toLocalDate()
    val represented = remember(todayTotals, monthTotals) {
        (todayTotals + monthTotals).filter { it.totalMinor > 0 }.map { it.currencyCode }.distinct()
    }
    val currency = if (homeCurrency in represented || represented.isEmpty()) homeCurrency
        else represented.minBy { currencyDisplayOrder(it, homeCurrency) }
    val todayTotal = todayTotals.firstOrNull { it.currencyCode == currency }?.totalMinor ?: 0L
    val monthTotal = monthTotals.firstOrNull { it.currencyCode == currency }?.totalMinor ?: 0L
    val trend = remember(expenses, currency, today) { sevenDaySpending(expenses, currency, today) }
    val categories = remember(monthCategoryTotals, currency) {
        monthCategoryTotals.filter { it.currencyCode == currency && it.totalMinor > 0 }
            .sortedByDescending(CategoryTotal::totalMinor).take(3)
    }

    Box(Modifier.fillMaxSize().background(ExpenseColors.Background)) {
        IzavoAtmosphericBackground(Modifier.fillMaxWidth().height(470.dp), AtmosphereIntensity.HOME)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .statusBarsPadding().padding(top = 20.dp, bottom = 112.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                        color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(greetingOverride ?: homeGreeting(clock.hour, displayName), color = ExpenseColors.Ink,
                        style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.ContentOrLtr),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            onGreetingPositioned(HomeGreetingPose(coordinates.positionInRoot(), coordinates.size))
                        }.graphicsLayer { alpha = if (greetingVisible) 1f else 0f })
                }
                AppearanceButton(darkTheme, onThemeToggle)
            }
            Spacer(Modifier.height(30.dp))
            Text("Today", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
            MoneyDisplay(todayTotal, currency, hero = true, horizontalAlignment = Alignment.Start)
            CompactCurrencyMetadata(todayTotals.filter { it.currencyCode != currency && it.totalMinor > 0 })
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("This month", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(formatCurrency(monthTotal, currency), color = ExpenseColors.Ink,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                    CompactCurrencyMetadata(monthTotals.filter { it.currencyCode != currency && it.totalMinor > 0 })
                }
                HomeSevenDaySparkline(trend, currency, onTrendClick)
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Spending", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("See statistics", color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.premiumClick(onClick = onStatisticsClick).padding(10.dp, 4.dp))
            }
            Text("By category", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
            if (categories.isEmpty()) Text("Your category spending will appear here.", color = ExpenseColors.InkSecondary,
                modifier = Modifier.padding(vertical = 18.dp))
            else categories.forEach { CategorySpendingRow(it.category, listOf(it), currency) }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent expenses", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("View all", color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.premiumClick(onClick = onViewAllClick).padding(10.dp, 4.dp))
            }
            if (expenses.isEmpty()) ExpenseEmptyState("No expenses yet", "Add your first expense in seconds.")
            else expenses.take(4).forEachIndexed { index, expense ->
                RecentExpenseRow(expense, { onExpenseClick(expense) }, index != expenses.take(4).lastIndex, showSeconds)
            }
        }
    }
}

@Composable
private fun AppearanceButton(darkTheme: Boolean, onClick: (Offset) -> Unit) {
    val description = if (darkTheme) "Switch to light mode" else "Switch to dark mode"
    var center by remember { mutableStateOf(Offset.Zero) }
    Surface(modifier = Modifier.size(48.dp).onGloballyPositioned { coordinates ->
        val position = coordinates.positionInRoot()
        center = position + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
    }.premiumClick(pressedScale = .95f, onClick = { onClick(center) })
        .semantics { contentDescription = description }, shape = CircleShape,
        color = ExpenseColors.palette.surfaceGlass, border = BorderStroke(1.dp, ExpenseColors.GlassBorder), shadowElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(darkTheme, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "appearanceIcon") { dark ->
                Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, null,
                    Modifier.size(20.dp).rotate(if (dark) 12f else 0f), tint = ExpenseColors.Ink)
            }
        }
    }
}

@Composable
private fun CompactCurrencyMetadata(totals: List<CurrencyTotal>) {
    if (totals.isEmpty()) return
    val shown = totals.take(2)
    val remaining = totals.size - shown.size
    Text(buildString {
        append(shown.joinToString(" · ") { formatCurrency(it.totalMinor, it.currencyCode) })
        if (remaining > 0) append(" · +$remaining")
    }, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
}
