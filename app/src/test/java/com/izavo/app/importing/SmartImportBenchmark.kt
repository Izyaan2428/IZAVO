package com.izavo.app.importing

import java.io.File
import java.io.Reader

data class GroundTruthTransaction(
    val rowIndex: Int,
    val primaryReference: String?,
    val meaning: ImportDecision,
    val category: String?,
    val shouldRequireReview: Boolean
)

data class SmartImportBenchmarkMetrics(
    val sourceRows: Int,
    val uniqueRows: Int,
    val duplicateRows: Int,
    val automaticRows: Int,
    val reviewRows: Int,
    val initialHumanDecisions: Int,
    val groundTruthJoinedRows: Int,
    val automaticRowsMarkedForReview: Int?,
    val reviewedRowsMarkedAutomatic: Int?,
    val correctAutomatic: Int?,
    val incorrectAutomatic: Int?,
    val catastrophicErrors: Int?,
    val falseExpense: Int?,
    val falseIncome: Int?,
    val falseMovement: Int?,
    val falseRefund: Int?,
    val categoryCorrect: Int?,
    val categoryCompared: Int?,
    val reviewByTruth: Map<ImportDecision, Int>,
    val automaticByEvidence: Map<EvidenceType, Int>,
    val reviewByReason: Map<EvidenceType, Int>,
    val confusion: Map<Pair<ImportDecision, ImportDecision>, Int>
) {
    val coveragePercent: Double get() = if (uniqueRows == 0) 0.0 else automaticRows * 100.0 / uniqueRows
    val abstentionPercent: Double get() = if (uniqueRows == 0) 0.0 else reviewRows * 100.0 / uniqueRows
    val selectivePrecisionPercent: Double? get() = correctAutomatic?.let { correct ->
        if (automaticRows == 0) 100.0 else correct * 100.0 / automaticRows
    }
}

data class AdaptiveBenchmarkMetrics(
    val initialReviewRows: Int,
    val initialHumanDecisions: Int,
    val finalHumanDecisions: Int,
    val adaptiveResolutionEvents: Int,
    val uniqueRowsAutoResolvedDuringReview: Int,
    val netHumanDecisionsSaved: Int
)

