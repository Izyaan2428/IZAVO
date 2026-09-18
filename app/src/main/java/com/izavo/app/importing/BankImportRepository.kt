package com.izavo.app.importing

import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.ImportBatchEntity
import com.izavo.app.data.ImportDao
import com.izavo.app.data.ImportPersistenceRow
import com.izavo.app.data.ImportedBankTransactionEntity

class BankImportRepository(
    private val dao: ImportDao,
    private val reviewQueueOptimizer: ReviewQueueOptimizer = ReviewQueueOptimizer()
) {
    suspend fun prepare(fileName: String, parsed: ImportParseResult): PreparedImport {
        val existing = parsed.candidates.map { it.fingerprint }.chunked(500)
            .flatMap { dao.findExistingFingerprints(it) }.toSet()
        val duplicateMarked = markDuplicates(parsed.candidates, existing)
        val identities = duplicateMarked.map { MerchantIdentityNormalizer.normalize(it.displayName) }.distinct()
        val rules = identities.chunked(400).flatMap { chunk ->
            if (chunk.isEmpty()) emptyList() else dao.findMerchantRules(BmlCsvParser.SOURCE_BANK, chunk)
        }.map { it.toDomain() }
        val intelligence = SmartTransactionClassifier().analyzeStatement(duplicateMarked, rules)
        val items = intelligence.transactions.map { analyzed ->
            val classified = analyzed.transaction
            ImportReviewItem(
                classified,
                defaultDecision(classified),
                classified.suggestedCategory,
                resolved = !analyzed.intelligence.requiresReview
            )
        }
        val groups = ReviewGroupBuilder.build(items)
        val prepared = PreparedImport(
            fileName, items, parsed.invalidRowCount,
            items.count { it.transaction.reviewStatus == ImportReviewStatus.DUPLICATE },
            groups,
            intelligence,
            persistedRules = rules,
            reviewProgress = AdaptiveReviewProgress(
                initialReviewRows = items.count { !it.resolved && it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE },
                initialDecisionCount = groups.count { !it.resolved }
            )
        )
        return reviewQueueOptimizer.optimize(prepared, initializeProgress = true)
    }

    suspend fun import(prepared: PreparedImport): ImportResultSummary {
        val now = System.currentTimeMillis()
        val uniqueRows = prepared.items.filter { it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }
        val persistenceRows = uniqueRows.map { item ->
            val candidate = item.transaction
            // The source ledger records every accepted row, but only a financial expense
            // becomes spending. Unknown, movement, income, refund and ignore stay ledger-only.
            val expense = if (createsExpense(item.decision)) ExpenseEntity(
                amountMinor = candidate.amountMinor,
                currencyCode = candidate.currencyCode,
                category = item.category,
                note = candidate.displayName,
                occurredAt = candidate.expenseTimestamp,
                createdAt = now
            ) else null
            ImportPersistenceRow(
                ImportedBankTransactionEntity(
                    sourceBank = candidate.sourceBank,
                    primaryReference = candidate.primaryReference,
                    secondaryReference = candidate.secondaryReference,
                    postedAt = candidate.postedAt,
                    valueAt = candidate.valueAt,
                    transactionAt = candidate.transactionAt,
                    rawType = candidate.rawType,
                    rawDescription = candidate.rawDescription,
                    rawDetail = candidate.rawDetail,
                    direction = candidate.direction.name,
                    amountMinor = candidate.amountMinor,
                    currencyCode = candidate.currencyCode,
                    runningBalanceMinor = candidate.runningBalanceMinor,
                    normalizedType = finalTypeForPersistence(
                        item,
                        candidate.fingerprint in prepared.reviewNecessityPlan.deferredFingerprints
                    ).name,
                    reviewStatus = if (item.manuallyClassified) "RESOLVED" else candidate.reviewStatus.name,
                    fingerprint = candidate.fingerprint,
                    importBatchId = 0,
                    importedAt = now,
                    linkedExpenseId = null,
                    normalizedIdentity = candidate.normalizedIdentity,
                    classificationConfidence = if (item.manuallyClassified) ConfidenceLevel.HIGH.name else candidate.classificationConfidence.name,
                    categoryConfidence = if (item.manuallyClassified && item.decision == ImportDecision.EXPENSE && item.category != null) ConfidenceLevel.HIGH.name else candidate.categoryConfidence.name
                ), expense
            )
        }
        val rememberedFromGroups = prepared.reviewGroups.filter { it.resolved && it.rememberRule && it.rememberEligible }.map { group ->
            val nowRule = System.currentTimeMillis()
            val classification = finalTypeForPersistence(group.items.first())
            com.izavo.app.data.MerchantRuleEntity(
                sourceBank = group.items.first().transaction.sourceBank,
                normalizedIdentity = group.identity,
                bankRail = group.bankRail,
                direction = group.direction.name,
                classification = classification.name,
                category = group.suggestedCategory,
                source = RuleSource.USER_CONFIRMED.name,
                confidence = ConfidenceLevel.HIGH.name,
                userConfirmed = true,
                createdAt = nowRule,
                updatedAt = nowRule
            )
        }
        val rememberedFromAdaptiveReview = prepared.confirmedRules
            .filter { it.persistAfterImport && it.rule.isEligible }
            .map { confirmed ->
                val rule = confirmed.rule
                val nowRule = System.currentTimeMillis()
                com.izavo.app.data.MerchantRuleEntity(
                    sourceBank = rule.sourceBank,
                    normalizedIdentity = rule.normalizedIdentity,
                    bankRail = BankRailNormalizer.legacyName(rule.normalizedRail),
                    direction = rule.direction.name,
                    classification = finalTypeForDecision(rule.decision).name,
                    category = rule.category,
                    source = RuleSource.USER_CONFIRMED.name,
                    confidence = ConfidenceLevel.HIGH.name,
                    userConfirmed = true,
                    createdAt = nowRule,
                    updatedAt = nowRule
                )
            }
        val rememberedRules = (rememberedFromGroups + rememberedFromAdaptiveReview).distinctBy {
            listOf(it.sourceBank, it.normalizedIdentity, it.bankRail, it.direction).joinToString("|")
        }
        val result = dao.persistBatch(
            ImportBatchEntity(sourceBank = BmlCsvParser.SOURCE_BANK, fileName = prepared.fileName, importedAt = now,
                totalRows = prepared.items.size, invalidRows = prepared.invalidRowCount),
            persistenceRows,
            rememberedRules
        )
        return ImportResultSummary(
            transactionCount = result.storedTransactionCount,
            expenseCount = result.expenseCount,
            excludedCount = uniqueRows.size - result.expenseCount,
            duplicateCount = prepared.duplicateCount,
            invalidRowCount = prepared.invalidRowCount
        )
    }

    private fun defaultDecision(candidate: CandidateTransaction) = when (candidate.reviewStatus) {
        ImportReviewStatus.READY -> ImportDecision.EXPENSE
        else -> ReviewGroupBuilder.decisionFor(candidate.normalizedType)
    }

}

