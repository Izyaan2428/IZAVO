package com.izavo.app.data

import java.util.Locale

const val HOME_CURRENCY_CODE = "MVR"

val SUPPORTED_CURRENCY_CODES = listOf(
    HOME_CURRENCY_CODE,
    "USD",
    "EUR",
    "GBP"
)

val CURRENCY_NAMES = mapOf(
    "MVR" to "Maldivian Rufiyaa",
    "USD" to "US Dollar",
    "EUR" to "Euro",
    "GBP" to "British Pound"
)

/**
 * V1 stores all supported amounts as 100 minor units per major unit.
 * Currencies with a different exponent, such as JPY, need currency metadata
 * before they can be added safely.
 */
fun formatCurrency(
    amountMinor: Long,
    currencyCode: String
): String {
    return String.format(
        Locale.US,
        "%s %,.2f",
        currencyCode,
        amountMinor / 100.0
    )
}

fun currencyDisplayOrder(
    currencyCode: String,
    homeCurrencyCode: String = HOME_CURRENCY_CODE
): Int {
    if (currencyCode == homeCurrencyCode) return -1
    val supportedIndex = SUPPORTED_CURRENCY_CODES.indexOf(currencyCode)
    return if (supportedIndex >= 0) supportedIndex else Int.MAX_VALUE
}
