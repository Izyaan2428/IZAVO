package com.izavo.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class BenchmarkHarnessSafetyTest {
    @Test fun controlledGroundTruthSeparatesAutomaticPrecisionFromAbstention() {
        val statement = listOf(
            row("Purchase", "CORAL CAFE", "P1", "10", ""),
            row("Salary", "ACME EMPLOYER", "S1", "", "100"),
            row("ATM Withdrawal", "ATM", "A1", "20", ""),
            row("Favara Credit", "AHMED", "F1", "", "15")
        ).joinToString("\n")
        val truth = "Primary Reference,Expected Meaning,Expected Category,Should Require Review\n" +
            "P1,EXPENSE,Food,false\n" +
            "S1,INCOME,,false\n" +
            "A1,MONEY_MOVEMENT,,false\n" +
            "F1,UNKNOWN,,true"
        val metrics = SmartImportBenchmark.measure(StringReader(statement), StringReader(truth)).first

        assertEquals(4, metrics.uniqueRows)
        assertEquals(3, metrics.automaticRows)
        assertEquals(1, metrics.reviewRows)
        assertEquals(0, metrics.catastrophicErrors)
        assertEquals(100.0, metrics.selectivePrecisionPercent ?: 0.0, 0.001)
        assertEquals(1, metrics.reviewByTruth[ImportDecision.UNKNOWN])
        assertTrue(metrics.confusion.isNotEmpty())
    }

    @Test fun duplicateGroundTruthKeepsTheEligibleFirstOccurrence() {
        val original = row("Favara Debit", "MY OTHER BANK ACCOUNT", "F1", "15", "")
        val statement = "$original\n$original"
        val truth = "Financial Meaning,Should Require Review\n" +
            "MONEY_MOVEMENT,true\n" +
            "MONEY_MOVEMENT,false"

        val metrics = SmartImportBenchmark.measure(StringReader(statement), StringReader(truth)).first

        assertEquals(1, metrics.uniqueRows)
        assertEquals(1, metrics.duplicateRows)
        assertEquals(1, metrics.reviewRows)
        assertEquals(0, metrics.reviewedRowsMarkedAutomatic)
    }

    private fun row(type: String, description: String, reference: String, debit: String, credit: String) =
        listOf("2026/09/02", "2026/09/02", type, "=\"$reference\"", "=\"FT-$reference\"",
            "02-09-2026 18-42-37", "=\"$description\"", "detail", debit, credit, "1000")
            .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
}
