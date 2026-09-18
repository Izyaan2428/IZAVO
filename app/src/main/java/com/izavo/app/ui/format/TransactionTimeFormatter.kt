package com.izavo.app.ui.format

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TransactionTimeFormatter {
    fun time(epochMillis: Long, showSeconds: Boolean, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(pattern(showSeconds))

    fun dateTime(epochMillis: Long, showSeconds: Boolean, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(DateTimeFormatter.ofPattern(
            if (showSeconds) "d MMM yyyy, HH:mm:ss" else "d MMM yyyy, HH:mm", Locale.ENGLISH
        ))

    fun editor(dateTime: LocalDateTime, showSeconds: Boolean, expanded: Boolean): String = dateTime.format(
        DateTimeFormatter.ofPattern(
            if (showSeconds) "d MMM, HH:mm:ss ${if (expanded) "▴" else "▾"}" else "d MMM, HH:mm ${if (expanded) "▴" else "▾"}",
            Locale.ENGLISH
        )
    )

    fun localTime(dateTime: LocalDateTime, showSeconds: Boolean): String = dateTime.format(pattern(showSeconds))
    private fun pattern(showSeconds: Boolean) = DateTimeFormatter.ofPattern(if (showSeconds) "HH:mm:ss" else "HH:mm", Locale.ENGLISH)
}
