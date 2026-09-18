package com.izavo.app.ui.statistics

import com.izavo.app.data.ExpenseCategories
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.ExpenseMerchantMetadata
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object StatisticsCalculator {
    fun calculate(
        expenses: List<ExpenseEntity>, metadata: List<ExpenseMerchantMetadata>, period: StatisticsPeriod,
        currencyCode: String, now: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()
    ): StatisticsSnapshot {
        val positive = expenses.filter { it.amountMinor > 0 && it.currencyCode == currencyCode }
        val bounds = periodBounds(period, now)
        val selected = positive.inBounds(bounds, zoneId)
        val priorBounds = previousBounds(period, now)
        val previous = priorBounds?.let { positive.inBounds(it, zoneId) }.orEmpty()
        val total = selected.sumOf(ExpenseEntity::amountMinor)
        val previousTotal = previous.sumOf(ExpenseEntity::amountMinor)
        val comparison = priorBounds?.let { SpendingComparison(total, previousTotal, percentChange(total, previousTotal), previousLabel(period)) }
        val previousCategories = previous.groupBy { ExpenseCategories.normalized(it.category) }.mapValues { it.value.sumOf(ExpenseEntity::amountMinor) }
        val categories = selected.groupBy { ExpenseCategories.normalized(it.category) }.map { (category, rows) ->
            val amount = rows.sumOf(ExpenseEntity::amountMinor)
            CategoryStatistic(category, amount, rows.size, if (total == 0L) 0f else amount.toFloat() / total,
                percentChange(amount, previousCategories[category] ?: 0L))
        }.sortedByDescending(CategoryStatistic::totalMinor)
        val metadataByExpense = metadata.associateBy { it.expenseId }
        val merchants = selected.mapNotNull { expense ->
            metadataByExpense[expense.id]?.let(::stableMerchant)?.let { Triple(it, ExpenseCategories.normalized(expense.category), expense) }
        }.groupBy({ it.first to it.second }, { it.third }).map { (identityAndCategory, rows) ->
            MerchantStatistic(identityAndCategory.first, identityAndCategory.second, rows.sumOf(ExpenseEntity::amountMinor), rows.size)
        }.sortedByDescending(MerchantStatistic::totalMinor)
        val chart = if (period.mode == StatisticsPeriodMode.MONTH) dailyPoints(selected, bounds, zoneId) else monthlyPoints(selected, period.year, zoneId)
        val metrics = metrics(selected, period, zoneId, currencyCode)
        val decomposition = decomposition(total, previousTotal, categories, previousCategories)
        val insights = StatisticsInsights.generate(positive, selected, categories, merchants, period, currencyCode, now, zoneId)
        return StatisticsSnapshot(total, comparison, chart, metrics, categories, merchants, insights, decomposition,
            selected.sortedByDescending(ExpenseEntity::occurredAt), selected.mapNotNull { expense -> metadataByExpense[expense.id]?.let(::stableMerchant)?.let { expense.id } }.toSet())
    }

    fun trendPoints(expenses: List<ExpenseEntity>, range: TrendRange, now: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): List<SpendPoint> {
        val end = YearMonth.from(now)
        val totals = expenses.filter { it.amountMinor > 0 }.groupBy { YearMonth.from(localDate(it, zoneId)) }.mapValues { it.value.sumOf(ExpenseEntity::amountMinor) }
        val first = range.months?.let { end.minusMonths((it - 1).toLong()) } ?: totals.keys.minOrNull() ?: end
        return generateSequence(first) { if (it < end) it.plusMonths(1) else null }.map { month ->
            SpendPoint(month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), totals[month] ?: 0L)
        }.toList()
    }

    private fun List<ExpenseEntity>.inBounds(bounds: DateBounds, zone: ZoneId) = filter {
        val date = localDate(it, zone); date >= bounds.start && date < bounds.endExclusive
    }

    private fun dailyPoints(rows: List<ExpenseEntity>, bounds: DateBounds, zone: ZoneId): List<SpendPoint> {
        val totals = rows.groupBy { localDate(it, zone) }.mapValues { it.value.sumOf(ExpenseEntity::amountMinor) }
        return generateSequence(bounds.start) { if (it.plusDays(1) < bounds.endExclusive) it.plusDays(1) else null }
            .map { SpendPoint(it.dayOfMonth.toString(), totals[it] ?: 0L, it.toEpochDay()) }.toList()
    }

    private fun monthlyPoints(rows: List<ExpenseEntity>, year: Int, zone: ZoneId): List<SpendPoint> {
        val totals = rows.groupBy { YearMonth.from(localDate(it, zone)) }.mapValues { it.value.sumOf(ExpenseEntity::amountMinor) }
        return (1..12).map { month -> val ym = YearMonth.of(year, month); SpendPoint(ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), totals[ym] ?: 0L) }
    }

    private fun metrics(rows: List<ExpenseEntity>, period: StatisticsPeriod, zone: ZoneId, currency: String): List<StatisticMetric> {
        if (rows.isEmpty()) return emptyList()
        return if (period.mode == StatisticsPeriodMode.MONTH) {
            val groups = rows.groupBy { localDate(it, zone) }.mapValues { it.value.sumOf(ExpenseEntity::amountMinor) }
            val highest = groups.maxBy { it.value }
            listOf(StatisticMetric("Average day", "$currency ${money(rows.sumOf(ExpenseEntity::amountMinor) / groups.size)}"),
                StatisticMetric("Highest day", "${highest.key.dayOfMonth} ${highest.key.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · $currency ${money(highest.value)}"))
        } else {
            val groups = rows.groupBy { YearMonth.from(localDate(it, zone)) }.mapValues { it.value.sumOf(ExpenseEntity::amountMinor) }
            val highest = groups.maxBy { it.value }
            listOf(StatisticMetric("Average month", "$currency ${money(rows.sumOf(ExpenseEntity::amountMinor) / groups.size)}"),
                StatisticMetric("Highest month", "${highest.key.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · $currency ${money(highest.value)}"))
        }
    }

    private fun decomposition(total: Long, previousTotal: Long, categories: List<CategoryStatistic>, prior: Map<String, Long>): SpendingDecomposition? {
        if (previousTotal <= 0) return null
        val delta = total - previousTotal
        if (abs(delta) < 100L || abs(delta.toDouble() / previousTotal) < .10) return null
        val rows = (ExpenseCategories.all + prior.keys + categories.map(CategoryStatistic::category)).distinct().mapNotNull { category ->
            val change = (categories.firstOrNull { it.category == category }?.totalMinor ?: 0L) - (prior[category] ?: 0L)
            change.takeIf { it != 0L }?.let { CategoryDelta(category, it) }
        }.sortedByDescending { abs(it.deltaMinor) }
        return rows.firstOrNull()?.let { SpendingDecomposition(delta, rows, it.category) }
    }

    internal fun periodBounds(period: StatisticsPeriod, now: LocalDate): DateBounds = when (period.mode) {
        StatisticsPeriodMode.MONTH -> period.month.atDay(1).let { start -> DateBounds(start, if (period.month == YearMonth.from(now)) now.plusDays(1) else period.month.plusMonths(1).atDay(1)) }
        StatisticsPeriodMode.YEAR -> LocalDate.of(period.year, 1, 1).let { start -> DateBounds(start, if (period.year == now.year) now.plusDays(1) else start.plusYears(1)) }
    }

    private fun previousBounds(period: StatisticsPeriod, now: LocalDate): DateBounds = when (period.mode) {
        StatisticsPeriodMode.MONTH -> {
            val current = periodBounds(period, now); val start = period.month.minusMonths(1).atDay(1)
            DateBounds(start, minOf(start.plusDays(java.time.temporal.ChronoUnit.DAYS.between(current.start, current.endExclusive)), period.month.atDay(1)))
        }
        StatisticsPeriodMode.YEAR -> {
            val current = periodBounds(period, now); val start = LocalDate.of(period.year - 1, 1, 1)
            DateBounds(start, minOf(start.plusDays(java.time.temporal.ChronoUnit.DAYS.between(current.start, current.endExclusive)), start.plusYears(1)))
        }
    }

    private fun previousLabel(period: StatisticsPeriod) = if (period.mode == StatisticsPeriodMode.MONTH) period.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else (period.year - 1).toString()
    internal fun percentChange(current: Long, previous: Long): Int? = if (previous <= 0) null else (((current - previous).toDouble() / previous) * 100).roundToInt()
    internal fun localDate(expense: ExpenseEntity, zone: ZoneId): LocalDate = Instant.ofEpochMilli(expense.occurredAt).atZone(zone).toLocalDate()
    internal fun money(minor: Long) = String.format(Locale.US, "%,.2f", minor / 100.0)
    private fun stableMerchant(row: ExpenseMerchantMetadata): String? {
        val normalized = row.normalizedIdentity?.trim().orEmpty()
        if (normalized.length < 3 || normalized.all { it.isDigit() || it.isWhitespace() || it in "-_/" }) return null
        return row.rawDescription.trim().replace(Regex("\\s+"), " ").takeIf { it.length >= 3 }
            ?: normalized.lowercase().replaceFirstChar(Char::uppercase)
    }
}

