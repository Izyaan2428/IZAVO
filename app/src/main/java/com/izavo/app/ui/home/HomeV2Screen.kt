package com.izavo.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.izavo.app.R
import com.izavo.app.data.CategoryTotal
import com.izavo.app.data.CurrencyTotal
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.ExpenseCategories
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseEmptyState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HomeCategories = ExpenseCategories.all

@Composable
fun HomeV2Screen(
    todayTotals: List<CurrencyTotal>,
    monthTotals: List<CurrencyTotal>,
    monthCategoryTotals: List<CategoryTotal>,
    expenses: List<ExpenseEntity>,
    homeCurrency: String,
    showSeconds: Boolean = false,
    onSettingsClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onViewAllClick: () -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit
) {
    val categoryTotals = monthCategoryTotals.groupBy { it.category }
    val quietClick = remember { MutableInteractionSource() }

    Column(
        Modifier.fillMaxSize().background(ExpenseColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(currentDateLabel(), color = ExpenseColors.InkSecondary,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Surface(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp).padding(6.dp),
                shape = CircleShape,
                color = ExpenseColors.Surface.copy(alpha = .58f),
                border = BorderStroke(1.dp, ExpenseColors.GlassBorder.copy(alpha = .9f))
            ) { androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_figma_settings), "Settings", tint = ExpenseColors.InkSecondary, modifier = Modifier.size(18.dp))
            } }
        }
        Spacer(Modifier.height(2.dp))
        Text(greetingText(), color = ExpenseColors.Ink, style = MaterialTheme.typography.headlineMedium)

        SpendingHero(todayTotals, monthTotals, homeCurrency)

        Text("Spending", color = ExpenseColors.Ink, style = MaterialTheme.typography.titleLarge)
        Text("By category", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(7.dp))
        if (monthCategoryTotals.isEmpty()) {
            Text("Category spending will appear here.", color = ExpenseColors.InkSecondary,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            HomeCategories.filter { categoryTotals[it].orEmpty().isNotEmpty() }.forEach { category ->
                CategorySpendingRow(category, categoryTotals[category].orEmpty(), homeCurrency)
            }
        }
        Text("See statistics", color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(quietClick, indication = null, onClick = onStatisticsClick).padding(vertical = 10.dp))

        Spacer(Modifier.height(27.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent expenses", color = ExpenseColors.Ink, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("View all", color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(quietClick, indication = null, onClick = onViewAllClick)
                    .padding(vertical = 10.dp))
        }
        Spacer(Modifier.height(3.dp))
        if (expenses.isEmpty()) {
            ExpenseEmptyState("No expenses yet", "Add your first expense in seconds.")
        } else {
            val recent = expenses.take(2)
            recent.forEachIndexed { index, expense ->
                RecentExpenseRow(expense, { onExpenseClick(expense) }, showDivider = index != recent.lastIndex, showSeconds = showSeconds)
            }
        }
    }
}

private fun currentDateLabel(): String = LocalDate.now().format(
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
)

private fun greetingText(): String = when (LocalTime.now().hour) {
    in 0..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
