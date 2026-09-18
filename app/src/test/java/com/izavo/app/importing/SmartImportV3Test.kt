package com.izavo.app.importing

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.time.ZoneOffset

class SmartImportV3Test {
    private val parser = BmlCsvParser(ZoneOffset.UTC)
    private val classifier = SmartTransactionClassifier()

    @Test fun onlyExpenseDecisionCreatesExpense() {
        assertTrue(createsExpense(ImportDecision.EXPENSE))
        ImportDecision.entries.filterNot { it == ImportDecision.EXPENSE }.forEach { assertFalse(createsExpense(it)) }
    }

    @Test fun outgoingPaidForSomethingBecomesExpense() = assertDecision(ImportDecision.EXPENSE, NormalizedTransactionType.EXPENSE, true)
    @Test fun outgoingMovedMoneyStaysLedgerOnly() = assertDecision(ImportDecision.MONEY_MOVEMENT, NormalizedTransactionType.MONEY_MOVEMENT, false)
    @Test fun outgoingNotSureIsUnknownAndLedgerOnly() = assertDecision(ImportDecision.UNKNOWN, NormalizedTransactionType.UNKNOWN, false)
    @Test fun incomingIncomeStaysLedgerOnly() = assertDecision(ImportDecision.INCOME, NormalizedTransactionType.INCOME, false)
    @Test fun incomingMovementStaysLedgerOnly() = assertDecision(ImportDecision.MONEY_MOVEMENT, NormalizedTransactionType.MONEY_MOVEMENT, false)
    @Test fun incomingRefundStaysLedgerOnly() = assertDecision(ImportDecision.REFUND, NormalizedTransactionType.REFUND, false)
    @Test fun incomingNotSureIsUnknownAndLedgerOnly() = assertDecision(ImportDecision.UNKNOWN, NormalizedTransactionType.UNKNOWN, false)
    @Test fun ignoreIsDistinctFromUnknownAndLedgerOnly() = assertDecision(ImportDecision.IGNORE, NormalizedTransactionType.IGNORE, false)

    @Test fun incomingAndOutgoingSameIdentityNeverGroupTogether() {
        val out = review(classified("Favara Debit", "AHMED NIYAZ", debit = "10", credit = "", ref = "OUT"))
        val incoming = review(classified("Favara Credit", "AHMED NIYAZ", debit = "", credit = "10", ref = "IN"))
        val groups = ReviewGroupBuilder.build(listOf(out, incoming))
        assertEquals(2, groups.size)
        assertEquals(setOf(TransactionDirection.DEBIT, TransactionDirection.CREDIT), groups.map { it.direction }.toSet())
    }

    @Test fun businessSuffixAloneDoesNotGroupGenericTransfers() {
        val groups = ReviewGroupBuilder.build(listOf(
            review(classified("Transfer Debit", "ECHBEES LLP", ref = "A")),
            review(classified("Transfer Debit", "ECHBEES LLP", ref = "B"))
        ))
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.transactionCount == 1 && !it.rememberEligible })
    }

    @Test fun groupUnknownDecisionAffectsOnlyThatGroup() {
        val ahmed = ReviewGroupBuilder.build(listOf(review(classified("Favara Debit", "AHMED NIYAZ", ref = "A")))).single()
        val mariyam = ReviewGroupBuilder.build(listOf(review(classified("Favara Debit", "MARIYAM", ref = "B")))).single()
        val changed = ReviewGroupBuilder.applyDecision(ahmed, ImportDecision.UNKNOWN, null, false)
        assertEquals(ImportDecision.UNKNOWN, changed.items.single().decision)
        assertNotEquals(ImportDecision.UNKNOWN, mariyam.items.single().decision)
    }

    @Test fun purchaseDebitRemainsHighConfidenceExpense() {
        val result = classified("Purchase", "LOCAL STORE")
        assertEquals(NormalizedTransactionType.EXPENSE, result.normalizedType)
        assertEquals(ConfidenceLevel.HIGH, result.classificationConfidence)
    }

    @Test fun billPaymentRemainsBillsExpense() {
        val result = classified("BillPAY Debit", "UTILITY")
        assertEquals(NormalizedTransactionType.EXPENSE, result.normalizedType)
        assertEquals("Bills", result.suggestedCategory)
    }

    @Test fun salaryCreditRemainsIncome() {
        assertEquals(NormalizedTransactionType.INCOME, classified("Salary", "EMPLOYER", debit = "", credit = "100").normalizedType)
    }

    private fun assertDecision(decision: ImportDecision, type: NormalizedTransactionType, expense: Boolean) {
        assertEquals(type, finalTypeForDecision(decision))
        assertEquals(expense, createsExpense(decision))
    }

    private fun review(candidate: CandidateTransaction) = ImportReviewItem(
        candidate, ReviewGroupBuilder.decisionFor(candidate.normalizedType), candidate.suggestedCategory,
        candidate.classificationConfidence == ConfidenceLevel.HIGH
    )

    private fun classified(type: String, description: String, debit: String = "10", credit: String = "", ref: String = "R1") =
        classifier.classify(parser.parse(StringReader(row(type, description, debit, credit, ref))).candidates.single(), emptyList())

    private fun row(type: String, description: String, debit: String, credit: String, ref: String) =
        listOf("2026/09/02", "2026/09/02", type, "=\"$ref\"", "=\"FT1\\B26\"", "02-09-2026 18-42-37", "=\"$description\"", "detail", debit, credit, "100")
            .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
}
