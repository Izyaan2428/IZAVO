package com.izavo.app.ui.statistics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import com.izavo.app.ui.design.IzavoMotion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.izavo.app.ui.design.ExpenseCategoryVisuals
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseMotion
import kotlin.math.roundToInt

@Composable
fun SpendingTrendChart(points: List<SpendPoint>, currency: String, bars: Boolean = false, modifier: Modifier = Modifier) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val revealAnimation = remember { Animatable(0f) }
    LaunchedEffect(points, bars) { revealAnimation.snapTo(0f); revealAnimation.animateTo(1f, tween(IzavoMotion.Chart)) }
    val reveal = revealAnimation.value
    val max = points.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L
    Column(modifier.semantics {
        contentDescription = "Spending chart in $currency"
        customActions = listOf(CustomAccessibilityAction("Next chart value") { if (points.isNotEmpty()) selected = ((selected ?: -1) + 1).coerceAtMost(points.lastIndex); true },
            CustomAccessibilityAction("Previous chart value") { if (points.isNotEmpty()) selected = ((selected ?: 1) - 1).coerceAtLeast(0); true })
    }) {
        Box(Modifier.fillMaxWidth().height(38.dp)) {
            val index = selected
            Text(if (index != null) "${points[index].label} · $currency ${StatisticsCalculator.money(points[index].amountMinor)}" else "Touch the chart to explore",
                color = ExpenseColors.Ink, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(176.dp)
            .pointerInput(points) {
                detectTapGestures { tap ->
                    if (points.isNotEmpty()) selected = if (bars) ((tap.x / size.width) * points.size).toInt().coerceIn(0, points.lastIndex) else ((tap.x / size.width) * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)
                }
            }
            .pointerInput(points) {
                fun selectAt(x: Float) {
                    if (points.isNotEmpty()) selected = if (bars) ((x / size.width) * points.size).toInt().coerceIn(0, points.lastIndex) else ((x / size.width) * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)
                }
                detectHorizontalDragGestures(onDragStart = { selectAt(it.x) }) { change, _ -> selectAt(change.position.x) }
            }) {
            if (points.isEmpty()) return@Canvas
            val top = 10.dp.toPx(); val bottom = size.height - 22.dp.toPx(); val chartHeight = bottom - top
            repeat(3) { index ->
                val y = top + chartHeight * index / 2f
                drawLine(ExpenseColors.ChartGrid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (bars) {
                val slot = size.width / points.size
                points.forEachIndexed { index, point ->
                    val h = chartHeight * (point.amountMinor.toFloat() / max) * reveal
                    if (point.amountMinor <= 0) return@forEachIndexed
                    drawRoundRect(
                        color = if (selected == index) ExpenseColors.ChartPrimary else ExpenseColors.ChartSecondary.copy(alpha = .55f),
                        topLeft = Offset(index * slot + slot * .22f, bottom - h),
                        size = Size(slot * .56f, h.coerceAtLeast(2.dp.toPx())),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    )
                }
            } else {
                val step = if (points.size == 1) 0f else size.width / (points.size - 1)
                if (points.size == 1) {
                    val y = bottom - chartHeight * (points[0].amountMinor.toFloat() / max) * reveal
                    drawCircle(ExpenseColors.ChartPrimary.copy(alpha = reveal), 4.dp.toPx(), Offset(size.width / 2f, y))
                } else {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val y = bottom - chartHeight * (point.amountMinor.toFloat() / max) * reveal
                        if (index == 0) path.moveTo(0f, y) else path.lineTo(index * step, y)
                    }
                    val fill = Path().apply { addPath(path); lineTo(size.width, bottom); lineTo(0f, bottom); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(ExpenseColors.ChartPrimary.copy(.08f), Color.Transparent)))
                    drawPath(path, ExpenseColors.ChartPrimary.copy(alpha = reveal), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    selected?.let { index ->
                        val x = index * step; val y = bottom - chartHeight * (points[index].amountMinor.toFloat() / max)
                        drawCircle(ExpenseColors.Surface, 6.dp.toPx(), Offset(x, y)); drawCircle(ExpenseColors.Cyan, 4.dp.toPx(), Offset(x, y))
                    }
                }
            }
            val labelIndices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
            labelIndices.forEach { index ->
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(107,117,130); textSize = 10.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER }
                    paint.textAlign = when (index) { 0 -> android.graphics.Paint.Align.LEFT; points.lastIndex -> android.graphics.Paint.Align.RIGHT; else -> android.graphics.Paint.Align.CENTER }
                    drawText(points[index].label, if (points.size == 1) size.width / 2 else index * (size.width / (points.size - 1)), size.height, paint)
                }
            }
        }
    }
}

