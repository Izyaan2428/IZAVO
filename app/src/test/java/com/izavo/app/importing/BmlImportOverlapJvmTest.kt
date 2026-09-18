package com.izavo.app.importing

import com.izavo.app.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.ZoneOffset

/** Fast repository-level companion to the real Room instrumentation overlap test. */
class BmlImportOverlapJvmTest {
    private val parser = BmlCsvParser(ZoneOffset.UTC)

    @Test fun level3ThenCompleteOverlapAddsNothing() = runBlocking {
        val dao = AtomicMemoryImportDao()
        val repository = BankImportRepository(dao)
        val uniqueRows = (1..1198).map(::row)
        val full = parser.parse(StringReader((uniqueRows + uniqueRows[98] + uniqueRows[698]).joinToString("\n")))
        val firstPrepared = resolve(repository.prepare("level3.csv", full))
        assertEquals(2, firstPrepared.duplicateCount)
        val first = repository.import(firstPrepared)
        assertEquals(1198, first.transactionCount)
        assertEquals(1198, dao.ledgerCount)
        assertEquals(first.expenseCount, dao.expenseCount)
        val expenseCountBeforeOverlap = dao.expenseCount

        val overlap = parser.parse(StringReader(uniqueRows.subList(400, 900).joinToString("\n")))
        val linksBefore = dao.links.toMap()
        val overlapPrepared = repository.prepare("overlap.csv", overlap)
        assertEquals(500, overlapPrepared.duplicateCount)
        val second = repository.import(overlapPrepared)

        assertEquals(0, second.transactionCount)
        assertEquals(0, second.expenseCount)
        assertEquals(500, second.duplicateCount)
        assertEquals(1198, dao.ledgerCount)
        assertEquals(expenseCountBeforeOverlap, dao.expenseCount)
        assertEquals(1198, dao.fingerprints.size)
        assertEquals(linksBefore, dao.links)
        assertTrue(dao.links.values.all { it in dao.expenseIds })
        assertEquals(dao.links.size, dao.links.values.toSet().size)
        assertEquals(2, dao.batchCount)
    }

    @Test fun exactReimportAddsNothing() = runBlocking {
        val dao = AtomicMemoryImportDao()
        val repository = BankImportRepository(dao)
        val csv = (1..60).joinToString("\n", transform = ::row)
        val first = repository.import(resolve(repository.prepare("first.csv", parser.parse(StringReader(csv)))))
        assertEquals(60, dao.ledgerCount)
        assertEquals(first.expenseCount, dao.expenseCount)
        val expenseCountBeforeReimport = dao.expenseCount

        val repeated = repository.prepare("again.csv", parser.parse(StringReader(csv)))
        val result = repository.import(repeated)
        assertEquals(60, result.duplicateCount)
        assertEquals(0, result.transactionCount)
        assertEquals(0, result.expenseCount)
        assertEquals(60, dao.ledgerCount)
        assertEquals(expenseCountBeforeReimport, dao.expenseCount)
        assertEquals(2, dao.batchCount)
    }

    @Test fun partialOverlapAddsOnlyEAndF() = runBlocking {
        val dao = AtomicMemoryImportDao()
        val repository = BankImportRepository(dao)
        repository.import(resolve(repository.prepare("abcd.csv", parser.parse(StringReader((1..4).joinToString("\n", transform = ::row))))))
        assertEquals(4, dao.ledgerCount)
        assertEquals(2, dao.expenseCount)

        val prepared = resolve(repository.prepare("cdef.csv", parser.parse(StringReader((3..6).joinToString("\n", transform = ::row)))))
        val result = repository.import(prepared)
        assertEquals(2, result.duplicateCount)
        assertEquals(2, result.transactionCount)
        assertEquals(2, result.expenseCount)
        assertEquals(6, dao.ledgerCount)
        assertEquals(4, dao.expenseCount)
        assertEquals(6, dao.fingerprints.size)
        assertEquals(2, dao.batchCount)
    }

    @Test fun conflictLeavesNoPartialBatchExpenseOrRule() = runBlocking {
        val dao = AtomicMemoryImportDao()
        val repository = BankImportRepository(dao)
        val prepared = resolve(repository.prepare("conflict.csv", parser.parse(StringReader(row(5000)))))
        dao.seedFingerprint(prepared.items.single().transaction.fingerprint)
        val before = dao.snapshot()

        var failed = false
        try { repository.import(prepared) } catch (_: IllegalStateException) { failed = true }

        assertTrue(failed)
        assertEquals(before, dao.snapshot())
    }

