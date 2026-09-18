package com.izavo.app.ui.home

import com.izavo.app.data.ExpenseEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HomeSevenDayTrendTest {
    private val zone = ZoneId.of("UTC")
    private fun expense(date: LocalDate, amount: Long, currency: String = "MVR") = ExpenseEntity(
        amountMinor = amount, currencyCode = currency, category = "Other", note = "",
        occurredAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    )

    @Test fun returnsExactlySevenBucketsOldestToToday() {
        val today = LocalDate.of(2026, 9, 5)
        val result = sevenDaySpending(emptyList(), "MVR", today, zone)
        assertEquals(7, result.size)
        assertEquals(today.minusDays(6), result.first().date)
        assertEquals(today, result.last().date)
        assertEquals(result.map { it.date }.sorted(), result.map { it.date })
    }

    @Test fun retainsZeroSpendDays() {
        val today = LocalDate.of(2026, 9, 5)
        val result = sevenDaySpending(listOf(expense(today.minusDays(2), 500)), "MVR", today, zone)
        assertEquals(listOf(0L, 0L, 0L, 0L, 500L, 0L, 0L), result.map { it.amountMinor })
    }

    @Test fun crossesMonthBoundary() {
        val today = LocalDate.of(2026, 9, 3)
        val date = LocalDate.of(2026, 8, 29)
        assertEquals(125L, sevenDaySpending(listOf(expense(date, 125)), "MVR", today, zone).single { it.date == date }.amountMinor)
    }

    @Test fun crossesYearBoundary() {
        val today = LocalDate.of(2027, 1, 2)
        val date = LocalDate.of(2026, 12, 29)
        assertEquals(225L, sevenDaySpending(listOf(expense(date, 225)), "MVR", today, zone).single { it.date == date }.amountMinor)
    }

    @Test fun sumsMultipleExpensesOnSameDay() {
        val today = LocalDate.of(2026, 9, 5)
        assertEquals(350L, sevenDaySpending(listOf(expense(today, 100), expense(today, 250)), "MVR", today, zone).last().amountMinor)
    }

    @Test fun excludesOtherCurrencies() {
        val today = LocalDate.of(2026, 9, 5)
        assertEquals(100L, sevenDaySpending(listOf(expense(today, 100), expense(today, 900, "USD")), "MVR", today, zone).last().amountMinor)
    }

    @Test fun allZeroInputIsRepresentedHonestly() {
        assertTrue(sevenDaySpending(emptyList(), "MVR", LocalDate.of(2026, 9, 5), zone).all { it.amountMinor == 0L })
    }

    @Test fun ignoresNonPositiveRowsAndDatesOutsideWindow() {
        val today = LocalDate.of(2026, 9, 5)
        val rows = listOf(expense(today, 0), expense(today, -50), expense(today.minusDays(7), 500))
        assertTrue(sevenDaySpending(rows, "MVR", today, zone).all { it.amountMinor == 0L })
    }

    @Test fun countsImportedExpenseOnceThroughExpenseEntitySourceOfTruth() {
        val today = LocalDate.of(2026, 9, 5)
        assertEquals(42900L, sevenDaySpending(listOf(expense(today, 42900)), "MVR", today, zone).last().amountMinor)
    }

    @Test fun respectsLocalDateInProvidedTimezone() {
        val male = ZoneId.of("Indian/Maldives")
        val today = LocalDate.of(2026, 9, 5)
        val row = ExpenseEntity(amountMinor = 100, currencyCode = "MVR", category = null, note = "",
            occurredAt = today.atStartOfDay(male).toInstant().toEpochMilli())
        assertEquals(100L, sevenDaySpending(listOf(row), "MVR", today, male).last().amountMinor)
    }
}
