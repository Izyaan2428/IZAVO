package com.izavo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.izavo.app.ui.design.ExpenseColors
import com.izavo.app.ui.design.ExpenseTypography
import com.izavo.app.ui.design.IzavoLightPalette
import com.izavo.app.ui.design.IzavoDarkPalette
import com.izavo.app.ui.design.IzavoPalette

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val target = if (darkTheme) IzavoDarkPalette else IzavoLightPalette
    val palette = target
    ExpenseColors.palette = palette
    val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val colors = scheme.copy(primary = palette.accent, secondary = palette.textSecondary,
        tertiary = palette.accent, background = palette.background, surface = palette.surface,
        surfaceVariant = palette.controlSurface, onPrimary = palette.onAction,
        onSecondary = palette.onAction, onTertiary = palette.onAction,
        onBackground = palette.textPrimary, onSurface = palette.textPrimary,
        onSurfaceVariant = palette.textSecondary, outline = palette.borderSubtle,
        error = palette.destructive, onError = palette.onAction)
    val view = LocalView.current
    SideEffect {
        (view.context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (darkTheme) SystemBarStyle.dark(palette.background.toArgb())
            else SystemBarStyle.light(palette.background.toArgb(), palette.background.toArgb())
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = ExpenseTypography,
        content = content
    )
}
