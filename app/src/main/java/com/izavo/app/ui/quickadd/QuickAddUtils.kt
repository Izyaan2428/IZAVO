package com.izavo.app.ui.quickadd

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.izavo.app.ui.format.TransactionTimeFormatter

fun isValidAmount(
    amount: String
): Boolean {

    val parsed =
        amount.toBigDecimalOrNull()

    return parsed != null &&
            parsed > BigDecimal.ZERO
}

fun formatNowLabel(
    dateTime: LocalDateTime,
    expanded: Boolean = false,
    showSeconds: Boolean = false
): String {

    val now = LocalDateTime.now()

    val sameDate =
        now.toLocalDate() ==
                dateTime.toLocalDate()

    val sameMinute =
        now.hour == dateTime.hour &&
                now.minute == dateTime.minute

    return if (sameDate && sameMinute) {

        if (expanded) "Now ▴" else "Now ▾"

    } else {

        TransactionTimeFormatter.editor(dateTime, showSeconds, expanded)
    }
}

fun isAllowedAmountInput(amount: String): Boolean {
    return amount.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?$"))
}
