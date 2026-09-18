package com.izavo.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewQueueOptimizerTest {
    private val optimizer = ReviewQueueOptimizer()

    @Test fun incomingUnresolvedBecomesResolvedUnknown() {
        val result = optimizer.optimize(prepared(listOf(row("incoming", TransactionDirection.CREDIT))), true)
        val item = result.items.single()
        assertTrue(item.resolved)
        assertEquals(ImportDecision.UNKNOWN, item.decision)
        assertEquals(setOf("incoming"), result.reviewNecessityPlan.deferredFingerprints)
    }

    @Test fun materialOutgoingRemainsAnUnresolvedQuestion() {
        val result = optimizer.optimize(prepared(listOf(row("outgoing"))), true)
        assertFalse(result.items.single().resolved)
        assertEquals(setOf("outgoing"), result.reviewNecessityPlan.askFingerprints)
    }

    @Test fun initialProgressCountsOnlyAskRowsAndGroups() {
        val result = optimizer.optimize(prepared(listOf(
            row("incoming", TransactionDirection.CREDIT), row("outgoing")
        )), true)
        assertEquals(1, result.reviewProgress.initialReviewRows)
        assertEquals(1, result.reviewProgress.initialDecisionCount)
    }

    @Test fun v4ResolvedRowsRemainUnchanged() {
        val ready = row("ready", requiresReview = false, proposed = NormalizedTransactionType.EXPENSE)
        val result = optimizer.optimize(prepared(listOf(ready)), true).items.single()
        assertTrue(result.resolved)
        assertEquals(ImportDecision.EXPENSE, result.decision)
    }

    @Test fun duplicateRowsRemainProtected() {
        val duplicate = row("duplicate", status = ImportReviewStatus.DUPLICATE)
        val result = optimizer.optimize(prepared(listOf(duplicate)), true).items.single()
        assertEquals(ImportReviewStatus.DUPLICATE, result.transaction.reviewStatus)
    }

    @Test fun explicitAnswerIsImmutableDuringRecalculation() {
        val base = prepared(listOf(row("answered"), row("other"))).copy(
            items = listOf(
                item(row("answered"), ImportDecision.EXPENSE, resolved = true, manual = true),
                item(row("other"), ImportDecision.UNKNOWN, resolved = false)
            ),
            explicitlyDecidedFingerprints = setOf("answered")
        ).regroup()
        val result = optimizer.optimize(base)
        val answered = result.items.single { it.transaction.fingerprint == "answered" }
        assertTrue(answered.resolved)
        assertTrue(answered.manuallyClassified)
        assertEquals(ImportDecision.EXPENSE, answered.decision)
    }

    @Test fun skipMarksEveryRemainingQuestionUnknown() {
        val optimized = optimizer.optimize(prepared((1..5).map { row("r$it") }), true)
        val result = optimizer.deferRemaining(optimized)
        assertTrue(result.reviewGroups.all { it.resolved })
        assertTrue(result.items.all { it.resolved && it.decision == ImportDecision.UNKNOWN })
        assertTrue(result.reviewNecessityPlan.decisions.values.all {
            it.reason == ReviewNecessityReason.DEFER_USER_SKIPPED
        })
    }

    @Test fun skipPreservesEarlierHumanDecision() {
        val rows = (1..4).map { row("r$it") }
        val optimized = optimizer.optimize(prepared(rows), true)
        val answeredFingerprint = optimized.reviewGroups.first { !it.resolved }.items.first().transaction.fingerprint
        val answeredItems = optimized.items.map {
            if (it.transaction.fingerprint == answeredFingerprint) it.copy(
                decision = ImportDecision.MONEY_MOVEMENT, resolved = true, manuallyClassified = true
            ) else it
        }
        val beforeSkip = optimized.copy(
            items = answeredItems,
            reviewGroups = ReviewGroupBuilder.build(answeredItems),
            explicitlyDecidedFingerprints = setOf(answeredFingerprint)
        )
        val result = optimizer.deferRemaining(beforeSkip)
        val answered = result.items.single { it.transaction.fingerprint == answeredFingerprint }
        assertEquals(ImportDecision.MONEY_MOVEMENT, answered.decision)
        assertTrue(answered.manuallyClassified)
        assertTrue(result.reviewGroups.all { it.resolved })
    }

    @Test fun skipDoesNotCreateRules() {
        val optimized = optimizer.optimize(prepared(listOf(row("merchant", identity = "CORAL CAFE"))), true)
        val result = optimizer.deferRemaining(optimized)
        assertTrue(result.temporaryRules.isEmpty())
        assertTrue(result.confirmedRules.isEmpty())
    }

    @Test fun deferredRowsCannotBeSelectedAsExpenses() {
        val optimized = optimizer.optimize(prepared(listOf(row("incoming", TransactionDirection.CREDIT))), true)
        val summary = optimized.decisionSummary()
        assertEquals(0, summary.expenses)
        assertEquals(1, summary.unknown)
        assertEquals(
            NormalizedTransactionType.UNKNOWN,
            finalTypeForPersistence(optimized.items.single(), deferred = true)
        )
    }

    @Test fun explicitHumanAnswerOverridesAnEarlierDeferredPlanAtPersistence() {
        val answered = item(row("answered"), ImportDecision.EXPENSE, resolved = true, manual = true)
        assertEquals(
            NormalizedTransactionType.EXPENSE,
            finalTypeForPersistence(answered, deferred = true)
        )
    }

    @Test fun lowImpactTailIsResolvedWithoutHidingMaterialRows() {
        val rows = listOf(row("large", amount = 10_000)) + (1..20).map { row("tiny$it", amount = 1) }
        val result = optimizer.optimize(prepared(rows), true)
        assertFalse(result.items.single { it.transaction.fingerprint == "large" }.resolved)
        assertTrue(result.items.any { it.transaction.fingerprint.startsWith("tiny") && it.resolved })
    }

    @Test fun optimizingAgainIsIdempotent() {
        val once = optimizer.optimize(prepared((1..20).map { row("r$it", amount = it.toLong()) }), true)
        val twice = optimizer.optimize(once)
        assertEquals(once.reviewNecessityPlan, twice.reviewNecessityPlan)
        assertEquals(once.items, twice.items)
    }

    private fun PreparedImport.regroup() = copy(reviewGroups = ReviewGroupBuilder.build(items))

    private fun prepared(rows: List<AnalyzedTransaction>): PreparedImport {
        val items = rows.map { analyzed ->
            item(
                analyzed,
                ReviewGroupBuilder.decisionFor(analyzed.transaction.normalizedType),
                resolved = !analyzed.intelligence.requiresReview
            )
        }
        return PreparedImport(
            fileName = "test.csv",
            items = items,
            invalidRowCount = 0,
            duplicateCount = rows.count { it.transaction.reviewStatus == ImportReviewStatus.DUPLICATE },
            reviewGroups = ReviewGroupBuilder.build(items),
            intelligence = StatementIntelligenceResult(rows, emptyMap(), emptyMap(), emptyList())
        )
    }

    private fun item(
        analyzed: AnalyzedTransaction,
        decision: ImportDecision,
        resolved: Boolean,
        manual: Boolean = false
    ) = ImportReviewItem(analyzed.transaction, decision, null, resolved, manual)

    private fun row(
        id: String,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        amount: Long = 100,
        requiresReview: Boolean = true,
        proposed: NormalizedTransactionType = NormalizedTransactionType.UNKNOWN,
        status: ImportReviewStatus = if (requiresReview) ImportReviewStatus.NEEDS_REVIEW else ImportReviewStatus.READY,
        identity: String = "ENTITY $id"
    ): AnalyzedTransaction {
        val candidate = CandidateTransaction(
            sourceBank = "TEST", postedAt = id.hashCode().toLong(), valueAt = null, transactionAt = null,
            rawType = "Transfer", rawDescription = identity, rawDetail = "", primaryReference = id,
            secondaryReference = null, direction = direction, amountMinor = amount, currencyCode = "MVR",
            runningBalanceMinor = null, normalizedType = proposed, reviewStatus = status,
            displayName = identity, fingerprint = id, normalizedIdentity = identity,
            bankRail = "TRANSFER", normalizedRail = NormalizedRail.BANK_TRANSFER
        )
        val intelligence = TransactionIntelligence(
            fingerprint = id,
            proposedMeaning = proposed,
            meaningConfidence = if (requiresReview) IntelligenceConfidence.LOW else IntelligenceConfidence.HIGH,
            proposedCategory = null,
            categoryConfidence = IntelligenceConfidence.UNKNOWN,
            entity = EntityResolution(EntityKind.UNKNOWN, IntelligenceConfidence.UNKNOWN, emptyList()),
            relationships = emptyList(),
            relationshipConfidence = IntelligenceConfidence.UNKNOWN,
            evidence = emptyList(),
            requiresReview = requiresReview,
            automationVetoes = if (requiresReview) listOf(EvidenceType.LOW_MEANING_CONFIDENCE) else emptyList()
        )
        return AnalyzedTransaction(candidate, intelligence)
    }
}
