package com.izavo.app.importing

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class IntelligenceDecisionEngineTest {
    private val engine = IntelligenceDecisionEngine(ZoneOffset.UTC)

    @Test fun merchantPurchaseIsVeryHighExpenseWithRailEvidence() {
        val result = analyze(tx("p", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "CORAL CAFE"))
        assertEquals(NormalizedTransactionType.EXPENSE, result.proposedMeaning)
        assertEquals(IntelligenceConfidence.VERY_HIGH, result.meaningConfidence)
        assertFalse(result.requiresReview)
        assertEvidence(result, EvidenceType.BANK_RAIL_PURCHASE)
    }

    @Test fun unknownPurchaseCategoryDoesNotCauseMeaningReview() {
        val result = analyze(tx("p", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "NEW MERCHANT"))
        assertEquals(IntelligenceConfidence.LOW, result.categoryConfidence)
        assertEquals("Other", result.proposedCategory)
        assertFalse(result.requiresReview)
    }

    @Test fun billPaymentIsBillsExpenseEvenWhenAmountsVary() {
        val rows = listOf(45000L, 52000L, 48700L).mapIndexed { index, amount ->
            tx("b$index", NormalizedRail.BILL_PAYMENT, TransactionDirection.DEBIT, "UTILITY BILLER", amount, day = 1L + index * 30)
        }
        val result = engine.analyzeStatement(rows, emptyList())
        assertTrue(result.transactions.all { it.intelligence.proposedMeaning == NormalizedTransactionType.EXPENSE })
        assertTrue(result.transactions.all { it.intelligence.proposedCategory == "Bills" && !it.intelligence.requiresReview })
    }

    @Test fun recurringSubscriptionHasRecurringEvidence() {
        val rows = (0..2).map { tx("s$it", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "GOOGLE ONE", 4900, day = 1L + it * 30) }
        val result = engine.analyzeStatement(rows, emptyList())
        assertEquals("Subscriptions", result.transactions.first().intelligence.proposedCategory)
        assertTrue(result.relationships.any { it.type == RelationshipType.RECURRING_STREAM_MEMBER })
    }

    @Test fun recurringEmployerTransferBecomesIncome() {
        val rows = listOf(1250000L, 1250000L, 1275000L).mapIndexed { index, amount ->
            tx("salary$index", NormalizedRail.BANK_TRANSFER, TransactionDirection.CREDIT, "ACME PVT LTD", amount, day = 1L + index * 30)
        }
        val result = engine.analyzeStatement(rows, emptyList())
        assertTrue(result.transactions.all { it.intelligence.proposedMeaning == NormalizedTransactionType.INCOME })
        assertTrue(result.transactions.all { !it.intelligence.requiresReview })
        assertEvidence(result.transactions.first().intelligence, EvidenceType.RECURRING_INCOMING_STREAM)
    }

    @Test fun twoIrregularBusinessCreditsDoNotBecomeIncome() {
        val rows = listOf(
            tx("i1", NormalizedRail.BANK_TRANSFER, TransactionDirection.CREDIT, "ACME PVT LTD", 10000, day = 1),
            tx("i2", NormalizedRail.BANK_TRANSFER, TransactionDirection.CREDIT, "ACME PVT LTD", 90000, day = 11)
        )
        assertTrue(engine.analyzeStatement(rows, emptyList()).transactions.all { it.intelligence.requiresReview })
    }

    @Test fun outgoingAndIncomingP2pRemainReviewWithExplicitVeto() {
        val rows = listOf(
            tx("out", NormalizedRail.P2P_TRANSFER, TransactionDirection.DEBIT, "AHMED RASHEED"),
            tx("in", NormalizedRail.P2P_TRANSFER, TransactionDirection.CREDIT, "AHMED RASHEED")
        )
        val result = engine.analyzeStatement(rows, emptyList())
        assertTrue(result.transactions.all { it.intelligence.requiresReview })
        assertTrue(result.transactions.all { EvidenceType.P2P_AMBIGUITY in it.intelligence.automationVetoes })
        assertTrue(result.identityProfiles.getValue("AHMED RASHEED").mixedDirections)
    }

    @Test fun repeatedOutgoingPersonRowsNeverShareAutomaticMeaning() {
        val rows = listOf(
            tx("a", NormalizedRail.P2P_TRANSFER, TransactionDirection.DEBIT, "AHMED RASHEED", 35602),
            tx("b", NormalizedRail.P2P_TRANSFER, TransactionDirection.DEBIT, "AHMED RASHEED", 8417)
        )
        assertTrue(engine.analyzeStatement(rows, emptyList()).transactions.all { it.intelligence.requiresReview })
    }

    @Test fun businessNameAloneDoesNotTurnTransferIntoExpense() {
        val result = analyze(tx("t", NormalizedRail.BANK_TRANSFER, TransactionDirection.DEBIT, "CORAL WORKS LLP"))
        assertTrue(result.requiresReview)
        assertNotEquals(NormalizedTransactionType.EXPENSE, result.proposedMeaning)
    }

    @Test fun atmIsCashMovementNotExpense() {
        val result = analyze(tx("atm", NormalizedRail.ATM_WITHDRAWAL, TransactionDirection.DEBIT, "ATM"))
        assertEquals(NormalizedTransactionType.CASH_MOVEMENT, result.proposedMeaning)
        assertFalse(result.requiresReview)
        assertEvidence(result, EvidenceType.BANK_RAIL_ATM)
    }

    @Test fun cardAndLoanPaymentsAreDebtMovement() {
        listOf(NormalizedRail.CARD_PAYMENT, NormalizedRail.LOAN_PAYMENT).forEach { rail ->
            val result = analyze(tx(rail.name, rail, TransactionDirection.DEBIT, "BANK"))
            assertEquals(NormalizedTransactionType.DEBT_PAYMENT, result.proposedMeaning)
            assertFalse(result.requiresReview)
        }
    }

    @Test fun feeAndInterestRespectDirection() {
        assertEquals(NormalizedTransactionType.EXPENSE, analyze(tx("fee", NormalizedRail.BANK_FEE, TransactionDirection.DEBIT, "BANK")).proposedMeaning)
        assertEquals(NormalizedTransactionType.INCOME, analyze(tx("interest-in", NormalizedRail.INTEREST, TransactionDirection.CREDIT, "BANK")).proposedMeaning)
        assertEquals(NormalizedTransactionType.EXPENSE, analyze(tx("interest-out", NormalizedRail.INTEREST, TransactionDirection.DEBIT, "BANK")).proposedMeaning)
    }

    @Test fun normalizedRailMappingIsBankNeutralAndDirectionIndependent() {
        assertEquals(NormalizedRail.P2P_TRANSFER, BankRailNormalizer.fromRaw("Favara Debit"))
        assertEquals(NormalizedRail.BILL_PAYMENT, BankRailNormalizer.fromRaw("BillPAY Debit"))
        assertEquals(NormalizedRail.CARD_PAYMENT, BankRailNormalizer.fromRaw("Credit Card Payment"))
        assertEquals(NormalizedRail.UNKNOWN, BankRailNormalizer.fromRaw("Future Bank Code 91"))
    }

    @Test fun canonicalMeaningIsIndependentOfSourceBankName() {
        val bml = tx("bml", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "CORAL CAFE")
        val futureBank = bml.copy(sourceBank = "FUTURE_BANK", fingerprint = "future")
        val results = engine.analyzeStatement(listOf(bml, futureBank), emptyList()).transactions
        assertTrue(results.all { it.intelligence.proposedMeaning == NormalizedTransactionType.EXPENSE })
        assertTrue(results.all { !it.intelligence.requiresReview })
    }

    @Test fun merchantFamilyRemovesSafeDescriptorNoiseWithoutChangingPersonIdentity() {
        assertEquals("YOUTUBE PREMIUM", MerchantIdentityNormalizer.merchantFamily("MPGOOGLE*YOUTUBE 4029357733"))
        assertEquals("AHMED RASHEED", MerchantIdentityNormalizer.merchantFamily("Ahmed Rasheed"))
    }

    @Test fun misleadingSalaryHintDoesNotMakeOutgoingIncome() {
        val result = analyze(tx("salary-out", NormalizedRail.SALARY_HINT, TransactionDirection.DEBIT, "SALARY SAVINGS"))
        assertTrue(result.requiresReview)
        assertNotEquals(NormalizedTransactionType.INCOME, result.proposedMeaning)
    }

    @Test fun bankCategoryCannotOverrideCardPaymentMeaning() {
        val row = tx("bank-category", NormalizedRail.CARD_PAYMENT, TransactionDirection.DEBIT, "BANK")
            .copy(bankProvidedCategory = "Shopping")
        val result = analyze(row)
        assertEquals(NormalizedTransactionType.DEBT_PAYMENT, result.proposedMeaning)
        assertFalse(result.requiresReview)
    }

    @Test fun recurrenceHandlesLeapYearMonthBoundaries() {
        val dates = listOf(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31))
        val rows = dates.mapIndexed { index, date ->
            tx("leap$index", NormalizedRail.BANK_TRANSFER, TransactionDirection.CREDIT, "ACME PVT LTD")
                .copy(postedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        }
        val result = engine.analyzeStatement(rows, emptyList())
        assertTrue(result.identityProfiles.getValue("ACME PVT LTD").recurrence.monthly)
        assertTrue(result.transactions.all { it.intelligence.proposedMeaning == NormalizedTransactionType.INCOME })
    }

    @Test fun parserMarkedReversalKeepsDecisiveReversalEvidence() {
        val row = tx("reversal", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "SHOP")
            .copy(normalizedType = NormalizedTransactionType.REVERSED, reviewStatus = ImportReviewStatus.REVERSED)
        val result = analyze(row)
        assertEquals(NormalizedTransactionType.REVERSED, result.proposedMeaning)
        assertFalse(result.requiresReview)
        assertEvidence(result, EvidenceType.EXACT_REVERSAL)
    }

    @Test fun exactMerchantRefundIsStrongButPartialRefundReviews() {
        val purchase = tx("purchase", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "CORAL CAFE", 35000, day = 1)
        val exact = tx("exact", NormalizedRail.REFUND_HINT, TransactionDirection.CREDIT, "CORAL CAFE", 35000, day = 4)
        val exactResult = engine.analyzeStatement(listOf(purchase, exact), emptyList()).transactions.last().intelligence
        assertEquals(NormalizedTransactionType.REFUND, exactResult.proposedMeaning)
        assertFalse(exactResult.requiresReview)
        assertEvidence(exactResult, EvidenceType.MATCHED_REFUND)

        val partial = tx("partial", NormalizedRail.REFUND_HINT, TransactionDirection.CREDIT, "CORAL CAFE", 20000, day = 4)
        val partialResult = engine.analyzeStatement(listOf(purchase, partial), emptyList()).transactions.last().intelligence
        assertTrue(partialResult.requiresReview)
        assertTrue(partialResult.relationships.any { it.type == RelationshipType.PARTIAL_REFUND })
    }

    @Test fun equalOppositeP2pPersonAmountsAreNotCalledRefund() {
        val rows = listOf(
            tx("o", NormalizedRail.P2P_TRANSFER, TransactionDirection.DEBIT, "AHMED", 50000, day = 1),
            tx("i", NormalizedRail.P2P_TRANSFER, TransactionDirection.CREDIT, "AHMED", 50000, day = 2)
        )
        val result = engine.analyzeStatement(rows, emptyList())
        assertTrue(result.transactions.all { it.intelligence.proposedMeaning != NormalizedTransactionType.REFUND })
        assertTrue(result.transactions.all { it.intelligence.requiresReview })
    }

    @Test fun refundsNeverMatchAcrossCurrencies() {
        val rows = listOf(
            tx("usd", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "SHOP", 10000, "USD", 1),
            tx("mvr", NormalizedRail.REFUND_HINT, TransactionDirection.CREDIT, "SHOP", 10000, "MVR", 2)
        )
        assertTrue(engine.analyzeStatement(rows, emptyList()).relationships.none { it.type == RelationshipType.LIKELY_REFUND })
    }

    @Test fun explicitDifferentAccountKeysCreateOwnAccountCandidate() {
        val rows = listOf(
            tx("a", NormalizedRail.BANK_TRANSFER, TransactionDirection.DEBIT, "TRANSFER", account = "A"),
            tx("b", NormalizedRail.BANK_TRANSFER, TransactionDirection.CREDIT, "TRANSFER", account = "B")
        )
        val result = engine.analyzeStatement(rows, emptyList())
        assertTrue(result.relationships.any { it.type == RelationshipType.OWN_ACCOUNT_TRANSFER_CANDIDATE })
        assertTrue(result.transactions.all { it.intelligence.proposedMeaning == NormalizedTransactionType.MONEY_MOVEMENT })
    }

    @Test fun compatibleMerchantRuleAutomatesButPersonRuleIsVetoed() {
        val merchant = tx("m", NormalizedRail.BANK_TRANSFER, TransactionDirection.DEBIT, "VILLA MART")
        val merchantRule = rule("VILLA MART", "TRANSFER", TransactionDirection.DEBIT)
        assertFalse(engine.analyzeStatement(listOf(merchant), listOf(merchantRule)).transactions.single().intelligence.requiresReview)

        val person = tx("p", NormalizedRail.P2P_TRANSFER, TransactionDirection.DEBIT, "AHMED RASHEED")
        val personRule = rule("AHMED RASHEED", "FAVARA", TransactionDirection.DEBIT)
        assertTrue(engine.analyzeStatement(listOf(person), listOf(personRule)).transactions.single().intelligence.requiresReview)
    }

    @Test fun conflictingCardRailAndExpenseRuleAbstains() {
        val row = tx("conflict", NormalizedRail.CARD_PAYMENT, TransactionDirection.DEBIT, "VILLA MART")
        val result = engine.analyzeStatement(listOf(row), listOf(rule("VILLA MART", "CARD_PAYMENT", TransactionDirection.DEBIT))).transactions.single().intelligence
        assertTrue(result.requiresReview)
        assertEvidence(result, EvidenceType.CONFLICTING_STRONG_EVIDENCE)
    }

    @Test fun unknownRailFallsBackToReviewWithoutDroppingRow() {
        val result = engine.analyzeStatement(listOf(tx("u", NormalizedRail.UNKNOWN, TransactionDirection.DEBIT, "NOISY 883739")), emptyList())
        assertEquals(1, result.transactions.size)
        assertTrue(result.transactions.single().intelligence.requiresReview)
        assertEvidence(result.transactions.single().intelligence, EvidenceType.UNKNOWN_RAIL)
    }

    @Test fun duplicateStatusIsPreservedOutsideRelationshipMeaning() {
        val duplicate = tx("dup", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "SHOP").copy(reviewStatus = ImportReviewStatus.DUPLICATE)
        val result = engine.analyzeStatement(listOf(duplicate), emptyList()).transactions.single().transaction
        assertEquals(ImportReviewStatus.DUPLICATE, result.reviewStatus)
    }

    @Test fun reEvaluationUsesNewRuleWithoutReparsing() {
        val row = tx("r", NormalizedRail.BANK_TRANSFER, TransactionDirection.DEBIT, "VILLA MART")
        val initial = engine.analyzeStatement(listOf(row), emptyList())
        assertTrue(initial.unresolved.isNotEmpty())
        val updated = engine.reEvaluate(initial, listOf(rule("VILLA MART", "TRANSFER", TransactionDirection.DEBIT)))
        assertTrue(updated.unresolved.isEmpty())
        assertEvidence(updated.transactions.single().intelligence, EvidenceType.USER_RULE)
    }

    @Test fun statementOrderDoesNotChangeDecisionsOrProfiles() {
        val rows = (0..5).map { tx("order$it", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "GOOGLE ONE", 4900, day = 1L + it * 30) }
        val forward = engine.analyzeStatement(rows, emptyList())
        val reversed = engine.analyzeStatement(rows.reversed(), emptyList())
        assertEquals(forward.identityProfiles, reversed.identityProfiles)
        assertEquals(forward.transactions.associate { it.transaction.fingerprint to it.intelligence.proposedMeaning },
            reversed.transactions.associate { it.transaction.fingerprint to it.intelligence.proposedMeaning })
    }

    @Test fun fiveThousandRowsRemainCompleteAndDeterministic() {
        val rows = (1..5000).map { index ->
            tx("large$index", NormalizedRail.MERCHANT_PURCHASE, TransactionDirection.DEBIT, "MERCHANT ${index % 50}", index.toLong())
        }
        val first = engine.analyzeStatement(rows, emptyList())
        val second = engine.analyzeStatement(rows, emptyList())
        assertEquals(5000, first.transactions.size)
        assertEquals(first.transactions.map { it.intelligence.proposedMeaning }, second.transactions.map { it.intelligence.proposedMeaning })
    }

    private fun analyze(transaction: CandidateTransaction) = engine.analyzeStatement(listOf(transaction), emptyList()).transactions.single().intelligence

    private fun assertEvidence(result: TransactionIntelligence, type: EvidenceType) =
        assertTrue("Expected $type in ${result.evidence.map { it.type }}", result.evidence.any { it.type == type })

    private fun tx(
        id: String,
        rail: NormalizedRail,
        direction: TransactionDirection,
        identity: String,
        amount: Long = 10_000,
        currency: String = "MVR",
        day: Long = 1,
        account: String? = null
    ) = CandidateTransaction(
        sourceBank = "TEST", postedAt = LocalDate.of(2026, 1, 1).plusDays(day - 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        valueAt = null, transactionAt = null, rawType = rail.name, rawDescription = identity, rawDetail = identity,
        primaryReference = id, secondaryReference = null, direction = direction, amountMinor = amount,
        currencyCode = currency, runningBalanceMinor = null, normalizedType = NormalizedTransactionType.UNKNOWN,
        reviewStatus = ImportReviewStatus.NEEDS_REVIEW, displayName = identity, fingerprint = id,
        normalizedRail = rail, sourceFormat = "TEST", sourceAccountKey = account
    )

    private fun rule(identity: String, rail: String, direction: TransactionDirection) = MerchantRule(
        MerchantIdentityNormalizer.normalize(identity), rail, direction, NormalizedTransactionType.EXPENSE,
        "Shopping", RuleSource.USER_CONFIRMED, ConfidenceLevel.HIGH, true
    )
}
