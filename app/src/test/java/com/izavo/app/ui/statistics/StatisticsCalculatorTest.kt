package com.izavo.app.ui.statistics

import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.ExpenseMerchantMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class StatisticsCalculatorTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDate.of(2026, 9, 12)
    private val september = StatisticsPeriod(month = YearMonth.of(2026, 9), year = 2026)

    @Test fun monthlyTotalUsesOnlySelectedPeriod() {
        val result = calculate(expense(1, 1000, "2026-09-02"), expense(2, 2000, "2026-08-02"))
        assertEquals(1000, result.totalMinor)
    }

    @Test fun currenciesAreNeverCombined() {
        val rows = listOf(expense(1, 1000, "2026-09-02"), expense(2, 5000, "2026-09-02", currency = "USD"))
        assertEquals(1000, calculate(*rows.toTypedArray()).totalMinor)
        assertEquals(5000, StatisticsCalculator.calculate(rows, emptyList(), september, "USD", now, zone).totalMinor)
    }

    @Test fun categoriesSumToTotalAndCountTransactions() {
        val result = calculate(expense(1, 1000, "2026-09-02", category = "Food"), expense(2, 2500, "2026-09-03", category = "Food"), expense(3, 500, "2026-09-03", category = null))
        assertEquals(result.totalMinor, result.categories.sumOf { it.totalMinor })
        assertEquals(2, result.categories.first { it.category == "Food" }.count)
        assertEquals(1, result.categories.first { it.category == "Other" }.count)
    }

    @Test fun currentMonthComparesSameDaySpan() {
        val result = calculate(expense(1, 2000, "2026-09-10"), expense(2, 1000, "2026-08-10"), expense(3, 9000, "2026-08-20"))
        assertEquals(100, result.comparison?.percent)
        assertEquals(1000L, result.comparison?.previousMinor)
    }

    @Test fun completedMonthComparesFullPreviousMonth() {
        val period = StatisticsPeriod(month = YearMonth.of(2026, 8), year = 2026)
        val rows = listOf(expense(1, 3000, "2026-08-31"), expense(2, 2000, "2026-07-30"))
        assertEquals(50, StatisticsCalculator.calculate(rows, emptyList(), period, "MVR", now, zone).comparison?.percent)
    }

    @Test fun zeroPreviousNeverCreatesInfinitePercentage() {
        assertNull(calculate(expense(1, 1000, "2026-09-02")).comparison?.percent)
        assertNull(StatisticsCalculator.percentChange(1000, 0))
    }

    @Test fun percentageMathSupportsDecrease() { assertEquals(-25, StatisticsCalculator.percentChange(750, 1000)) }

    @Test fun topCategoryIsSortedFirst() {
        val result = calculate(expense(1, 1000, "2026-09-02", category = "Food"), expense(2, 4000, "2026-09-03", category = "Bills"))
        assertEquals("Bills", result.categories.first().category)
    }

    @Test fun merchantUsesLinkedLedgerMetadataWithoutDoubleCounting() {
        val row = expense(7, 2500, "2026-09-02", category = "Shopping")
        val metadata = listOf(ExpenseMerchantMetadata(7, "ALIEXPRESS", "AliExpress"))
        val result = StatisticsCalculator.calculate(listOf(row), metadata, september, "MVR", now, zone)
        assertEquals(2500, result.totalMinor)
        assertEquals(2500, result.merchants.single().totalMinor)
        assertEquals("Shopping", result.merchants.single().category)
    }

    @Test fun manualExpenseContributesWithoutFakeMerchant() {
        val result = calculate(expense(1, 1800, "2026-09-02", note = "Coffee"))
        assertEquals(1800, result.totalMinor)
        assertTrue(result.merchants.isEmpty())
    }

    @Test fun unstableNumericMerchantIdentityIsIgnored() {
        val row = expense(7, 2500, "2026-09-02")
        val result = StatisticsCalculator.calculate(listOf(row), listOf(ExpenseMerchantMetadata(7, "123456", "123456")), september, "MVR", now, zone)
        assertTrue(result.merchants.isEmpty())
    }

    @Test fun mvrSmallPurchasesInsightMeetsThreshold() {
        val result = calculate(expense(1, 6000, "2026-09-02"), expense(2, 5000, "2026-09-03"))
        assertTrue(result.insights.any { it.kind == StatisticsInsight.Kind.SMALL_PURCHASES })
    }

    @Test fun smallPurchaseInsightIsOmittedForUsd() {
        val rows = listOf(expense(1, 6000, "2026-09-02", currency = "USD"), expense(2, 5000, "2026-09-03", currency = "USD"))
        val result = StatisticsCalculator.calculate(rows, emptyList(), september, "USD", now, zone)
        assertFalse(result.insights.any { it.kind == StatisticsInsight.Kind.SMALL_PURCHASES })
    }

    @Test fun behavioralBaselineRequiresEnoughHistory() {
        val result = calculate(expense(1, 50000, "2026-09-02", category = "Shopping"), expense(2, 10000, "2026-08-02", category = "Shopping"))
        assertFalse(result.insights.any { it.kind == StatisticsInsight.Kind.CATEGORY_BASELINE })
    }

    @Test fun sixMonthBaselineCanProduceCategoryInsight() {
        val rows = mutableListOf(expense(1, 60000, "2026-09-02", category = "Shopping"))
        (1..6).forEach { index -> rows += expense((index + 1).toLong(), 10000, YearMonth.of(2026, 9).minusMonths(index.toLong()).atDay(2).toString(), category = "Shopping") }
        assertTrue(calculate(*rows.toTypedArray()).insights.any { it.kind == StatisticsInsight.Kind.CATEGORY_BASELINE })
    }

    @Test fun uniqueHighestMonthRequiresFourActiveMonths() {
        val rows = listOf(expense(1, 60000, "2026-09-02"), expense(2, 10000, "2026-08-02"), expense(3, 9000, "2026-07-02"), expense(4, 8000, "2026-06-02"))
        assertTrue(calculate(*rows.toTypedArray()).insights.any { it.kind == StatisticsInsight.Kind.HIGHEST_MONTH })
    }

    @Test fun weekdayInsightRequiresMultipleDatesAndWeekdays() {
        val result = calculate(expense(1, 1000, "2026-09-04"), expense(2, 1000, "2026-09-04"), expense(3, 4000, "2026-09-05"))
        assertTrue(result.insights.any { it.kind == StatisticsInsight.Kind.WEEKDAY })
    }

    @Test fun meaningfulChangeCreatesCategoryDecomposition() {
        val result = calculate(expense(1, 3000, "2026-08-02", category = "Food"), expense(2, 6000, "2026-09-02", category = "Shopping"))
        assertEquals("Shopping", result.decomposition?.largestDriver)
        assertEquals(3000L, result.decomposition?.totalDeltaMinor)
    }

    @Test fun emptyPeriodIsIntentionalAndSafe() { assertEquals(0, calculate().totalMinor) }

    @Test fun negativeAndZeroRowsAreExcluded() {
        val result = calculate(expense(1, -1000, "2026-09-02"), expense(2, 0, "2026-09-03"), expense(3, 400, "2026-09-04"))
        assertEquals(400, result.totalMinor)
    }

    @Test fun largeValuesRemainLongAccurate() {
        val amount = 900_000_000_000L
        assertEquals(amount * 2, calculate(expense(1, amount, "2026-09-02"), expense(2, amount, "2026-09-03")).totalMinor)
    }

    @Test fun storedTimestampControlsDailyBucket() {
        val result = calculate(expense(1, 1000, "2026-09-12"))
        assertEquals(1000, result.chartPoints.first { it.label == "12" }.amountMinor)
    }

    @Test fun trendAggregationIncludesZeroCalendarMonths() {
        val points = StatisticsCalculator.trendPoints(listOf(expense(1, 1000, "2026-09-02")), TrendRange.THREE_MONTHS, now, zone)
        assertEquals(3, points.size)
        assertEquals(listOf(0L, 0L, 1000L), points.map { it.amountMinor })
    }

    @Test fun fiveThousandExpensesAggregateWithoutPerRowDatabaseWork() {
        val rows = (1L..5_000L).map { id -> expense(id, 125, "2026-09-${((id - 1) % 12 + 1).toString().padStart(2, '0')}", category = if (id % 2L == 0L) "Food" else "Transport") }
        val result = StatisticsCalculator.calculate(rows, emptyList(), september, "MVR", now, zone)
        assertEquals(625_000L, result.totalMinor)
        assertEquals(2, result.categories.size)
    }

    private fun calculate(vararg rows: ExpenseEntity) = StatisticsCalculator.calculate(rows.toList(), emptyList(), september, "MVR", now, zone)

    private fun expense(id: Long, amount: Long, date: String, category: String? = "Other", currency: String = "MVR", note: String = "") = ExpenseEntity(
        id = id, amountMinor = amount, currencyCode = currency, category = category, note = note,
        occurredAt = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(), createdAt = 1L
    )
}
