package dev.russell.mmtrainer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** The desktop app's palette (ui/theme.py). */
object Palette {
    val BG = Color(0xFF15171C)
    val SURFACE = Color(0xFF1E2128)
    val SURFACE_2 = Color(0xFF272B34)
    val BORDER = Color(0xFF343944)
    val TEXT = Color(0xFFE6E8EE)
    val MUTED = Color(0xFF9097A6)
    val ACCENT = Color(0xFF6EA8FE)
    val GOOD = Color(0xFF4CC38A)
    val BAD = Color(0xFFF06A6A)
    val WARN = Color(0xFFF0B35A)
    val SERIES = listOf(Color(0xFF6EA8FE), Color(0xFF4CC38A), Color(0xFFF0B35A), Color(0xFFC58AF9),
        Color(0xFFF06A6A), Color(0xFF5FD3E0))
}

private val colors = darkColorScheme(
    primary = Palette.ACCENT,
    onPrimary = Palette.BG,
    secondary = Palette.GOOD,
    onSecondary = Palette.BG,
    background = Palette.BG,
    onBackground = Palette.TEXT,
    surface = Palette.BG,
    onSurface = Palette.TEXT,
    surfaceVariant = Palette.SURFACE,
    onSurfaceVariant = Palette.MUTED,
    surfaceContainer = Palette.SURFACE,
    surfaceContainerHigh = Palette.SURFACE_2,
    surfaceContainerHighest = Palette.SURFACE_2,
    surfaceContainerLow = Palette.SURFACE,
    outline = Palette.BORDER,
    outlineVariant = Palette.BORDER,
    error = Palette.BAD,
)

object Type {
    val h1 = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TEXT)
    val h2 = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Palette.TEXT)
    val body = TextStyle(fontSize = 16.sp, color = Palette.TEXT, lineHeight = 23.sp)
    val muted = TextStyle(fontSize = 14.sp, color = Palette.MUTED, lineHeight = 19.sp)
    val bigStat = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.TEXT)
    val prompt = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Medium, color = Palette.TEXT, lineHeight = 54.sp)
    val answer = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium, color = Palette.TEXT)
    val timer = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = Palette.MUTED)
    val tag = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.BG)
}

/** Dark theme; [fontScale] is the in-app "Text size" setting, applied on top of the system font scale. */
@Composable
fun MmtTheme(fontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
        MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
    }
}
