package com.izavo.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseCsvExporterTest {
    @Test
    fun escapesCommasQuotesAndLineBreaks() {
        assertEquals("\"coffee, lunch\"", ExpenseCsvExporter.escape("coffee, lunch"))
        assertEquals("\"She said \"\"great\"\"\"", ExpenseCsvExporter.escape("She said \"great\""))
        assertEquals("\"line one\nline two\"", ExpenseCsvExporter.escape("line one\nline two"))
    }

    @Test
    fun preservesUnicodeAndPlainValues() {
        assertEquals("☕ 日本語 ދިވެހި", ExpenseCsvExporter.escape("☕ 日本語 ދިވެހި"))
        assertEquals("MVR", ExpenseCsvExporter.escape("MVR"))
    }

    @Test
    fun isolatesDhivehiRunsInsideMixedText() {
        assertEquals(
            "Coffee \u2067ދިވެހި\u2069 test",
            ExpenseCsvExporter.stabilizeDirection("Coffee ދިވެހި test")
        )
        assertEquals("plain English", ExpenseCsvExporter.stabilizeDirection("plain English"))
    }
}
