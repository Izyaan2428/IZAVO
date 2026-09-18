package com.izavo.app.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izavo.app.data.ExpenseCategories
import com.izavo.app.ui.design.ExpenseCategoryVisuals
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseEmptyState
import com.izavo.app.ui.design.ExpensePrimaryButton
import com.izavo.app.ui.design.ExpenseShapes
import com.izavo.app.ui.design.ExpenseTransactionRow
import com.izavo.app.ui.design.HairlineDivider
import com.izavo.app.ui.design.QuietChoice
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.izavo.app.ui.design.MoneyDisplay
import com.izavo.app.ui.design.premiumClick
import com.izavo.app.ui.design.IzavoMotion
import com.izavo.app.ui.design.IzavoMaterial
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.activity.compose.BackHandler

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onSection: (StatisticsSection) -> Unit,
    onCurrency: (String) -> Unit,
    onMonth: (YearMonth) -> Unit,
    onYear: (Int) -> Unit,
    onRange: (TrendRange) -> Unit,
    onCategory: (String?) -> Unit,
    onExpenseClick: (com.izavo.app.data.ExpenseEntity) -> Unit,
    onAddExpense: () -> Unit,
    onImport: () -> Unit
) {
    BackHandler(state.selectedCategory != null) { onCategory(null) }
    AnimatedContent(state.selectedCategory, transitionSpec = {
        val direction = if (targetState == null) -1 else 1
        (fadeIn(tween(IzavoMotion.Screen)) + slideInHorizontally(tween(IzavoMotion.Screen)) { direction * 24 }) togetherWith
            (fadeOut(tween(IzavoMotion.Control)) + slideOutHorizontally(tween(IzavoMotion.Screen)) { -direction * 24 })
    }, label = "categoryDetail") { category ->
        if (category != null) CategoryDetail(state.copy(selectedCategory = category), { onCategory(null) }, onExpenseClick)
        else StatisticsMain(state, onSection, onCurrency, onMonth, onYear, onRange, onCategory, onAddExpense, onImport)
    }
}

