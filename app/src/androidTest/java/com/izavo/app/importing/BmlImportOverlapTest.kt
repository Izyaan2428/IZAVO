package com.izavo.app.importing

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.izavo.app.data.ExpenseDatabase
import com.izavo.app.data.ImportBatchEntity
import com.izavo.app.data.ImportedBankTransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.time.ZoneOffset

/**
 * Exercises overlap protection through the production parser, classifier, repository and Room
 * transaction. The generated Level 3 representation has 1,200 BML rows and two deliberate exact
 * duplicates, matching the torture fixture's overlap property without embedding user bank data.
 */
@RunWith(AndroidJUnit4::class)
class BmlImportOverlapTest {
    private lateinit var database: ExpenseDatabase
    private lateinit var repository: BankImportRepository
    private val parser = BmlCsvParser(ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ExpenseDatabase::class.java
        ).build()
        repository = BankImportRepository(database.importDao())
    }

    @After fun tearDown() = database.close()

    @Test
    fun fullLevel3HistoryThenRows401To900DoesNotDuplicateLedgerOrExpenses() = runBlocking {
        val uniqueRows = (1..1198).map(::bmlRow)
        val fullHistory = (uniqueRows + uniqueRows[98] + uniqueRows[698]).joinToString("\n")
        val parsedFull = parser.parse(StringReader(fullHistory))
        assertEquals(1200, parsedFull.candidates.size)

        val firstPrepared = resolveReviews(repository.prepare("level3.csv", parsedFull))
        assertEquals(2, firstPrepared.duplicateCount)
        val firstResult = repository.import(firstPrepared)
        val before = snapshot()

        assertEquals(1198, firstResult.transactionCount)
        assertEquals(1198L, before.ledgerCount)
        assertEquals(1198L, before.distinctFingerprintCount)
        assertEquals(before.expenseCount, before.linkedExpenseCount)
        assertEquals(0L, before.brokenLinkCount)

        val overlapParsed = parser.parse(StringReader(uniqueRows.subList(400, 900).joinToString("\n")))
        val overlapFingerprints = overlapParsed.candidates.map { it.fingerprint }.toSet()
        val linksBefore = linkedExpenses(overlapFingerprints)
        val overlapPrepared = repository.prepare("level3-overlap-401-900.csv", overlapParsed)

        assertEquals(500, overlapPrepared.duplicateCount)
        assertTrue(overlapPrepared.items.all { it.transaction.reviewStatus == ImportReviewStatus.DUPLICATE })
        val overlapResult = repository.import(overlapPrepared)
        val after = snapshot()

        assertEquals(0, overlapResult.transactionCount)
        assertEquals(0, overlapResult.expenseCount)
        assertEquals(500, overlapResult.duplicateCount)
        assertEquals(before.ledgerCount, after.ledgerCount)
        assertEquals(before.expenseCount, after.expenseCount)
        assertEquals(before.distinctFingerprintCount, after.distinctFingerprintCount)
        assertEquals(0L, after.brokenLinkCount)
        assertEquals(0L, after.duplicateLinkedExpenseCount)
        assertEquals(2L, after.batchCount)
        assertEquals(linksBefore, linkedExpenses(overlapFingerprints))
    }

    @Test
    fun exactStatementReimportAddsNoLedgerRowsOrExpenses() = runBlocking {
        val csv = (1..60).joinToString("\n", transform = ::bmlRow)
        val parsed = parser.parse(StringReader(csv))
        repository.import(resolveReviews(repository.prepare("statement.csv", parsed)))
        val before = snapshot()

        val repeated = repository.prepare("statement-again.csv", parser.parse(StringReader(csv)))
        assertEquals(60, repeated.duplicateCount)
        val result = repository.import(repeated)
        val after = snapshot()

        assertEquals(0, result.transactionCount)
        assertEquals(0, result.expenseCount)
        assertEquals(60, result.duplicateCount)
        assertEquals(before.ledgerCount, after.ledgerCount)
        assertEquals(before.expenseCount, after.expenseCount)
        assertEquals(before.distinctFingerprintCount, after.distinctFingerprintCount)
        assertEquals(0L, after.brokenLinkCount)
        assertEquals(0L, after.duplicateLinkedExpenseCount)
        assertEquals(2L, after.batchCount)
    }

    @Test
    fun partialOverlapImportsOnlyNewRows() = runBlocking {
        val firstCsv = (1..4).joinToString("\n", transform = ::bmlRow)
        repository.import(resolveReviews(repository.prepare("abcd.csv", parser.parse(StringReader(firstCsv)))))
        val before = snapshot()

        val secondCsv = (3..6).joinToString("\n", transform = ::bmlRow)
        val prepared = resolveReviews(repository.prepare("cdef.csv", parser.parse(StringReader(secondCsv))))
        assertEquals(2, prepared.duplicateCount)
        val expectedNewExpenses = prepared.items.count {
            it.transaction.reviewStatus != ImportReviewStatus.DUPLICATE && it.decision == ImportDecision.EXPENSE
        }
        val result = repository.import(prepared)
        val after = snapshot()

        assertEquals(2, result.transactionCount)
        assertEquals(expectedNewExpenses, result.expenseCount)
        assertEquals(2L, after.ledgerCount - before.ledgerCount)
        assertEquals(expectedNewExpenses.toLong(), after.expenseCount - before.expenseCount)
        assertEquals(after.ledgerCount, after.distinctFingerprintCount)
        assertEquals(0L, after.brokenLinkCount)
        assertEquals(0L, after.duplicateLinkedExpenseCount)
        assertEquals(2L, after.batchCount)
    }

    @Test
    fun unexpectedFingerprintConflictRollsBackBatchAndExpenseTogether() = runBlocking {
        val parsed = parser.parse(StringReader(bmlRow(5000)))
        val prepared = resolveReviews(repository.prepare("conflict.csv", parsed))
        val candidate = prepared.items.single().transaction
        val collisionBatch = database.importDao().insertBatch(
            ImportBatchEntity(sourceBank = "BML", fileName = "collision", importedAt = 1, totalRows = 1, invalidRows = 0)
        )
        database.importDao().insertImportedTransactions(listOf(candidate.asLedger(collisionBatch)))
        val before = snapshot()

        var failed = false
        try {
            repository.import(prepared)
        } catch (_: IllegalStateException) {
            failed = true
        }
        val after = snapshot()

        assertTrue("Expected the conflicting import to fail", failed)
        assertEquals(before, after)
        assertEquals(0L, after.brokenLinkCount)
    }

    private fun resolveReviews(prepared: PreparedImport): PreparedImport {
        val groups = prepared.reviewGroups.mapIndexed { index, group ->
            if (group.resolved) group else {
                val decision = when (index % 3) {
                    0 -> ImportDecision.EXPENSE
                    1 -> ImportDecision.MONEY_MOVEMENT
                    else -> ImportDecision.UNKNOWN
                }
                ReviewGroupBuilder.applyDecision(
                    group, decision, if (decision == ImportDecision.EXPENSE) "Other" else null, remember = false
                )
            }
        }
        val byFingerprint = groups.flatMap { it.items }.associateBy { it.transaction.fingerprint }
        return prepared.copy(
            reviewGroups = groups,
            items = prepared.items.map { byFingerprint[it.transaction.fingerprint] ?: it }
        )
    }

    private suspend fun snapshot() = DatabaseSnapshot(
        ledgerCount = scalar("SELECT COUNT(*) FROM imported_bank_transactions"),
        distinctFingerprintCount = scalar("SELECT COUNT(DISTINCT fingerprint) FROM imported_bank_transactions"),
        expenseCount = scalar("SELECT COUNT(*) FROM expenses"),
        linkedExpenseCount = scalar("SELECT COUNT(*) FROM imported_bank_transactions WHERE linkedExpenseId IS NOT NULL"),
        brokenLinkCount = scalar(
            """SELECT COUNT(*) FROM imported_bank_transactions bank
               LEFT JOIN expenses expense ON expense.id = bank.linkedExpenseId
               WHERE bank.linkedExpenseId IS NOT NULL AND expense.id IS NULL"""
        ),
        duplicateLinkedExpenseCount = scalar(
            """SELECT COUNT(*) FROM (
                   SELECT linkedExpenseId FROM imported_bank_transactions
                   WHERE linkedExpenseId IS NOT NULL
                   GROUP BY linkedExpenseId HAVING COUNT(*) > 1
               )"""
        ),
        batchCount = scalar("SELECT COUNT(*) FROM import_batches")
    )

    private suspend fun scalar(sql: String): Long = database.useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            check(statement.step())
            statement.getLong(0)
        }
    }

    private suspend fun linkedExpenses(fingerprints: Set<String>): Map<String, Long?> =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT fingerprint, linkedExpenseId FROM imported_bank_transactions") { statement ->
                buildMap {
                    while (statement.step()) {
                        val fingerprint = statement.getText(0)
                        if (fingerprint in fingerprints) {
                            put(fingerprint, if (statement.isNull(1)) null else statement.getLong(1))
                        }
                    }
                }
            }
        }

    private fun CandidateTransaction.asLedger(batchId: Long) = ImportedBankTransactionEntity(
        sourceBank = sourceBank, primaryReference = primaryReference, secondaryReference = secondaryReference,
        postedAt = postedAt, valueAt = valueAt, transactionAt = transactionAt, rawType = rawType,
        rawDescription = rawDescription, rawDetail = rawDetail, direction = direction.name,
        amountMinor = amountMinor, currencyCode = currencyCode, runningBalanceMinor = runningBalanceMinor,
        normalizedType = normalizedType.name, reviewStatus = reviewStatus.name, fingerprint = fingerprint,
        importBatchId = batchId, importedAt = 1, linkedExpenseId = null, normalizedIdentity = normalizedIdentity,
        classificationConfidence = classificationConfidence.name, categoryConfidence = categoryConfidence.name
    )

    private fun bmlRow(index: Int): String {
        val day = (index % 27) + 1
        val month = ((index / 27) % 12) + 1
        val date = "2026/%02d/%02d".format(month, day)
        val timestamp = "%02d-%02d-2026 %02d-%02d-%02d".format(day, month, index % 24, index % 60, index % 60)
        val row = when (index % 3) {
            0 -> RowValues("Purchase", "AVAS RIDE", amount(index), "", timestamp)
            1 -> RowValues("Salary", "EMPLOYER $index", "", amount(index), timestamp)
            else -> RowValues("Favara Debit", "PERSON $index", amount(index), "", "$timestamp Favara")
        }
        return listOf(
            date, date, row.type, "=\"RB%08d\"".format(index), "=\"FT%08d\"".format(index),
            row.description, "=\"${row.description}\"", row.detail, row.debit, row.credit, "100000.00"
        ).joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
    }

    private fun amount(index: Int) = "%d.%02d".format((index % 400) + 1, index % 100)

    private data class RowValues(
        val type: String, val description: String, val debit: String, val credit: String, val detail: String
    )

    private data class DatabaseSnapshot(
        val ledgerCount: Long,
        val distinctFingerprintCount: Long,
        val expenseCount: Long,
        val linkedExpenseCount: Long,
        val brokenLinkCount: Long,
        val duplicateLinkedExpenseCount: Long,
        val batchCount: Long
    )
}
