package com.izavo.app.importing

/**
 * Applies one immutable human decision, then re-runs the statement brain over the
 * already parsed snapshot. No database writes happen here.
 */
class AdaptiveReviewEngine(
    private val decisionEngine: IntelligenceDecisionEngine = IntelligenceDecisionEngine()
) {
    fun resolve(
        prepared: PreparedImport,
        groupId: String,
        decision: ImportDecision,
        category: String?,
        remember: Boolean
    ): PreparedImport {
        val group = prepared.reviewGroups.firstOrNull { it.id == groupId && !it.resolved }
            ?: return prepared
        val decidedGroup = ReviewGroupBuilder.applyDecision(group, decision, category, remember)
        val explicitFingerprints = prepared.explicitlyDecidedFingerprints +
            decidedGroup.items.map { it.transaction.fingerprint }
        val decidedItemsByFingerprint = decidedGroup.items.associateBy { it.transaction.fingerprint }
        val explicitlyUpdatedItems = prepared.items.map { item ->
            decidedItemsByFingerprint[item.transaction.fingerprint] ?: item
        }

        val temporaryRule = createTemporaryRule(group, decision, category)
        val temporaryRules = if (temporaryRule == null) prepared.temporaryRules else {
            prepared.temporaryRules.filterNot { it.scopeKey() == temporaryRule.scopeKey() } + temporaryRule
        }
        val confirmedRules = if (temporaryRule == null) prepared.confirmedRules else {
            prepared.confirmedRules.filterNot { it.rule.scopeKey() == temporaryRule.scopeKey() } +
                ConfirmedImportRule(temporaryRule, remember && group.rememberEligible)
        }

        val explicitlyDecided = prepared.copy(
            items = explicitlyUpdatedItems,
            reviewGroups = ReviewGroupBuilder.build(explicitlyUpdatedItems),
            temporaryRules = temporaryRules,
            confirmedRules = confirmedRules,
            explicitlyDecidedFingerprints = explicitFingerprints,
            reviewProgress = prepared.reviewProgress.copy(
                completedHumanDecisions = prepared.reviewProgress.completedHumanDecisions + 1
            )
        )
        val snapshot = prepared.intelligence ?: return explicitlyDecided

        val unresolvedBefore = prepared.items.asSequence()
            .filterNot { it.resolved || it.transaction.reviewStatus == ImportReviewStatus.DUPLICATE }
            .map { it.transaction.fingerprint }
            .toSet() - explicitFingerprints
        val reEvaluated = runCatching {
            decisionEngine.reEvaluate(snapshot, prepared.persistedRules, temporaryRules)
        }.getOrElse {
            // The human answer is authoritative and remains in the session even if
            // optional adaptive queue reduction cannot be completed.
            return explicitlyDecided
        }
        val analyzedByFingerprint = reEvaluated.transactions.associateBy { it.transaction.fingerprint }
        val finalItems = explicitlyUpdatedItems.map { item ->
            if (item.transaction.fingerprint in explicitFingerprints ||
                item.transaction.reviewStatus == ImportReviewStatus.DUPLICATE
            ) item else analyzedByFingerprint[item.transaction.fingerprint]?.toReviewItem() ?: item
        }
        val autoResolvedNow = finalItems.count {
            it.transaction.fingerprint in unresolvedBefore && it.resolved
        }
        return prepared.copy(
            items = finalItems,
            reviewGroups = ReviewGroupBuilder.build(finalItems),
            intelligence = reEvaluated,
            temporaryRules = temporaryRules,
            confirmedRules = confirmedRules,
            explicitlyDecidedFingerprints = explicitFingerprints,
            reviewProgress = explicitlyDecided.reviewProgress.copy(
                autoResolvedDuringReview = explicitlyDecided.reviewProgress.autoResolvedDuringReview + autoResolvedNow
            )
        )
    }

    private fun createTemporaryRule(
        group: ReviewGroup,
        decision: ImportDecision,
        category: String?
    ): TemporaryImportRule? {
        val transaction = group.items.firstOrNull()?.transaction ?: return null
        val eligibility = MerchantIdentityNormalizer.ruleEligibility(transaction)
        if (eligibility != RuleEligibilityType.STABLE_MERCHANT) return null
        if (decision !in setOf(ImportDecision.EXPENSE, ImportDecision.MONEY_MOVEMENT, ImportDecision.IGNORE)) return null
        return TemporaryImportRule(
            sourceBank = transaction.sourceBank,
            normalizedIdentity = transaction.normalizedIdentity,
            direction = transaction.direction,
            normalizedRail = transaction.normalizedRail,
            currencyCode = transaction.currencyCode,
            decision = decision,
            category = category.takeIf { decision == ImportDecision.EXPENSE },
            eligibilityType = eligibility
        )
    }

    private fun AnalyzedTransaction.toReviewItem() = ImportReviewItem(
        transaction = transaction,
        decision = ReviewGroupBuilder.decisionFor(transaction.normalizedType),
        category = transaction.suggestedCategory,
        resolved = !intelligence.requiresReview,
        manuallyClassified = false
    )

    private fun TemporaryImportRule.scopeKey() = listOf(
        sourceBank, normalizedIdentity, direction.name, normalizedRail.name, currencyCode
    ).joinToString("|")
}
