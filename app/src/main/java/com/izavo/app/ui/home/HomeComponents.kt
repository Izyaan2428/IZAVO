package com.izavo.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.izavo.app.R
import com.izavo.app.data.CategoryTotal
import com.izavo.app.data.CurrencyTotal
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.data.currencyDisplayOrder
import com.izavo.app.data.formatCurrency
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseTransactionRow
import com.izavo.app.ui.design.ExpenseCategoryVisuals
import com.izavo.app.ui.design.amountNumber

@Composable
fun SpendingHero(
    todayTotals: List<CurrencyTotal>,
    monthTotals: List<CurrencyTotal>,
    homeCurrency: String
) {
    val today = orderedTotals(todayTotals, homeCurrency)
    val month = orderedTotals(monthTotals, homeCurrency)
    val todayHero = today.firstOrNull { it.currencyCode == homeCurrency }
    val monthHero = month.firstOrNull { it.currencyCode == homeCurrency }

    Box(Modifier.fillMaxWidth().height(210.dp)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to ExpenseColors.CyanAtmosphere.copy(alpha = .15f),
                    1f to ExpenseColors.CyanAtmosphere.copy(alpha = 0f),
                    center = Offset(size.width * .48f, size.height * .48f),
                    radius = size.width * .69f
                ), radius = size.width * .69f, center = Offset(size.width * .48f, size.height * .48f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to ExpenseColors.BlueAtmosphere.copy(alpha = .065f),
                    1f to ExpenseColors.BlueAtmosphere.copy(alpha = 0f),
                    center = Offset(size.width * .72f, size.height * .62f),
                    radius = size.width * .45f
                ), radius = size.width * .45f, center = Offset(size.width * .72f, size.height * .62f)
            )
        }
        Column(Modifier.padding(top = 25.dp)) {
            Text("Today", color = ExpenseColors.InkSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(5.dp))
            AdaptiveHeroAmount(todayHero?.totalMinor ?: 0L)
            Text(homeCurrency, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelMedium)
            SecondaryCurrencyLine(today.filterNot { it.currencyCode == homeCurrency })
            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("This month", color = ExpenseColors.InkSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(amountNumber(monthHero?.totalMinor ?: 0L), color = ExpenseColors.Ink,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(7.dp))
                Text(homeCurrency, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelSmall)
            }
            SecondaryCurrencyLine(month.filterNot { it.currencyCode == homeCurrency }, alignEnd = true)
        }
    }
}

@Composable
private fun AdaptiveHeroAmount(amountMinor: Long) {
    val value = amountNumber(amountMinor)
    val size = when {
        value.length <= 12 -> 50.sp
        value.length <= 15 -> 42.sp
        else -> 34.sp
    }
    Text(value, color = ExpenseColors.Ink, fontSize = size, lineHeight = 52.sp,
        letterSpacing = (-.6).sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"))
}

@Composable
private fun SecondaryCurrencyLine(totals: List<CurrencyTotal>, alignEnd: Boolean = false) {
    if (totals.isEmpty()) return
    val shown = totals.take(2)
    val hidden = totals.size - shown.size
    val label = buildString {
        append(shown.joinToString(" · ") { formatCurrency(it.totalMinor, it.currencyCode) })
        if (hidden > 0) append(" · +$hidden")
    }
    Box(Modifier.fillMaxWidth().padding(top = 3.dp), contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(label, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun orderedTotals(totals: List<CurrencyTotal>, homeCurrency: String) = totals.sortedWith(
    compareBy<CurrencyTotal> { currencyDisplayOrder(it.currencyCode, homeCurrency) }.thenBy { it.currencyCode }
)

@Composable
fun CategorySpendingRow(category: String, totals: List<CategoryTotal>, homeCurrency: String) {
    val ordered = totals.sortedWith(compareBy<CategoryTotal> {
        currencyDisplayOrder(it.currencyCode, homeCurrency)
    }.thenBy { it.currencyCode })
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val visual = ExpenseCategoryVisuals.forKey(category)
        Icon(visual.icon, null, tint = visual.color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(category, color = ExpenseColors.Ink, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            ordered.take(2).forEachIndexed { index, total ->
                Text(amountNumber(total.totalMinor), color = if (index == 0) ExpenseColors.Ink else ExpenseColors.InkSecondary,
                    style = if (index == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
                Text(total.currencyCode, color = ExpenseColors.InkSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val ColorCategoryIcon = androidx.compose.ui.graphics.Color(0xFF747D88)

@Composable
fun RecentExpenseRow(expense: ExpenseEntity, onClick: () -> Unit, showDivider: Boolean, showSeconds: Boolean = false) {
    ExpenseTransactionRow(expense, onClick, showDivider = showDivider, rowHeight = 62.dp, showSeconds = showSeconds)
}
