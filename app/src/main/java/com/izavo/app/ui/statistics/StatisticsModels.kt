package com.izavo.app.ui.statistics

import com.izavo.app.data.ExpenseEntity
import java.time.LocalDate
import java.time.YearMonth

enum class StatisticsSection { OVERVIEW, CATEGORIES, TRENDS }
enum class StatisticsPeriodMode { MONTH, YEAR }
enum class TrendRange(val months: Int?) { THREE_MONTHS(3), SIX_MONTHS(6), ONE_YEAR(12), ALL(null) }

data class StatisticsPeriod(
    val mode: StatisticsPeriodMode = StatisticsPeriodMode.MONTH,
    val month: YearMonth = YearMonth.now(),
    val year: Int = YearMonth.now().year
) {
    val label: String get() = when (mode) {
        StatisticsPeriodMode.MONTH -> month.month.name.lowercase().replaceFirstChar(Char::uppercase) + " ${month.year}"
        StatisticsPeriodMode.YEAR -> year.toString()
    }
}

data class SpendPoint(val label: String, val amountMinor: Long, val epochDay: Long? = null)
data class CategoryStatistic(val category: String, val totalMinor: Long, val count: Int, val share: Float, val comparisonPercent: Int?)
data class MerchantStatistic(val identity: String, val category: String, val totalMinor: Long, val count: Int)
data class StatisticMetric(val label: String, val value: String)
data class SpendingComparison(val currentMinor: Long, val previousMinor: Long, val percent: Int?, val previousLabel: String)
data class CategoryDelta(val category: String, val deltaMinor: Long)
data class SpendingDecomposition(val totalDeltaMinor: Long, val rows: List<CategoryDelta>, val largestDriver: String)
data class StatisticsInsight(val kind: Kind, val text: String) {
    enum class Kind { CATEGORY_BASELINE, HIGHEST_MONTH, SMALL_PURCHASES, TOP_MERCHANT, WEEKDAY, LARGEST_PURCHASE }
}

data class StatisticsSnapshot(
    val totalMinor: Long = 0,
    val comparison: SpendingComparison? = null,
    val chartPoints: List<SpendPoint> = emptyList(),
    val metrics: List<StatisticMetric> = emptyList(),
    val categories: List<CategoryStatistic> = emptyList(),
    val merchants: List<MerchantStatistic> = emptyList(),
    val insights: List<StatisticsInsight> = emptyList(),
    val decomposition: SpendingDecomposition? = null,
    val expenses: List<ExpenseEntity> = emptyList(),
    val merchantLinkedExpenseIds: Set<Long> = emptySet()
)

data class StatisticsUiState(
    val loading: Boolean = true,
    val section: StatisticsSection = StatisticsSection.OVERVIEW,
    val period: StatisticsPeriod = StatisticsPeriod(),
    val trendRange: TrendRange = TrendRange.SIX_MONTHS,
    val currencyCode: String = "MVR",
    val availableCurrencies: List<String> = emptyList(),
    val availableMonths: List<YearMonth> = emptyList(),
    val snapshot: StatisticsSnapshot = StatisticsSnapshot(),
    val trendPoints: List<SpendPoint> = emptyList(),
    val selectedCategory: String? = null
)

internal data class DateBounds(val start: LocalDate, val endExclusive: LocalDate)
