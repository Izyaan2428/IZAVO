package com.izavo.app.importing

import java.io.Reader
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class BmlCsvParser(private val zoneId: ZoneId = ZoneId.systemDefault()) : BankStatementParser {
    override fun parse(reader: Reader): ImportParseResult {
        val rows = CsvReader.read(reader)
        if (rows.isEmpty()) return ImportParseResult(emptyList(), 0, listOf("The CSV is empty"))
        var invalid = 0
        val errors = mutableListOf<String>()
        val parsed = rows.mapIndexedNotNull { index, row ->
            if (row.all(String::isBlank)) return@mapIndexedNotNull null
            try { parseRow(row) } catch (exception: IllegalArgumentException) {
                invalid++
                if (errors.size < 5) errors += "Row ${index + 1}: ${exception.message ?: "could not be read"}"
                null
            }
        }.toMutableList()
        matchReversals(parsed)
        if (parsed.isEmpty() && invalid == 0) errors += "No BML transactions were found"
        return ImportParseResult(parsed, invalid, errors)
    }

    private fun parseRow(row: List<String>): CandidateTransaction {
        require(row.size >= 11) { "unsupported BML CSV structure" }
        val postedDate = parsePostedDate(row[0])
        val valueDate = cleanCell(row[1]).takeIf(String::isNotEmpty)?.let(::parsePostedDate)
        val rawType = cleanCell(row[2])
        require(rawType.isNotBlank()) { "missing transaction type" }
        val primaryReference = cleanCell(row[3]).ifBlank { null }
        val secondaryReference = cleanCell(row[4]).ifBlank { null }
        val rawDetail = cleanCell(row[5])
        val rawDescription = cleanCell(row[6])
        val extraDetail = cleanCell(row[7])
        val debit = parseOptionalAmount(row[8])
        val credit = parseOptionalAmount(row[9])
        require((debit != null) xor (credit != null)) { "exactly one debit or credit amount is required" }
        val direction = if (debit != null) TransactionDirection.DEBIT else TransactionDirection.CREDIT
        val amountMinor = debit ?: credit!!
        require(amountMinor > 0) { "amount must be greater than zero" }
        val classification = classify(rawType, direction, rawDescription, rawDetail, extraDetail)
        val transactionAt = parseTransactionAt("$rawDetail $extraDetail")
        val postedAt = postedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        return CandidateTransaction(
            sourceBank = SOURCE_BANK,
            postedAt = postedAt,
            valueAt = valueDate?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli(),
            transactionAt = transactionAt?.atZone(zoneId)?.toInstant()?.toEpochMilli(),
            rawType = rawType,
            rawDescription = rawDescription,
            rawDetail = listOf(rawDetail, extraDetail).filter(String::isNotBlank).joinToString(" · "),
            primaryReference = primaryReference,
            secondaryReference = secondaryReference,
            direction = direction,
            amountMinor = amountMinor,
            currencyCode = DEFAULT_CURRENCY,
            runningBalanceMinor = parseOptionalAmount(row[10]),
            normalizedType = classification.first,
            reviewStatus = classification.second,
            displayName = merchantName(rawType, rawDescription, rawDetail),
            fingerprint = fingerprint(primaryReference, secondaryReference, direction, amountMinor, postedDate, rawType, rawDescription, rawDetail),
            normalizedRail = BankRailNormalizer.fromRaw(rawType),
            channel = when {
                rawType.startsWith("Favara", true) -> "P2P"
                rawType.equals("Purchase", true) -> "MERCHANT"
                else -> null
            }
        )
    }

    private fun classify(rawType: String, direction: TransactionDirection, description: String, detail: String, extra: String): Pair<NormalizedTransactionType, ImportReviewStatus> {
        val type = rawType.trim().lowercase()
        val allText = "$type $description $detail $extra".lowercase()
        if ("credit card" in allText || "loan payment" in allText || "loan repayment" in allText) {
            return NormalizedTransactionType.DEBT_PAYMENT to ImportReviewStatus.EXCLUDED
        }
        return when {
            type == "purchase" && direction == TransactionDirection.DEBIT -> NormalizedTransactionType.EXPENSE to ImportReviewStatus.READY
            type == "purchase" && direction == TransactionDirection.CREDIT -> NormalizedTransactionType.REFUND to ImportReviewStatus.NEEDS_REVIEW
            type == "salary" && direction == TransactionDirection.CREDIT -> NormalizedTransactionType.INCOME to ImportReviewStatus.EXCLUDED
            type == "favara credit" || type == "transfer credit" -> NormalizedTransactionType.INCOMING_TRANSFER to ImportReviewStatus.EXCLUDED
            type == "favara debit" || type == "transfer debit" -> NormalizedTransactionType.OUTGOING_TRANSFER to ImportReviewStatus.NEEDS_REVIEW
            "atm" in type || "cash withdrawal" in type || "cash deposit" in type -> NormalizedTransactionType.CASH_MOVEMENT to ImportReviewStatus.EXCLUDED
            ("billpay" in type || "bill payment" in type) && direction == TransactionDirection.DEBIT -> NormalizedTransactionType.EXPENSE to ImportReviewStatus.READY
            else -> NormalizedTransactionType.UNKNOWN to ImportReviewStatus.NEEDS_REVIEW
        }
    }

    private fun matchReversals(items: MutableList<CandidateTransaction>) {
        items.withIndex()
            .filter { it.value.rawType.equals("Purchase", true) && !it.value.primaryReference.isNullOrBlank() && !it.value.secondaryReference.isNullOrBlank() }
            .groupBy { Triple(it.value.primaryReference, it.value.secondaryReference, it.value.amountMinor) }
            .values.forEach { matches ->
                val credits = matches.filter { it.value.direction == TransactionDirection.CREDIT }.toMutableList()
                matches.filter { it.value.direction == TransactionDirection.DEBIT }.forEach { debit ->
                    val credit = credits.minByOrNull { kotlin.math.abs(it.value.postedAt - debit.value.postedAt) } ?: return@forEach
                    credits.remove(credit)
                    items[debit.index] = debit.value.copy(normalizedType = NormalizedTransactionType.REVERSED, reviewStatus = ImportReviewStatus.REVERSED)
                    items[credit.index] = credit.value.copy(normalizedType = NormalizedTransactionType.REVERSED, reviewStatus = ImportReviewStatus.REVERSED)
                }
            }
    }

    private fun parsePostedDate(value: String): LocalDate = try {
        LocalDate.parse(cleanCell(value), POSTED_DATE)
    } catch (_: DateTimeParseException) { throw IllegalArgumentException("invalid date") }

    private fun parseTransactionAt(value: String): LocalDateTime? {
        val clean = cleanCell(value)
        DATE_TIME.find(clean)?.let { match ->
            return try { LocalDateTime.parse(match.value, TRANSACTION_DATE_TIME) } catch (_: DateTimeParseException) { null }
        }
        DATE_ONLY.find(clean)?.let { match ->
            return try { LocalDate.parse(match.value, TRANSACTION_DATE).atStartOfDay() } catch (_: DateTimeParseException) { null }
        }
        return null
    }

    private fun parseOptionalAmount(value: String): Long? {
        val clean = cleanCell(value).replace(",", "")
        if (clean.isBlank()) return null
        return try {
            BigDecimal(clean).multiply(HUNDRED).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: Exception) { throw IllegalArgumentException("invalid amount") }
    }

    private fun merchantName(rawType: String, description: String, detail: String): String {
        val type = rawType.lowercase()
        var value = when {
            type == "salary" || type.startsWith("favara") -> detail.ifBlank { description }
            else -> description.ifBlank { detail }
        }.trim().replace(Regex("\\s+"), " ")
        if (rawType.equals("Purchase", true) && value.startsWith("MP") && value.length > 2 && value[2].isLetter()) value = value.drop(2).trimStart()
        return value.ifBlank { rawType }
    }

    private fun fingerprint(primary: String?, secondary: String?, direction: TransactionDirection, amount: Long, posted: LocalDate, type: String, description: String, detail: String): String {
        val stable = if (!primary.isNullOrBlank() || !secondary.isNullOrBlank()) {
            listOf(SOURCE_BANK, primary.orEmpty(), secondary.orEmpty(), direction.name, amount, posted)
        } else listOf(SOURCE_BANK, posted, direction.name, amount, type.trim().lowercase(), description.trim().lowercase(), detail.trim().lowercase())
        return MessageDigest.getInstance("SHA-256").digest(stable.joinToString("|").toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SOURCE_BANK = "BML"
        const val DEFAULT_CURRENCY = "MVR"
        private val HUNDRED = BigDecimal("100")
        private val POSTED_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        private val TRANSACTION_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        private val TRANSACTION_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm-ss")
        private val DATE_TIME = Regex("\\d{2}-\\d{2}-\\d{4} \\d{2}-\\d{2}-\\d{2}")
        private val DATE_ONLY = Regex("\\d{2}-\\d{2}-\\d{4}")

        fun cleanCell(value: String): String {
            val trimmed = value.trim().removePrefix("\uFEFF")
            return if (trimmed.startsWith("=\"") && trimmed.endsWith('"') && trimmed.length >= 3) trimmed.substring(2, trimmed.length - 1).replace("\"\"", "\"") else trimmed
        }
    }
}

internal object CsvReader {
    fun read(reader: Reader): List<List<String>> {
        val text = reader.readText()
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> { field.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { row += field.toString(); field.clear() }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row += field.toString(); field.clear()
                    if (row.any(String::isNotBlank)) rows += row
                    row = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); if (row.any(String::isNotBlank)) rows += row }
        return rows
    }
}
