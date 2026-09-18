package com.izavo.app.preferences

import android.content.Context
import com.izavo.app.data.HOME_CURRENCY_CODE
import com.izavo.app.data.SUPPORTED_CURRENCY_CODES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CurrencyPreferencesRepository(context: Context) {
    private val preferences = context.getSharedPreferences(
        "currency_preferences",
        Context.MODE_PRIVATE
    )

    private val mutableState = MutableStateFlow(readPreferences())
    val state: StateFlow<CurrencyPreferences> = mutableState.asStateFlow()

    fun completeOnboarding(
        homeCurrency: String,
        defaultExpenseCurrency: String,
        quickCurrencies: List<String>,
        displayName: String = state.value.displayName
    ) {
        save(
            state.value.copy(
                homeCurrency = supportedOrDefault(homeCurrency),
                defaultExpenseCurrency = supportedOrDefault(defaultExpenseCurrency),
                quickCurrencies = quickCurrencies,
                onboardingCompleted = true,
                displayName = displayName
            )
        )
    }

    fun setHomeCurrency(currencyCode: String) {
        save(state.value.copy(homeCurrency = supportedOrDefault(currencyCode)))
    }

    fun setDefaultExpenseCurrency(currencyCode: String) {
        save(state.value.copy(defaultExpenseCurrency = supportedOrDefault(currencyCode)))
    }

    fun setQuickCurrencies(currencyCodes: List<String>) {
        save(state.value.copy(quickCurrencies = currencyCodes))
    }

    fun setShowSeconds(show: Boolean) {
        save(state.value.copy(showSeconds = show))
    }

    fun setDisplayName(name: String) {
        save(state.value.copy(displayName = normalizeDisplayName(name)))
    }

    fun setAppearance(appearance: AppAppearance) {
        save(state.value.copy(appearance = appearance))
    }

    private fun save(value: CurrencyPreferences) {
        val normalized = normalize(value)
        preferences.edit()
            .putString(KEY_HOME, normalized.homeCurrency)
            .putString(KEY_DEFAULT, normalized.defaultExpenseCurrency)
            .putStringSet(KEY_QUICK, normalized.quickCurrencies.toSet())
            .putBoolean(KEY_ONBOARDING, normalized.onboardingCompleted)
            .putBoolean(KEY_SHOW_SECONDS, normalized.showSeconds)
            .putString(KEY_NAME, normalized.displayName)
            .putString(KEY_APPEARANCE, normalized.appearance.name)
            .apply()
        mutableState.value = normalized
    }

    private fun readPreferences(): CurrencyPreferences = normalize(
        CurrencyPreferences(
            homeCurrency = preferences.getString(KEY_HOME, HOME_CURRENCY_CODE)
                ?: HOME_CURRENCY_CODE,
            defaultExpenseCurrency = preferences.getString(KEY_DEFAULT, HOME_CURRENCY_CODE)
                ?: HOME_CURRENCY_CODE,
            quickCurrencies = preferences.getStringSet(
                KEY_QUICK,
                setOf(HOME_CURRENCY_CODE, "USD")
            ).orEmpty().toList(),
            onboardingCompleted = preferences.getBoolean(KEY_ONBOARDING, false),
            showSeconds = preferences.getBoolean(KEY_SHOW_SECONDS, false),
            displayName = preferences.getString(KEY_NAME, "").orEmpty(),
            appearance = runCatching {
                AppAppearance.valueOf(preferences.getString(KEY_APPEARANCE, AppAppearance.LIGHT.name).orEmpty())
            }.getOrDefault(AppAppearance.LIGHT)
        )
    )

    private fun normalize(value: CurrencyPreferences): CurrencyPreferences {
        val home = supportedOrDefault(value.homeCurrency)
        val defaultExpense = supportedOrDefault(value.defaultExpenseCurrency)
        val requested = value.quickCurrencies.filter { it in SUPPORTED_CURRENCY_CODES }
        val quick = SUPPORTED_CURRENCY_CODES.filter {
            it in requested || it == home || it == defaultExpense
        }
        return value.copy(
            homeCurrency = home,
            defaultExpenseCurrency = defaultExpense,
            quickCurrencies = quick,
            displayName = normalizeDisplayName(value.displayName)
        )
    }

    private fun supportedOrDefault(currencyCode: String): String =
        currencyCode.takeIf { it in SUPPORTED_CURRENCY_CODES } ?: HOME_CURRENCY_CODE

    private companion object {
        const val KEY_HOME = "home_currency"
        const val KEY_DEFAULT = "default_expense_currency"
        const val KEY_QUICK = "quick_currencies"
        const val KEY_ONBOARDING = "onboarding_completed"
        const val KEY_SHOW_SECONDS = "show_seconds"
        const val KEY_NAME = "displayName"
        const val KEY_APPEARANCE = "appearance"
    }
}