    @Test fun skipForNowPreservesEveryUniqueRowAsUnknownLedgerOnly() = runBlocking {
        val dao = AtomicMemoryImportDao()
        val repository = BankImportRepository(dao)
        val parsed = parser.parse(StringReader((1..20).joinToString("\n") { index ->
            ambiguousTransfer(index)
        }))
        val prepared = repository.prepare("skip.csv", parsed)
        assertTrue(prepared.reviewGroups.any { !it.resolved })

        val result = repository.import(ReviewQueueOptimizer().deferRemaining(prepared))

        assertEquals(20, result.transactionCount)
        assertEquals(0, result.expenseCount)
        assertEquals(20, dao.ledgerCount)
        assertEquals(setOf(NormalizedTransactionType.UNKNOWN.name), dao.normalizedTypes)
        assertEquals(0, dao.ruleCount)
    }

    @Test fun skipMidReviewPreservesTheAnswerAndDefersOnlyTheRemainder() = runBlocking {
        val dao = AtomicMemoryImportDao()
        val repository = BankImportRepository(dao)
        val parsed = parser.parse(StringReader((1..20).joinToString("\n") { index ->
            ambiguousTransfer(index)
        }))
        val prepared = repository.prepare("skip-mid.csv", parsed)
        val firstGroup = prepared.reviewGroups.first { !it.resolved }
        val answered = AdaptiveReviewEngine().resolve(
            prepared, firstGroup.id, ImportDecision.EXPENSE, "Other", remember = false
        )
        val optimized = ReviewQueueOptimizer().optimize(answered)
        val final = ReviewQueueOptimizer().deferRemaining(optimized)

        val result = repository.import(final)

        assertEquals(20, result.transactionCount)
        assertEquals(firstGroup.transactionCount, result.expenseCount)
        assertEquals(20, dao.ledgerCount)
        assertEquals(firstGroup.transactionCount, dao.expenseCount)
        assertEquals(0, dao.ruleCount)
        assertTrue(NormalizedTransactionType.EXPENSE.name in dao.normalizedTypes)
        assertTrue(NormalizedTransactionType.UNKNOWN.name in dao.normalizedTypes)
    }

    private fun resolve(prepared: PreparedImport): PreparedImport {
        val groups = prepared.reviewGroups.mapIndexed { index, group ->
            if (group.resolved) group else {
                val decision = when (index % 3) {
                    0 -> ImportDecision.EXPENSE
                    1 -> ImportDecision.MONEY_MOVEMENT
                    else -> ImportDecision.UNKNOWN
                }
                ReviewGroupBuilder.applyDecision(group, decision, "Other".takeIf { decision == ImportDecision.EXPENSE }, false)
            }
        }
        val changed = groups.flatMap { it.items }.associateBy { it.transaction.fingerprint }
        return prepared.copy(reviewGroups = groups, items = prepared.items.map { changed[it.transaction.fingerprint] ?: it })
    }

    private fun row(index: Int): String {
        val day = (index % 27) + 1
        val month = ((index / 27) % 12) + 1
        val date = "2026/%02d/%02d".format(month, day)
        val time = "%02d-%02d-2026 %02d-%02d-%02d".format(day, month, index % 24, index % 60, index % 60)
        val values = when (index % 3) {
            0 -> listOf("Purchase", "AVAS RIDE", amount(index), "", time)
            1 -> listOf("Salary", "EMPLOYER $index", "", amount(index), time)
            else -> listOf("Favara Debit", "PERSON $index", amount(index), "", "$time Favara")
        }
        return listOf(date, date, values[0], "=\"RB%08d\"".format(index), "=\"FT%08d\"".format(index),
            values[1], "=\"${values[1]}\"", values[4], values[2], values[3], "100000.00")
            .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
    }

    private fun amount(index: Int) = "%d.%02d".format((index % 400) + 1, index % 100)