object SmartImportBenchmark {
    fun measure(statementReader: Reader, groundTruthReader: Reader? = null): Pair<SmartImportBenchmarkMetrics, PreparedImport> {
        val parsed = BmlCsvParser().parse(statementReader)
        val candidates = markDuplicates(parsed.candidates, emptySet())
        val intelligence = IntelligenceDecisionEngine().analyzeStatement(candidates, emptyList())
        val eligible = intelligence.transactions.filter { it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }
        val items = intelligence.transactions.map { analyzed ->
            ImportReviewItem(
                analyzed.transaction,
                ReviewGroupBuilder.decisionFor(analyzed.transaction.normalizedType),
                analyzed.transaction.suggestedCategory,
                resolved = !analyzed.intelligence.requiresReview
            )
        }
        val groups = ReviewGroupBuilder.build(items)
        val prepared = PreparedImport(
            "benchmark.csv", items, parsed.invalidRowCount,
            candidates.count { it.reviewStatus == ImportReviewStatus.DUPLICATE }, groups, intelligence,
            reviewProgress = AdaptiveReviewProgress(
                items.count { !it.resolved && it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE },
                groups.count { !it.resolved }
            )
        )
        val truth = groundTruthReader?.let(GroundTruthCsv::read).orEmpty()
        val truthByFingerprint = joinTruth(parsed.candidates, truth)
        if (truth.isNotEmpty()) {
            require(eligible.all { it.transaction.fingerprint in truthByFingerprint }) {
                "Ground-truth join did not cover every unique eligible transaction"
            }
        }
        val automatic = eligible.filterNot { it.intelligence.requiresReview }
        val reviewed = eligible.filter { it.intelligence.requiresReview }
        val compared = automatic.mapNotNull { analyzed ->
            truthByFingerprint[analyzed.transaction.fingerprint]?.let { it to analyzed }
        }
        val wrong = compared.filter { (expected, actual) -> expected.meaning != actual.intelligence.proposedMeaning.toDecision() }
        val autoExpenseCompared = compared.filter { (_, actual) -> actual.intelligence.proposedMeaning == NormalizedTransactionType.EXPENSE }
        val categoryCompared = autoExpenseCompared.filter { it.first.category != null }
        val metrics = SmartImportBenchmarkMetrics(
            sourceRows = parsed.candidates.size + parsed.invalidRowCount,
            uniqueRows = eligible.size,
            duplicateRows = candidates.count { it.reviewStatus == ImportReviewStatus.DUPLICATE },
            automaticRows = automatic.size,
            reviewRows = reviewed.size,
            initialHumanDecisions = groups.count { !it.resolved },
            groundTruthJoinedRows = truthByFingerprint.size,
            automaticRowsMarkedForReview = truth.takeIf { it.isNotEmpty() }?.let {
                automatic.count { truthByFingerprint.getValue(it.transaction.fingerprint).shouldRequireReview }
            },
            reviewedRowsMarkedAutomatic = truth.takeIf { it.isNotEmpty() }?.let {
                reviewed.count { !truthByFingerprint.getValue(it.transaction.fingerprint).shouldRequireReview }
            },
            correctAutomatic = truth.takeIf { it.isNotEmpty() }?.let { compared.size - wrong.size },
            incorrectAutomatic = truth.takeIf { it.isNotEmpty() }?.let { wrong.size },
            catastrophicErrors = truth.takeIf { it.isNotEmpty() }?.let { wrong.size },
            falseExpense = falseCount(wrong, ImportDecision.EXPENSE, truth),
            falseIncome = falseCount(wrong, ImportDecision.INCOME, truth),
            falseMovement = falseCount(wrong, ImportDecision.MONEY_MOVEMENT, truth),
            falseRefund = falseCount(wrong, ImportDecision.REFUND, truth),
            categoryCorrect = truth.takeIf { it.isNotEmpty() }?.let {
                categoryCompared.count { (expected, actual) -> expected.category == actual.intelligence.proposedCategory }
            },
            categoryCompared = truth.takeIf { it.isNotEmpty() }?.let { categoryCompared.size },
            reviewByTruth = reviewed.mapNotNull { analyzed -> truthByFingerprint[analyzed.transaction.fingerprint]?.meaning }
                .groupingBy { it }.eachCount(),
            automaticByEvidence = automatic.mapNotNull { dominantEvidence(it.intelligence) }.groupingBy { it }.eachCount(),
            reviewByReason = reviewed.flatMap { it.intelligence.automationVetoes.ifEmpty { listOf(EvidenceType.LOW_MEANING_CONFIDENCE) } }
                .groupingBy { it }.eachCount(),
            confusion = compared.groupingBy { (expected, actual) -> expected.meaning to actual.intelligence.proposedMeaning.toDecision() }.eachCount()
        )
        return metrics to prepared
    }

