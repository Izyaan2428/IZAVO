package com.izavo.app.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** IZAVO's shared warm-neutral visual palette. */
data class IzavoPalette(
    val background: Color, val backgroundElevated: Color, val surface: Color,
    val surfaceElevated: Color, val surfaceGlass: Color, val surfaceGlassStrong: Color,
    val controlSurface: Color, val navSurface: Color, val sheetSurface: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val separator: Color, val borderSubtle: Color, val accent: Color, val accentSoft: Color,
    val accentSelected: Color, val chartPrimary: Color, val chartSecondary: Color,
    val chartGrid: Color, val scrim: Color, val destructive: Color,
    val destructiveSoft: Color, val action: Color, val onAction: Color,
    val atmosphereCyan: Color, val atmosphereBlue: Color, val atmosphereViolet: Color,
    val dark: Boolean
)

val IzavoLightPalette = IzavoPalette(
    Color(0xFFFAFBFC), Color(0xFFFFFFFF), Color(0xFFFEFEFE), Color(0xFFFFFFFF),
    Color(0xEAFBFDFF), Color(0xF7FBFDFE), Color(0xFFF1F4F6), Color(0xF2FFFFFF),
    Color(0xFFFBFDFE), Color(0xFF111820), Color(0xFF677381), Color(0xFF929DA9),
    Color(0x4AD7E0E8), Color(0x75DCE5EC), Color(0xFF137EBA), Color(0xFFDDF5FB),
    Color(0xFF087A91), Color(0xFF237FC2), Color(0xFF7EC9E8), Color(0x3AD2DDE7),
    Color(0x38070B10), Color(0xFFC7292E), Color(0xFFFBE8E9), Color(0xFF0E1218),
    Color.White, Color(0xFF65E1F7), Color(0xFF5599FF), Color(0xFF9B86F2), false
)

val IzavoDarkPalette = IzavoPalette(
    Color(0xFF080C12), Color(0xFF0D131C), Color(0xFF101720), Color(0xFF151E29),
    Color(0xE6101721), Color(0xFA121A24), Color(0xFF151E28), Color(0xF2161F2A),
    Color(0xFF111923), Color(0xFFF1F5F8), Color(0xFFAAB5C1), Color(0xFF75818F),
    Color(0x526A7888), Color(0x526D7E90), Color(0xFF62B9EC), Color(0xFF173844),
    Color(0xFF8BDCF0), Color(0xFF62B9EC), Color(0xFF315F7A), Color(0x385F7185),
    Color(0x99000000), Color(0xFFFF777C), Color(0xFF381B21), Color(0xFFF0F4F8),
    Color(0xFF0A0F16), Color(0xFF39D6ED), Color(0xFF397EEB), Color(0xFF7965DC), true
)

object ExpenseColors {
    var palette: IzavoPalette = IzavoLightPalette
    val IsDark get() = palette.dark
    val Background get() = palette.background
    val BackgroundElevated get() = palette.backgroundElevated
    val Surface get() = palette.surface
    val SurfaceElevated get() = palette.surfaceElevated
    val SurfaceQuiet get() = palette.controlSurface
    val Ink get() = palette.textPrimary
    val Action get() = palette.action
    val OnAction get() = palette.onAction
    val InkSecondary get() = palette.textSecondary
    val InkTertiary get() = palette.textTertiary
    val Cyan get() = palette.accent
    val CyanSelected get() = palette.accentSelected
    val CyanSoft get() = palette.accentSoft
    val CyanAtmosphere get() = palette.atmosphereCyan
    val BlueAtmosphere get() = palette.atmosphereBlue
    val LavenderAtmosphere get() = palette.atmosphereViolet
    val Divider get() = palette.separator
    val Danger get() = palette.destructive
    val DangerSoft get() = palette.destructiveSoft
    val Glass get() = palette.surfaceGlassStrong
    val GlassBorder get() = palette.borderSubtle
    val Scrim get() = palette.scrim
    val ChartPrimary get() = palette.chartPrimary
    val ChartSecondary get() = palette.chartSecondary
    val ChartGrid get() = palette.chartGrid
}

object ExpenseSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Section = 32.dp
    val Hero = 40.dp
    val ScreenGutter = 24.dp
}

object ExpenseShapes {
    val Control = 18.dp
    val Selector = 15.dp
    val Surface = 24.dp
    val Sheet = 34.dp
    val FloatingBar = 30.dp
}

object ExpenseMotion {
    const val Fast = IzavoMotion.Control
    const val Standard = IzavoMotion.Screen
    const val Slow = IzavoMotion.Chart
}

private val systemFont = FontFamily.Default
private const val tabularNumbers = "tnum"

val ExpenseTypography = Typography(
    displayLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 50.sp, lineHeight = 54.sp, letterSpacing = (-0.6).sp, fontFeatureSettings = tabularNumbers),
    displayMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = tabularNumbers),
    displaySmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = tabularNumbers),
    headlineLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 29.sp, lineHeight = 35.sp),
    headlineSmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp)
)