object StatisticsInsights {
    fun generate(all: List<ExpenseEntity>, selected: List<ExpenseEntity>, categories: List<CategoryStatistic>, merchants: List<MerchantStatistic>,
                 period: StatisticsPeriod, currency: String, now: LocalDate, zone: ZoneId): List<StatisticsInsight> {
        if (selected.isEmpty()) return emptyList()
        val result = mutableListOf<StatisticsInsight>()
        categoryBaseline(all, categories, period, zone)?.let(result::add)
        highestMonth(all, period, zone)?.let(result::add)
        if (currency == "MVR") {
            val small = selected.filter { it.amountMinor < 10_000L }; val sum = small.sumOf(ExpenseEntity::amountMinor)
            if (small.size >= 2 && sum >= 10_000L) result += StatisticsInsight(StatisticsInsight.Kind.SMALL_PURCHASES, "Purchases under MVR 100 added up to MVR ${StatisticsCalculator.money(sum)}.")
        }
        merchants.groupBy { it.identity }.map { (identity, rows) -> MerchantStatistic(identity, rows.first().category, rows.sumOf { it.totalMinor }, rows.sumOf { it.count }) }
            .maxByOrNull { it.totalMinor }?.let { result += StatisticsInsight(StatisticsInsight.Kind.TOP_MERCHANT, "${it.identity} accounted for $currency ${StatisticsCalculator.money(it.totalMinor)}.") }
        val dates = selected.map { StatisticsCalculator.localDate(it, zone) }
        if (selected.size >= 3 && dates.distinct().size >= 2 && dates.map(LocalDate::getDayOfWeek).distinct().size >= 2) {
            val day = selected.groupBy { StatisticsCalculator.localDate(it, zone).dayOfWeek }.maxBy { it.value.sumOf(ExpenseEntity::amountMinor) }.key
            result += StatisticsInsight(StatisticsInsight.Kind.WEEKDAY, "${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} was your highest-spending day.")
        }
        val largest = selected.maxBy(ExpenseEntity::amountMinor)
        val title = largest.note.takeIf(String::isNotBlank) ?: ExpenseCategories.normalized(largest.category)
        result += StatisticsInsight(StatisticsInsight.Kind.LARGEST_PURCHASE, "Your largest purchase was $currency ${StatisticsCalculator.money(largest.amountMinor)} · $title.")
        return result.distinctBy(StatisticsInsight::text).take(4)
    }

