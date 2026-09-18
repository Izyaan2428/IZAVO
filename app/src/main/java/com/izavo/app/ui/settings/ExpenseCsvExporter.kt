package com.izavo.app.ui.settings

import android.content.Context
import android.net.Uri
import com.izavo.app.data.ExpenseEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExpenseCsvExporter {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun export(context: Context, destination: Uri, expenses: List<ExpenseEntity>): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(destination)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                requireNotNull(writer)
                // Excel otherwise commonly interprets CSV files as a legacy Windows encoding.
                // A UTF-8 BOM makes Dhivehi and other non-Latin text reliably detectable.
                writer.append('\uFEFF')
                writer.appendLine("ID,Date,Time,Timestamp,Amount,Currency,Category,Note,Created At")
                expenses.sortedByDescending { it.occurredAt }.forEach { expense ->
                    val occurred = Instant.ofEpochMilli(expense.occurredAt).atZone(ZoneId.systemDefault())
                    val values = listOf(
                        expense.id.toString(),
                        occurred.format(dateFormatter),
                        occurred.format(timeFormatter),
                        expense.occurredAt.toString(),
                        BigDecimal.valueOf(expense.amountMinor, 2).toPlainString(),
                        expense.currencyCode,
                        expense.category.orEmpty(),
                        expense.note,
                        expense.createdAt.toString()
                    )
                    writer.appendLine(values.joinToString(",") { escape(stabilizeDirection(it)) })
                }
            }
        }.isSuccess

    internal fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (value.any { it == ',' || it == '\"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else escaped
    }

    /**
     * CSV has no cell-direction metadata. Isolating Thaana runs prevents surrounding
     * Latin text and punctuation from changing their visual order in Excel/Sheets.
     */
    internal fun stabilizeDirection(value: String): String =
        THAANA_RUN.replace(value) { match -> "$RIGHT_TO_LEFT_ISOLATE${match.value}$POP_DIRECTIONAL_ISOLATE" }

    private val THAANA_RUN = Regex("[\\u0780-\\u07BF]+(?:[ \\u00A0]+[\\u0780-\\u07BF]+)*")
    private const val RIGHT_TO_LEFT_ISOLATE = '\u2067'
    private const val POP_DIRECTIONAL_ISOLATE = '\u2069'
}
