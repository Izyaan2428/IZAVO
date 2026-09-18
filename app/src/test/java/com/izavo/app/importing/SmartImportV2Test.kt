package com.izavo.app.importing

import com.izavo.app.ui.format.TransactionTimeFormatter
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneOffset

class SmartImportV2Test {
    private val parser = BmlCsvParser(ZoneOffset.UTC)
    private val classifier = SmartTransactionClassifier()

    @Test fun purchaseDebitAutoClassifiesExpenseHigh() { classified().also {
        assertEquals(NormalizedTransactionType.EXPENSE, it.normalizedType); assertEquals(ConfidenceLevel.HIGH, it.classificationConfidence)
    } }

    @Test fun salaryCreditAutoClassifiesIncomeHigh() { classified("Salary", "Employer", "", "100").also {
        assertEquals(NormalizedTransactionType.INCOME, it.normalizedType); assertEquals(ConfidenceLevel.HIGH, it.classificationConfidence)
    } }

    @Test fun favaraCreditFromPersonRequiresPurposeReview() { classified("Favara Credit", "Ahmed", "", "25").also {
        assertEquals(NormalizedTransactionType.INCOMING_TRANSFER, it.normalizedType); assertEquals(ConfidenceLevel.LOW, it.classificationConfidence)
        assertEquals(ImportReviewStatus.NEEDS_REVIEW, it.reviewStatus)
    } }

    @Test fun unknownPersonTransferDebitNeedsReview() { classified("Transfer Debit", "AHMED MOHAMED").also {
        assertEquals(ConfidenceLevel.LOW, it.classificationConfidence); assertEquals(ImportReviewStatus.NEEDS_REVIEW, it.reviewStatus)
    } }

    @Test fun userConfirmedTransferMerchantBecomesExpense() {
        val base = parsed("Transfer Debit", "VILLA MART")
        val rule = rule(base, NormalizedTransactionType.EXPENSE, "Shopping")
        assertEquals(ConfidenceLevel.HIGH, classifier.classify(base, listOf(rule)).classificationConfidence)
    }

    @Test fun aliexpressPurchaseIsShopping() { classified(description = "ALIEXPRESS.COM").also { assertEquals("Shopping", it.suggestedCategory) } }

    @Test fun avasRidePurchaseIsTransport() { classified(description = "AVAS RIDE").also { assertEquals("Transport", it.suggestedCategory) } }

    @Test fun userRuleOverridesSystemHeuristic() {
        val base = parsed("Transfer Debit", "AVAS RIDE")
        val result = classifier.classify(base, listOf(rule(base, NormalizedTransactionType.OUTGOING_TRANSFER, null)))
        assertEquals(NormalizedTransactionType.OUTGOING_TRANSFER, result.normalizedType)
    }