    private fun categoryBaseline(all: List<ExpenseEntity>, current: List<CategoryStatistic>, period: StatisticsPeriod, zone: ZoneId): StatisticsInsight? {
        if (period.mode != StatisticsPeriodMode.MONTH) return null
        val months = (1L..6L).map(period.month::minusMonths)
        if (months.count { month -> all.any { YearMonth.from(StatisticsCalculator.localDate(it, zone)) == month } } < 4) return null
        if (all.minOfOrNull { YearMonth.from(StatisticsCalculator.localDate(it, zone)) }?.isAfter(period.month.minusMonths(6)) != false) return null
        val selectedTotal = current.sumOf(CategoryStatistic::totalMinor)
        return current.firstNotNullOfOrNull { category ->
            val average = months.sumOf { month -> all.filter { YearMonth.from(StatisticsCalculator.localDate(it, zone)) == month && ExpenseCategories.normalized(it.category) == category.category }.sumOf(ExpenseEntity::amountMinor) } / 6
            val pct = StatisticsCalculator.percentChange(category.totalMinor, average) ?: return@firstNotNullOfOrNull null
            if (abs(pct) >= 20 && abs(category.totalMinor - average) >= (selectedTotal * .05).toLong())
                StatisticsInsight(StatisticsInsight.Kind.CATEGORY_BASELINE, "${category.category} was ${abs(pct)}% ${if (pct > 0) "higher" else "lower"} than its 6-month average.") else null
        }
    }

    private fun highestMonth(all: List<ExpenseEntity>, period: StatisticsPeriod, zone: ZoneId): StatisticsInsight? {
        if (period.mode != StatisticsPeriodMode.MONTH) return null
        val months = (0L..5L).map(period.month::minusMonths)
        val totals = months.associateWith { month -> all.filter { YearMonth.from(StatisticsCalculator.localDate(it, zone)) == month }.sumOf(ExpenseEntity::amountMinor) }
        val max = totals.maxOf { it.value }
        return if (totals.count { it.value > 0 } >= 4 && totals[period.month] == max && totals.count { it.value == max } == 1)
            StatisticsInsight(StatisticsInsight.Kind.HIGHEST_MONTH, "This is your highest-spending month in 6 months.") else null
    }
}