@Composable
private fun StatisticsMain(state: StatisticsUiState, onSection: (StatisticsSection) -> Unit,
    onCurrency: (String) -> Unit, onMonth: (YearMonth) -> Unit, onYear: (Int) -> Unit,
    onRange: (TrendRange) -> Unit, onCategory: (String?) -> Unit, onAddExpense: () -> Unit, onImport: () -> Unit) {
    var showPeriod by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(ExpenseColors.Background).padding(horizontal = 24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 112.dp)
    ) {
        item {
            Text("Statistics", style = MaterialTheme.typography.headlineLarge, color = ExpenseColors.Ink)
            Spacer(Modifier.height(14.dp))
            StatisticsSegments(state.section, onSection)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Selector(state.period.label) { showPeriod = true }
                Spacer(Modifier.weight(1f))
                if (state.availableCurrencies.size > 1) {
                    Spacer(Modifier.width(10.dp)); Selector(state.currencyCode) { showCurrency = true }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (state.loading) {
            item { StatisticsLoading() }
        } else if (state.snapshot.totalMinor == 0L && state.section != StatisticsSection.TRENDS) {
            item {
                ExpenseEmptyState("Your spending story starts here.", "Add expenses or import a BML statement to see trends, categories and insights in ${state.currencyCode}.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(onClick = onAddExpense, shape = RoundedCornerShape(18.dp), color = ExpenseColors.Action,
                        modifier = Modifier.weight(1f).height(52.dp)) { Box(contentAlignment = Alignment.Center) { Text("Add expense", color = ExpenseColors.Surface) } }
                    Surface(onClick = onImport, shape = RoundedCornerShape(18.dp), color = ExpenseColors.SurfaceQuiet,
                        modifier = Modifier.weight(1f).height(52.dp)) { Box(contentAlignment = Alignment.Center) { Text("Import", color = ExpenseColors.Ink) } }
                }
            }
        } else when (state.section) {
            StatisticsSection.OVERVIEW -> overviewItems(state, onCategory)
            StatisticsSection.CATEGORIES -> categoryItems(state, onCategory)
            StatisticsSection.TRENDS -> trendItems(state, onRange)
        }
    }
    if (showPeriod) PeriodPickerSheet(state, { showPeriod = false }, { onMonth(it); showPeriod = false }, { onYear(it); showPeriod = false })
    if (showCurrency) CurrencyPickerSheet(state, { showCurrency = false }) { onCurrency(it); showCurrency = false }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(state: StatisticsUiState, onCategory: (String?) -> Unit) {
    item {
        StatisticsHero(state.snapshot.totalMinor, state.currencyCode, "Total spending")
        ComparisonText(state.snapshot.comparison)
        Spacer(Modifier.height(28.dp))
        SectionEyebrow("Spending over time")
        Spacer(Modifier.height(10.dp))
        SpendingTrendChart(state.snapshot.chartPoints, state.currencyCode, bars = state.period.mode == StatisticsPeriodMode.YEAR)
        Spacer(Modifier.height(22.dp))
        if (state.snapshot.metrics.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { state.snapshot.metrics.take(2).forEach { MetricPanel(it, Modifier.weight(1f)) } }
            Spacer(Modifier.height(32.dp))
        }
        SectionTitle("Where it went")
        Spacer(Modifier.height(4.dp))
    }
    items(state.snapshot.categories.take(4), key = { it.category }) { CategoryRow(it, state.currencyCode) { onCategory(it.category) } }
    if (state.snapshot.merchants.isNotEmpty()) {
        item { Spacer(Modifier.height(30.dp)); SectionTitle("Top merchants"); Spacer(Modifier.height(6.dp)) }
        items(state.snapshot.merchants.take(5).withIndex().toList(), key = { it.value.identity }) { ranked ->
            MerchantRow(ranked.value, state.currencyCode, ranked.index + 1)
        }
    }
    if (state.snapshot.insights.isNotEmpty()) {
        item { Spacer(Modifier.height(28.dp)); SectionTitle("Worth noticing") }
        items(state.snapshot.insights, key = { it.text }) { insight ->
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text(insightTitle(insight), color = ExpenseColors.Ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(insight.text, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categoryItems(state: StatisticsUiState, onCategory: (String?) -> Unit) {
    item {
        StatisticsHero(state.snapshot.totalMinor, state.currencyCode, "Total spending")
        Text(state.period.label, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        CategoryCompositionBar(state.snapshot.categories, state.currencyCode)
        state.snapshot.categories.firstOrNull()?.let { top ->
            Spacer(Modifier.height(16.dp))
            Text("${top.category} leads this period", color = ExpenseColors.Ink,
                style = MaterialTheme.typography.titleMedium)
            Text("${(top.share * 100).roundToInt()}% of spending went to ${top.category}.",
                color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle("Categories")
        Spacer(Modifier.height(4.dp))
    }
    items(state.snapshot.categories, key = { it.category }) { CategoryRow(it, state.currencyCode) { onCategory(it.category) } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trendItems(state: StatisticsUiState, onRange: (TrendRange) -> Unit) {
    val nonZero = state.trendPoints.filter { it.amountMinor > 0 }
    val average = if (nonZero.isEmpty()) 0L else nonZero.sumOf { it.amountMinor } / nonZero.size
    item {
        StatisticsHero(average, state.currencyCode, "Average monthly spending")
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(TrendRange.THREE_MONTHS to "3M", TrendRange.SIX_MONTHS to "6M", TrendRange.ONE_YEAR to "1Y", TrendRange.ALL to "All").forEach { (range, label) ->
                QuietChoice(label, state.trendRange == range, Modifier.weight(1f)) { onRange(range) }
            }
        }
        Spacer(Modifier.height(26.dp))
        SectionEyebrow("Monthly rhythm")
        Spacer(Modifier.height(10.dp))
        if (nonZero.isEmpty()) ExpenseEmptyState("No ${state.currencyCode} history yet", "Choose another currency or add an expense.")
        else SpendingTrendChart(state.trendPoints, state.currencyCode, bars = state.trendRange == TrendRange.ONE_YEAR)
        state.snapshot.decomposition?.let { change ->
            Spacer(Modifier.height(26.dp)); SectionTitle("Why?")
            Text("${if (change.totalDeltaMinor >= 0) "You spent" else "You spent"} ${state.currencyCode} ${StatisticsCalculator.money(abs(change.totalDeltaMinor))} ${if (change.totalDeltaMinor >= 0) "more" else "less"} than ${state.snapshot.comparison?.previousLabel ?: "before"}.", color = ExpenseColors.InkSecondary)
            Spacer(Modifier.height(12.dp))
            change.rows.take(6).forEach { row -> DeltaRow(row, state.currencyCode) }
            Spacer(Modifier.height(8.dp)); Text("${change.largestDriver} accounted for most of the change.", color = ExpenseColors.Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun CategoryDetail(state: StatisticsUiState, onBack: () -> Unit, onExpenseClick: (com.izavo.app.data.ExpenseEntity) -> Unit) {
    val category = state.selectedCategory ?: return
    val data = state.snapshot.categories.firstOrNull { it.category == category }
    val expenses = state.snapshot.expenses.filter { ExpenseCategories.normalized(it.category) == category }
    LazyColumn(Modifier.fillMaxSize().background(ExpenseColors.Background).padding(horizontal = 24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 112.dp)) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ExpenseColors.InkSecondary) }
            Text(category, style = MaterialTheme.typography.headlineLarge, color = ExpenseColors.Ink)
            Spacer(Modifier.height(14.dp))
            StatisticsHero(data?.totalMinor ?: 0L, state.currencyCode, "Category spending")
            Text("${data?.let { (it.share * 100).roundToInt() } ?: 0}% of total · ${state.period.label}", color = ExpenseColors.InkSecondary)
            data?.comparisonPercent?.let { Text("${if (it > 0) "↑" else "↓"} ${abs(it)}% vs previous period", color = ExpenseColors.Cyan, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp)); SpendingTrendChart(categoryChartPoints(state, category), state.currencyCode, bars = state.period.mode == StatisticsPeriodMode.YEAR)
            Spacer(Modifier.height(25.dp)); SectionTitle("Top places")
            val categoryMerchants = state.snapshot.merchants.filter { it.category == category }
            categoryMerchants.take(5).forEachIndexed { index, merchant -> MerchantRow(merchant, state.currencyCode, index + 1) }
            val manualTotal = expenses.filterNot { it.id in state.snapshot.merchantLinkedExpenseIds }.sumOf { it.amountMinor }
            if (manualTotal > 0) MerchantRow(MerchantStatistic("Other / manually added", category, manualTotal,
                expenses.count { it.id !in state.snapshot.merchantLinkedExpenseIds }), state.currencyCode, categoryMerchants.size + 1)
            if (categoryMerchants.isEmpty() && manualTotal == 0L) Text("Imported merchant details will appear here.", color = ExpenseColors.InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
            if (expenses.isNotEmpty()) { Spacer(Modifier.height(25.dp)); SectionTitle("Transactions") }
        }
        items(expenses, key = { it.id }) { expense -> ExpenseTransactionRow(expense, { onExpenseClick(expense) }) }
    }
}

private fun categoryChartPoints(state: StatisticsUiState, category: String): List<SpendPoint> {
    val zone = ZoneId.systemDefault()
    val rows = state.snapshot.expenses.filter { ExpenseCategories.normalized(it.category) == category }
    return if (state.period.mode == StatisticsPeriodMode.MONTH) {
        val totals = rows.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth }.mapValues { it.value.sumOf { row -> row.amountMinor } }
        state.snapshot.chartPoints.map { it.copy(amountMinor = totals[it.label.toIntOrNull()] ?: 0L) }
    } else {
        val totals = rows.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }.mapValues { it.value.sumOf { row -> row.amountMinor } }
        state.snapshot.chartPoints.map { it.copy(amountMinor = totals[it.label] ?: 0L) }
    }
}

@Composable private fun StatisticsSegments(selected: StatisticsSection, onSelect: (StatisticsSection) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(IzavoMaterial.Segmented.copy(alpha = .7f)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        StatisticsSection.entries.forEach { section ->
            val fill by animateColorAsState(if (selected == section) ExpenseColors.Surface else androidx.compose.ui.graphics.Color.Transparent, tween(IzavoMotion.Control), label = "segment")
            Surface(modifier = Modifier.weight(1f).fillMaxSize().semantics { this.selected = selected == section }.premiumClick(role = Role.Tab) { onSelect(section) }, shape = RoundedCornerShape(13.dp),
                color = fill,
                shadowElevation = 0.dp) {
                Box(contentAlignment = Alignment.Center) { Text(section.name.lowercase().replaceFirstChar(Char::uppercase), color = if (selected == section) ExpenseColors.Ink else ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected == section) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
    }
}

@Composable private fun Selector(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.height(42.dp).premiumClick(onClick = onClick), color = IzavoMaterial.FloatingControl, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f, fill = false), color = ExpenseColors.Ink, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.width(4.dp)); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = ExpenseColors.InkSecondary) }
    }
}

@Composable private fun StatisticsHero(amountMinor: Long, currency: String, label: String) {
    val amount = StatisticsCalculator.money(amountMinor)
    val amountSize = when {
        amount.length <= 12 -> 42.sp
        amount.length <= 15 -> 36.sp
        else -> 30.sp
    }
    Text(label, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(5.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(currency, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(9.dp))
        Text(
            amount,
            color = ExpenseColors.Ink,
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = amountSize,
                lineHeight = amountSize * 1.12f,
                fontFeatureSettings = "tnum",
                textDirection = TextDirection.Ltr
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable private fun ComparisonText(comparison: SpendingComparison?) {
    val pct = comparison?.percent
    Text(when { comparison == null -> ""; pct == null -> "No comparable spending in ${comparison.previousLabel}"; pct == 0 -> "About the same as ${comparison.previousLabel}"; else -> "${if (pct < 0) "↓" else "↑"} ${abs(pct)}% vs ${comparison.previousLabel}" },
        color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
}

@Composable private fun MetricPanel(metric: StatisticMetric, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp)) { Text(metric.label, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(7.dp)); Text(metric.value, color = ExpenseColors.Ink, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable private fun CategoryRow(item: CategoryStatistic, currency: String, onClick: () -> Unit) {
    val visual = ExpenseCategoryVisuals.forKey(item.category)
    val share = item.share.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    Column(Modifier.fillMaxWidth().premiumClick(pressedScale = .99f, onClick = onClick).padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(34.dp), shape = CircleShape, color = visual.color.copy(alpha = if (ExpenseColors.IsDark) .18f else .12f)) { Box(contentAlignment = Alignment.Center) { Icon(visual.icon, null, Modifier.size(17.dp), tint = visual.color) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.category, color = ExpenseColors.Ink, style = MaterialTheme.typography.titleMedium)
                Text("${(share * 100).roundToInt()}% · ${item.count} transaction${if (item.count == 1) "" else "s"}", color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("$currency ${StatisticsCalculator.money(item.totalMinor)}", color = ExpenseColors.Ink,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr),
                maxLines = 1)
        }
        Spacer(Modifier.height(11.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(ExpenseColors.ChartGrid, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(share).height(3.dp).background(visual.color.copy(alpha = .78f), RoundedCornerShape(2.dp)))
        }
    }
    HairlineDivider()
}

@Composable private fun MerchantRow(item: MerchantStatistic, currency: String, rank: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(rank.toString().padStart(2, '0'), color = ExpenseColors.InkTertiary,
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(item.identity, color = ExpenseColors.Ink, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.count} transaction${if (item.count == 1) "" else "s"}",
                color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text("$currency ${StatisticsCalculator.money(item.totalMinor)}", color = ExpenseColors.Ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr),
            fontWeight = FontWeight.Medium)
    }
}
@Composable private fun DeltaRow(item: CategoryDelta, currency: String) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) { Text(item.category, Modifier.weight(1f)); Text("${if (item.deltaMinor >= 0) "+" else "−"}$currency ${StatisticsCalculator.money(abs(item.deltaMinor))}", color = if (item.deltaMinor >= 0) ExpenseColors.Ink else ExpenseColors.InkSecondary) } }
@Composable private fun SectionTitle(title: String) { Text(title, color = ExpenseColors.Ink, style = MaterialTheme.typography.titleLarge) }
@Composable private fun SectionEyebrow(title: String) { Text(title, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium) }
private fun insightTitle(insight: StatisticsInsight): String = when (insight.kind) {
    StatisticsInsight.Kind.CATEGORY_BASELINE -> "A category moved"
    StatisticsInsight.Kind.HIGHEST_MONTH -> "A standout month"
    StatisticsInsight.Kind.SMALL_PURCHASES -> "Small purchases added up"
    StatisticsInsight.Kind.TOP_MERCHANT -> "Your top merchant"
    StatisticsInsight.Kind.WEEKDAY -> "Your busiest spending day"
    StatisticsInsight.Kind.LARGEST_PURCHASE -> "Largest purchase"
}
@Composable private fun StatisticsLoading() { Column { repeat(4) { Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(if (it == 0) 72.dp else 46.dp).background(ExpenseColors.SurfaceQuiet, RoundedCornerShape(18.dp))) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PeriodPickerSheet(state: StatisticsUiState, onDismiss: () -> Unit, onMonth: (YearMonth) -> Unit, onYear: (Int) -> Unit) {
    val months = (state.availableMonths + (0L..11L).map { YearMonth.now().minusMonths(it) }).distinct().sortedDescending().take(18)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IzavoMaterial.Sheet, scrimColor = ExpenseColors.Scrim, shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Choose period", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { months.map { it.year }.distinct().take(4).forEach { year -> QuietChoice(year.toString(), state.period.mode == StatisticsPeriodMode.YEAR && state.period.year == year) { onYear(year) } } }
            Spacer(Modifier.height(12.dp))
            months.take(12).forEach { month ->
                Row(Modifier.fillMaxWidth().height(48.dp).premiumClick { onMonth(month) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}", Modifier.weight(1f)); if (state.period.mode == StatisticsPeriodMode.MONTH && state.period.month == month) Text("✓", color = ExpenseColors.Cyan)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CurrencyPickerSheet(state: StatisticsUiState, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IzavoMaterial.Sheet, scrimColor = ExpenseColors.Scrim, shape = RoundedCornerShape(topStart = ExpenseShapes.Sheet, topEnd = ExpenseShapes.Sheet)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Statistics currency", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(14.dp))
            state.availableCurrencies.forEach { currency -> Row(Modifier.fillMaxWidth().height(52.dp).premiumClick { onSelect(currency) }, verticalAlignment = Alignment.CenterVertically) { Text(currency, Modifier.weight(1f)); if (currency == state.currencyCode) Text("✓", color = ExpenseColors.Cyan) } }
        }
    }
}