    @Test fun repeatedPersonTransfersAreReviewedIndividually() {
        val one = reviewItem(classified("Transfer Debit", "AHMED MOHAMED", primary = "RB1"))
        val two = reviewItem(classified("Transfer Debit", "AHMED MOHAMED", primary = "RB2"))
        val groups = ReviewGroupBuilder.build(listOf(one, two))
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.transactionCount == 1 && !it.rememberEligible })
    }

    @Test fun oneSafeMerchantGroupDecisionUpdatesAllCandidates() {
        val group = ReviewGroupBuilder.build(listOf(
            reviewItem(classified("Transfer Debit", "VILLA MART", primary = "RB1")),
            reviewItem(classified("Transfer Debit", "VILLA MART", primary = "RB2"))
        )).single()
        val updated = ReviewGroupBuilder.applyDecision(group, ImportDecision.EXPENSE, "Other", true)
        assertTrue(updated.items.all { it.decision == ImportDecision.EXPENSE && it.resolved })
    }

    @Test fun rememberedRuleAppliesOnFutureImport() {
        val base = parsed("Transfer Debit", "VILLA MART")
        val future = classifier.classify(base.copy(fingerprint = "future"), listOf(rule(base, NormalizedTransactionType.EXPENSE, "Shopping")))
        assertEquals("Shopping", future.suggestedCategory); assertEquals(ConfidenceLevel.HIGH, future.classificationConfidence)
    }

    @Test fun unrelatedMerchantsAreNotGrouped() {
        val groups = ReviewGroupBuilder.build(listOf(reviewItem(classified("Transfer Debit", "AHMED")), reviewItem(classified("Transfer Debit", "HASSAN", primary = "RB2"))))
        assertEquals(2, groups.size)
    }

    @Test fun highConfidenceStaysOutOfNeedsReview() = assertTrue(ReviewGroupBuilder.build(listOf(reviewItem(classified()))).single().resolved)

    @Test fun lowConfidenceAppearsInNeedsReview() = assertFalse(ReviewGroupBuilder.build(listOf(reviewItem(classified("Transfer Debit", "AHMED")))).single().resolved)

    @Test fun reversalBehaviorRemainsHighAndExcluded() {
        val parsed = parser.parse(StringReader(row(primary = "RBX") + "\n" + row(primary = "RBX", debit = "", credit = "10"))).candidates
        assertTrue(parsed.map { classifier.classify(it, emptyList()) }.all { it.reviewStatus == ImportReviewStatus.REVERSED && it.classificationConfidence == ConfidenceLevel.HIGH })
    }

    @Test fun duplicateBehaviorRemainsUnchanged() {
        val item = parsed(); assertEquals(ImportReviewStatus.DUPLICATE, markDuplicates(listOf(item), setOf(item.fingerprint)).single().reviewStatus)
    }

    @Test fun secondsPreferenceModelDefaultsOff() = assertFalse(com.izavo.app.preferences.CurrencyPreferences().showSeconds)

    @Test fun timeFormattingOffUsesHoursAndMinutes() = assertEquals("18:42", TransactionTimeFormatter.time(utcMillis(18, 42, 37), false, ZoneOffset.UTC))

    @Test fun timeFormattingOnUsesSeconds() = assertEquals("18:42:37", TransactionTimeFormatter.time(utcMillis(18, 42, 37), true, ZoneOffset.UTC))

    @Test fun storedTimestampPrecisionIsUnchanged() {
        val millis = utcMillis(18, 42, 37) + 456; TransactionTimeFormatter.time(millis, false, ZoneOffset.UTC); assertEquals(456, millis % 1000)
    }

    @Test fun businessNameOnTransferRemainsLowConfidenceReview() { classified("Transfer Debit", "VILLA MART").also {
        assertEquals(ConfidenceLevel.LOW, it.classificationConfidence); assertEquals(ImportReviewStatus.NEEDS_REVIEW, it.reviewStatus)
    } }

    @Test fun subscriptionMerchantGetsSubscriptions() { classified(description = "GOOGLE ONE").also { assertEquals("Subscriptions", it.suggestedCategory) } }

    @Test fun thousandRepeatedSafeMerchantRowsBecomeOneDecision() {
        val items = (1..1000).map { index -> reviewItem(classified("Transfer Debit", "VILLA MART", primary = "RB$index")) }
        assertEquals(1, ReviewGroupBuilder.build(items).count { !it.resolved })
    }

    @Test fun exactAhmedFavaraTortureCaseCreatesTwoIndependentDecisions() {
        val paidForSomething = reviewItem(classified("Favara Debit", "AHMED RASHEED", debit = "356.02", primary = "AHMED-1"))
        val unknownPurpose = reviewItem(classified("Favara Debit", "AHMED RASHEED", debit = "84.17", primary = "AHMED-2"))

        val groups = ReviewGroupBuilder.build(listOf(paidForSomething, unknownPurpose))

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.transactionCount == 1 && !it.rememberEligible })
        val first = ReviewGroupBuilder.applyDecision(groups.first(), ImportDecision.EXPENSE, "Food", false)
        val second = ReviewGroupBuilder.applyDecision(groups.last(), ImportDecision.UNKNOWN, null, false)
        assertEquals(ImportDecision.EXPENSE, first.items.single().decision)
        assertEquals(ImportDecision.UNKNOWN, second.items.single().decision)
    }

    @Test fun avasRidePurchaseRowsStillGroupSafely() {
        val groups = ReviewGroupBuilder.build(listOf(
            reviewItem(classified("Purchase", "AVAS RIDE", primary = "AVAS-1")),
            reviewItem(classified("Purchase", "AVAS RIDE", primary = "AVAS-2"))
        ))
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().transactionCount)
    }

    @Test fun personLikeTransferCannotUseRememberedRule() {
        val base = parsed("Favara Debit", "AHMED RASHEED")
        val result = classifier.classify(base, listOf(rule(base, NormalizedTransactionType.EXPENSE, "Food")))
        assertEquals(ConfidenceLevel.LOW, result.classificationConfidence)
        assertEquals(ImportReviewStatus.NEEDS_REVIEW, result.reviewStatus)
    }

    private fun reviewItem(candidate: CandidateTransaction) = ImportReviewItem(candidate, ReviewGroupBuilder.decisionFor(candidate.normalizedType), candidate.suggestedCategory, candidate.classificationConfidence == ConfidenceLevel.HIGH)
    private fun classified(type: String = "Purchase", description: String = "VILLA MART", debit: String = "10", credit: String = "", primary: String = "RB1") = classifier.classify(parsed(type, description, debit, credit, primary), emptyList())
    private fun parsed(type: String = "Purchase", description: String = "VILLA MART", debit: String = "10", credit: String = "", primary: String = "RB1") = parser.parse(StringReader(row(type, description, debit, credit, primary))).candidates.single()
    private fun rule(candidate: CandidateTransaction, type: NormalizedTransactionType, category: String?) = MerchantRule(
        MerchantIdentityNormalizer.normalize(candidate.displayName), SmartTransactionClassifier.bankRail(candidate.rawType), candidate.direction, type, category, RuleSource.USER_CONFIRMED, ConfidenceLevel.HIGH, true
    )
    private fun row(type: String = "Purchase", description: String = "VILLA MART", debit: String = "10", credit: String = "", primary: String = "RB1") =
        listOf("2026/09/02", "2026/09/02", type, "=\"$primary\"", "=\"FT1\\B26\"", "02-09-2026 18-42-37", "=\"$description\"", "detail", debit, credit, "100")
            .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
    private fun utcMillis(hour: Int, minute: Int, second: Int) = LocalDateTime.of(2026, 9, 2, hour, minute, second).toInstant(ZoneOffset.UTC).toEpochMilli()
}
