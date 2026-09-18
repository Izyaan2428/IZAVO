package com.izavo.app.importing

enum class NormalizedRail {
    MERCHANT_PURCHASE, BILL_PAYMENT, P2P_TRANSFER, BANK_TRANSFER,
    OWN_ACCOUNT_TRANSFER_HINT, ATM_WITHDRAWAL, CASH_DEPOSIT, SALARY_HINT,
    CARD_PAYMENT, LOAN_PAYMENT, REFUND_HINT, REVERSAL, BANK_FEE, INTEREST,
    DIRECT_DEBIT, STANDING_ORDER, CASH_MOVEMENT, UNKNOWN
}

enum class EntityKind {
    MERCHANT, PERSON, EMPLOYER, BILLER, FINANCIAL_INSTITUTION,
    OWN_ACCOUNT, PAYMENT_PLATFORM, GOVERNMENT, UNKNOWN_BUSINESS, UNKNOWN
}

enum class IntelligenceConfidence { VERY_HIGH, HIGH, MEDIUM, LOW, UNKNOWN }
enum class EvidenceStrength { DECISIVE, STRONG, MODERATE, WEAK }

enum class EvidenceType {
    BANK_RAIL_PURCHASE, BANK_RAIL_BILL_PAYMENT, BANK_RAIL_ATM,
    BANK_RAIL_CARD_PAYMENT, BANK_RAIL_LOAN_PAYMENT, BANK_RAIL_REVERSAL,
    BANK_RAIL_TRANSFER, BANK_FEE, INTEREST_INCOME, INTEREST_EXPENSE,
    KNOWN_MERCHANT, MERCHANT_LIKE_IDENTITY, PERSON_LIKE_IDENTITY,
    EMPLOYER_LIKE_IDENTITY, RECURRING_INCOMING_STREAM,
    RECURRING_MERCHANT_STREAM, MATCHED_REFUND, PARTIAL_REFUND,
    EXACT_REVERSAL, MIXED_DIRECTION_IDENTITY, P2P_AMBIGUITY,
    TEMPORARY_CONFIRMED_RULE, USER_RULE, BANK_CATEGORY_HINT, OWN_ACCOUNT_CANDIDATE,
    CONFLICTING_STRONG_EVIDENCE, LOW_MEANING_CONFIDENCE, UNKNOWN_RAIL
}

enum class RuleEligibilityType {
    STABLE_MERCHANT,
    INELIGIBLE_PERSON_OR_P2P,
    INELIGIBLE_UNSTABLE_IDENTITY,
    INELIGIBLE_UNSAFE_RAIL,
    INELIGIBLE_MIXED_PURPOSE
}

data class TemporaryImportRule(
    val sourceBank: String,
    val normalizedIdentity: String,
    val direction: TransactionDirection,
    val normalizedRail: NormalizedRail,
    val currencyCode: String,
    val decision: ImportDecision,
    val category: String?,
    val eligibilityType: RuleEligibilityType
) {
    val isEligible: Boolean get() = eligibilityType == RuleEligibilityType.STABLE_MERCHANT
}

data class ConfirmedImportRule(
    val rule: TemporaryImportRule,
    val persistAfterImport: Boolean
)

data class TransactionEvidence(
    val type: EvidenceType,
    val target: NormalizedTransactionType?,
    val strength: EvidenceStrength,
    val reason: String
)

enum class RelationshipType {
    EXACT_REVERSAL, LIKELY_REFUND, PARTIAL_REFUND, OWN_ACCOUNT_TRANSFER_CANDIDATE,
    CARD_PAYMENT, RECURRING_STREAM_MEMBER, POTENTIAL_REIMBURSEMENT,
    MATCHED_OPPOSITE_DIRECTION
}

data class TransactionRelationship(
    val type: RelationshipType,
    val confidence: IntelligenceConfidence,
    val fingerprints: Set<String>,
    val evidence: List<TransactionEvidence>
)

data class EntityResolution(
    val kind: EntityKind,
    val confidence: IntelligenceConfidence,
    val evidence: List<TransactionEvidence>
)

data class SemanticContextKey(
    val sourceBank: String,
    val identity: String,
    val direction: TransactionDirection,
    val rail: NormalizedRail,
    val currencyCode: String
)

data class RecurrenceProfile(
    val monthly: Boolean = false,
    val weekly: Boolean = false,
    val amountStable: Boolean = false,
    val observationCount: Int = 0
)

data class IdentityProfile(
    val identity: String,
    val transactionCount: Int,
    val incomingCount: Int,
    val outgoingCount: Int,
    val currencies: Set<String>,
    val rails: Set<NormalizedRail>,
    val minimumMinor: Long,
    val maximumMinor: Long,
    val medianMinor: Long,
    val mixedDirections: Boolean,
    val recurrence: RecurrenceProfile
)

data class IdentityContextProfile(
    val key: SemanticContextKey,
    val transactionCount: Int,
    val recurrence: RecurrenceProfile
)

data class TransactionIntelligence(
    val fingerprint: String,
    val proposedMeaning: NormalizedTransactionType,
    val meaningConfidence: IntelligenceConfidence,
    val proposedCategory: String?,
    val categoryConfidence: IntelligenceConfidence,
    val entity: EntityResolution,
    val relationships: List<TransactionRelationship>,
    val relationshipConfidence: IntelligenceConfidence,
    val evidence: List<TransactionEvidence>,
    val requiresReview: Boolean,
    val automationVetoes: List<EvidenceType>
)

data class AnalyzedTransaction(
    val transaction: CandidateTransaction,
    val intelligence: TransactionIntelligence
)

data class StatementIntelligenceResult(
    val transactions: List<AnalyzedTransaction>,
    val identityProfiles: Map<String, IdentityProfile>,
    val contextProfiles: Map<SemanticContextKey, IdentityContextProfile>,
    val relationships: List<TransactionRelationship>
) {
    val unresolved: List<AnalyzedTransaction> get() = transactions.filter { it.intelligence.requiresReview }
    val autoResolved: List<AnalyzedTransaction> get() = transactions.filterNot { it.intelligence.requiresReview }
}

object MeaningAutomationPolicy {
    private val minimum = mapOf(
        NormalizedTransactionType.EXPENSE to IntelligenceConfidence.HIGH,
        NormalizedTransactionType.INCOME to IntelligenceConfidence.HIGH,
        NormalizedTransactionType.MONEY_MOVEMENT to IntelligenceConfidence.HIGH,
        NormalizedTransactionType.CASH_MOVEMENT to IntelligenceConfidence.HIGH,
        NormalizedTransactionType.DEBT_PAYMENT to IntelligenceConfidence.HIGH,
        NormalizedTransactionType.REFUND to IntelligenceConfidence.VERY_HIGH,
        NormalizedTransactionType.REVERSED to IntelligenceConfidence.VERY_HIGH
    )

    fun permits(meaning: NormalizedTransactionType, confidence: IntelligenceConfidence): Boolean {
        val required = minimum[meaning] ?: return false
        return confidence.ordinal <= required.ordinal
    }
}
