package com.izavo.app.importing

/** Applies ASK/DEFER presentation without mutating the retained V4 intelligence snapshot. */
class ReviewQueueOptimizer(
    private val model: ReviewNecessityModel = ReviewNecessityModel()
) {
    fun optimize(prepared: PreparedImport, initializeProgress: Boolean = false): PreparedImport {
        val intelligence = prepared.intelligence ?: return prepared
        val plan = model.evaluate(intelligence)
        val items = prepared.items.map { item ->
            when {
                item.transaction.reviewStatus == ImportReviewStatus.DUPLICATE -> item
                item.transaction.fingerprint in prepared.explicitlyDecidedFingerprints -> item
                plan.decisions[item.transaction.fingerprint]?.disposition == ReviewDisposition.DEFER ->
                    item.copy(decision = ImportDecision.UNKNOWN, category = null, resolved = true, manuallyClassified = false)
                plan.decisions[item.transaction.fingerprint]?.disposition == ReviewDisposition.ASK_NOW ->
                    item.copy(resolved = false, manuallyClassified = false)
                else -> item
            }
        }
        val groups = ReviewGroupBuilder.build(items).sortedWith(
            compareBy<ReviewGroup> { it.resolved }
                .thenBy { group -> group.items.minOfOrNull { plan.decisions[it.transaction.fingerprint]?.priority ?: Int.MAX_VALUE } ?: Int.MAX_VALUE }
                .thenBy { it.id }
        )
        val unresolvedGroups = groups.count { !it.resolved }
        val progress = if (initializeProgress) AdaptiveReviewProgress(
            initialReviewRows = items.count { !it.resolved && it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE },
            initialDecisionCount = unresolvedGroups
        ) else prepared.reviewProgress
        return prepared.copy(
            items = items,
            reviewGroups = groups,
            reviewNecessityPlan = plan,
            reviewProgress = progress
        )
    }

    fun deferRemaining(prepared: PreparedImport): PreparedImport {
        val remaining = prepared.reviewGroups.asSequence().filterNot { it.resolved }
            .flatMap { it.items.asSequence() }.map { it.transaction.fingerprint }.toSet()
        if (remaining.isEmpty()) return prepared
        val items = prepared.items.map { item ->
            if (item.transaction.fingerprint in remaining) {
                item.copy(decision = ImportDecision.UNKNOWN, category = null, resolved = true, manuallyClassified = false)
            } else item
        }
        val extraDecisions = remaining.associateWith { fingerprint ->
            ReviewNecessityDecision(
                fingerprint,
                ReviewDisposition.DEFER,
                ReviewNecessityReason.DEFER_USER_SKIPPED
            )
        }
        return prepared.copy(
            items = items,
            reviewGroups = ReviewGroupBuilder.build(items),
            reviewNecessityPlan = prepared.reviewNecessityPlan.copy(
                decisions = prepared.reviewNecessityPlan.decisions + extraDecisions
            )
        )
    }
}
