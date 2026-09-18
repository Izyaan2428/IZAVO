package com.izavo.app.importing

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ReviewGroupBuilder {
    fun build(items: List<ImportReviewItem>): List<ReviewGroup> {
        val eligible = items.filter { it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }
        return eligible.groupBy { item ->
            val t = item.transaction
            val identity = t.normalizedIdentity.takeIf {
                MerchantIdentityNormalizer.ruleEligibility(t) == RuleEligibilityType.STABLE_MERCHANT
            } ?: t.fingerprint
            listOf(identity, t.sourceBank, t.bankRail, t.direction.name, t.currencyCode, t.normalizedType.name).joinToString("|")
        }.map { (key, grouped) ->
            val first = grouped.first()
            val t = first.transaction
            val learnable = MerchantIdentityNormalizer.ruleEligibility(t) == RuleEligibilityType.STABLE_MERCHANT
            ReviewGroup(
                id = hash(key),
                identity = t.normalizedIdentity.ifBlank { t.displayName },
                bankRail = t.bankRail,
                direction = t.direction,
                currencyCode = t.currencyCode,
                items = grouped,
                suggestedDecision = decisionFor(t.normalizedType),
                suggestedCategory = t.suggestedCategory,
                confidence = grouped.minOf { it.transaction.classificationConfidence },
                resolved = grouped.all { it.resolved },
                rememberEligible = learnable,
                rememberRule = false
            )
        }.sortedWith(
            compareBy<ReviewGroup> { it.resolved }
                .thenByDescending { it.rememberEligible }
                .thenByDescending { it.transactionCount }
                .thenBy { it.items.minOf { item -> item.transaction.expenseTimestamp } }
                .thenBy { it.id }
        )
    }

    fun applyDecision(group: ReviewGroup, decision: ImportDecision, category: String?, remember: Boolean): ReviewGroup =
        group.copy(
            items = group.items.map { it.copy(decision = decision, category = if (decision == ImportDecision.EXPENSE) category else null, resolved = true, manuallyClassified = true) },
            suggestedDecision = decision,
            suggestedCategory = if (decision == ImportDecision.EXPENSE) category else null,
            resolved = true,
            rememberRule = remember && group.rememberEligible
        )

    fun decisionFor(type: NormalizedTransactionType) = when (type) {
        NormalizedTransactionType.EXPENSE -> ImportDecision.EXPENSE
        NormalizedTransactionType.INCOME -> ImportDecision.INCOME
        NormalizedTransactionType.MONEY_MOVEMENT,
        NormalizedTransactionType.INCOMING_TRANSFER, NormalizedTransactionType.OUTGOING_TRANSFER,
        NormalizedTransactionType.CASH_MOVEMENT, NormalizedTransactionType.DEBT_PAYMENT -> ImportDecision.MONEY_MOVEMENT
        NormalizedTransactionType.REFUND, NormalizedTransactionType.REVERSED -> ImportDecision.REFUND
        NormalizedTransactionType.UNKNOWN -> ImportDecision.UNKNOWN
        NormalizedTransactionType.IGNORE -> ImportDecision.IGNORE
    }

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
}
