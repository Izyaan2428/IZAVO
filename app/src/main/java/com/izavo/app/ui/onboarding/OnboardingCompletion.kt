package com.izavo.app.ui.onboarding

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.izavo.app.ui.design.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import com.izavo.app.ui.home.HomeGreetingPose

/** The preference is already saved. This overlay is disposable presentation only. */
internal const val ONBOARDING_GREETING_HOLD_MILLIS = 2_000L
internal const val ONBOARDING_HOME_TRANSFORM_MILLIS = 3_500
internal const val TRANSITION_GREETING_MAX_LINES = 2
internal const val TRANSITION_GREETING_USES_ELLIPSIS = false
private const val START_GREETING_SCALE = 32f / 19f
private val LiquidEasing = CubicBezierEasing(.58f, 0f, .78f, 1f)

internal fun liquidProgress(rawProgress: Float): Float =
    LiquidEasing.transform(rawProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)

internal fun interpolatedCoordinate(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * liquidProgress(progress)

@Composable
fun OnboardingCompletion(greeting: String, targetPose: HomeGreetingPose? = null, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val animationsEnabled = android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(targetPose) {
        if (targetPose == null) return@LaunchedEffect
        delay(ONBOARDING_GREETING_HOLD_MILLIS)
        progress.animateTo(1f, tween(if (animationsEnabled) ONBOARDING_HOME_TRANSFORM_MILLIS else 1, easing = LinearEasing))
        finish()
    }
    val t = liquidProgress(progress.value)
    val waveT = liquidProgress(((progress.value - .10f) / .90f).coerceIn(0f, 1f))
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var textSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }
        .pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize()) {
            val waveY = (-20.dp.toPx()) + (size.height + 40.dp.toPx()) * waveT
            val bend = 13.dp.toPx()
            val path = Path().apply {
                moveTo(0f, waveY)
                cubicTo(size.width * .22f, waveY - bend, size.width * .36f, waveY + bend, size.width * .52f, waveY)
                cubicTo(size.width * .7f, waveY - bend, size.width * .84f, waveY + bend, size.width, waveY - bend * .2f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, ExpenseColors.Background)
        }
        val layerScale = START_GREETING_SCALE + (1f - START_GREETING_SCALE) * t
        val startX = (viewport.width - textSize.width * START_GREETING_SCALE) / 2f
        val startY = (viewport.height - textSize.height * START_GREETING_SCALE) / 2f
        val endX = targetPose?.topLeft?.x ?: startX
        val endY = targetPose?.topLeft?.y ?: startY
        val finalWidth = targetPose?.size?.width?.let { with(density) { it.toDp() } }
        Text(greeting,
            style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.ContentOrLtr),
            textAlign = TextAlign.Start,
            color = ExpenseColors.Ink,
            maxLines = TRANSITION_GREETING_MAX_LINES,
            overflow = TextOverflow.Clip,
            modifier = Modifier.then(if (finalWidth != null) Modifier.width(finalWidth) else Modifier)
                .onSizeChanged { textSize = it }.offset {
                IntOffset(
                    interpolatedCoordinate(startX, endX, progress.value).roundToInt(),
                    interpolatedCoordinate(startY, endY, progress.value).roundToInt()
                )
            }.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = layerScale
                scaleY = layerScale
            })
    }
}