internal fun finalTypeForPersistence(
    item: ImportReviewItem,
    deferred: Boolean = false
): NormalizedTransactionType {
    if (item.manuallyClassified) return finalTypeForDecision(item.decision)
    if (deferred) return NormalizedTransactionType.UNKNOWN
    return item.transaction.normalizedType
}

internal fun createsExpense(decision: ImportDecision): Boolean = decision == ImportDecision.EXPENSE

internal fun finalTypeForDecision(decision: ImportDecision): NormalizedTransactionType = when (decision) {
    ImportDecision.EXPENSE -> NormalizedTransactionType.EXPENSE
    ImportDecision.INCOME -> NormalizedTransactionType.INCOME
    ImportDecision.MONEY_MOVEMENT -> NormalizedTransactionType.MONEY_MOVEMENT
    ImportDecision.REFUND -> NormalizedTransactionType.REFUND
    ImportDecision.UNKNOWN -> NormalizedTransactionType.UNKNOWN
    ImportDecision.IGNORE -> NormalizedTransactionType.IGNORE
}

internal fun markDuplicates(candidates: List<CandidateTransaction>, existing: Set<String>): List<CandidateTransaction> =
    buildList {
        val seen = existing.toMutableSet()
        candidates.forEach { candidate ->
            if (!seen.add(candidate.fingerprint)) add(candidate.copy(reviewStatus = ImportReviewStatus.DUPLICATE))
            else add(candidate)
        }
    }
