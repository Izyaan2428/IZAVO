package com.izavo.app.data

data class CurrencyCoverage(
    val currencyCode: String,
    val firstOccurredAt: Long,
    val lastOccurredAt: Long,
    val transactionCount: Int
)

data class ExpenseMerchantMetadata(
    val expenseId: Long,
    val normalizedIdentity: String?,
    val rawDescription: String
)
