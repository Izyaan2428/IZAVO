package com.izavo.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun ThemeRevealHost(
    onCommitTheme: () -> Unit,
    content: @Composable (requestReveal: (Offset) -> Unit) -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val radius = remember { Animatable(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var oldFrame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var running by remember { mutableStateOf(false) }
    val coordinator = remember { ThemeRevealCoordinator() }

    Box(Modifier.fillMaxSize().background(com.izavo.app.ui.design.ExpenseColors.Background).onSizeChanged { viewport = it }) {
        content { buttonCenter ->
            if (!coordinator.tryStart() || view.width <= 0 || view.height <= 0 || viewport == IntSize.Zero) {
                if (view.width <= 0 || view.height <= 0 || viewport == IntSize.Zero) coordinator.complete()
                return@content
            }
            val snapshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(AndroidCanvas(snapshot))
            oldFrame = snapshot.asImageBitmap()
            origin = buttonCenter
            running = true
            onCommitTheme()
            scope.launch {
                try {
                    radius.snapTo(0f)
                    awaitFrame()
                    val farX = max(origin.x, viewport.width - origin.x)
                    val farY = max(origin.y, viewport.height - origin.y)
                    radius.animateTo(hypot(farX, farY), tween(580, easing = FastOutSlowInEasing))
                } finally {
                    oldFrame = null
                    running = false
                    coordinator.complete()
                    radius.snapTo(0f)
                }
            }
        }
        oldFrame?.let { frame ->
            Canvas(
                Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                drawImage(frame)
                drawCircle(Color.Transparent, radius.value, origin, blendMode = BlendMode.Clear)
            }
        }
    }
}

internal class ThemeRevealCoordinator {
    var isRunning: Boolean = false
        private set

    fun tryStart(): Boolean {
        if (isRunning) return false
        isRunning = true
        return true
    }

    fun complete() { isRunning = false }
}
