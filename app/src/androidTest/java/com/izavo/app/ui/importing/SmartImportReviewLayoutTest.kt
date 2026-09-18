package com.izavo.app.ui.importing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.izavo.app.importing.*
import com.izavo.app.ui.theme.ExpenseTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SmartImportReviewLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryAndGuidedReviewUseVerticalNonOverlappingLayouts() {
        val transaction = CandidateTransaction(
            sourceBank = "BML", postedAt = 1_788_300_000_000, valueAt = null, transactionAt = null,
            rawType = "Favara Debit", rawDescription = "ECHBEES LLP", rawDetail = "Payment",
            primaryReference = "R1", secondaryReference = null, direction = TransactionDirection.DEBIT,
            amountMinor = 8_900, currencyCode = "MVR", runningBalanceMinor = null,
            normalizedType = NormalizedTransactionType.OUTGOING_TRANSFER,
            reviewStatus = ImportReviewStatus.NEEDS_REVIEW, displayName = "ECHBEES LLP", fingerprint = "fp",
            normalizedIdentity = "ECHBEES LLP", bankRail = "FAVARA",
            classificationConfidence = ConfidenceLevel.LOW
        )
        val item = ImportReviewItem(transaction, ImportDecision.MONEY_MOVEMENT)
        val group = ReviewGroupBuilder.build(listOf(item)).single()
        val prepared = PreparedImport("statement.csv", listOf(item), 0, 0, listOf(group))

        compose.setContent {
            ExpenseTrackerTheme {
                SmartImportFlowScreen(
                    ImportUiState.Review(prepared), false, {}, { _, _, _, _ -> },
                    { _, _ -> }, {}, {}, {}
                )
            }
        }

        compose.onNodeWithText("What was this money for?").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.onNodeWithText("1 similar transactions").assertDoesNotExist()
        compose.onNodeWithText("These look like payments involving the same person or business.").assertDoesNotExist()
        val question = compose.onNodeWithText("What was this money for?").fetchSemanticsNode().boundsInRoot
        val continueButton = compose.onNodeWithText("Continue").fetchSemanticsNode().boundsInRoot
        assertTrue(question.bottom < continueButton.top)
    }

    @Test fun safeGroupedReviewUsesOnlyConciseGroupCopy() {
        val one = reviewItem("merchant-one", "AVAS RIDE", rawType = "Purchase", rail = "PURCHASE")
        val two = reviewItem("merchant-two", "AVAS RIDE", rawType = "Purchase", rail = "PURCHASE")
        val groups = ReviewGroupBuilder.build(listOf(one, two))
        val prepared = PreparedImport("group.csv", listOf(one, two), 0, 0, groups)
        compose.setContent { ExpenseTrackerTheme {
            SmartImportFlowScreen(ImportUiState.Review(prepared), false, {}, { _, _, _, _ -> }, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("2 similar transactions").assertIsDisplayed()
        compose.onNodeWithText("Apply to all 2").assertIsDisplayed()
        compose.onNodeWithText("These look like payments involving the same person or business.").assertDoesNotExist()
    }

    @Test fun lastDecisionTransitionsToFinalConfirmationWithoutReadingEmptyList() {
        val transaction = CandidateTransaction(
            sourceBank = "BML", postedAt = 1_788_300_000_000, valueAt = null, transactionAt = null,
            rawType = "Favara Debit", rawDescription = "AHMED", rawDetail = "Payment",
            primaryReference = "R2", secondaryReference = null, direction = TransactionDirection.DEBIT,
            amountMinor = 1_500, currencyCode = "MVR", runningBalanceMinor = null,
            normalizedType = NormalizedTransactionType.OUTGOING_TRANSFER,
            reviewStatus = ImportReviewStatus.NEEDS_REVIEW, displayName = "AHMED", fingerprint = "last",
            normalizedIdentity = "AHMED", bankRail = "FAVARA", classificationConfidence = ConfidenceLevel.LOW
        )
        val item = ImportReviewItem(transaction, ImportDecision.MONEY_MOVEMENT)
        val initialGroup = ReviewGroupBuilder.build(listOf(item)).single()
        var prepared by mutableStateOf(PreparedImport("statement.csv", listOf(item), 0, 0, listOf(initialGroup)))

        compose.setContent {
            ExpenseTrackerTheme {
                SmartImportFlowScreen(
                    ImportUiState.Review(prepared), false, {},
                    { id, decision, category, remember ->
                        val groups = prepared.reviewGroups.map { group ->
                            if (group.id == id) ReviewGroupBuilder.applyDecision(group, decision, category, remember) else group
                        }
                        val changed = groups.flatMap { it.items }.associateBy { it.transaction.fingerprint }
                        prepared = prepared.copy(reviewGroups = groups, items = prepared.items.map { changed[it.transaction.fingerprint] ?: it })
                    },
                    { _, _ -> }, {}, {}, {}
                )
            }
        }

        compose.onNodeWithText("Don't remember").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ready to import").assertIsDisplayed()
        compose.onNodeWithText("1 other transactions saved").assertIsDisplayed()
    }

    @Test fun reviewProgressUsesStableGroupCount() {
        val one = reviewItem("one", "AHMED")
        val two = reviewItem("two", "MARIYAM")
        val groups = ReviewGroupBuilder.build(listOf(one, two))
        var prepared by mutableStateOf(PreparedImport("two.csv", listOf(one, two), 0, 0, groups))
        compose.setContent { ExpenseTrackerTheme {
            SmartImportFlowScreen(ImportUiState.Review(prepared), false, {}, { id, decision, category, remember ->
                val changedGroups = prepared.reviewGroups.map {
                    if (it.id == id) ReviewGroupBuilder.applyDecision(it, decision, category, remember) else it
                }
                val changedItems = changedGroups.flatMap { it.items }.associateBy { it.transaction.fingerprint }
                prepared = prepared.copy(reviewGroups = changedGroups,
                    items = prepared.items.map { changedItems[it.transaction.fingerprint] ?: it },
                    reviewProgress = prepared.reviewProgress.copy(
                        completedHumanDecisions = prepared.reviewProgress.completedHumanDecisions + 1
                    ))
            }, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("Review 1 · 2 remaining").assertIsDisplayed()
        compose.onNodeWithText("Don't remember").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Review 2 · 1 remaining").assertIsDisplayed()
    }

    @Test fun importCompleteOmitsZeroSummaryLinesAndDoneWorks() {
        var done = false
        compose.setContent { ExpenseTrackerTheme {
            SmartImportFlowScreen(ImportUiState.Complete(ImportResultSummary(3, 3, 0, 0, 0)), false,
                { done = true }, { _, _, _, _ -> }, { _, _ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("3 expenses added").assertIsDisplayed()
        compose.onNodeWithText("0 other transactions saved").assertDoesNotExist()
        compose.onNodeWithText("0 duplicates skipped").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(done) }
    }

    private fun reviewItem(
        fingerprint: String,
        identity: String,
        rawType: String = "Favara Debit",
        rail: String = "FAVARA"
    ): ImportReviewItem {
        val transaction = CandidateTransaction(
            sourceBank = "BML", postedAt = 1_788_300_000_000, valueAt = null, transactionAt = null,
            rawType = rawType, rawDescription = identity, rawDetail = "Payment",
            primaryReference = fingerprint, secondaryReference = null, direction = TransactionDirection.DEBIT,
            amountMinor = 1_500, currencyCode = "MVR", runningBalanceMinor = null,
            normalizedType = NormalizedTransactionType.OUTGOING_TRANSFER,
            reviewStatus = ImportReviewStatus.NEEDS_REVIEW, displayName = identity, fingerprint = fingerprint,
            normalizedIdentity = identity, bankRail = rail,
            normalizedRail = if (rawType == "Purchase") NormalizedRail.MERCHANT_PURCHASE else NormalizedRail.P2P_TRANSFER,
            classificationConfidence = ConfidenceLevel.LOW
        )
        return ImportReviewItem(transaction, ImportDecision.MONEY_MOVEMENT)
    }
}
