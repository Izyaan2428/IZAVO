package com.izavo.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.izavo.app.data.CategoryTotal
import com.izavo.app.data.CurrencyTotal
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.currencyDisplayOrder
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseEmptyState
import com.izavo.app.ui.design.MoneyDisplay
import com.izavo.app.ui.statistics.MiniSparkline
import com.izavo.app.ui.statistics.SpendPoint
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeV3Screen(
    monthTotals: List<CurrencyTotal>, monthCategoryTotals: List<CategoryTotal>, expenses: List<ExpenseEntity>,
    homeCurrency: String, showSeconds: Boolean = false, onStatisticsClick: () -> Unit,
    onViewAllClick: () -> Unit, onExpenseClick: (ExpenseEntity) -> Unit, displayName: String = ""
) {
    val clock = rememberHomeTime()
    val now = clock.toLocalDate(); val zone = ZoneId.systemDefault(); val currentMonth = YearMonth.from(now)
    val hero = remember(monthTotals, homeCurrency) {
        monthTotals.filter { it.totalMinor > 0 }.sortedWith(compareBy<CurrencyTotal> { currencyDisplayOrder(it.currencyCode, homeCurrency) }.thenBy { it.currencyCode }).firstOrNull()
    }
    val currency = hero?.currencyCode ?: homeCurrency
    val monthExpenses = remember(expenses, currency, currentMonth) { expenses.filter { it.currencyCode == currency && it.amountMinor > 0 && YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) == currentMonth } }
    val priorTotal = remember(expenses, currency, currentMonth, now) {
        val previous = currentMonth.minusMonths(1); val day = minOf(now.dayOfMonth, previous.lengthOfMonth())
        expenses.filter { it.currencyCode == currency && it.amountMinor > 0 }.filter {
            val date = Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
            YearMonth.from(date) == previous && date.dayOfMonth <= day
        }.sumOf(ExpenseEntity::amountMinor)
    }
    val comparison = if (priorTotal > 0) ((((hero?.totalMinor ?: 0) - priorTotal).toDouble() / priorTotal) * 100).roundToInt() else null
    val spark = remember(monthExpenses, now) {
        val totals = monthExpenses.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth }.mapValues { it.value.sumOf(ExpenseEntity::amountMinor) }
        var cumulative = 0L
        (1..now.dayOfMonth).map { cumulative += totals[it] ?: 0; SpendPoint(it.toString(), cumulative) }
    }
    val categories = remember(monthCategoryTotals, currency) { monthCategoryTotals.filter { it.currencyCode == currency && it.totalMinor > 0 }.sortedByDescending(CategoryTotal::totalMinor).take(3) }
    val interaction = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxSize().background(ExpenseColors.Background).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 18.dp)) {
        Box(Modifier.fillMaxWidth()) {
            com.izavo.app.ui.design.HeroAtmosphere(Modifier.matchParentSize())
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(com.izavo.app.preferences.homeGreeting(clock.hour, displayName), color = ExpenseColors.Ink,
                    style = MaterialTheme.typography.titleLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr))
                Spacer(Modifier.height(26.dp))
                Text(currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Text(if (hero == null) "Nothing spent this month." else "Total spending", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(5.dp)); MoneyDisplay(hero?.totalMinor ?: 0, currency, hero = true, horizontalAlignment = Alignment.Start)
                Spacer(Modifier.height(7.dp))
                Text(when { hero == null -> "A quiet month so far."; comparison == null -> "Your month, at a glance."; comparison == 0 -> "About the same as last month"; else -> "${if (comparison < 0) "↓" else "↑"} ${abs(comparison)}% vs last month to date" }, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
                if (spark.count { it.amountMinor > 0 } >= 2) MiniSparkline(spark, Modifier.padding(top = 12.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("This month", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("See statistics", color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(interaction, null, onClick = onStatisticsClick).padding(vertical = 10.dp))
        }
        if (categories.isEmpty()) Text("Category spending will appear here.", color = ExpenseColors.InkSecondary, modifier = Modifier.padding(vertical = 18.dp))
        else categories.forEach { CategorySpendingRow(it.category, listOf(it), currency) }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("View all", color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable(interaction, null, onClick = onViewAllClick).padding(vertical = 10.dp))
        }
        if (expenses.isEmpty()) ExpenseEmptyState("No expenses yet", "Add your first expense in seconds.")
        else expenses.take(4).forEachIndexed { index, expense -> RecentExpenseRow(expense, { onExpenseClick(expense) }, index != expenses.take(4).lastIndex, showSeconds) }
    }
}
