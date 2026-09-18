package com.izavo.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.izavo.app.ui.theme.ExpenseTrackerTheme
import com.izavo.app.ui.design.NameEditor
import com.izavo.app.ui.home.HomeV22Screen
import com.izavo.app.ui.home.HomeGreetingPose
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.izavo.app.ui.onboarding.OnboardingCompletion
import com.izavo.app.data.CurrencyTotal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RefinementUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationExposesSelectionAndAddAction() {
        var adds = 0
        compose.setContent { ExpenseTrackerTheme {
            var tab by remember { mutableStateOf(MainTab.HOME) }
            ExpenseBottomNavigation(tab, { tab = it }, { adds++ })
        } }
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Statistics").performClick().assertIsSelected()
        compose.onNodeWithText("Home").assertIsNotSelected()
        compose.onNodeWithContentDescription("Add Expense").performClick()
        compose.runOnIdle { assertEquals(1, adds) }
    }

    @Test fun unicodeNameInputAndImeDone() {
        var submitted = ""
        compose.setContent { ExpenseTrackerTheme {
            var name by remember { mutableStateOf("") }
            NameEditor(name, { name = it }, { submitted = name })
        } }
        compose.onNodeWithContentDescription("Your name").performTextInput("އާދަމް")
        compose.onNodeWithContentDescription("Your name").performImeAction()
        compose.runOnIdle { assertEquals("އާދަމް", submitted) }
    }

    @Test fun completionFinishesWithoutAUserAction() {
        var finished = false
        compose.mainClock.autoAdvance = false
        compose.setContent { ExpenseTrackerTheme {
            OnboardingCompletion("Good evening, Adam", HomeGreetingPose(Offset(24f, 68f), IntSize(240, 48))) { finished = true }
        } }
        compose.mainClock.advanceTimeBy(5700)
        compose.runOnIdle { assertTrue(finished) }
    }

    @Test fun emptyHomeHasAnIntentionalCurrentMonthState() {
        compose.setContent { ExpenseTrackerTheme {
            HomeV22Screen(emptyList(), emptyList(), emptyList(), "MVR", onStatisticsClick = {}, onViewAllClick = {}, onExpenseClick = {}, displayName = "Adam")
        } }
        compose.onNodeWithText("Nothing spent this month.").assertIsDisplayed()
        compose.onNodeWithText("A quiet month so far.").assertIsDisplayed()
    }

    @Test fun largeForeignHeroDoesNotInventHomeCurrencySpending() {
        compose.setContent { ExpenseTrackerTheme {
            HomeV22Screen(listOf(CurrencyTotal("USD", 100000000L)), emptyList(), emptyList(), "MVR", onStatisticsClick = {}, onViewAllClick = {}, onExpenseClick = {})
        } }
        compose.onNodeWithText("1,000,000.00").assertIsDisplayed()
        compose.onNodeWithText("USD").assertIsDisplayed()
        compose.onNodeWithText("MVR").assertDoesNotExist()
    }
}
