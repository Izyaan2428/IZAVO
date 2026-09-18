package com.izavo.app.importing

enum class ReviewDisposition { ALREADY_RESOLVED, ASK_NOW, DEFER }

enum class ReviewNecessityReason {
    V4_ALREADY_RESOLVED,
    DEFER_INCOMING_NO_EXPENSE_EFFECT,
    ASK_SPARSE_OUTGOING,
    ASK_SAFE_COHORT_HIGH_VALUE,
    ASK_MATERIAL_OUTGOING,
    DEFER_LOW_IMPACT_OUTGOING,
    DEFER_NON_POSITIVE,
    DEFER_USER_SKIPPED
}

enum class MaterialityTier { CRITICAL, HIGH, MEDIUM, LOW, NEGLIGIBLE }

enum class ReviewNecessityPolicyVersion { V1 }

data class ReviewNecessityDecision(
    val fingerprint: String,
    val disposition: ReviewDisposition,
    val reason: ReviewNecessityReason,
    val materiality: MaterialityTier? = null,
    val priority: Int = Int.MAX_VALUE
)

data class ReviewNecessityPlan(
    val policyVersion: ReviewNecessityPolicyVersion = ReviewNecessityPolicyVersion.V1,
    val decisions: Map<String, ReviewNecessityDecision> = emptyMap()
) {
    val askFingerprints: Set<String> get() = decisions.values.asSequence()
        .filter { it.disposition == ReviewDisposition.ASK_NOW }.map { it.fingerprint }.toSet()
    val deferredFingerprints: Set<String> get() = decisions.values.asSequence()
        .filter { it.disposition == ReviewDisposition.DEFER }.map { it.fingerprint }.toSet()
}

data class ReviewNecessityPolicy(
    /** Preserve questions covering 96% of plausible unresolved outgoing value per currency. */
    val outgoingValueCoverageBasisPoints: Int = 9_600,
    val sparseOutgoingCount: Int = 3
)
