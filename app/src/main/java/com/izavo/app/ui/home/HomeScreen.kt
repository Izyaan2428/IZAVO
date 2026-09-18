package com.izavo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.izavo.app.data.CategoryTotal
import com.izavo.app.data.CurrencyTotal
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.HOME_CURRENCY_CODE
import com.izavo.app.data.currencyDisplayOrder
import com.izavo.app.data.formatCurrency
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DashboardCardColor = Color(0xFF121917)
private val DashboardAccent = Color(0xFF69D1B5)
private val DashboardMutedText = Color(0xFF88928E)
private val DashboardFoodColor = Color(0xFF64D8B0)
private val DashboardShoppingColor = Color(0xFFD8B04A)
private val DashboardTransportColor = Color(0xFF5FA8E6)
private val DashboardBillsColor = Color(0xFFD86AA7)
private val DashboardOtherColor = Color(0xFFD1D5D3)

@Composable
fun HomeScreen(
    todayTotals: List<CurrencyTotal>,
    monthTotals: List<CurrencyTotal>,
    monthCategoryTotals: List<CategoryTotal>,
    expenses: List<ExpenseEntity>,
    onViewAllClick: () -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit
) {
    val scrollState = rememberScrollState()

    val categoryMap = monthCategoryTotals.groupBy { it.category }
    val orderedMonthTotals = monthTotals.sortedBy {
        currencyDisplayOrder(it.currencyCode)
    }

    val orderedCategories = listOf(
        "Food",
        "Shopping",
        "Transport",
        "Bills",
        "Other"
    )

    val dateLabel = LocalDate.now()
        .format(
            DateTimeFormatter.ofPattern(
                "EEEE, d MMM",
                Locale.ENGLISH
            )
        )
        .uppercase(Locale.ENGLISH)

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = DashboardMutedText
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = greetingText(),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Today's Spending",
                totals = todayTotals,
                highlight = false
            )

            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "This Month",
                totals = monthTotals,
                highlight = true
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        SectionTitle(
            text = "${currentMonthName().uppercase(Locale.ENGLISH)} CATEGORIES"
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = DashboardCardColor
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 16.dp
                )
            ) {
                orderedCategories.forEachIndexed { index, category ->

                    val categoryTotals = categoryMap[category]
                        .orEmpty()
                        .sortedBy { currencyDisplayOrder(it.currencyCode) }

                    val progressCurrency =
                        orderedMonthTotals.firstOrNull()?.currencyCode
                            ?: HOME_CURRENCY_CODE

                    val categoryProgressAmount = categoryTotals
                        .firstOrNull { it.currencyCode == progressCurrency }
                        ?.totalMinor
                        ?: 0L

                    val progressCurrencyTotal = orderedMonthTotals
                        .firstOrNull { it.currencyCode == progressCurrency }
                        ?.totalMinor
                        ?: 0L

                    val progress =
                        if (progressCurrencyTotal > 0) {
                            categoryProgressAmount.toFloat() /
                                progressCurrencyTotal.toFloat()
                        } else {
                            0f
                        }

                    CategoryBreakdownRow(
                        category = category,
                        totals = categoryTotals,
                        progress = progress,
                        progressCurrency = progressCurrency,
                        progressColor = categoryColor(category)
                    )

                    if (index != orderedCategories.lastIndex) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(
                text = "RECENT EXPENSES"
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "View All",
                color = DashboardAccent,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable {
                    onViewAllClick()
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = DashboardCardColor
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                )
            ) {
                if (expenses.isEmpty()) {
                    Text(
                        text = "No expenses yet.",
                        color = DashboardMutedText,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    expenses.take(3).forEachIndexed { index, expense ->

                        ExpenseRow(
                            expense = expense,
                            onClick = {
                                onExpenseClick(expense)
                            }
                        )

                        if (index != expenses.take(3).lastIndex) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    totals: List<CurrencyTotal>,
    highlight: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = DashboardCardColor
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 14.dp
            )
        ) {
            Text(
                text = title,
                color = DashboardMutedText,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(10.dp))

            val orderedTotals = totals.sortedBy {
                currencyDisplayOrder(it.currencyCode)
            }

            if (orderedTotals.isEmpty()) {
                Text(
                    text = formatCurrency(0, HOME_CURRENCY_CODE),
                    color = if (highlight) DashboardAccent else Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                orderedTotals.forEachIndexed { index, total ->
                    Text(
                        text = formatCurrency(total.totalMinor, total.currencyCode),
                        color = if (highlight && index == 0) {
                            DashboardAccent
                        } else {
                            Color.White
                        },
                        style = if (index == 0) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String
) {
    Text(
        text = text,
        color = DashboardMutedText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CategoryBreakdownRow(
    category: String,
    totals: List<CategoryTotal>,
    progress: Float,
    progressCurrency: String,
    progressColor: Color
) {
    val percentage =
        (progress * 100).roundToInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = category,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .widthIn(max = 88.dp)
                    .height(6.dp),
                color = progressColor,
                trackColor = Color(0xFF23302C)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End
        ) {
            if (totals.isEmpty()) {
                Text(
                    text = formatCurrency(0, HOME_CURRENCY_CODE),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            } else {
                totals.forEach { total ->
                    Text(
                        text = formatCurrency(total.totalMinor, total.currencyCode),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = "$percentage% $progressCurrency",
                color = DashboardMutedText,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun ExpenseRow(
    expense: ExpenseEntity,
    onClick: () -> Unit
) {
    val category = expense.category ?: "Other"

    val zoneId = ZoneId.systemDefault()
    val dateTime =
        Instant.ofEpochMilli(expense.occurredAt)
            .atZone(zoneId)

    val timeText =
        dateTime.format(
            DateTimeFormatter.ofPattern(
                "h:mm a",
                Locale.ENGLISH
            )
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = Color(0xFF17211F),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = category,
                tint = DashboardAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = expenseTitle(expense),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = category,
                color = DashboardMutedText,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = formatCurrency(expense.amountMinor, expense.currencyCode),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun greetingText(): String {
    val hour = LocalTime.now().hour

    return when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun currentMonthName(): String {
    return LocalDate.now()
        .format(
            DateTimeFormatter.ofPattern(
                "MMMM",
                Locale.ENGLISH
            )
        )
}

private fun categoryColor(
    category: String
): Color {
    return when (category) {
        "Food" -> DashboardFoodColor
        "Shopping" -> DashboardShoppingColor
        "Transport" -> DashboardTransportColor
        "Bills" -> DashboardBillsColor
        else -> DashboardOtherColor
    }
}

private fun categoryIcon(
    category: String
): ImageVector {
    return when (category) {
        "Food" -> Icons.Outlined.Restaurant
        "Transport" -> Icons.Outlined.DirectionsCar
        "Shopping" -> Icons.Outlined.ShoppingBag
        "Bills" -> Icons.Outlined.ReceiptLong
        else -> Icons.Outlined.MoreHoriz
    }
}

private fun expenseTitle(
    expense: ExpenseEntity
): String {
    return if (expense.note.isNotBlank()) {
        expense.note
    } else {
        expense.category ?: "Expense"
    }
}
