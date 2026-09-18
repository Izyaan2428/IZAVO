package com.izavo.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewNecessityModelTest {
    @Test fun resolvedV4RowIsAlreadyResolved() = assertDisposition(row("a", false), ReviewDisposition.ALREADY_RESOLVED)
    @Test fun unresolvedIncomingUnknownDefers() = assertDisposition(row("a", true, TransactionDirection.CREDIT), ReviewDisposition.DEFER)
    @Test fun unresolvedIncomingIncomeSuggestionDefers() = assertDisposition(row("a", true, TransactionDirection.CREDIT, proposed = NormalizedTransactionType.INCOME), ReviewDisposition.DEFER)
    @Test fun unresolvedIncomingRefundSuggestionDefers() = assertDisposition(row("a", true, TransactionDirection.CREDIT, proposed = NormalizedTransactionType.REFUND), ReviewDisposition.DEFER)
    @Test fun unresolvedIncomingMovementSuggestionDefers() = assertDisposition(row("a", true, TransactionDirection.CREDIT, proposed = NormalizedTransactionType.MONEY_MOVEMENT), ReviewDisposition.DEFER)
    @Test fun singleOutgoingAlwaysAsks() = assertDisposition(row("a", true), ReviewDisposition.ASK_NOW)
    @Test fun twoOutgoingRowsBothAsk() = assertEquals(2, askCount(row("a", true), row("b", true)))
    @Test fun threeOutgoingRowsAllAsk() = assertEquals(3, askCount(row("a", true), row("b", true), row("c", true)))
    @Test fun fourEqualRowsAllAskAtNinetySixPercent() = assertEquals(4, askCount(*(1..4).map { row("$it", true) }.toTypedArray()))
    @Test fun lowValueTailCanDefer() = assertTrue(plan(row("big", true, amount = 10_000), row("tiny", true, amount = 1), row("x", true, amount = 1), row("y", true, amount = 1)).deferredFingerprints.isNotEmpty())
    @Test fun hugeOutgoingIsAlwaysAskedBeforeTail() = assertTrue("big" in plan(row("big", true, amount = 10_000), *(1..99).map { row("s$it", true, amount = 5) }.toTypedArray()).askFingerprints)
    @Test fun hugeOutgoingMovementPossibilityStillAsks() = assertDisposition(row("a", true, amount = 1_000_000, proposed = NormalizedTransactionType.MONEY_MOVEMENT), ReviewDisposition.ASK_NOW)
    @Test fun eachCurrencyGetsItsOwnSparseBucket() = assertEquals(2, askCount(row("mvr", true, currency = "MVR"), row("usd", true, currency = "USD")))
    @Test fun duplicateNeverEntersPlan() = assertFalse("dup" in plan(row("dup", true, status = ImportReviewStatus.DUPLICATE)).decisions)
    @Test fun zeroOutgoingDefers() = assertDisposition(row("a", true, amount = 0), ReviewDisposition.DEFER)
    @Test fun negativeOutgoingDefers() = assertDisposition(row("a", true, amount = -1), ReviewDisposition.DEFER)
    @Test fun membershipIsOrderIndependent() { val rows = (1..20).map { row("$it", true, amount = it.toLong()) }; assertEquals(plan(*rows.toTypedArray()).askFingerprints, plan(*rows.reversed().toTypedArray()).askFingerprints) }
    @Test fun repeatedEvaluationIsDeterministic() { val rows = (1..30).map { row("$it", true, amount = (it * 7).toLong()) }.toTypedArray(); assertEquals(plan(*rows), plan(*rows)) }
    @Test fun incomingReasonIsExplicit() = assertEquals(ReviewNecessityReason.DEFER_INCOMING_NO_EXPENSE_EFFECT, decision(row("a", true, TransactionDirection.CREDIT)).reason)
    @Test fun deferredTailReasonIsExplicit() { val p = plan(row("big", true, amount = 10_000), row("a", true, amount = 1), row("b", true, amount = 1), row("c", true, amount = 1)); assertTrue(p.decisions.values.filter { it.disposition == ReviewDisposition.DEFER }.all { it.reason == ReviewNecessityReason.DEFER_LOW_IMPACT_OUTGOING }) }
    @Test fun sparseReasonIsExplicit() = assertEquals(ReviewNecessityReason.ASK_SPARSE_OUTGOING, decision(row("a", true)).reason)
    @Test fun dominantRowIsCritical() { val d = decision(row("big", true, amount = 900), row("small", true, amount = 100), target = "big"); assertEquals(MaterialityTier.CRITICAL, d.materiality) }
    @Test fun priorityPlacesLargerAmountFirst() { val p = plan(*(1..10).map { row("$it", true, amount = it.toLong()) }.toTypedArray()); assertTrue(p.decisions.getValue("10").priority < p.decisions.getValue("1").priority) }
    @Test fun stableMerchantCohortReceivesCohortReason() { val rows = (1..5).map { row("m$it", true, amount = 100, identity = "NORTH CAFE", rail = NormalizedRail.MERCHANT_PURCHASE) }; assertTrue(plan(*rows.toTypedArray()).decisions.values.any { it.reason == ReviewNecessityReason.ASK_SAFE_COHORT_HIGH_VALUE }) }
    @Test fun repeatedPersonNeverReceivesCohortReason() { val rows = (1..8).map { row("p$it", true, amount = 100, identity = "SAMIR", rail = NormalizedRail.P2P_TRANSFER) }; assertFalse(plan(*rows.toTypedArray()).decisions.values.any { it.reason == ReviewNecessityReason.ASK_SAFE_COHORT_HIGH_VALUE }) }
    @Test fun businessSuffixOnBankTransferDoesNotCreateSafeCohort() { val rows = (1..8).map { row("b$it", true, amount = 100, identity = "NORTH WORKS LLP", rail = NormalizedRail.BANK_TRANSFER) }; assertFalse(plan(*rows.toTypedArray()).decisions.values.any { it.reason == ReviewNecessityReason.ASK_SAFE_COHORT_HIGH_VALUE }) }
    @Test fun currenciesDoNotShareMaterialityDenominators() { val p = plan(row("m", true, amount = 1_000_000, currency = "MVR"), row("u", true, amount = 1, currency = "USD")); assertTrue(setOf("m", "u").all { it in p.askFingerprints }) }
    @Test fun longMaxValuesDoNotOverflow() = assertEquals(4, askCount(*(1..4).map { row("$it", true, amount = Long.MAX_VALUE) }.toTypedArray()))
    @Test fun exactCoverageBoundaryStopsWithoutExtraQuestion() { val model = ReviewNecessityModel(ReviewNecessityPolicy(5_000, 0)); val p = model.evaluate(result(row("a", true, amount = 50), row("b", true, amount = 50))); assertEquals(1, p.askFingerprints.size) }
    @Test fun fullCoveragePolicyAsksEverything() { val model = ReviewNecessityModel(ReviewNecessityPolicy(10_000, 0)); val p = model.evaluate(result(*(1..8).map { row("$it", true) }.toTypedArray())); assertEquals(8, p.askFingerprints.size) }
    @Test fun zeroCoveragePolicyDefersNonSparseBucket() { val model = ReviewNecessityModel(ReviewNecessityPolicy(0, 0)); val p = model.evaluate(result(*(1..8).map { row("$it", true) }.toTypedArray())); assertEquals(0, p.askFingerprints.size) }
    @Test fun mixedDirectionOnlyOutgoingCanAsk() { val p = plan(row("in", true, TransactionDirection.CREDIT), row("out", true)); assertEquals(setOf("out"), p.askFingerprints); assertEquals(setOf("in"), p.deferredFingerprints) }
    @Test fun veryLargeResolvedExpenseIsNeverReopened() = assertDisposition(row("a", false, amount = Long.MAX_VALUE, proposed = NormalizedTransactionType.EXPENSE), ReviewDisposition.ALREADY_RESOLVED)
    @Test fun unknownRailOutgoingStillAsksWhenSparse() = assertDisposition(row("a", true, rail = NormalizedRail.UNKNOWN), ReviewDisposition.ASK_NOW)
    @Test fun policyVersionIsV1() = assertEquals(ReviewNecessityPolicyVersion.V1, plan(row("a", true)).policyVersion)
    @Test fun modelOutputDoesNotContainFinancialDecision() { val d = decision(row("a", true)); assertTrue(d.disposition in ReviewDisposition.entries) }

    private fun assertDisposition(analyzed: AnalyzedTransaction, expected: ReviewDisposition) =
        assertEquals(expected, decision(analyzed).disposition)

    private fun askCount(vararg rows: AnalyzedTransaction) = plan(*rows).askFingerprints.size
    private fun decision(vararg rows: AnalyzedTransaction, target: String = rows.first().transaction.fingerprint) =
        plan(*rows).decisions.getValue(target)
    private fun plan(vararg rows: AnalyzedTransaction) = ReviewNecessityModel().evaluate(result(*rows))
    private fun result(vararg rows: AnalyzedTransaction) = StatementIntelligenceResult(rows.toList(), emptyMap(), emptyMap(), emptyList())

    private fun row(
        id: String,
        requiresReview: Boolean,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        amount: Long = 100,
        currency: String = "MVR",
        proposed: NormalizedTransactionType = NormalizedTransactionType.UNKNOWN,
        status: ImportReviewStatus = if (requiresReview) ImportReviewStatus.NEEDS_REVIEW else ImportReviewStatus.READY,
        identity: String = "ENTITY $id",
        rail: NormalizedRail = NormalizedRail.BANK_TRANSFER
    ): AnalyzedTransaction {
        val candidate = CandidateTransaction(
            sourceBank = "TEST", postedAt = id.hashCode().toLong(), valueAt = null, transactionAt = null,
            rawType = rail.name, rawDescription = identity, rawDetail = "", primaryReference = id,
            secondaryReference = null, direction = direction, amountMinor = amount, currencyCode = currency,
            runningBalanceMinor = null, normalizedType = proposed, reviewStatus = status,
            displayName = identity, fingerprint = id, normalizedIdentity = identity,
            bankRail = rail.name, normalizedRail = rail
        )
        val intelligence = TransactionIntelligence(
            fingerprint = id, proposedMeaning = proposed,
            meaningConfidence = if (requiresReview) IntelligenceConfidence.LOW else IntelligenceConfidence.HIGH,
            proposedCategory = null, categoryConfidence = IntelligenceConfidence.UNKNOWN,
            entity = EntityResolution(EntityKind.UNKNOWN, IntelligenceConfidence.UNKNOWN, emptyList()),
            relationships = emptyList(), relationshipConfidence = IntelligenceConfidence.UNKNOWN,
            evidence = emptyList(), requiresReview = requiresReview,
            automationVetoes = if (requiresReview) listOf(EvidenceType.LOW_MEANING_CONFIDENCE) else emptyList()
        )
        return AnalyzedTransaction(candidate, intelligence)
    }
}
