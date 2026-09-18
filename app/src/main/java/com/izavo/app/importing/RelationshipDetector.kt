package com.izavo.app.importing

import kotlin.math.abs

class RelationshipDetector {
    fun detect(
        transactions: List<CanonicalTransaction>,
        contexts: Map<SemanticContextKey, IdentityContextProfile> = emptyMap()
    ): List<TransactionRelationship> {
        val relationships = mutableListOf<TransactionRelationship>()
        transactions.groupBy {
            SemanticContextKey(it.sourceBank, it.counterpartyNormalized, it.direction, it.rail, it.currencyCode)
        }.forEach { (key, rows) ->
            val recurrence = contexts[key]?.recurrence
            if (recurrence != null && (recurrence.monthly || recurrence.weekly)) {
                relationships += TransactionRelationship(
                    RelationshipType.RECURRING_STREAM_MEMBER,
                    IntelligenceConfidence.HIGH,
                    rows.mapTo(linkedSetOf()) { it.fingerprint },
                    listOf(TransactionEvidence(
                        if (key.direction == TransactionDirection.CREDIT) EvidenceType.RECURRING_INCOMING_STREAM else EvidenceType.RECURRING_MERCHANT_STREAM,
                        if (key.direction == TransactionDirection.CREDIT) NormalizedTransactionType.INCOME else NormalizedTransactionType.EXPENSE,
                        EvidenceStrength.STRONG,
                        if (recurrence.monthly) "Recurring monthly statement pattern" else "Recurring weekly statement pattern"
                    ))
                )
            }
        }
        val byIdentityCurrency = transactions.groupBy { it.merchantFamily to it.currencyCode }
        byIdentityCurrency.values.forEach { rows ->
            val ordered = rows.sortedBy { it.transactionAt ?: it.postedAt }
            val outgoingByAmount = mutableMapOf<Long, ArrayDeque<CanonicalTransaction>>()
            val recentPurchases = ArrayDeque<CanonicalTransaction>()
            ordered.forEach { incoming ->
                val incomingTime = incoming.transactionAt ?: incoming.postedAt
                while (recentPurchases.isNotEmpty() && incomingTime - (recentPurchases.first().transactionAt ?: recentPurchases.first().postedAt) > THIRTY_DAYS) {
                    recentPurchases.removeFirst()
                }
                if (incoming.direction == TransactionDirection.DEBIT) {
                    outgoingByAmount.getOrPut(incoming.amountMinor, ::ArrayDeque).addLast(incoming)
                    if (incoming.rail == NormalizedRail.MERCHANT_PURCHASE) recentPurchases.addLast(incoming)
                    return@forEach
                }
                val exact = outgoingByAmount[incoming.amountMinor]?.lastOrNull()
                    ?.takeIf { compatibleRefund(it, incoming) }
                if (exact != null && exact.rail == NormalizedRail.MERCHANT_PURCHASE && incoming.rail != NormalizedRail.P2P_TRANSFER) {
                    relationships += relationship(RelationshipType.LIKELY_REFUND, IntelligenceConfidence.VERY_HIGH, exact, incoming, EvidenceType.MATCHED_REFUND, "Same merchant, amount and currency within 30 days")
                } else {
                    // Bound ambiguous partial-refund search to recent local context. This avoids
                    // all-pairs growth for dense statements without asserting a weak match.
                    val partial = recentPurchases.asReversed().take(MAX_PARTIAL_CANDIDATES).firstOrNull {
                        it.amountMinor > incoming.amountMinor && compatibleRefund(it, incoming)
                    }
                    if (partial != null) {
                        relationships += relationship(RelationshipType.PARTIAL_REFUND, IntelligenceConfidence.MEDIUM, partial, incoming, EvidenceType.PARTIAL_REFUND, "Possible partial merchant refund")
                    }
                }
            }
        }
        val ownAccountBuckets = transactions.filter { it.sourceAccountKey != null && it.rail in TRANSFER_RAILS }
            .groupBy { Triple(it.currencyCode, it.amountMinor, (it.transactionAt ?: it.postedAt) / SIX_HOURS) }
        ownAccountBuckets.values.forEach { rows ->
            rows.filter { it.direction == TransactionDirection.DEBIT }.forEach { out ->
                rows.firstOrNull { incoming ->
                    incoming.direction == TransactionDirection.CREDIT && incoming.sourceAccountKey != out.sourceAccountKey &&
                        abs((incoming.transactionAt ?: incoming.postedAt) - (out.transactionAt ?: out.postedAt)) <= SIX_HOURS
                }?.let { incoming ->
                    relationships += relationship(RelationshipType.OWN_ACCOUNT_TRANSFER_CANDIDATE, IntelligenceConfidence.HIGH, out, incoming, EvidenceType.OWN_ACCOUNT_CANDIDATE, "Equal opposite transfers between explicit account keys")
                }
            }
        }
        return relationships.sortedWith(compareBy<TransactionRelationship> { it.fingerprints.minOrNull() }.thenBy { it.type.name })
    }

    private fun compatibleRefund(out: CanonicalTransaction, incoming: CanonicalTransaction): Boolean {
        val gap = (incoming.transactionAt ?: incoming.postedAt) - (out.transactionAt ?: out.postedAt)
        return out.currencyCode == incoming.currencyCode && gap in 0..THIRTY_DAYS
    }

    private fun relationship(type: RelationshipType, confidence: IntelligenceConfidence, first: CanonicalTransaction,
        second: CanonicalTransaction, evidenceType: EvidenceType, reason: String) = TransactionRelationship(
        type, confidence, setOf(first.fingerprint, second.fingerprint),
        listOf(TransactionEvidence(evidenceType, if (type == RelationshipType.LIKELY_REFUND) NormalizedTransactionType.REFUND else null, EvidenceStrength.STRONG, reason))
    )

    companion object {
        private const val THIRTY_DAYS = 30L * 24 * 60 * 60 * 1000
        private const val SIX_HOURS = 6L * 60 * 60 * 1000
        private const val MAX_PARTIAL_CANDIDATES = 64
        private val TRANSFER_RAILS = setOf(NormalizedRail.P2P_TRANSFER, NormalizedRail.BANK_TRANSFER, NormalizedRail.OWN_ACCOUNT_TRANSFER_HINT)
    }
}
