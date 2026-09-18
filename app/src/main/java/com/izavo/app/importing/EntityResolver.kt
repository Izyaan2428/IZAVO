package com.izavo.app.importing

class EntityResolver {
    fun resolve(transaction: CanonicalTransaction, profile: IdentityProfile?): EntityResolution {
        val identity = transaction.merchantFamily
        val business = MerchantIdentityNormalizer.businessMarkers.any { marker -> marker in identity }
        val known = MerchantIdentityNormalizer.knownMerchants.any { merchant -> merchant in identity }
        val evidence = mutableListOf<TransactionEvidence>()
        val result = when {
            transaction.rail == NormalizedRail.BILL_PAYMENT -> EntityKind.BILLER to IntelligenceConfidence.VERY_HIGH
            transaction.rail in setOf(NormalizedRail.CARD_PAYMENT, NormalizedRail.LOAN_PAYMENT) -> EntityKind.FINANCIAL_INSTITUTION to IntelligenceConfidence.HIGH
            transaction.rail == NormalizedRail.SALARY_HINT -> EntityKind.EMPLOYER to IntelligenceConfidence.VERY_HIGH
            transaction.rail == NormalizedRail.MERCHANT_PURCHASE || known -> EntityKind.MERCHANT to IntelligenceConfidence.HIGH
            transaction.rail == NormalizedRail.P2P_TRANSFER && !business -> EntityKind.PERSON to IntelligenceConfidence.HIGH
            business && profile != null && profile.incomingCount == profile.transactionCount && profile.recurrence.monthly -> EntityKind.EMPLOYER to IntelligenceConfidence.HIGH
            business -> EntityKind.UNKNOWN_BUSINESS to IntelligenceConfidence.MEDIUM
            else -> EntityKind.UNKNOWN to IntelligenceConfidence.LOW
        }
        when (result.first) {
            EntityKind.MERCHANT, EntityKind.BILLER -> evidence += TransactionEvidence(EvidenceType.MERCHANT_LIKE_IDENTITY, NormalizedTransactionType.EXPENSE, EvidenceStrength.MODERATE, "Stable merchant or biller context")
            EntityKind.PERSON -> evidence += TransactionEvidence(EvidenceType.PERSON_LIKE_IDENTITY, null, EvidenceStrength.STRONG, "Person-like identity on a P2P rail")
            EntityKind.EMPLOYER -> evidence += TransactionEvidence(EvidenceType.EMPLOYER_LIKE_IDENTITY, NormalizedTransactionType.INCOME, EvidenceStrength.STRONG, "Employer-like incoming context")
            else -> Unit
        }
        if (profile?.mixedDirections == true) evidence += TransactionEvidence(EvidenceType.MIXED_DIRECTION_IDENTITY, null, EvidenceStrength.STRONG, "Identity appears in both directions")
        return EntityResolution(result.first, result.second, evidence)
    }
}
