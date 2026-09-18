package com.izavo.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.izavo.app.preferences.CurrencyPreferencesRepository
import com.izavo.app.preferences.AppAppearance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayNamePreferenceTest {
    // Test-package preferences keep installed IZAVO user data untouched.
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    @Test fun namePersistsAndEditingPreservesOnboardingAndSeconds() {
        val repo = CurrencyPreferencesRepository(context)
        repo.setShowSeconds(true)
        repo.completeOnboarding("MVR", "USD", listOf("MVR", "USD"), " Adam ")
        assertEquals("Adam", CurrencyPreferencesRepository(context).state.value.displayName)
        repo.setDisplayName(" އާދަމް ")
        val saved = CurrencyPreferencesRepository(context).state.value
        assertEquals("އާދަމް", saved.displayName)
        assertTrue(saved.onboardingCompleted)
        assertTrue(saved.showSeconds)
        assertEquals("USD", saved.defaultExpenseCurrency)
        repo.setDisplayName(" ")
        assertEquals("", CurrencyPreferencesRepository(context).state.value.displayName)
        assertTrue(CurrencyPreferencesRepository(context).state.value.onboardingCompleted)
        repo.setAppearance(AppAppearance.DARK)
        val themed = CurrencyPreferencesRepository(context).state.value
        assertEquals(AppAppearance.DARK, themed.appearance)
        assertTrue(themed.onboardingCompleted)
    }
}
