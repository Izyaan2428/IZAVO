package com.izavo.app.ui.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

object IzavoMotion {
    const val Press = 90
    const val Fast = 130
    const val Control = 210
    const val Screen = 240
    const val Chart = 360
    const val Sheet = 300
    const val Completion = 2380L
}

object IzavoMaterial {
    val Navigation get() = ExpenseColors.palette.navSurface
    val Segmented get() = ExpenseColors.palette.controlSurface.copy(alpha = .8f)
    val Sheet get() = ExpenseColors.palette.sheetSurface
    val FloatingControl get() = ExpenseColors.palette.controlSurface.copy(alpha = .65f)
    val Tooltip get() = ExpenseColors.palette.surfaceElevated
    val Highlight get() = ExpenseColors.GlassBorder
    val NavigationRadius = 30.dp
    val ControlRadius = 18.dp
}

/** Ripple-free clicks retain native click, keyboard, hover and accessibility handling. */
fun Modifier.premiumClick(
    role: Role = Role.Button,
    pressedScale: Float = .98f,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f,
        tween(if (pressed) IzavoMotion.Press else IzavoMotion.Control), label = "press")
    this.graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed) .86f else 1f }
        .drawWithContent {
            drawContent()
            if (focused) drawRoundRect(ExpenseColors.Cyan, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
        .clickable(interactionSource = source, indication = null, role = role, onClick = onClick)
}