    fun simulateAdaptive(prepared: PreparedImport, truth: List<GroundTruthTransaction>): AdaptiveBenchmarkMetrics {
        val parsedOrder = prepared.items.filter { it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE }
        val truthByFingerprint = joinTruth(parsedOrder.map { it.transaction }, truth)
        var current = prepared
        val engine = AdaptiveReviewEngine()
        val adaptivelyResolvedFingerprints = mutableSetOf<String>()
        while (true) {
            val group = current.reviewGroups.firstOrNull { !it.resolved } ?: break
            val expected = group.items.mapNotNull { truthByFingerprint[it.transaction.fingerprint] }
            require(expected.size == group.items.size) { "Ground truth is missing for review group ${group.id}" }
            require(expected.map { it.meaning }.distinct().size == 1) { "Unsafe review group mixes top-level meanings: ${group.identity}" }
            val decision = expected.first().meaning
            val category = expected.mapNotNull { it.category }.distinct().singleOrNull()
            val unresolvedBefore = current.items.filter { !it.resolved }.map { it.transaction.fingerprint }.toSet()
            val next = engine.resolve(current, group.id, decision, category, remember = false)
            val directlyReviewed = group.items.map { it.transaction.fingerprint }.toSet()
            val adaptivelyResolved = next.items.filter {
                it.resolved && it.transaction.fingerprint in unresolvedBefore && it.transaction.fingerprint !in directlyReviewed
            }
            adaptivelyResolved.forEach { item ->
                val expectedMeaning = truthByFingerprint.getValue(item.transaction.fingerprint).meaning
                require(item.decision == expectedMeaning) {
                    "Unsafe adaptive propagation for ${item.transaction.fingerprint}: expected $expectedMeaning, got ${item.decision}"
                }
                adaptivelyResolvedFingerprints += item.transaction.fingerprint
            }
            current = next
        }
        return AdaptiveBenchmarkMetrics(
            initialReviewRows = prepared.reviewProgress.initialReviewRows,
            initialHumanDecisions = prepared.reviewProgress.initialDecisionCount,
            finalHumanDecisions = current.reviewProgress.completedHumanDecisions,
            adaptiveResolutionEvents = current.reviewProgress.autoResolvedDuringReview,
            uniqueRowsAutoResolvedDuringReview = adaptivelyResolvedFingerprints.size,
            netHumanDecisionsSaved = prepared.reviewProgress.initialDecisionCount -
                current.reviewProgress.completedHumanDecisions
        )
    }

    private fun joinTruth(candidates: List<CandidateTransaction>, truth: List<GroundTruthTransaction>): Map<String, GroundTruthTransaction> {
        if (truth.isEmpty()) return emptyMap()
        val byReference = truth.mapNotNull { row -> row.primaryReference?.let { it to row } }.toMap()
        val joined = linkedMapOf<String, GroundTruthTransaction>()
        candidates.forEachIndexed { index, candidate ->
            val expected = candidate.primaryReference?.let(byReference::get)
                ?: truth.firstOrNull { it.rowIndex == index }
            // Exact duplicate source rows share a fingerprint. Keep the first
            // occurrence's truth because that is the eligible transaction;
            // later occurrences are removed by duplicate handling.
            expected?.let { joined.putIfAbsent(candidate.fingerprint, it) }
        }
        return joined
    }

    private fun falseCount(
        wrong: List<Pair<GroundTruthTransaction, AnalyzedTransaction>>,
        predicted: ImportDecision,
        truth: List<GroundTruthTransaction>
    ): Int? = truth.takeIf { it.isNotEmpty() }?.let {
        wrong.count { (_, actual) -> actual.intelligence.proposedMeaning.toDecision() == predicted }
    }

    private fun dominantEvidence(intelligence: TransactionIntelligence): EvidenceType? = intelligence.evidence
        .sortedWith(compareBy<TransactionEvidence> { it.strength.ordinal }.thenBy { it.type.ordinal })
        .firstOrNull()?.type

    private fun NormalizedTransactionType.toDecision() = when (this) {
        NormalizedTransactionType.EXPENSE -> ImportDecision.EXPENSE
        NormalizedTransactionType.INCOME -> ImportDecision.INCOME
        NormalizedTransactionType.MONEY_MOVEMENT,
        NormalizedTransactionType.INCOMING_TRANSFER,
        NormalizedTransactionType.OUTGOING_TRANSFER,
        NormalizedTransactionType.CASH_MOVEMENT,
        NormalizedTransactionType.DEBT_PAYMENT -> ImportDecision.MONEY_MOVEMENT
        NormalizedTransactionType.REFUND, NormalizedTransactionType.REVERSED -> ImportDecision.REFUND
        NormalizedTransactionType.UNKNOWN -> ImportDecision.UNKNOWN
        NormalizedTransactionType.IGNORE -> ImportDecision.IGNORE
    }

}

