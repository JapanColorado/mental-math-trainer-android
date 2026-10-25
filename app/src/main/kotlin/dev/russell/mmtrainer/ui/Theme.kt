package dev.russell.mmtrainer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Material You (wallpaper colors) is available from Android 12. */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The app's color roles. Surfaces, text and the accent come from the Material color scheme
 * (wallpaper-derived when dynamic color is on); the status colors (good / warn / bad) and the
 * chart series stay fixed so "green = correct" reads the same on every wallpaper, with
 * separate tones for light and dark backgrounds.
 */
@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val bad: Color,
    val warn: Color,
    val onStatus: Color,       // text on good/warn/bad chips
    val series: List<Color>,
)

private fun appColors(scheme: ColorScheme, dark: Boolean): AppColors {
    val good = if (dark) Color(0xFF4CC38A) else Color(0xFF1B8A55)
    val bad = if (dark) Color(0xFFF06A6A) else Color(0xFFC62F2F)
    val warn = if (dark) Color(0xFFF0B35A) else Color(0xFFA86200)
    val series = if (dark) listOf(Color(0xFFF0B35A), Color(0xFFC58AF9), Color(0xFFF06A6A), Color(0xFF5FD3E0))
    else listOf(Color(0xFFA86200), Color(0xFF8A4FD1), Color(0xFFC62F2F), Color(0xFF00838F))
    return AppColors(
        bg = scheme.background,
        surface = scheme.surfaceContainer,
        surface2 = scheme.surfaceContainerHighest,
        border = scheme.outlineVariant,
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        good = good,
        bad = bad,
        warn = warn,
        onStatus = if (dark) Color(0xFF15171C) else Color.White,
        series = listOf(scheme.primary, good) + series,
    )
}

private val LocalAppColors = staticCompositionLocalOf { appColors(fallbackDark, dark = true) }

/** Current theme colors (formerly the desktop's fixed ui/theme.py palette). */
object Palette {
    val BG: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bg
    val SURFACE: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
    val SURFACE_2: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface2
    val BORDER: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.border
    val TEXT: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.text
    val MUTED: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.muted
    val ACCENT: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
    val ON_ACCENT: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onAccent
    val GOOD: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.good
    val BAD: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bad
    val WARN: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warn
    val ON_STATUS: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onStatus
    val SERIES: List<Color> @Composable @ReadOnlyComposable get() = LocalAppColors.current.series
}

// The original blue-on-charcoal palette, used when dynamic color is off or unavailable.
private val fallbackDark = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = Color(0xFF15171C),
    secondary = Color(0xFF4CC38A),
    onSecondary = Color(0xFF15171C),
    background = Color(0xFF15171C),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF15171C),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1E2128),
    onSurfaceVariant = Color(0xFF9097A6),
    surfaceContainerLowest = Color(0xFF111317),
    surfaceContainerLow = Color(0xFF1A1D23),
    surfaceContainer = Color(0xFF1E2128),
    surfaceContainerHigh = Color(0xFF23262E),
    surfaceContainerHighest = Color(0xFF272B34),
    outline = Color(0xFF4A505C),
    outlineVariant = Color(0xFF343944),
    error = Color(0xFFF06A6A),
)

private val fallbackLight = lightColorScheme(
    primary = Color(0xFF2F65C8),
    onPrimary = Color.White,
    secondary = Color(0xFF1B8A55),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF181B21),
    surface = Color(0xFFF7F8FB),
    onSurface = Color(0xFF181B21),
    surfaceVariant = Color(0xFFE8EBF1),
    onSurfaceVariant = Color(0xFF5A6170),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F7),
    surfaceContainer = Color(0xFFECEFF4),
    surfaceContainerHigh = Color(0xFFE6E9EF),
    surfaceContainerHighest = Color(0xFFE0E4EB),
    outline = Color(0xFF8A909C),
    outlineVariant = Color(0xFFD3D8E0),
    error = Color(0xFFC62F2F),
)

/** Text styles; colors are read from the current theme, so these are composable getters. */
object Type {
    val h1: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TEXT)
    val h2: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Palette.TEXT)
    val body: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 16.sp, color = Palette.TEXT, lineHeight = 23.sp)
    val muted: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 14.sp, color = Palette.MUTED, lineHeight = 19.sp)
    val bigStat: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.TEXT)
    val prompt: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Medium, color = Palette.TEXT, lineHeight = 54.sp)
    val answer: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Palette.TEXT)
    val timer: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = Palette.MUTED)
    val tag: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * Follows the system light/dark setting. With [dynamicColor] (and Android 12+) the scheme is
 * derived from the wallpaper (Material You); otherwise the app's own blue palette is used.
 * [fontScale] is the in-app "Text size" setting, applied on top of the system font scale.
 */
@Composable
fun MmtTheme(fontScale: Float, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && dynamicColorSupported -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> fallbackDark
        else -> fallbackLight
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
        LocalAppColors provides appColors(scheme, dark),
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
