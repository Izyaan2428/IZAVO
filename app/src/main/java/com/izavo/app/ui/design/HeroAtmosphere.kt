package com.izavo.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Size

enum class AtmosphereIntensity { HOME, SUBTLE, NONE }

@Composable
fun IzavoAtmosphericBackground(
    modifier: Modifier = Modifier,
    intensity: AtmosphereIntensity = AtmosphereIntensity.HOME
) {
    Box(modifier.background(ExpenseColors.Background)) {
        if (intensity != AtmosphereIntensity.NONE) Canvas(Modifier.matchParentSize()) {
            val strength = if (intensity == AtmosphereIntensity.HOME) 1f else .25f
            // One dominant source: 170% of viewport width, centered above the viewport.
            val diameter = size.width * 1.7f
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, -size.height * .015f)
            drawCircle(
                Brush.radialGradient(
                    colorStops = arrayOf(0f to ExpenseColors.CyanAtmosphere.copy(alpha = (if (ExpenseColors.IsDark) .48f else .42f) * strength),
                    .38f to ExpenseColors.BlueAtmosphere.copy(alpha = (if (ExpenseColors.IsDark) .34f else .27f) * strength),
                    .68f to ExpenseColors.LavenderAtmosphere.copy(alpha = (if (ExpenseColors.IsDark) .18f else .14f) * strength),
                    1f to ExpenseColors.Background.copy(alpha = 0f)), center = center, radius = radius
                ), radius, center
            )
            // A broad, low-opacity cyan bias keeps the source luminous rather than violet.
            drawOval(
                Brush.radialGradient(
                    colors = listOf(ExpenseColors.CyanAtmosphere.copy(alpha = .13f * strength), ExpenseColors.Background.copy(alpha = 0f)),
                    center = Offset(size.width * .42f, size.height * .03f), radius = size.width
                ), topLeft = Offset(-size.width * .35f, -size.height * .36f),
                size = Size(size.width * 1.7f, size.height * 1.12f)
            )
        }
    }
}

@Composable
fun HeroAtmosphere(modifier: Modifier = Modifier) =
    IzavoAtmosphericBackground(modifier, AtmosphereIntensity.HOME)
