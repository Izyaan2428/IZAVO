package com.izavo.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.izavo.app.data.ExpenseEntity
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.amountNumber
import com.izavo.app.ui.design.premiumClick
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailySpending(val date: LocalDate, val amountMinor: Long)

fun sevenDaySpending(
    expenses: List<ExpenseEntity>,
    currencyCode: String,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<DailySpending> {
    val first = today.minusDays(6)
    val totals = expenses.asSequence()
        .filter { it.currencyCode == currencyCode && it.amountMinor > 0 }
        .map { Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate() to it.amountMinor }
        .filter { (date, _) -> !date.isBefore(first) && !date.isAfter(today) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum() }
    return (0L..6L).map { offset ->
        val date = first.plusDays(offset)
        DailySpending(date, totals[date] ?: 0L)
    }
}

@Composable
fun HomeSevenDaySparkline(
    points: List<DailySpending>, currencyCode: String, onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reveal = remember(currencyCode) { Animatable(0f) }
    LaunchedEffect(currencyCode) { reveal.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
    val today = points.lastOrNull()?.amountMinor ?: 0L
    val description = "Spending trend for the last 7 days. Today: $currencyCode ${amountNumber(today)}"
    Canvas(modifier.size(width = 120.dp, height = 54.dp).semantics { contentDescription = description }
        .premiumClick(pressedScale = .98f, onClick = onClick)) {
        if (points.size != 7) return@Canvas
        val values = points.map { it.amountMinor.coerceAtLeast(0L) }
        val max = values.maxOrNull() ?: 0L
        val horizontalPadding = 3.dp.toPx()
        val verticalPadding = 7.dp.toPx()
        val usableWidth = size.width - horizontalPadding * 2
        val usableHeight = size.height - verticalPadding * 2
        if (max == 0L) {
            drawLine(ExpenseColors.ChartGrid, Offset(horizontalPadding, size.height / 2f),
                Offset(size.width - horizontalPadding, size.height / 2f), 1.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        fun point(index: Int): Offset {
            val x = horizontalPadding + usableWidth * index / 6f
            val ratio = values[index].toFloat() / max.toFloat()
            return Offset(x, size.height - verticalPadding - usableHeight * ratio)
        }
        val path = Path()
        points.indices.forEach { index ->
            val p = point(index)
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        clipRect(right = size.width * reveal.value) {
            drawPath(path, ExpenseColors.ChartPrimary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        if (reveal.value > .98f) drawCircle(ExpenseColors.ChartPrimary, 2.5.dp.toPx(), point(6))
    }
}
