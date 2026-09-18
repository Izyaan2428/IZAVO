package com.izavo.app.importing

enum class TransactionDirection { DEBIT, CREDIT }
enum class NormalizedTransactionType { EXPENSE, REFUND, INCOME, MONEY_MOVEMENT, INCOMING_TRANSFER, OUTGOING_TRANSFER, CASH_MOVEMENT, DEBT_PAYMENT, UNKNOWN, IGNORE, REVERSED }
typealias TransactionClassification = NormalizedTransactionType
enum class ConfidenceLevel { HIGH, MEDIUM, LOW }
enum class RuleSource { SYSTEM, HEURISTIC, USER_CONFIRMED }
enum class ImportReviewStatus { READY, NEEDS_REVIEW, EXCLUDED, REVERSED, DUPLICATE }
enum class ImportDecision { EXPENSE, MONEY_MOVEMENT, INCOME, REFUND, UNKNOWN, IGNORE }

data class CandidateTransaction(
    val sourceBank: String,
    val postedAt: Long,
    val valueAt: Long?,
    val transactionAt: Long?,
    val rawType: String,
    val rawDescription: String,
    val rawDetail: String,
    val primaryReference: String?,
    val secondaryReference: String?,
    val direction: TransactionDirection,
    val amountMinor: Long,
    val currencyCode: String,
    val runningBalanceMinor: Long?,
    val normalizedType: NormalizedTransactionType,
    val reviewStatus: ImportReviewStatus,
    val displayName: String,
    val fingerprint: String,
    val normalizedIdentity: String = "",
    val bankRail: String = "UNKNOWN",
    val classificationConfidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val categoryConfidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val suggestedCategory: String? = null,
    val classificationReason: String = "Bank statement evidence",
    val sourceFormat: String = "BML_CSV",
    val sourceAccountKey: String? = null,
    val sourceAccountType: String? = null,
    val normalizedRail: NormalizedRail = NormalizedRail.UNKNOWN,
    val bankProvidedCategory: String? = null,
    val merchantCategoryCode: String? = null,
    val channel: String? = null,
    val intelligence: TransactionIntelligence? = null
) { val expenseTimestamp: Long get() = transactionAt ?: postedAt }

data class ImportParseResult(val candidates: List<CandidateTransaction>, val invalidRowCount: Int, val errors: List<String> = emptyList())
data class ImportReviewItem(
    val transaction: CandidateTransaction,
    val decision: ImportDecision,
    val category: String? = null,
    val resolved: Boolean = false,
    val manuallyClassified: Boolean = false
)

data class ReviewGroup(
    val id: String,
    val identity: String,
    val bankRail: String,
    val direction: TransactionDirection,
    val currencyCode: String,
    val items: List<ImportReviewItem>,
    val suggestedDecision: ImportDecision,
    val suggestedCategory: String?,
    val confidence: ConfidenceLevel,
    val resolved: Boolean,
    val rememberEligible: Boolean,
    val rememberRule: Boolean
) {
    val transactionCount: Int get() = items.size
    val totalMinor: Long get() = items.sumOf { it.transaction.amountMinor }
}

data class PreparedImport(
    val fileName: String,
    val items: List<ImportReviewItem>,
    val invalidRowCount: Int,
    val duplicateCount: Int,
    val reviewGroups: List<ReviewGroup> = emptyList(),
    val intelligence: StatementIntelligenceResult? = null,
    val persistedRules: List<MerchantRule> = emptyList(),
    val temporaryRules: List<TemporaryImportRule> = emptyList(),
    val confirmedRules: List<ConfirmedImportRule> = emptyList(),
    val explicitlyDecidedFingerprints: Set<String> = emptySet(),
    val reviewNecessityPlan: ReviewNecessityPlan = ReviewNecessityPlan(),
    val reviewProgress: AdaptiveReviewProgress = AdaptiveReviewProgress()
)

data class AdaptiveReviewProgress(
    val initialReviewRows: Int = 0,
    val initialDecisionCount: Int = 0,
    val completedHumanDecisions: Int = 0,
    val autoResolvedDuringReview: Int = 0
)
data class ImportResultSummary(val transactionCount: Int, val expenseCount: Int, val excludedCount: Int, val duplicateCount: Int, val invalidRowCount: Int)

data class ImportDecisionSummary(
    val expenses: Int,
    val income: Int,
    val moneyMovements: Int,
    val refunds: Int,
    val unknown: Int,
    val ignored: Int
)

fun PreparedImport.decisionSummary(): ImportDecisionSummary {
    val eligible = items.filter { it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }
    return ImportDecisionSummary(
        expenses = eligible.count { it.decision == ImportDecision.EXPENSE },
        income = eligible.count { it.decision == ImportDecision.INCOME },
        moneyMovements = eligible.count { it.decision == ImportDecision.MONEY_MOVEMENT },
        refunds = eligible.count { it.decision == ImportDecision.REFUND },
        unknown = eligible.count { it.decision == ImportDecision.UNKNOWN },
        ignored = eligible.count { it.decision == ImportDecision.IGNORE }
    )
}
