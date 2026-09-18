package com.izavo.app.importing

/** Compatibility façade retained for existing callers while V4 reasons statement-wide. */
class SmartTransactionClassifier {
    fun analyzeStatement(
        candidates: List<CandidateTransaction>,
        userRules: List<MerchantRule>
    ): StatementIntelligenceResult = IntelligenceDecisionEngine().analyzeStatement(candidates, userRules)

    fun classify(candidate: CandidateTransaction, userRules: List<MerchantRule>): CandidateTransaction =
        analyzeStatement(listOf(candidate), userRules).transactions.single().transaction

    companion object {
        fun bankRail(rawType: String): String = BankRailNormalizer.legacyName(BankRailNormalizer.fromRaw(rawType))
    }
}
