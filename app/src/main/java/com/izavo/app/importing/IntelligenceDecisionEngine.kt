package com.izavo.app.importing

import java.time.ZoneId

class IntelligenceDecisionEngine(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    fun analyzeStatement(
        transactions: List<CandidateTransaction>,
        existingRules: List<MerchantRule>,
        temporaryRules: List<TemporaryImportRule> = emptyList(),
        priorContext: StatementIntelligenceResult? = null
    ): StatementIntelligenceResult {
        val canonical = transactions.map(CanonicalTransactionAdapter::from)
        val profiles = StatementProfileBuilder(zoneId).build(canonical)
        val relationships = RelationshipDetector().detect(canonical, profiles.contexts)
        val relationshipIndex = relationships.flatMap { relationship ->
            relationship.fingerprints.map { it to relationship }
        }.groupBy({ it.first }, { it.second })
        val resolver = EntityResolver()
        val analyzed = transactions.zip(canonical).map { (original, normalized) ->
            val profile = profiles.identities[normalized.counterpartyNormalized]
            val entity = resolver.resolve(normalized, profile)
            decide(original, normalized, entity, profile, profiles.contexts, relationshipIndex[normalized.fingerprint].orEmpty(), existingRules, temporaryRules)
        }
        return StatementIntelligenceResult(analyzed, profiles.identities, profiles.contexts, relationships)
    }

    fun reEvaluate(
        snapshot: StatementIntelligenceResult,
        updatedRules: List<MerchantRule>,
        temporaryRules: List<TemporaryImportRule> = emptyList()
    ): StatementIntelligenceResult = analyzeStatement(
        snapshot.transactions.map { it.transaction }, updatedRules, temporaryRules, snapshot
    )

    private fun decide(
        original: CandidateTransaction,
        tx: CanonicalTransaction,
        entity: EntityResolution,
        profile: IdentityProfile?,
        contexts: Map<SemanticContextKey, IdentityContextProfile>,
        relationships: List<TransactionRelationship>,
        rules: List<MerchantRule>,
        temporaryRules: List<TemporaryImportRule>
    ): AnalyzedTransaction {
        val evidence = entity.evidence.toMutableList()
        val vetoes = mutableListOf<EvidenceType>()
        var meaning = original.normalizedType
        var meaningConfidence = IntelligenceConfidence.LOW
        var category = original.suggestedCategory
        var categoryConfidence = original.categoryConfidence.toIntelligence()

        if (original.reviewStatus == ImportReviewStatus.DUPLICATE) {
            return analyzed(original, tx, entity, relationships, evidence, meaning, IntelligenceConfidence.VERY_HIGH, category, categoryConfidence, false, vetoes)
        }
        if (original.reviewStatus == ImportReviewStatus.REVERSED || tx.rail == NormalizedRail.REVERSAL) {
            evidence += TransactionEvidence(EvidenceType.EXACT_REVERSAL, NormalizedTransactionType.REVERSED, EvidenceStrength.DECISIVE, "Bank reversal evidence")
            return analyzed(original, tx, entity, relationships, evidence, NormalizedTransactionType.REVERSED, IntelligenceConfidence.VERY_HIGH, null, IntelligenceConfidence.UNKNOWN, false, vetoes)
        }

        val relationship = relationships.maxByOrNull { it.confidence.ordinal.unconfidenceRank() }
        if (tx.direction == TransactionDirection.CREDIT && relationship?.type == RelationshipType.LIKELY_REFUND) {
            evidence += relationship.evidence
            meaning = NormalizedTransactionType.REFUND
            meaningConfidence = IntelligenceConfidence.VERY_HIGH
        } else if (relationship?.type == RelationshipType.OWN_ACCOUNT_TRANSFER_CANDIDATE) {
            evidence += relationship.evidence
            meaning = NormalizedTransactionType.MONEY_MOVEMENT
            meaningConfidence = IntelligenceConfidence.HIGH
        } else when (tx.rail) {
            NormalizedRail.MERCHANT_PURCHASE -> if (tx.direction == TransactionDirection.DEBIT) {
                evidence += TransactionEvidence(EvidenceType.BANK_RAIL_PURCHASE, NormalizedTransactionType.EXPENSE, EvidenceStrength.DECISIVE, "Merchant purchase rail")
                meaning = NormalizedTransactionType.EXPENSE
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
                category = knownCategory(tx.merchantFamily) ?: category ?: "Other"
                categoryConfidence = if (knownCategory(tx.merchantFamily) != null) IntelligenceConfidence.HIGH else IntelligenceConfidence.LOW
            } else {
                meaning = NormalizedTransactionType.REFUND
                meaningConfidence = IntelligenceConfidence.LOW
            }
            NormalizedRail.BILL_PAYMENT -> {
                evidence += TransactionEvidence(EvidenceType.BANK_RAIL_BILL_PAYMENT, NormalizedTransactionType.EXPENSE, EvidenceStrength.DECISIVE, "Bill-payment rail")
                meaning = NormalizedTransactionType.EXPENSE
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
                category = "Bills"
                categoryConfidence = IntelligenceConfidence.VERY_HIGH
            }
            NormalizedRail.ATM_WITHDRAWAL, NormalizedRail.CASH_DEPOSIT, NormalizedRail.CASH_MOVEMENT -> {
                evidence += TransactionEvidence(EvidenceType.BANK_RAIL_ATM, NormalizedTransactionType.MONEY_MOVEMENT, EvidenceStrength.DECISIVE, "Cash movement rail")
                meaning = NormalizedTransactionType.CASH_MOVEMENT
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
            }
            NormalizedRail.CARD_PAYMENT -> {
                evidence += TransactionEvidence(EvidenceType.BANK_RAIL_CARD_PAYMENT, NormalizedTransactionType.MONEY_MOVEMENT, EvidenceStrength.DECISIVE, "Card payment is debt movement")
                meaning = NormalizedTransactionType.DEBT_PAYMENT
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
            }
            NormalizedRail.LOAN_PAYMENT -> {
                evidence += TransactionEvidence(EvidenceType.BANK_RAIL_LOAN_PAYMENT, NormalizedTransactionType.MONEY_MOVEMENT, EvidenceStrength.DECISIVE, "Loan payment is debt movement")
                meaning = NormalizedTransactionType.DEBT_PAYMENT
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
            }
            NormalizedRail.SALARY_HINT -> if (tx.direction == TransactionDirection.CREDIT) {
                evidence += TransactionEvidence(EvidenceType.RECURRING_INCOMING_STREAM, NormalizedTransactionType.INCOME, EvidenceStrength.DECISIVE, "Explicit salary rail")
                meaning = NormalizedTransactionType.INCOME
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
            }
            NormalizedRail.BANK_FEE -> if (tx.direction == TransactionDirection.DEBIT) {
                evidence += TransactionEvidence(EvidenceType.BANK_FEE, NormalizedTransactionType.EXPENSE, EvidenceStrength.DECISIVE, "Outgoing bank fee")
                meaning = NormalizedTransactionType.EXPENSE
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
                category = "Other"
            }
            NormalizedRail.INTEREST -> {
                meaning = if (tx.direction == TransactionDirection.CREDIT) NormalizedTransactionType.INCOME else NormalizedTransactionType.EXPENSE
                evidence += TransactionEvidence(if (tx.direction == TransactionDirection.CREDIT) EvidenceType.INTEREST_INCOME else EvidenceType.INTEREST_EXPENSE, meaning, EvidenceStrength.DECISIVE, "Direction-compatible interest rail")
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
                if (meaning == NormalizedTransactionType.EXPENSE) category = "Other"
            }
            NormalizedRail.P2P_TRANSFER, NormalizedRail.BANK_TRANSFER -> {
                evidence += TransactionEvidence(EvidenceType.BANK_RAIL_TRANSFER, null, EvidenceStrength.WEAK, "Transfer rail does not reveal purpose")
                if (entity.kind == EntityKind.PERSON || tx.rail == NormalizedRail.P2P_TRANSFER) {
                    evidence += TransactionEvidence(EvidenceType.P2P_AMBIGUITY, null, EvidenceStrength.STRONG, "Person-to-person purpose is ambiguous")
                    vetoes += EvidenceType.P2P_AMBIGUITY
                }
                meaning = original.normalizedType
                meaningConfidence = IntelligenceConfidence.LOW
            }
            NormalizedRail.UNKNOWN -> {
                evidence += TransactionEvidence(EvidenceType.UNKNOWN_RAIL, null, EvidenceStrength.STRONG, "Unknown rail requires review")
                vetoes += EvidenceType.UNKNOWN_RAIL
                meaning = NormalizedTransactionType.UNKNOWN
                meaningConfidence = IntelligenceConfidence.UNKNOWN
            }
            else -> Unit
        }

        val context = contexts[SemanticContextKey(tx.sourceBank, tx.counterpartyNormalized, tx.direction, tx.rail, tx.currencyCode)]
        if (context?.recurrence?.monthly == true || context?.recurrence?.weekly == true) {
            if (tx.direction == TransactionDirection.CREDIT && entity.kind == EntityKind.EMPLOYER && profile?.mixedDirections == false) {
                evidence += TransactionEvidence(EvidenceType.RECURRING_INCOMING_STREAM, NormalizedTransactionType.INCOME, EvidenceStrength.STRONG, "Stable recurring incoming employer stream")
                if (tx.rail != NormalizedRail.P2P_TRANSFER) {
                    meaning = NormalizedTransactionType.INCOME
                    meaningConfidence = IntelligenceConfidence.HIGH
                    vetoes.remove(EvidenceType.UNKNOWN_RAIL)
                }
            } else if (tx.direction == TransactionDirection.DEBIT && entity.kind in setOf(EntityKind.MERCHANT, EntityKind.BILLER)) {
                evidence += TransactionEvidence(EvidenceType.RECURRING_MERCHANT_STREAM, NormalizedTransactionType.EXPENSE, EvidenceStrength.STRONG, "Recurring merchant stream")
            }
        }
        if (profile?.mixedDirections == true && tx.rail in setOf(NormalizedRail.P2P_TRANSFER, NormalizedRail.BANK_TRANSFER)) {
            vetoes += EvidenceType.MIXED_DIRECTION_IDENTITY
        }

        val legacyRail = BankRailNormalizer.legacyName(tx.rail)
        val rule = rules.firstOrNull {
            it.normalizedIdentity == tx.counterpartyNormalized && it.bankRail == legacyRail &&
                it.direction == tx.direction && MerchantIdentityNormalizer.isStableMerchant(tx.counterpartyNormalized, legacyRail)
        }
        val temporaryRule = temporaryRules.firstOrNull {
            it.isEligible && it.sourceBank == tx.sourceBank &&
                it.normalizedIdentity == tx.counterpartyNormalized && it.direction == tx.direction &&
                it.normalizedRail == tx.rail && it.currencyCode == tx.currencyCode
        }
        val temporaryMeaning = temporaryRule?.decision?.let(::finalTypeForDecision)
        val persistedConflict = rule != null && temporaryMeaning != null &&
            rule.classification.semanticFamily() != temporaryMeaning.semanticFamily()
        if (persistedConflict) {
            evidence += TransactionEvidence(EvidenceType.CONFLICTING_STRONG_EVIDENCE, null, EvidenceStrength.DECISIVE, "Current confirmation conflicts with a saved rule")
            vetoes += EvidenceType.CONFLICTING_STRONG_EVIDENCE
        } else if (temporaryRule != null && EvidenceType.P2P_AMBIGUITY !in vetoes) {
            val incompatibleStructuralTarget = evidence.any {
                it.strength <= EvidenceStrength.STRONG && it.target != null && it.target != temporaryMeaning
            }
            if (!incompatibleStructuralTarget) {
                evidence += TransactionEvidence(EvidenceType.TEMPORARY_CONFIRMED_RULE, temporaryMeaning, EvidenceStrength.DECISIVE, "Compatible confirmation from this import")
                meaning = temporaryMeaning ?: meaning
                meaningConfidence = IntelligenceConfidence.VERY_HIGH
                category = temporaryRule.category
                categoryConfidence = if (temporaryRule.category == null) IntelligenceConfidence.LOW else IntelligenceConfidence.VERY_HIGH
            }
        } else if (rule != null && EvidenceType.P2P_AMBIGUITY !in vetoes) {
            evidence += TransactionEvidence(EvidenceType.USER_RULE, rule.classification, EvidenceStrength.DECISIVE, "Compatible confirmed user rule")
            meaning = rule.classification
            meaningConfidence = IntelligenceConfidence.VERY_HIGH
            category = rule.category
            categoryConfidence = if (rule.category == null) IntelligenceConfidence.LOW else IntelligenceConfidence.VERY_HIGH
        }

        val strongTargets = evidence.filter { it.strength <= EvidenceStrength.STRONG }.mapNotNull { it.target }.toSet()
        if (strongTargets.size > 1 && !(strongTargets == setOf(NormalizedTransactionType.EXPENSE, NormalizedTransactionType.REFUND) && tx.direction == TransactionDirection.CREDIT)) {
            evidence += TransactionEvidence(EvidenceType.CONFLICTING_STRONG_EVIDENCE, null, EvidenceStrength.DECISIVE, "Conflicting strong meaning evidence")
            vetoes += EvidenceType.CONFLICTING_STRONG_EVIDENCE
        }
        val requiresReview = vetoes.isNotEmpty() || !MeaningAutomationPolicy.permits(meaning, meaningConfidence)
        return analyzed(original, tx, entity, relationships, evidence, meaning, meaningConfidence, category, categoryConfidence, requiresReview, vetoes)
    }

    private fun analyzed(original: CandidateTransaction, tx: CanonicalTransaction, entity: EntityResolution,
        relationships: List<TransactionRelationship>, evidence: List<TransactionEvidence>, meaning: NormalizedTransactionType,
        meaningConfidence: IntelligenceConfidence, category: String?, categoryConfidence: IntelligenceConfidence,
        requiresReview: Boolean, vetoes: List<EvidenceType>): AnalyzedTransaction {
        val intelligence = TransactionIntelligence(
            original.fingerprint, meaning, meaningConfidence, category, categoryConfidence, entity, relationships,
            relationships.minOfOrNull { it.confidence } ?: IntelligenceConfidence.UNKNOWN,
            evidence.distinctBy { Triple(it.type, it.target, it.reason) }, requiresReview, vetoes.distinct()
        )
        val legacyConfidence = when (meaningConfidence) {
            IntelligenceConfidence.VERY_HIGH, IntelligenceConfidence.HIGH -> ConfidenceLevel.HIGH
            IntelligenceConfidence.MEDIUM -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        val status = when {
            original.reviewStatus == ImportReviewStatus.DUPLICATE -> ImportReviewStatus.DUPLICATE
            meaning == NormalizedTransactionType.REVERSED -> ImportReviewStatus.REVERSED
            requiresReview -> ImportReviewStatus.NEEDS_REVIEW
            meaning == NormalizedTransactionType.EXPENSE -> ImportReviewStatus.READY
            else -> ImportReviewStatus.EXCLUDED
        }
        val updated = original.copy(
            normalizedIdentity = tx.counterpartyNormalized,
            bankRail = BankRailNormalizer.legacyName(tx.rail),
            normalizedRail = tx.rail,
            normalizedType = meaning,
            reviewStatus = status,
            classificationConfidence = legacyConfidence,
            categoryConfidence = when (categoryConfidence) {
                IntelligenceConfidence.VERY_HIGH, IntelligenceConfidence.HIGH -> ConfidenceLevel.HIGH
                IntelligenceConfidence.MEDIUM -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            },
            suggestedCategory = if (meaning == NormalizedTransactionType.EXPENSE) category ?: "Other" else null,
            classificationReason = evidence.firstOrNull()?.reason ?: "Needs your input",
            intelligence = intelligence
        )
        return AnalyzedTransaction(updated, intelligence)
    }

    private fun knownCategory(identity: String): String? = when {
        "ALIEXPRESS" in identity -> "Shopping"
        "AVAS RIDE" in identity -> "Transport"
        listOf("GOOGLE ONE", "YOUTUBE PREMIUM", "GOOGLE WORKSPACE").any { it in identity } -> "Subscriptions"
        "CAFE" in identity || "RESTAURANT" in identity -> "Food"
        else -> null
    }

    private fun ConfidenceLevel.toIntelligence() = when (this) {
        ConfidenceLevel.HIGH -> IntelligenceConfidence.HIGH
        ConfidenceLevel.MEDIUM -> IntelligenceConfidence.MEDIUM
        ConfidenceLevel.LOW -> IntelligenceConfidence.LOW
    }

    private fun Int.unconfidenceRank() = -this

    private fun NormalizedTransactionType.semanticFamily(): ImportDecision = when (this) {
        NormalizedTransactionType.EXPENSE -> ImportDecision.EXPENSE
        NormalizedTransactionType.INCOME -> ImportDecision.INCOME
        NormalizedTransactionType.MONEY_MOVEMENT,
        NormalizedTransactionType.INCOMING_TRANSFER,
        NormalizedTransactionType.OUTGOING_TRANSFER,
        NormalizedTransactionType.CASH_MOVEMENT,
        NormalizedTransactionType.DEBT_PAYMENT -> ImportDecision.MONEY_MOVEMENT
        NormalizedTransactionType.REFUND, NormalizedTransactionType.REVERSED -> ImportDecision.REFUND
        NormalizedTransactionType.UNKNOWN -> ImportDecision.UNKNOWN
        NormalizedTransactionType.IGNORE -> ImportDecision.IGNORE
    }
}
