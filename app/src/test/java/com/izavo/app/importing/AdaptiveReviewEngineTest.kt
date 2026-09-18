package com.izavo.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AdaptiveReviewEngineTest {
    private val intelligenceEngine = IntelligenceDecisionEngine(ZoneOffset.UTC)
    private val adaptive = AdaptiveReviewEngine(intelligenceEngine)

    @Test fun oneSafeMerchantAnswerCanResolveCompatibleRemainingContext() {
        val prepared = prepared((1..10).map { index ->
            transfer("coral-$index", "CORAL CAFE", if (index <= 5) NormalizedTransactionType.OUTGOING_TRANSFER else NormalizedTransactionType.UNKNOWN)
        })
        assertEquals(2, prepared.reviewGroups.count { !it.resolved })

        val first = prepared.reviewGroups.first { !it.resolved }
        val result = adaptive.resolve(prepared, first.id, ImportDecision.EXPENSE, "Food", false)

        assertEquals(1, result.reviewProgress.completedHumanDecisions)
        assertEquals(5, result.reviewProgress.autoResolvedDuringReview)
        assertTrue(result.reviewGroups.all { it.resolved })
        assertTrue(result.items.all { it.decision == ImportDecision.EXPENSE && it.category == "Food" })
        assertEquals(1, result.temporaryRules.size)
        assertFalse(result.confirmedRules.single().persistAfterImport)
    }

    @Test fun rememberControlsPersistenceButNotCurrentImportLearning() {
        val prepared = prepared(listOf(
            transfer("a", "AVAS RIDE", NormalizedTransactionType.OUTGOING_TRANSFER),
            transfer("b", "AVAS RIDE", NormalizedTransactionType.UNKNOWN)
        ))
        val result = adaptive.resolve(prepared, prepared.reviewGroups.first { !it.resolved }.id, ImportDecision.EXPENSE, "Transport", true)
        assertTrue(result.confirmedRules.single().persistAfterImport)
        assertTrue(result.items.all { it.resolved })
    }

    @Test fun personP2pAnswerNeverCreatesTemporaryRuleOrShrinksQueue() {
        val prepared = prepared((1..3).map { index ->
            transfer("ahmed-$index", "AHMED RASHEED", NormalizedTransactionType.OUTGOING_TRANSFER, NormalizedRail.P2P_TRANSFER)
        })
        assertEquals(3, prepared.reviewGroups.count { !it.resolved })
        val result = adaptive.resolve(prepared, prepared.reviewGroups.first { !it.resolved }.id, ImportDecision.EXPENSE, "Food", true)
        assertTrue(result.temporaryRules.isEmpty())
        assertTrue(result.confirmedRules.isEmpty())
        assertEquals(2, result.reviewGroups.count { !it.resolved })
        assertEquals(0, result.reviewProgress.autoResolvedDuringReview)
    }

    @Test fun companySuffixAloneIsNotLearnableOrGroupable() {
        val prepared = prepared((1..10).map { index ->
            transfer("echbees-$index", "ECHBEES LLP", NormalizedTransactionType.OUTGOING_TRANSFER)
        })
        assertEquals(10, prepared.reviewGroups.count { !it.resolved })
        assertTrue(prepared.reviewGroups.all { !it.rememberEligible })
        val result = adaptive.resolve(prepared, prepared.reviewGroups.first().id, ImportDecision.EXPENSE, "Food", true)
        assertEquals(9, result.reviewGroups.count { !it.resolved })
        assertTrue(result.temporaryRules.isEmpty())
    }

    @Test fun temporaryRuleIsScopedByDirectionAndCurrency() {
        val rows = listOf(
            transfer("mvr-out", "CORAL CAFE", NormalizedTransactionType.OUTGOING_TRANSFER, currency = "MVR"),
            transfer("usd-out", "CORAL CAFE", NormalizedTransactionType.OUTGOING_TRANSFER, currency = "USD"),
            transfer("mvr-in", "CORAL CAFE", NormalizedTransactionType.INCOMING_TRANSFER, direction = TransactionDirection.CREDIT)
        )
        val prepared = prepared(rows)
        val mvrDebit = prepared.reviewGroups.single { it.direction == TransactionDirection.DEBIT && it.currencyCode == "MVR" }
        val result = adaptive.resolve(prepared, mvrDebit.id, ImportDecision.EXPENSE, "Food", false)
        assertEquals(2, result.reviewGroups.count { !it.resolved })
        assertTrue(result.items.single { it.transaction.fingerprint == "mvr-out" }.resolved)
        assertFalse(result.items.single { it.transaction.fingerprint == "usd-out" }.resolved)
        assertFalse(result.items.single { it.transaction.fingerprint == "mvr-in" }.resolved)
    }

    @Test fun exactIncomingMerchantRefundIsNotTurnedIntoExpense() {
        val purchase = purchase("purchase", "CORAL CAFE", TransactionDirection.DEBIT)
        val refund = purchase("refund", "CORAL CAFE", TransactionDirection.CREDIT)
        val result = intelligenceEngine.analyzeStatement(listOf(purchase, refund), emptyList(), listOf(
            TemporaryImportRule("TEST", "CORAL CAFE", TransactionDirection.DEBIT, NormalizedRail.MERCHANT_PURCHASE,
                "MVR", ImportDecision.EXPENSE, "Food", RuleEligibilityType.STABLE_MERCHANT)
        ))
        val refundResult = result.transactions.single { it.transaction.fingerprint == "refund" }
        assertEquals(NormalizedTransactionType.REFUND, refundResult.intelligence.proposedMeaning)
        assertFalse(refundResult.intelligence.requiresReview)
    }

    @Test fun explicitDecisionRemainsImmutableAcrossLaterReevaluation() {
        val base = prepared(listOf(
            transfer("first", "CORAL CAFE", NormalizedTransactionType.OUTGOING_TRANSFER, currency = "MVR"),
            transfer("second", "CORAL CAFE", NormalizedTransactionType.OUTGOING_TRANSFER, currency = "USD"),
            transfer("third", "AVAS RIDE", NormalizedTransactionType.OUTGOING_TRANSFER, currency = "MVR")
        ))
        val first = adaptive.resolve(base, base.reviewGroups.single { it.currencyCode == "MVR" && it.identity == "CORAL CAFE" }.id,
            ImportDecision.MONEY_MOVEMENT, null, false)
        val second = adaptive.resolve(first, first.reviewGroups.single { !it.resolved && it.identity == "AVAS RIDE" }.id,
            ImportDecision.EXPENSE, "Transport", false)
        val preserved = second.items.single { it.transaction.fingerprint == "first" }
        assertEquals(ImportDecision.MONEY_MOVEMENT, preserved.decision)
        assertTrue(preserved.manuallyClassified)
    }

    @Test fun lastDecisionMovesToReadyWithoutQueueHeadAccess() {
        val prepared = prepared(listOf(transfer("one", "AHMED", NormalizedTransactionType.UNKNOWN, NormalizedRail.P2P_TRANSFER)))
        val result = adaptive.resolve(prepared, prepared.reviewGroups.single().id, ImportDecision.UNKNOWN, null, false)
        assertTrue(result.reviewGroups.all { it.resolved })
        assertEquals(1, result.reviewProgress.completedHumanDecisions)
    }

    @Test fun resultIsOrderIndependentBeforeHumanReview() {
        val rows = listOf(
            transfer("a", "CORAL CAFE", NormalizedTransactionType.OUTGOING_TRANSFER),
            transfer("b", "AHMED", NormalizedTransactionType.OUTGOING_TRANSFER, NormalizedRail.P2P_TRANSFER),
            purchase("c", "AVAS RIDE", TransactionDirection.DEBIT)
        )
        val forward = intelligenceEngine.analyzeStatement(rows, emptyList()).transactions.associate { it.transaction.fingerprint to it.intelligence.proposedMeaning }
        val reverse = intelligenceEngine.analyzeStatement(rows.reversed(), emptyList()).transactions.associate { it.transaction.fingerprint to it.intelligence.proposedMeaning }
        assertEquals(forward, reverse)
    }

    @Test fun rapidSubmissionGuardAllowsOnlyOneInFlightDecision() {
        val guard = ImportSubmissionGuard()
        assertTrue(guard.tryStart())
        assertFalse(guard.tryStart())
        guard.finish()
        assertTrue(guard.tryStart())
    }

    @Test fun adaptiveEvidenceHasExplicitProvenance() {
        val row = transfer("x", "CORAL CAFE", NormalizedTransactionType.UNKNOWN)
        val initial = intelligenceEngine.analyzeStatement(listOf(row), emptyList())
        val rule = TemporaryImportRule("TEST", "CORAL CAFE", TransactionDirection.DEBIT, NormalizedRail.BANK_TRANSFER,
            "MVR", ImportDecision.EXPENSE, "Food", RuleEligibilityType.STABLE_MERCHANT)
        val result = intelligenceEngine.reEvaluate(initial, emptyList(), listOf(rule)).transactions.single().intelligence
        assertTrue(result.evidence.any { it.type == EvidenceType.TEMPORARY_CONFIRMED_RULE })
        assertFalse(result.requiresReview)
    }

    @Test fun persistedRuleConflictIsSurfacedInsteadOfOverridden() {
        val row = transfer("conflict", "CORAL CAFE", NormalizedTransactionType.UNKNOWN)
        val initial = intelligenceEngine.analyzeStatement(listOf(row), emptyList())
        val persisted = MerchantRule(
            "CORAL CAFE", "TRANSFER", TransactionDirection.DEBIT, NormalizedTransactionType.MONEY_MOVEMENT,
            null, RuleSource.USER_CONFIRMED, ConfidenceLevel.HIGH, true
        )
        val temporary = TemporaryImportRule(
            "TEST", "CORAL CAFE", TransactionDirection.DEBIT, NormalizedRail.BANK_TRANSFER, "MVR",
            ImportDecision.EXPENSE, "Food", RuleEligibilityType.STABLE_MERCHANT
        )
        val result = intelligenceEngine.reEvaluate(initial, listOf(persisted), listOf(temporary)).transactions.single().intelligence
        assertTrue(result.requiresReview)
        assertTrue(EvidenceType.CONFLICTING_STRONG_EVIDENCE in result.automationVetoes)
    }

    @Test fun missingIntelligenceSnapshotStillKeepsExplicitAnswer() {
        val original = prepared(listOf(transfer("answer", "CORAL CAFE", NormalizedTransactionType.UNKNOWN))).copy(intelligence = null)
        val result = adaptive.resolve(original, original.reviewGroups.single().id, ImportDecision.EXPENSE, "Food", false)
        val item = result.items.single()
        assertTrue(item.resolved)
        assertTrue(item.manuallyClassified)
        assertEquals(ImportDecision.EXPENSE, item.decision)
        assertEquals(1, result.reviewProgress.completedHumanDecisions)
    }

    @Test fun rememberedSafeConfirmationAutomatesACompatibleReturningImport() {
        val prepared = prepared(listOf(transfer("old", "AVAS RIDE", NormalizedTransactionType.UNKNOWN)))
        val reviewed = adaptive.resolve(prepared, prepared.reviewGroups.single().id, ImportDecision.EXPENSE, "Transport", true)
        val confirmed = reviewed.confirmedRules.single()
        val persisted = MerchantRule(
            confirmed.rule.normalizedIdentity,
            BankRailNormalizer.legacyName(confirmed.rule.normalizedRail),
            confirmed.rule.direction,
            finalTypeForDecision(confirmed.rule.decision),
            confirmed.rule.category,
            RuleSource.USER_CONFIRMED,
            ConfidenceLevel.HIGH,
            true
        )
        val future = transfer("future", "AVAS RIDE", NormalizedTransactionType.UNKNOWN)
        val result = intelligenceEngine.analyzeStatement(listOf(future), listOf(persisted)).transactions.single()
        assertFalse(result.intelligence.requiresReview)
        assertEquals("Transport", result.intelligence.proposedCategory)
    }

    private fun prepared(rows: List<CandidateTransaction>): PreparedImport {
        val intelligence = intelligenceEngine.analyzeStatement(rows, emptyList())
        val items = intelligence.transactions.map {
            ImportReviewItem(
                it.transaction,
                ReviewGroupBuilder.decisionFor(it.transaction.normalizedType),
                it.transaction.suggestedCategory,
                resolved = !it.intelligence.requiresReview
            )
        }
        val groups = ReviewGroupBuilder.build(items)
        return PreparedImport(
            "adaptive.csv", items, 0, 0, groups, intelligence,
            reviewProgress = AdaptiveReviewProgress(
                items.count { !it.resolved }, groups.count { !it.resolved }
            )
        )
    }

    private fun transfer(
        id: String,
        identity: String,
        legacyMeaning: NormalizedTransactionType,
        rail: NormalizedRail = NormalizedRail.BANK_TRANSFER,
        currency: String = "MVR",
        direction: TransactionDirection = TransactionDirection.DEBIT
    ) = candidate(id, identity, legacyMeaning, rail, direction, currency)

    private fun purchase(id: String, identity: String, direction: TransactionDirection) =
        candidate(id, identity, NormalizedTransactionType.EXPENSE, NormalizedRail.MERCHANT_PURCHASE, direction, "MVR")

    private fun candidate(
        id: String,
        identity: String,
        legacyMeaning: NormalizedTransactionType,
        rail: NormalizedRail,
        direction: TransactionDirection,
        currency: String
    ) = CandidateTransaction(
        sourceBank = "TEST",
        postedAt = LocalDate.of(2026, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        valueAt = null,
        transactionAt = null,
        rawType = rail.name,
        rawDescription = identity,
        rawDetail = identity,
        primaryReference = id,
        secondaryReference = null,
        direction = direction,
        amountMinor = 10_000,
        currencyCode = currency,
        runningBalanceMinor = null,
        normalizedType = legacyMeaning,
        reviewStatus = ImportReviewStatus.NEEDS_REVIEW,
        displayName = identity,
        fingerprint = id,
        normalizedRail = rail,
        sourceFormat = "TEST"
    )
}