@Composable
fun MiniSparkline(points: List<SpendPoint>, modifier: Modifier = Modifier) {
    val max = points.maxOfOrNull(SpendPoint::amountMinor)?.coerceAtLeast(1L) ?: return
    Canvas(modifier.fillMaxWidth().height(62.dp).semantics { contentDescription = "Current month spending trend" }) {
        if (points.size < 2) return@Canvas
        val path = Path(); val step = size.width / (points.size - 1)
        points.forEachIndexed { index, point ->
            val y = size.height - (point.amountMinor.toFloat() / max * (size.height - 8.dp.toPx()))
            if (index == 0) path.moveTo(0f, y) else path.lineTo(index * step, y)
        }
        drawPath(path, ExpenseColors.Cyan.copy(alpha = .72f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

internal data class CategoryCompositionSegment(
    val sourceIndex: Int,
    val startFraction: Float,
    val endFraction: Float
)

/** Exact, monotonic composition geometry with no minimum-width distortion. */
internal fun categoryCompositionSegments(values: List<Long>): List<CategoryCompositionSegment> {
    val positive = values.mapIndexedNotNull { index, value -> (index to value).takeIf { value > 0L } }
    val total = positive.sumOf { it.second.toDouble() }
    if (total <= 0.0) return emptyList()
    var cursor = 0.0
    return positive.mapIndexed { position, (sourceIndex, value) ->
        val start = cursor
        cursor += value.toDouble() / total
        CategoryCompositionSegment(
            sourceIndex,
            start.toFloat().coerceIn(0f, 1f),
            if (position == positive.lastIndex) 1f else cursor.toFloat().coerceIn(0f, 1f)
        )
    }
}

@Composable
fun CategoryCompositionBar(
    categories: List<CategoryStatistic>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val total = categories.sumOf { it.totalMinor.coerceAtLeast(0L) }
    val segments = remember(categories) { categoryCompositionSegments(categories.map(CategoryStatistic::totalMinor)) }
    Canvas(
        modifier.fillMaxWidth().height(16.dp).semantics {
            contentDescription = buildString {
                append("Category composition, $currency ${StatisticsCalculator.money(total)}")
                categories.filter { it.totalMinor > 0L }.forEach {
                    append(", ${it.category} ${(it.share * 100).roundToInt()} percent")
                }
            }
        }
    ) {
        val radius = size.height / 2f
        drawRoundRect(ExpenseColors.ChartGrid, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        if (segments.isEmpty()) return@Canvas
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(radius)))
        }
        clipPath(clip) {
            segments.forEach { segment ->
                val left = size.width * segment.startFraction
                val right = size.width * segment.endFraction
                drawRect(
                    ExpenseCategoryVisuals.forKey(categories[segment.sourceIndex].category).color.copy(alpha = .82f),
                    topLeft = Offset(left, 0f),
                    size = Size((right - left).coerceAtLeast(0f), size.height)
                )
            }
        }
    }
}

/** Compatibility entry point for older previews; the artifact-prone ring is intentionally retired. */
@Composable
fun CategoryRingChart(categories: List<CategoryStatistic>, currency: String, modifier: Modifier = Modifier, onCategorySelected: (String?) -> Unit = {}) {
    CategoryCompositionBar(categories, currency, modifier)
}
