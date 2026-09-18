package com.izavo.app.importing

import java.math.BigInteger

/**
 * Decides only whether an immutable V4 abstention needs interruption now.
 * It never assigns EXPENSE, INCOME, MONEY_MOVEMENT or REFUND.
 */
class ReviewNecessityModel(
    private val policy: ReviewNecessityPolicy = ReviewNecessityPolicy()
) {
    fun evaluate(result: StatementIntelligenceResult): ReviewNecessityPlan {
        val eligible = result.transactions.filter {
            it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE
        }
        val decisions = linkedMapOf<String, ReviewNecessityDecision>()
        eligible.filterNot { it.intelligence.requiresReview }.forEach { analyzed ->
            decisions[analyzed.transaction.fingerprint] = ReviewNecessityDecision(
                analyzed.transaction.fingerprint,
                ReviewDisposition.ALREADY_RESOLVED,
                ReviewNecessityReason.V4_ALREADY_RESOLVED
            )
        }

        val unresolved = eligible.filter { it.intelligence.requiresReview }
        unresolved.filter { it.transaction.direction == TransactionDirection.CREDIT }.forEach { analyzed ->
            decisions[analyzed.transaction.fingerprint] = ReviewNecessityDecision(
                analyzed.transaction.fingerprint,
                ReviewDisposition.DEFER,
                ReviewNecessityReason.DEFER_INCOMING_NO_EXPENSE_EFFECT
            )
        }
        unresolved.filter { it.transaction.direction == TransactionDirection.DEBIT }
            .groupBy { it.transaction.currencyCode }
            .toSortedMap()
            .values
            .forEach { evaluateOutgoingBucket(it, decisions) }
        return ReviewNecessityPlan(decisions = decisions)
    }

    private fun evaluateOutgoingBucket(
        bucket: List<AnalyzedTransaction>,
        destination: MutableMap<String, ReviewNecessityDecision>
    ) {
        val positive = bucket.filter { it.transaction.amountMinor > 0L }
        bucket.filterNot { it.transaction.amountMinor > 0L }.forEach { analyzed ->
            destination[analyzed.transaction.fingerprint] = ReviewNecessityDecision(
                analyzed.transaction.fingerprint,
                ReviewDisposition.DEFER,
                ReviewNecessityReason.DEFER_NON_POSITIVE,
                MaterialityTier.NEGLIGIBLE
            )
        }
        if (positive.isEmpty()) return
        val total = positive.fold(BigInteger.ZERO) { sum, row ->
            sum + BigInteger.valueOf(row.transaction.amountMinor)
        }
        val orderedByImpact = positive.sortedWith(
            compareByDescending<AnalyzedTransaction> { it.transaction.amountMinor }
                .thenBy { it.transaction.fingerprint }
        )
        val ask = linkedSetOf<String>()
        if (positive.size <= policy.sparseOutgoingCount) {
            ask += orderedByImpact.map { it.transaction.fingerprint }
        } else {
            var covered = BigInteger.ZERO
            val target = BigInteger.valueOf(policy.outgoingValueCoverageBasisPoints.toLong())
            for (row in orderedByImpact) {
                if (covered * TEN_THOUSAND >= total * target) break
                ask += row.transaction.fingerprint
                covered += BigInteger.valueOf(row.transaction.amountMinor)
            }
        }

        val contextCounts = positive.groupingBy { contextKey(it.transaction) }.eachCount()
        val priorityOrder = positive.filter { it.transaction.fingerprint in ask }
            .sortedWith(
                compareByDescending<AnalyzedTransaction> {
                    val eligible = MerchantIdentityNormalizer.ruleEligibility(it.transaction) == RuleEligibilityType.STABLE_MERCHANT
                    if (eligible) contextCounts[contextKey(it.transaction)] ?: 1 else 1
                }.thenByDescending { it.transaction.amountMinor }
                    .thenBy { it.transaction.fingerprint }
            )
            .mapIndexed { index, row -> row.transaction.fingerprint to index }
            .toMap()

        positive.forEach { analyzed ->
            val transaction = analyzed.transaction
            val fractionBasisPoints = transaction.amountMinor.toBigInteger() * TEN_THOUSAND / total
            val tier = when {
                fractionBasisPoints >= BigInteger.valueOf(1_000) -> MaterialityTier.CRITICAL
                fractionBasisPoints >= BigInteger.valueOf(300) -> MaterialityTier.HIGH
                fractionBasisPoints >= BigInteger.valueOf(100) -> MaterialityTier.MEDIUM
                transaction.fingerprint in ask -> MaterialityTier.LOW
                else -> MaterialityTier.NEGLIGIBLE
            }
            val shouldAsk = transaction.fingerprint in ask
            val cohortCount = contextCounts[contextKey(transaction)] ?: 1
            val safeCohort = MerchantIdentityNormalizer.ruleEligibility(transaction) == RuleEligibilityType.STABLE_MERCHANT && cohortCount > 1
            destination[transaction.fingerprint] = ReviewNecessityDecision(
                fingerprint = transaction.fingerprint,
                disposition = if (shouldAsk) ReviewDisposition.ASK_NOW else ReviewDisposition.DEFER,
                reason = when {
                    !shouldAsk -> ReviewNecessityReason.DEFER_LOW_IMPACT_OUTGOING
                    positive.size <= policy.sparseOutgoingCount -> ReviewNecessityReason.ASK_SPARSE_OUTGOING
                    safeCohort -> ReviewNecessityReason.ASK_SAFE_COHORT_HIGH_VALUE
                    else -> ReviewNecessityReason.ASK_MATERIAL_OUTGOING
                },
                materiality = tier,
                priority = priorityOrder[transaction.fingerprint] ?: Int.MAX_VALUE
            )
        }
    }

    private fun contextKey(transaction: CandidateTransaction) = listOf(
        transaction.sourceBank,
        transaction.normalizedIdentity,
        transaction.direction.name,
        transaction.normalizedRail.name,
        transaction.currencyCode
    ).joinToString("|")

    private companion object {
        val TEN_THOUSAND: BigInteger = BigInteger.valueOf(10_000)
    }
}
