package com.izavo.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.ZoneOffset

class BmlCsvParserTest {
    private val parser = BmlCsvParser(ZoneOffset.UTC)

    @Test fun purchaseDebitIsExpenseCandidate() {
        assertParsed(type = "Purchase", debit = "24").single().also {
            assertEquals(NormalizedTransactionType.EXPENSE, it.normalizedType)
            assertEquals(ImportReviewStatus.READY, it.reviewStatus)
        }
    }

    @Test fun purchaseCreditIsRefundCandidate() {
        assertEquals(NormalizedTransactionType.REFUND, assertParsed(type = "Purchase", debit = "", credit = "24").single().normalizedType)
    }

    @Test fun matchingPurchaseDebitAndCreditBecomeNetZeroReversalPair() {
        val result = parser.parse(StringReader(row(debit = "24") + "\n" + row(debit = "", credit = "24")))
        assertEquals(2, result.candidates.count { it.reviewStatus == ImportReviewStatus.REVERSED })
    }

    @Test fun salaryCreditIsIncome() {
        assertEquals(NormalizedTransactionType.INCOME, assertParsed(type = "Salary", debit = "", credit = "1000").single().normalizedType)
    }

    @Test fun favaraCreditIsIncomingTransfer() {
        assertEquals(NormalizedTransactionType.INCOMING_TRANSFER, assertParsed(type = "Favara Credit", debit = "", credit = "10").single().normalizedType)
    }

    @Test fun favaraDebitRequiresReview() {
        assertEquals(ImportReviewStatus.NEEDS_REVIEW, assertParsed(type = "Favara Debit", debit = "10").single().reviewStatus)
    }

    @Test fun transferDebitRequiresReview() {
        assertEquals(ImportReviewStatus.NEEDS_REVIEW, assertParsed(type = "Transfer Debit", debit = "10").single().reviewStatus)
    }

    @Test fun transferCreditIsIncomingTransfer() {
        assertEquals(NormalizedTransactionType.INCOMING_TRANSFER, assertParsed(type = "Transfer Credit", debit = "", credit = "10").single().normalizedType)
    }

    @Test fun blankOptionalFieldsAreSafe() {
        val item = assertParsed(primary = "", secondary = "", description = "", detail = "", debit = "5").single()
        assertNull(item.primaryReference); assertEquals("Purchase", item.displayName)
    }

    @Test fun excelWrappedReferencesAreNormalized() {
        val item = assertParsed(primary = "=\"RB123\"", secondary = "=\"FT456\\B26\"").single()
        assertEquals("RB123", item.primaryReference); assertEquals("FT456\\B26", item.secondaryReference)
    }

    @Test fun malformedRowDoesNotAbortValidRows() {
        val result = parser.parse(StringReader("bad,row\n" + row()))
        assertEquals(1, result.candidates.size); assertEquals(1, result.invalidRowCount)
    }

    @Test fun duplicateImportOfIdenticalRowIsSkipped() {
        val candidate = assertParsed().single()
        assertEquals(ImportReviewStatus.DUPLICATE, markDuplicates(listOf(candidate), setOf(candidate.fingerprint)).single().reviewStatus)
    }

    @Test fun overlappingStatementDoesNotDuplicatePriorTransaction() {
        val first = assertParsed(primary = "RB1").single()
        val second = assertParsed(primary = "RB2").single()
        val marked = markDuplicates(listOf(first, second), setOf(first.fingerprint))
        assertEquals(1, marked.count { it.reviewStatus == ImportReviewStatus.DUPLICATE })
    }

    @Test fun amountUsesExactMinorUnits() {
        assertEquals(1395786L, assertParsed(debit = "13957.86").single().amountMinor)
    }

    @Test fun transactionDateIsParsedAndPostedDateIsFallback() {
        assertNotNull(assertParsed(detail = "30-08-2026 21-31-26").single().transactionAt)
        assertNull(assertParsed(detail = "counterparty").single().transactionAt)
    }

    @Test fun rawDescriptionIsPreserved() {
        assertEquals("MPVILLA MART", assertParsed(description = "=\"MPVILLA MART\"").single().rawDescription)
    }

    @Test fun unknownTypeRequiresReview() {
        val item = assertParsed(type = "Something New").single()
        assertEquals(NormalizedTransactionType.UNKNOWN, item.normalizedType)
        assertEquals(ImportReviewStatus.NEEDS_REVIEW, item.reviewStatus)
    }

    private fun assertParsed(
        type: String = "Purchase",
        primary: String = "=\"RB123\"",
        secondary: String = "=\"FT456\\B26\"",
        detail: String = "27-08-2026",
        description: String = "=\"MPVILLA MART\"",
        debit: String = "24",
        credit: String = ""
    ): List<CandidateTransaction> {
        val result = parser.parse(StringReader(row(type, primary, secondary, detail, description, debit, credit)))
        assertTrue(result.errors.joinToString().ifBlank { "ok" }, result.candidates.isNotEmpty())
        return result.candidates
    }

    private fun row(
        type: String = "Purchase",
        primary: String = "=\"RB123\"",
        secondary: String = "=\"FT456\\B26\"",
        detail: String = "27-08-2026",
        description: String = "=\"MPVILLA MART\"",
        debit: String = "24",
        credit: String = ""
    ): String = listOf("2026/08/30", "2026/08/30", type, primary, secondary, detail, description, "MALE MDV 260828", debit, credit, "382.8")
        .joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
}
