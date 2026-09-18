package com.izavo.app.preferences

import com.izavo.app.data.HOME_CURRENCY_CODE

enum class AppAppearance { LIGHT, DARK, SYSTEM }

data class CurrencyPreferences(
    val homeCurrency: String = HOME_CURRENCY_CODE,
    val defaultExpenseCurrency: String = HOME_CURRENCY_CODE,
    val quickCurrencies: List<String> = listOf(HOME_CURRENCY_CODE, "USD"),
    val onboardingCompleted: Boolean = false,
    val showSeconds: Boolean = false,
    val displayName: String = "",
    val appearance: AppAppearance = AppAppearance.LIGHT
)
