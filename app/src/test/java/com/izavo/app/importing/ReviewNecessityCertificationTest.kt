package com.izavo.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class ReviewNecessityCertificationTest {
    @Test fun exactTortureFixturesMeetSelectedSafetyFrontierAndPrintFullMetrics() {
        val root = locateTortureFixtureDirectory()
        val fixtures = listOf(
            Fixture("Level 1", "IZAVO_BML_TORTURE_V2_LEVEL1_100.csv", 100, 0, 51),
            Fixture("Level 2", "IZAVO_BML_TORTURE_V2_LEVEL2_500.csv", 498, 2, 260),
            Fixture("Level 3", "IZAVO_BML_TORTURE_V2_LEVEL3_1200.csv", 1198, 2, 630)
        )
        fixtures.forEach { fixture ->
            val statement = File(root, fixture.statement)
            val truthFile = File(root, statement.nameWithoutExtension + "_GROUND_TRUTH.csv")
            require(statement.isFile && truthFile.isFile) { "Missing exact certification fixture ${fixture.statement}" }
            val (v4, prepared) = statement.reader().use { input ->
                truthFile.reader().use { truth -> SmartImportBenchmark.measure(input, truth) }
            }
            val truth = truthByFingerprint(statement, truthFile)
            val optimized = ReviewQueueOptimizer().optimize(prepared, initializeProgress = true)
            val metrics = metrics(fixture.name, optimized, truth, v4.reviewRows)
            val adaptive = simulateAdaptive(optimized, truth)
            println(metrics.report())
            println("${fixture.name} adaptive decisions=${adaptive.finalHumanDecisions} saved=${adaptive.netHumanDecisionsSaved} autoDuringReview=${adaptive.uniqueRowsAutoResolvedDuringReview}")

            assertEquals(fixture.uniqueRows, v4.uniqueRows)
            assertEquals(fixture.duplicates, v4.duplicateRows)
            assertEquals(fixture.v4ReviewRows, v4.reviewRows)
            assertEquals(0, v4.catastrophicErrors)
            assertTrue("${fixture.name} expense-count capture", metrics.expenseCountCapturePercent >= 95.0)
            assertTrue("${fixture.name} expense-value capture", metrics.expenseValueCapturePercent >= 98.0)
            assertEquals(0, metrics.deferredRows.count { it.candidate.direction == TransactionDirection.CREDIT && it.disposition == ReviewDisposition.ASK_NOW })
            assertTrue(adaptive.finalHumanDecisions <= optimized.reviewProgress.initialDecisionCount)
        }
    }

    @Test fun candidatePolicyCalibrationIsReportedWithoutWeakeningV4() {
        val root = locateTortureFixtureDirectory()
        val statement = File(root, "IZAVO_BML_TORTURE_V2_LEVEL3_1200.csv")
        val truthFile = File(root, "IZAVO_BML_TORTURE_V2_LEVEL3_1200_GROUND_TRUTH.csv")
        if (!statement.isFile || !truthFile.isFile) return
        val prepared = statement.reader().use { input ->
            truthFile.reader().use { truth -> SmartImportBenchmark.measure(input, truth).second }
        }
        val truth = truthByFingerprint(statement, truthFile)
        listOf(9_000, 9_500, 9_600, 9_700, 9_900).forEach { basisPoints ->
            val optimized = ReviewQueueOptimizer(ReviewNecessityModel(ReviewNecessityPolicy(basisPoints, 3)))
                .optimize(prepared, initializeProgress = true)
            val result = metrics("P${basisPoints / 100.0}", optimized, truth, 630)
            println(result.report())
        }
    }

    @Test fun oneHundredMixedIncomingTransactionsRequireZeroMandatoryReview() {
        val rows = (1..100).map { index -> analyzed("in-$index", TransactionDirection.CREDIT, index.toLong() * 100) }
        val plan = ReviewNecessityModel().evaluate(StatementIntelligenceResult(rows, emptyMap(), emptyMap(), emptyList()))
        assertEquals(0, plan.askFingerprints.size)
        assertEquals(100, plan.deferredFingerprints.size)
    }

    @Test fun adversarialTinyTailNeverHidesTheLargeExpense() {
        val rows = (1..99).map { analyzed("tiny-$it", TransactionDirection.DEBIT, 500) } +
            analyzed("large", TransactionDirection.DEBIT, 1_000_000)
        val plan = ReviewNecessityModel().evaluate(StatementIntelligenceResult(rows, emptyMap(), emptyMap(), emptyList()))
        assertTrue("large" in plan.askFingerprints)
    }

    @Test fun twentyEqualSmallRowsAreNotBlindlyDeferred() {
        val rows = (1..20).map { analyzed("row-$it", TransactionDirection.DEBIT, 10_000) }
        val plan = ReviewNecessityModel().evaluate(StatementIntelligenceResult(rows, emptyMap(), emptyMap(), emptyList()))
        assertEquals(20, plan.askFingerprints.size)
    }

    @Test fun oneSmallOutgoingStillRequiresReview() {
        val plan = ReviewNecessityModel().evaluate(StatementIntelligenceResult(
            listOf(analyzed("small", TransactionDirection.DEBIT, 20_000)), emptyMap(), emptyMap(), emptyList()
        ))
        assertEquals(setOf("small"), plan.askFingerprints)
    }

    @Test fun syntheticHoldoutRetainsTheDocumentedValueCoverageInvariant() {
        val rows = (1..200).map { index -> analyzed("h-$index", TransactionDirection.DEBIT, (201 - index).toLong() * 100) }
        val plan = ReviewNecessityModel().evaluate(StatementIntelligenceResult(rows, emptyMap(), emptyMap(), emptyList()))
        val totalValue = rows.sumOf { it.transaction.amountMinor }
        val askedValue = rows.filter { it.transaction.fingerprint in plan.askFingerprints }.sumOf { it.transaction.amountMinor }
        assertTrue(askedValue * 100.0 / totalValue >= 96.0)
        assertTrue("h-1" in plan.askFingerprints)
    }

    @Test fun unseenIdentityHoldoutPreservesDailySpendingAndDefersIncomingAmbiguity() {
        val holdout = buildList {
            // Structurally resolved V4 purchases represent the ordinary daily tail.
            (1..50).forEach { index ->
                add(HoldoutRow(analyzed(
                    "daily-$index", TransactionDirection.DEBIT, (500 + index).toLong(),
                    requiresReview = false, proposed = NormalizedTransactionType.EXPENSE,
                    identity = "LAGOON KIOSK $index", rail = NormalizedRail.MERCHANT_PURCHASE
                ), ImportDecision.EXPENSE))
            }
            add(HoldoutRow(analyzed("large-purchase", TransactionDirection.DEBIT, 1_000_000, identity = "NORTH HARBOR"), ImportDecision.EXPENSE))
            add(HoldoutRow(analyzed("person-purchase", TransactionDirection.DEBIT, 200_000, identity = "REENA LATHEEF"), ImportDecision.EXPENSE))
            add(HoldoutRow(analyzed("person-repayment", TransactionDirection.DEBIT, 800_000, identity = "REENA LATHEEF"), ImportDecision.MONEY_MOVEMENT))
            add(HoldoutRow(analyzed("own-account", TransactionDirection.DEBIT, 500_000, identity = "MY SAVINGS"), ImportDecision.MONEY_MOVEMENT))
            add(HoldoutRow(analyzed("generic-business", TransactionDirection.DEBIT, 300_000, identity = "ATOLL WORKS"), ImportDecision.EXPENSE))
            add(HoldoutRow(analyzed("tiny-purchase", TransactionDirection.DEBIT, 100, identity = "NIGHT SNACK"), ImportDecision.EXPENSE))
            add(HoldoutRow(analyzed("foreign", TransactionDirection.DEBIT, 5_000, identity = "ISLAND HOST", currency = "USD"), ImportDecision.EXPENSE))
            add(HoldoutRow(analyzed("gift", TransactionDirection.CREDIT, 50_000, identity = "FARAH"), ImportDecision.INCOME))
            add(HoldoutRow(analyzed("reimbursement", TransactionDirection.CREDIT, 25_000, identity = "REENA LATHEEF"), ImportDecision.REFUND))
            add(HoldoutRow(analyzed("client", TransactionDirection.CREDIT, 90_000, identity = "OCEAN STUDIO"), ImportDecision.INCOME))
            add(HoldoutRow(analyzed("incoming-own", TransactionDirection.CREDIT, 70_000, identity = "MY SAVINGS"), ImportDecision.MONEY_MOVEMENT))
        }
        val plan = ReviewNecessityModel().evaluate(StatementIntelligenceResult(
            holdout.map { it.analyzed }, emptyMap(), emptyMap(), emptyList()
        ))
        val captured = holdout.filter {
            it.truth == ImportDecision.EXPENSE &&
                plan.decisions.getValue(it.analyzed.transaction.fingerprint).disposition != ReviewDisposition.DEFER
        }
        val expenses = holdout.filter { it.truth == ImportDecision.EXPENSE }
        val capturedValue = captured.sumOf { it.analyzed.transaction.amountMinor }
        val expenseValue = expenses.sumOf { it.analyzed.transaction.amountMinor }
        val countCapture = captured.size * 100.0 / expenses.size
        val valueCapture = capturedValue * 100.0 / expenseValue
        println("Holdout rows=${holdout.size} ASK=${plan.askFingerprints.size} DEFER=${plan.deferredFingerprints.size} expenseCapture=${"%.2f".format(countCapture)}% valueCapture=${"%.2f".format(valueCapture)}%")
        assertTrue(countCapture >= 95.0)
        assertTrue(valueCapture >= 98.0)
        assertTrue(holdout.filter { it.analyzed.transaction.direction == TransactionDirection.CREDIT }.all {
            plan.decisions.getValue(it.analyzed.transaction.fingerprint).disposition == ReviewDisposition.DEFER
        })
        assertTrue(plan.decisions.getValue("large-purchase").disposition == ReviewDisposition.ASK_NOW)
        assertTrue(plan.decisions.values.none {
            it.fingerprint in setOf("person-purchase", "person-repayment") &&
                it.reason == ReviewNecessityReason.ASK_SAFE_COHORT_HIGH_VALUE
        })
    }

    @Test fun fiveAndTenThousandRowEvaluationIsLinearEnoughForLocalUse() {
        listOf(5_000, 10_000).forEach { size ->
            val rows = (1..size).map { analyzed("perf-$it", TransactionDirection.DEBIT, (it % 10_000 + 1).toLong()) }
            var asks = 0
            val elapsed = measureTimeMillis {
                asks = ReviewNecessityModel().evaluate(
                    StatementIntelligenceResult(rows, emptyMap(), emptyMap(), emptyList())
                ).askFingerprints.size
            }
            println("ReviewNecessity performance rows=$size asks=$asks elapsedMs=$elapsed")
            assertTrue(asks > 0)
            assertTrue("Unexpectedly slow deterministic local evaluation: ${elapsed}ms", elapsed < 10_000)
        }
    }

    private fun simulateAdaptive(
        prepared: PreparedImport,
        truth: Map<String, TruthRow>
    ): AdaptiveBenchmarkMetrics {
        var current = prepared
        val adaptive = AdaptiveReviewEngine()
        val optimizer = ReviewQueueOptimizer()
        val autoResolved = mutableSetOf<String>()
        while (true) {
            val group = current.reviewGroups.firstOrNull { !it.resolved } ?: break
            val expected = group.items.map { truth.getValue(it.transaction.fingerprint) }
            require(expected.map { it.meaning }.distinct().size == 1) {
                "Unsafe question group mixes meanings: ${group.identity}"
            }
            val category = expected.mapNotNull { it.category }.distinct().singleOrNull()
            val unresolvedBefore = current.items.filterNot { it.resolved }.map { it.transaction.fingerprint }.toSet()
            val directlyAnswered = group.items.map { it.transaction.fingerprint }.toSet()
            val resolved = adaptive.resolve(current, group.id, expected.first().meaning, category, remember = false)
            val next = optimizer.optimize(resolved)
            next.items.filter {
                it.resolved && it.transaction.fingerprint in unresolvedBefore &&
                    it.transaction.fingerprint !in directlyAnswered &&
                    it.transaction.fingerprint !in next.reviewNecessityPlan.deferredFingerprints
            }.forEach { item ->
                require(item.decision == truth.getValue(item.transaction.fingerprint).meaning) {
                    "Unsafe adaptive propagation for ${item.transaction.fingerprint}"
                }
                autoResolved += item.transaction.fingerprint
            }
            current = next
        }
        return AdaptiveBenchmarkMetrics(
            initialReviewRows = prepared.reviewProgress.initialReviewRows,
            initialHumanDecisions = prepared.reviewProgress.initialDecisionCount,
            finalHumanDecisions = current.reviewProgress.completedHumanDecisions,
            adaptiveResolutionEvents = current.reviewProgress.autoResolvedDuringReview,
            uniqueRowsAutoResolvedDuringReview = autoResolved.size,
            netHumanDecisionsSaved = prepared.reviewProgress.initialDecisionCount - current.reviewProgress.completedHumanDecisions
        )
    }

    private fun metrics(
        name: String,
        prepared: PreparedImport,
        truth: Map<String, TruthRow>,
        v4ReviewRows: Int
    ): NecessityMetrics {
        val eligible = prepared.items.filter { it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }
        val asks = prepared.reviewNecessityPlan.askFingerprints
        val deferred = prepared.reviewNecessityPlan.deferredFingerprints
        val auto = eligible.map { it.transaction.fingerprint }.toSet() - asks - deferred
        val expenseRows = truth.filterValues { it.meaning == ImportDecision.EXPENSE }
        val captured = expenseRows.filterKeys { it in auto || it in asks }
        val automaticExpenses = expenseRows.filterKeys { it in auto }
        val totalValue = expenseRows.values.sumOf { it.candidate.amountMinor }
        val capturedValue = captured.values.sumOf { it.candidate.amountMinor }
        val automaticValue = automaticExpenses.values.sumOf { it.candidate.amountMinor }
        return NecessityMetrics(
            name, eligible.size, v4ReviewRows, asks.size,
            prepared.reviewGroups.count { !it.resolved }, deferred.mapNotNull { fingerprint ->
                truth[fingerprint]?.let { DeferredTruth(it.candidate, prepared.reviewNecessityPlan.decisions.getValue(fingerprint).disposition, it.meaning) }
            },
            expenseRows.size, automaticExpenses.size, captured.size, totalValue, automaticValue, capturedValue,
            prepared.reviewNecessityPlan.decisions.values.groupingBy { it.reason }.eachCount()
        )
    }

    private fun truthByFingerprint(statement: File, truthFile: File): Map<String, TruthRow> {
        val candidates = statement.reader().use { BmlCsvParser().parse(it).candidates }
        val truth = truthFile.reader().use(GroundTruthCsv::read)
        require(candidates.size == truth.size) { "Statement/truth row count mismatch" }
        return buildMap {
            candidates.forEachIndexed { index, candidate -> putIfAbsent(candidate.fingerprint, TruthRow(candidate, truth[index].meaning, truth[index].category)) }
        }
    }

    private fun analyzed(
        id: String,
        direction: TransactionDirection,
        amount: Long,
        requiresReview: Boolean = true,
        proposed: NormalizedTransactionType = NormalizedTransactionType.UNKNOWN,
        identity: String = "Person $id",
        rail: NormalizedRail = NormalizedRail.P2P_TRANSFER,
        currency: String = "MVR"
    ): AnalyzedTransaction {
        val candidate = CandidateTransaction(
            "TEST", id.hashCode().toLong(), null, null, rail.name, identity, "", id, null,
            direction, amount, currency, null, proposed,
            if (requiresReview) ImportReviewStatus.NEEDS_REVIEW else ImportReviewStatus.READY, identity, id,
            normalizedIdentity = identity.uppercase(), bankRail = rail.name, normalizedRail = rail
        )
        return AnalyzedTransaction(candidate, TransactionIntelligence(
            id, proposed, if (requiresReview) IntelligenceConfidence.LOW else IntelligenceConfidence.HIGH, null,
            IntelligenceConfidence.UNKNOWN,
            EntityResolution(if (rail == NormalizedRail.MERCHANT_PURCHASE) EntityKind.MERCHANT else EntityKind.PERSON, IntelligenceConfidence.MEDIUM, emptyList()),
            emptyList(), IntelligenceConfidence.UNKNOWN, emptyList(), requiresReview,
            if (requiresReview) listOf(EvidenceType.LOW_MEANING_CONFIDENCE) else emptyList()
        ))
    }

    private data class Fixture(val name: String, val statement: String, val uniqueRows: Int, val duplicates: Int, val v4ReviewRows: Int)
    private data class HoldoutRow(val analyzed: AnalyzedTransaction, val truth: ImportDecision)
    private data class TruthRow(val candidate: CandidateTransaction, val meaning: ImportDecision, val category: String?)
    private data class DeferredTruth(val candidate: CandidateTransaction, val disposition: ReviewDisposition, val truth: ImportDecision)
    private data class NecessityMetrics(
        val name: String,
        val uniqueRows: Int,
        val v4ReviewRows: Int,
        val askRows: Int,
        val askDecisions: Int,
        val deferredRows: List<DeferredTruth>,
        val trueExpenseCount: Int,
        val automaticExpenseCount: Int,
        val capturedExpenseCount: Int,
        val trueExpenseValue: Long,
        val automaticExpenseValue: Long,
        val capturedExpenseValue: Long,
        val reasons: Map<ReviewNecessityReason, Int>
    ) {
        val expenseCountCapturePercent = if (trueExpenseCount == 0) 100.0 else capturedExpenseCount * 100.0 / trueExpenseCount
        val expenseValueCapturePercent = if (trueExpenseValue == 0L) 100.0 else capturedExpenseValue * 100.0 / trueExpenseValue
        fun report() = buildString {
            appendLine("$name Review Necessity: unique=$uniqueRows V4review=$v4ReviewRows ASKrows=$askRows ASKdecisions=$askDecisions DEFER=${deferredRows.size}")
            appendLine("zero-review capture count=$automaticExpenseCount/$trueExpenseCount (${"%.2f".format(if (trueExpenseCount == 0) 100.0 else automaticExpenseCount * 100.0 / trueExpenseCount)}%) value=$automaticExpenseValue/$trueExpenseValue (${"%.2f".format(if (trueExpenseValue == 0L) 100.0 else automaticExpenseValue * 100.0 / trueExpenseValue)}%)")
            appendLine("capture count=$capturedExpenseCount/$trueExpenseCount (${"%.2f".format(expenseCountCapturePercent)}%) value=$capturedExpenseValue/$trueExpenseValue (${"%.2f".format(expenseValueCapturePercent)}%)")
            appendLine("deferred incoming=${deferredRows.count { it.candidate.direction == TransactionDirection.CREDIT }} outgoing=${deferredRows.count { it.candidate.direction == TransactionDirection.DEBIT }}")
            val missed = deferredRows.filter { it.truth == ImportDecision.EXPENSE }
            appendLine("deferred expense count=${missed.size} value=${missed.sumOf { it.candidate.amountMinor }} max=${missed.maxOfOrNull { it.candidate.amountMinor } ?: 0}")
            appendLine("deferred truth=${deferredRows.groupingBy { it.truth }.eachCount()} reasons=$reasons")
        }
    }
}
