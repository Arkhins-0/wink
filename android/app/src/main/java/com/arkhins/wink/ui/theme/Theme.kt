package com.arkhins.wink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Night with one gold accent: the CTR yellow on near-black, so the mark
 * sits on the same ground everywhere — launcher, splash, and every screen.
 */
val Night = Color(0xFF0B0B0C)
val NightPanel = Color(0xFF161618)
val NightLine = Color(0xFF26262A)
val Snow = Color(0xFFF4F4F5)
val SnowSoft = Color(0xFFB4B4BA)
val SnowFaint = Color(0xFF7A7A82)
val Gold = Color(0xFFFFD100)
val GoldDeep = Color(0xFFE0A800)
val Danger = Color(0xFFFF5A5F)

val Display: FontFamily = FontFamily.SansSerif
val Body: FontFamily = FontFamily.SansSerif

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Night,
    primaryContainer = GoldDeep,
    onPrimaryContainer = Night,
    secondary = Snow,
    onSecondary = Night,
    tertiary = GoldDeep,
    onTertiary = Night,
    background = Night,
    onBackground = Snow,
    surface = NightPanel,
    onSurface = Snow,
    surfaceVariant = NightPanel,
    onSurfaceVariant = SnowSoft,
    outline = NightLine,
    outlineVariant = NightLine,
    error = Danger,
    onError = Night,
)

val WinkTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp),
)

@Composable
fun WinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = WinkTypography, content = content)
}
