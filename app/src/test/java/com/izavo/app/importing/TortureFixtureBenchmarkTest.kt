package com.izavo.app.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TortureFixtureBenchmarkTest {
    @Test fun exactAvailableTortureFixturesRetainDuplicateAndCoverageBaselines() {
        val root = locateTortureFixtureDirectory()
        val fixtures = listOf(
            Fixture("Level 1", "IZAVO_BML_TORTURE_V2_LEVEL1_100.csv", 100, 100, 0, 51),
            Fixture("Level 2", "IZAVO_BML_TORTURE_V2_LEVEL2_500.csv", 500, 498, 2, 260),
            Fixture("Level 3", "IZAVO_BML_TORTURE_V2_LEVEL3_1200.csv", 1200, 1198, 2, 630)
        )
        val available = fixtures.filter { File(root, it.statement).isFile }
        if (available.isEmpty()) return
        available.forEach { fixture ->
            val statement = File(root, fixture.statement)
            val groundTruth = File(root, statement.nameWithoutExtension + "_GROUND_TRUTH.csv").takeIf(File::isFile)
            val (metrics, prepared) = statement.reader().use { statementReader ->
                groundTruth?.reader()?.use { truthReader -> SmartImportBenchmark.measure(statementReader, truthReader) }
                    ?: SmartImportBenchmark.measure(statementReader)
            }
            println(metrics.report(fixture.name))
            assertEquals(fixture.sourceRows, metrics.sourceRows)
            assertEquals(fixture.uniqueRows, metrics.uniqueRows)
            assertEquals(fixture.duplicates, metrics.duplicateRows)
            assertEquals(metrics.uniqueRows, metrics.automaticRows + metrics.reviewRows)
            if (groundTruth != null) {
                assertEquals(metrics.uniqueRows, metrics.groundTruthJoinedRows)
                assertEquals(0, metrics.catastrophicErrors)
                assertEquals(0, metrics.falseExpense)
                assertEquals(0, metrics.falseIncome)
                assertEquals(0, metrics.automaticRowsMarkedForReview)
                assertEquals(0, metrics.reviewedRowsMarkedAutomatic)
                assertEquals(fixture.groundTruthReviewRows, metrics.reviewRows)
                val truth = groundTruth.reader().use(GroundTruthCsv::read)
                val adaptive = SmartImportBenchmark.simulateAdaptive(prepared, truth)
                println("${fixture.name} adaptive: $adaptive")
                assertTrue(adaptive.finalHumanDecisions <= adaptive.initialHumanDecisions)
            }
        }
    }

    private data class Fixture(
        val name: String,
        val statement: String,
        val sourceRows: Int,
        val uniqueRows: Int,
        val duplicates: Int,
        val groundTruthReviewRows: Int
    )
}
