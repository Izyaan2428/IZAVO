package com.izavo.app.importing

import com.izavo.app.data.MerchantRuleEntity
import java.text.Normalizer
import java.util.Locale

object MerchantIdentityNormalizer {
    private val whitespace = Regex("\\s+")
    private val dateLike = Regex(".*\\d{2}[-/]\\d{2}[-/]\\d{2,4}.*")
    private val referenceLike = Regex("^(RB|FT|MADVIPS|MALBIPS|BLAZ)[A-Z0-9\\\\-]+$")
    private val generic = setOf("PURCHASE", "TRANSFER DEBIT", "TRANSFER CREDIT", "FAVARA DEBIT", "FAVARA CREDIT", "SALARY")
    internal val knownMerchants = listOf("ALIEXPRESS", "AVAS RIDE", "GOOGLE ONE", "YOUTUBE PREMIUM", "GOOGLE WORKSPACE")
    internal val merchantMarkers = listOf(
        "MART", "STORE", "SHOP", "CAFE", "RESTAURANT", "HOTEL", "PHARMACY",
        "AIRLINES"
    )
    internal val businessSuffixes = listOf("LLP", "PVT", "LTD", "INC", "LLC")
    internal val businessMarkers = merchantMarkers + businessSuffixes
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val terminalNoise = Regex("(?:\\s+|^)(?:REF|TXN)?\\d{6,}$")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().replace(whitespace, " ").uppercase(Locale.ROOT)

    fun isLearnable(identity: String): Boolean {
        if (identity.length !in 3..100 || identity in generic || dateLike.matches(identity) || referenceLike.matches(identity)) return false
        val letters = identity.count(Char::isLetter)
        val digits = identity.count(Char::isDigit)
        return letters >= 3 && letters >= digits
    }

    fun merchantFamily(identity: String): String {
        val normalized = normalize(identity)
            .replace("MPGOOGLE", "GOOGLE")
            .replace("GOOGLE*", "GOOGLE ")
            .replace(punctuation, " ")
            .replace(terminalNoise, "")
            .replace(whitespace, " ")
            .trim()
        return when {
            normalized.startsWith("GOOGLE YOUTUBE") -> "YOUTUBE PREMIUM"
            normalized.startsWith("GOOGLE ONE") -> "GOOGLE ONE"
            normalized.startsWith("GOOGLE WORKSPACE") -> "GOOGLE WORKSPACE"
            else -> normalized
        }
    }

    /**
     * A readable name is not automatically a merchant. In transfer-like rails it may
     * simply be a person, so grouping or learning one decision would be unsafe.
     */
    fun isStableMerchant(identity: String, bankRail: String): Boolean {
        if (!isLearnable(identity)) return false
        if (bankRail == "PURCHASE" || bankRail == "BILLPAY") return true
        return knownMerchants.any { it in identity } || merchantMarkers.any { it in identity }
    }

    fun ruleEligibility(transaction: CandidateTransaction): RuleEligibilityType {
        val intelligence = transaction.intelligence
        if (transaction.normalizedRail == NormalizedRail.P2P_TRANSFER ||
            intelligence?.entity?.kind == EntityKind.PERSON ||
            EvidenceType.P2P_AMBIGUITY in intelligence?.automationVetoes.orEmpty()
        ) return RuleEligibilityType.INELIGIBLE_PERSON_OR_P2P
        if (!isLearnable(transaction.normalizedIdentity)) return RuleEligibilityType.INELIGIBLE_UNSTABLE_IDENTITY
        if (EvidenceType.MIXED_DIRECTION_IDENTITY in intelligence?.automationVetoes.orEmpty()) {
            return RuleEligibilityType.INELIGIBLE_MIXED_PURPOSE
        }
        val safeRail = transaction.normalizedRail in setOf(
            NormalizedRail.MERCHANT_PURCHASE,
            NormalizedRail.BILL_PAYMENT,
            NormalizedRail.DIRECT_DEBIT,
            NormalizedRail.STANDING_ORDER
        )
        val independentlyMerchantLike = knownMerchants.any { it in transaction.normalizedIdentity } ||
            merchantMarkers.any { it in transaction.normalizedIdentity }
        return if (safeRail || independentlyMerchantLike) RuleEligibilityType.STABLE_MERCHANT
        else RuleEligibilityType.INELIGIBLE_UNSAFE_RAIL
    }
}

data class MerchantRule(
    val normalizedIdentity: String,
    val bankRail: String,
    val direction: TransactionDirection,
    val classification: TransactionClassification,
    val category: String?,
    val source: RuleSource,
    val confidence: ConfidenceLevel,
    val userConfirmed: Boolean
)

fun MerchantRuleEntity.toDomain() = MerchantRule(
    normalizedIdentity, bankRail, TransactionDirection.valueOf(direction),
    TransactionClassification.valueOf(classification), category,
    RuleSource.valueOf(source), ConfidenceLevel.valueOf(confidence), userConfirmed
)
