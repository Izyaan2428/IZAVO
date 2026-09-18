package com.izavo.app.ui.statistics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.izavo.app.ui.theme.ExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsAllThreeSectionsAndChangesSection() {
        compose.setContent {
            ExpenseTrackerTheme {
                StatisticsScreen(StatisticsUiState(loading = false, availableCurrencies = listOf("MVR")), {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onNodeWithText("Categories").assertIsDisplayed()
        compose.onNodeWithText("Trends").assertIsDisplayed().performClick()
    }

    @Test fun emptyStateUsesRealCurrencySafeCopy() {
        compose.setContent {
            ExpenseTrackerTheme {
                StatisticsScreen(StatisticsUiState(loading = false, currencyCode = "USD", availableCurrencies = listOf("MVR", "USD")), {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Your spending story starts here.").assertIsDisplayed()
    }
}
