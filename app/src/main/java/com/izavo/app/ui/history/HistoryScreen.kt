package com.izavo.app.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.ui.home.HistoryExpenseRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    expenses: List<ExpenseEntity>,
    onExpenseClick: (ExpenseEntity) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "History",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        if (expenses.isEmpty()) {

            Text(
                text = "No expenses yet."
            )

            return
        }

        val groupedExpenses =
            expenses.groupBy {
                expenseDate(it)
            }

        LazyColumn {

            groupedExpenses.forEach {
                    (date, dateExpenses) ->

                item {

                    Text(
                        text =
                            formatDateHeader(date),
                        style =
                            MaterialTheme
                                .typography
                                .labelLarge,
                        modifier =
                            Modifier.padding(
                                vertical = 12.dp
                            )
                    )
                }

                items(
                    items = dateExpenses,
                    key = {
                        it.id
                    }
                ) { expense ->

                    HistoryExpenseRow(
                        expense = expense,
                        onClick = {
                            onExpenseClick(expense)
                        }
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )
                }
            }
        }
    }
}

private fun expenseDate(
    expense: ExpenseEntity
): LocalDate {

    return Instant
        .ofEpochMilli(
            expense.occurredAt
        )
        .atZone(
            ZoneId.systemDefault()
        )
        .toLocalDate()
}

private fun formatDateHeader(
    date: LocalDate
): String {

    val today =
        LocalDate.now()

    return when (date) {

        today ->
            "TODAY"

        today.minusDays(1) ->
            "YESTERDAY"

        else ->
            date.format(
                DateTimeFormatter.ofPattern(
                    "EEE, d MMM"
                )
            ).uppercase()
    }
}
