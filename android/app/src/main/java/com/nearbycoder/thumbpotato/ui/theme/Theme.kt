package com.nearbycoder.thumbpotato.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbycoder.thumbpotato.domain.BacklogStatus

/** Design tokens from DESIGN.md. */
@Immutable
data class ThumbColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val danger: Color,
    val info: Color,
    /** Secondary button fill: white 12% (dark) / black 6% (light). */
    val secondaryFill: Color,
    val shadow: Color,
) {
    fun statusColor(status: BacklogStatus): Color = when (status) {
        BacklogStatus.QUEUED -> info
        BacklogStatus.PLAYING -> accent
        BacklogStatus.BEATEN -> success
        BacklogStatus.SHELVED -> textTertiary
    }

    /** Critic score bands: >= 75 accent, 50–74 yellow, < 50 danger. */
    fun scoreColor(score: Int): Color = when {
        score >= 75 -> accent
        score >= 50 -> Color(0xFFF0C94A)
        else -> danger
    }
}

val DarkThumbColors = ThumbColors(
    isDark = true,
    bg = Color(0xFF0B0C10),
    surface = Color(0xFF14151B),
    surfaceElevated = Color(0xFF1C1D25),
    hairline = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF4F5F7),
    textSecondary = Color(0x9EF4F5F7),
    textTertiary = Color(0x66F4F5F7),
    accent = Color(0xFFB8F23D),
    onAccent = Color(0xFF122000),
    success = Color(0xFFFFC54D),
    danger = Color(0xFFF2545B),
    info = Color(0xFF5B8CFF),
    secondaryFill = Color(0x1FFFFFFF),
    shadow = Color(0x73000000),
)

val LightThumbColors = ThumbColors(
    isDark = false,
    bg = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    hairline = Color(0x14000000),
    textPrimary = Color(0xFF111114),
    textSecondary = Color(0x9E111114),
    textTertiary = Color(0x66111114),
    accent = Color(0xFF4F8A00),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFFB7800F),
    danger = Color(0xFFD63E45),
    info = Color(0xFF2F6FE0),
    secondaryFill = Color(0x0F000000),
    shadow = Color(0x1A000000),
)

val LocalThumbColors = staticCompositionLocalOf { DarkThumbColors }

object Thumb {
    val colors: ThumbColors
        @Composable @ReadOnlyComposable get() = LocalThumbColors.current
}

private fun schemeFor(c: ThumbColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accent.copy(alpha = 0.18f),
        onPrimaryContainer = c.accent,
        secondary = c.accent,
        onSecondary = c.onAccent,
        secondaryContainer = c.accent.copy(alpha = 0.18f),
        onSecondaryContainer = c.accent,
        tertiary = c.info,
        onTertiary = Color.White,
        tertiaryContainer = c.info.copy(alpha = 0.18f),
        onTertiaryContainer = c.info,
        error = c.danger,
        onError = Color.White,
        errorContainer = c.danger.copy(alpha = 0.18f),
        onErrorContainer = c.danger,
        background = c.bg,
        onBackground = c.textPrimary,
        surface = c.bg,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceElevated,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.bg,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceElevated,
        surfaceContainerHighest = c.surfaceElevated,
        surfaceBright = c.surfaceElevated,
        surfaceDim = c.bg,
        outline = if (c.isDark) Color(0x33FFFFFF) else Color(0x29000000),
        outlineVariant = c.hairline,
        scrim = Color.Black,
    )
}

private val systemFont = FontFamily.Default

val ThumbTypography = Typography(
    displaySmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = systemFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

val ThumbShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Radii {
    val coverThumb = 8.dp
    val coverRow = 10.dp
    val coverFeatured = 14.dp
    val card = 16.dp
    val sheet = 24.dp
}

@Composable
fun ThumbPotatoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val thumb = if (darkTheme) DarkThumbColors else LightThumbColors
    CompositionLocalProvider(LocalThumbColors provides thumb) {
        MaterialTheme(colorScheme = schemeFor(thumb), typography = ThumbTypography, shapes = ThumbShapes, content = content)
    }
}