    private fun ambiguousTransfer(index: Int): String {
        val day = (index % 27) + 1
        val date = "2026/08/%02d".format(day)
        val time = "%02d-08-2026 12-00-%02d".format(day, index % 60)
        return listOf(
            date, date, "Favara Debit", "=\"SKIP%08d\"".format(index),
            "=\"SKIPFT%08d\"".format(index), "PERSON $index", "=\"PERSON $index\"", time,
            "100.00", "", "10000.00"
        ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
    }
}

private class AtomicMemoryImportDao : ImportDao {
    private val ledger = linkedMapOf<String, ImportedBankTransactionEntity>()
    private val expenses = linkedMapOf<Long, ExpenseEntity>()
    private val rules = mutableListOf<MerchantRuleEntity>()
    private var nextBatchId = 1L
    private var nextExpenseId = 1L
    var batchCount = 0; private set

    val ledgerCount get() = ledger.size
    val expenseCount get() = expenses.size
    val fingerprints get() = ledger.keys.toSet()
    val expenseIds get() = expenses.keys.toSet()
    val links get() = ledger.mapValues { it.value.linkedExpenseId }.filterValues { it != null }.mapValues { it.value!! }
    val normalizedTypes get() = ledger.values.map { it.normalizedType }.toSet()
    val ruleCount get() = rules.size

    override fun observeExpenseMerchantMetadata(): Flow<List<ExpenseMerchantMetadata>> = flowOf(emptyList())
    override suspend fun findExistingFingerprints(fingerprints: List<String>) = fingerprints.filter(ledger::containsKey)
    override suspend fun findMerchantRules(sourceBank: String, identities: List<String>) =
        rules.filter { it.sourceBank == sourceBank && it.normalizedIdentity in identities }
    override suspend fun upsertMerchantRules(rules: List<MerchantRuleEntity>) { this.rules.addAll(rules) }
    override suspend fun insertBatch(batch: ImportBatchEntity): Long { batchCount++; return nextBatchId++ }
    override suspend fun insertImportedTransactions(transactions: List<ImportedBankTransactionEntity>): List<Long> =
        transactions.map { transaction ->
            if (ledger.containsKey(transaction.fingerprint)) -1L else {
                ledger[transaction.fingerprint] = transaction
                ledger.size.toLong()
            }
        }
    override suspend fun insertExpenses(expenses: List<ExpenseEntity>): List<Long> = expenses.map { expense ->
        val id = nextExpenseId++; this.expenses[id] = expense.copy(id = id); id
    }

    override suspend fun persistBatch(
        batch: ImportBatchEntity,
        rows: List<ImportPersistenceRow>,
        rules: List<MerchantRuleEntity>
    ): ImportPersistenceResult {
        check(rows.none { it.transaction.fingerprint in ledger }) { "An imported transaction already exists" }
        val batchId = nextBatchId
        val stagedExpenses = linkedMapOf<Long, ExpenseEntity>()
        val stagedLedger = linkedMapOf<String, ImportedBankTransactionEntity>()
        rows.forEach { row ->
            val expenseId = row.expense?.let {
                val id = nextExpenseId + stagedExpenses.size
                stagedExpenses[id] = it.copy(id = id)
                id
            }
            stagedLedger[row.transaction.fingerprint] = row.transaction.copy(importBatchId = batchId, linkedExpenseId = expenseId)
        }
        nextBatchId++
        nextExpenseId += stagedExpenses.size
        batchCount++
        expenses.putAll(stagedExpenses)
        ledger.putAll(stagedLedger)
        this.rules.addAll(rules)
        return ImportPersistenceResult(stagedExpenses.size, stagedLedger.size)
    }

    fun seedFingerprint(fingerprint: String) {
        ledger[fingerprint] = ImportedBankTransactionEntity(
            sourceBank = "BML", primaryReference = null, secondaryReference = null,
            postedAt = 1, valueAt = null, transactionAt = null,
            rawType = "Purchase", rawDescription = "collision",
            rawDetail = "collision", direction = "DEBIT", amountMinor = 1, currencyCode = "MVR",
            runningBalanceMinor = null,
            normalizedType = "EXPENSE", reviewStatus = "READY", fingerprint = fingerprint,
            importBatchId = 1, importedAt = 1, linkedExpenseId = null,
            normalizedIdentity = "COLLISION", classificationConfidence = "HIGH", categoryConfidence = "LOW"
        )
    }

    fun snapshot() = listOf(ledgerCount, expenseCount, batchCount, rules.size, nextBatchId.toInt(), nextExpenseId.toInt())
}
