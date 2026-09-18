package com.izavo.app.importing

data class CanonicalTransaction(
    val sourceBank: String,
    val sourceFormat: String,
    val sourceAccountKey: String?,
    val sourceAccountType: String?,
    val postedAt: Long,
    val transactionAt: Long?,
    val amountMinor: Long,
    val currencyCode: String,
    val direction: TransactionDirection,
    val rail: NormalizedRail,
    val rawTransactionType: String,
    val counterpartyRaw: String,
    val counterpartyNormalized: String,
    val merchantFamily: String,
    val descriptionRaw: String,
    val additionalDescription: String,
    val primaryReference: String?,
    val secondaryReference: String?,
    val balanceMinor: Long?,
    val bankProvidedCategory: String?,
    val merchantCategoryCode: String?,
    val channel: String?,
    val fingerprint: String
)

object CanonicalTransactionAdapter {
    fun from(candidate: CandidateTransaction): CanonicalTransaction {
        val identity = MerchantIdentityNormalizer.normalize(candidate.displayName)
        val rail = candidate.normalizedRail.takeUnless { it == NormalizedRail.UNKNOWN }
            ?: BankRailNormalizer.fromRaw(candidate.rawType)
        return CanonicalTransaction(
            sourceBank = candidate.sourceBank,
            sourceFormat = candidate.sourceFormat,
            sourceAccountKey = candidate.sourceAccountKey,
            sourceAccountType = candidate.sourceAccountType,
            postedAt = candidate.postedAt,
            transactionAt = candidate.transactionAt,
            amountMinor = candidate.amountMinor,
            currencyCode = candidate.currencyCode,
            direction = candidate.direction,
            rail = rail,
            rawTransactionType = candidate.rawType,
            counterpartyRaw = candidate.displayName,
            counterpartyNormalized = identity,
            merchantFamily = MerchantIdentityNormalizer.merchantFamily(identity),
            descriptionRaw = candidate.rawDescription,
            additionalDescription = candidate.rawDetail,
            primaryReference = candidate.primaryReference,
            secondaryReference = candidate.secondaryReference,
            balanceMinor = candidate.runningBalanceMinor,
            bankProvidedCategory = candidate.bankProvidedCategory,
            merchantCategoryCode = candidate.merchantCategoryCode,
            channel = candidate.channel,
            fingerprint = candidate.fingerprint
        )
    }
}

object BankRailNormalizer {
    fun fromRaw(rawType: String): NormalizedRail {
        val type = rawType.trim().uppercase()
        return when {
            type == "PURCHASE" -> NormalizedRail.MERCHANT_PURCHASE
            "BILLPAY" in type || "BILL PAYMENT" in type -> NormalizedRail.BILL_PAYMENT
            type.startsWith("FAVARA") -> NormalizedRail.P2P_TRANSFER
            type.startsWith("TRANSFER") -> NormalizedRail.BANK_TRANSFER
            "ATM" in type || "CASH WITHDRAWAL" in type -> NormalizedRail.ATM_WITHDRAWAL
            "CASH DEPOSIT" in type -> NormalizedRail.CASH_DEPOSIT
            "SALARY" in type || "PAYROLL" in type -> NormalizedRail.SALARY_HINT
            "CREDIT CARD" in type || "CARD PAYMENT" in type || "CARD AUTOPAY" in type -> NormalizedRail.CARD_PAYMENT
            "LOAN PAYMENT" in type || "LOAN REPAYMENT" in type -> NormalizedRail.LOAN_PAYMENT
            "REVERS" in type -> NormalizedRail.REVERSAL
            "REFUND" in type -> NormalizedRail.REFUND_HINT
            "BANK FEE" in type || type == "FEE" || "SERVICE CHARGE" in type -> NormalizedRail.BANK_FEE
            "INTEREST" in type -> NormalizedRail.INTEREST
            "DIRECT DEBIT" in type -> NormalizedRail.DIRECT_DEBIT
            "STANDING ORDER" in type -> NormalizedRail.STANDING_ORDER
            else -> NormalizedRail.UNKNOWN
        }
    }

    fun legacyName(rail: NormalizedRail): String = when (rail) {
        NormalizedRail.MERCHANT_PURCHASE -> "PURCHASE"
        NormalizedRail.BILL_PAYMENT -> "BILLPAY"
        NormalizedRail.P2P_TRANSFER -> "FAVARA"
        NormalizedRail.BANK_TRANSFER -> "TRANSFER"
        NormalizedRail.SALARY_HINT -> "SALARY"
        NormalizedRail.ATM_WITHDRAWAL, NormalizedRail.CASH_DEPOSIT, NormalizedRail.CASH_MOVEMENT -> "CASH"
        else -> rail.name
    }
}
