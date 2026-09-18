package com.izavo.app.importing

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class StatementProfileBuilder(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    data class Profiles(
        val identities: Map<String, IdentityProfile>,
        val contexts: Map<SemanticContextKey, IdentityContextProfile>
    )

    fun build(transactions: List<CanonicalTransaction>): Profiles {
        val stable = transactions.sortedWith(compareBy<CanonicalTransaction> { it.postedAt }.thenBy { it.fingerprint })
        val identities = stable.groupBy { it.counterpartyNormalized }.toSortedMap().mapValues { (identity, rows) ->
            val amounts = rows.map { it.amountMinor }.sorted()
            IdentityProfile(
                identity = identity,
                transactionCount = rows.size,
                incomingCount = rows.count { it.direction == TransactionDirection.CREDIT },
                outgoingCount = rows.count { it.direction == TransactionDirection.DEBIT },
                currencies = rows.mapTo(sortedSetOf()) { it.currencyCode },
                rails = rows.mapTo(linkedSetOf()) { it.rail },
                minimumMinor = amounts.firstOrNull() ?: 0,
                maximumMinor = amounts.lastOrNull() ?: 0,
                medianMinor = amounts.getOrElse(amounts.size / 2) { 0 },
                mixedDirections = rows.map { it.direction }.distinct().size > 1,
                recurrence = recurrence(rows)
            )
        }
        val contexts = stable.groupBy {
            SemanticContextKey(it.sourceBank, it.counterpartyNormalized, it.direction, it.rail, it.currencyCode)
        }.entries.sortedBy { it.key.toString() }.associate { (key, rows) ->
            key to IdentityContextProfile(key, rows.size, recurrence(rows))
        }
        return Profiles(identities, contexts)
    }

    private fun recurrence(rows: List<CanonicalTransaction>): RecurrenceProfile {
        if (rows.size < 3) return RecurrenceProfile(observationCount = rows.size)
        val dates = rows.map { Instant.ofEpochMilli(it.transactionAt ?: it.postedAt).atZone(zoneId).toLocalDate() }.distinct().sorted()
        if (dates.size < 3) return RecurrenceProfile(observationCount = rows.size)
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        val monthly = gaps.count { it in 24..38 } >= gaps.size - 1
        val weekly = gaps.count { it in 5..9 } >= gaps.size - 1
        val median = rows.map { it.amountMinor }.sorted()[rows.size / 2].coerceAtLeast(1)
        val stableAmounts = rows.count { abs(it.amountMinor - median) <= median / 5 } * 4 >= rows.size * 3
        return RecurrenceProfile(monthly, weekly, stableAmounts, rows.size)
    }
}