object GroundTruthCsv {
    fun read(reader: Reader): List<GroundTruthTransaction> {
        val rows = CsvReader.read(reader)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map(::key)
        val referenceIndex = header.indexOfFirst { it in setOf("primaryreference", "reference", "transactionreference", "transactionid", "id") }
        val meaningIndex = header.indexOfFirst { it in setOf("expectedmeaning", "groundtruth", "classification", "expectedclassification", "meaning", "expectedtype", "financialmeaning") }
        val categoryIndex = header.indexOfFirst { it in setOf("expectedcategory", "category") }
        val reviewIndex = header.indexOfFirst { it in setOf("shouldrequirereview", "requiresreview", "reviewrequired") }
        require(meaningIndex >= 0) { "Ground-truth CSV has no recognized expected-meaning column" }
        return rows.drop(1).mapIndexedNotNull { index, row ->
            val meaning = row.getOrNull(meaningIndex)?.let(::parseMeaning) ?: return@mapIndexedNotNull null
            GroundTruthTransaction(
                rowIndex = index,
                primaryReference = referenceIndex.takeIf { it >= 0 }
                    ?.let(row::getOrNull)
                    ?.let(BmlCsvParser::cleanCell)
                    ?.ifBlank { null },
                meaning = meaning,
                category = categoryIndex.takeIf { it >= 0 }
                    ?.let(row::getOrNull)
                    ?.let(BmlCsvParser::cleanCell)
                    ?.ifBlank { null },
                shouldRequireReview = row.getOrNull(reviewIndex)?.trim()?.equals("true", ignoreCase = true) == true
            )
        }
    }

    private fun key(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun parseMeaning(value: String): ImportDecision? = when (key(value)) {
        "expense" -> ImportDecision.EXPENSE
        "income" -> ImportDecision.INCOME
        "moneymovement", "transfer", "cashmovement", "debtpayment" -> ImportDecision.MONEY_MOVEMENT
        "refund", "reversal", "refundreversal" -> ImportDecision.REFUND
        "unknown", "notsure", "ignoreunknown" -> ImportDecision.UNKNOWN
        "ignore", "ignored" -> ImportDecision.IGNORE
        else -> null
    }
}

fun SmartImportBenchmarkMetrics.report(name: String): String = buildString {
    appendLine("$name: source=$sourceRows unique=$uniqueRows duplicates=$duplicateRows")
    appendLine("auto=$automaticRows review=$reviewRows decisions=$initialHumanDecisions coverage=${"%.2f".format(coveragePercent)}% abstention=${"%.2f".format(abstentionPercent)}%")
    if (selectivePrecisionPercent != null) appendLine("precision=${"%.2f".format(selectivePrecisionPercent)}% catastrophic=$catastrophicErrors falseExpense=$falseExpense falseIncome=$falseIncome falseMovement=$falseMovement falseRefund=$falseRefund")
    if (correctAutomatic != null) appendLine("truthJoined=$groundTruthJoinedRows autoWhenReviewRequired=$automaticRowsMarkedForReview reviewWhenAutomaticExpected=$reviewedRowsMarkedAutomatic category=$categoryCorrect/$categoryCompared")
    if (reviewByTruth.isNotEmpty()) appendLine("reviewByTruth=$reviewByTruth")
    if (confusion.isNotEmpty()) appendLine("confusion=$confusion")
    appendLine("automaticEvidence=$automaticByEvidence")
    appendLine("reviewReasons=$reviewByReason")
}

fun locateTortureFixtureDirectory(): File {
    System.getProperty("izavo.benchmark.dir")?.let(::File)?.takeIf(File::isDirectory)?.let { return it }
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
    listOf(
        File(workingDirectory, "IZAVO_BML_TORTURE_V2"),
        File(workingDirectory.parentFile, "IZAVO_BML_TORTURE_V2"),
        File(System.getProperty("user.home"), "Downloads")
    ).firstOrNull(File::isDirectory)?.let { return it }
    return workingDirectory
}
