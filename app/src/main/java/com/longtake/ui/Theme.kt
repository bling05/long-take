package com.longtake.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.longtake.data.ThemeMode

// Warm dark theme by default: brown-tinted charcoal, cream text and one amber accent.

private val Dark = darkColorScheme(
    primary = Color(0xFFE6A55E),            // amber
    onPrimary = Color(0xFF2A1A08),
    primaryContainer = Color(0xFF4A3420),
    onPrimaryContainer = Color(0xFFF6D9B5),
    secondary = Color(0xFFB9A88F),
    onSecondary = Color(0xFF2A2218),
    secondaryContainer = Color(0xFF3A322A),
    onSecondaryContainer = Color(0xFFE9DCC8),
    tertiary = Color(0xFF9FB59A),           // muted sage
    background = Color(0xFF171412),
    onBackground = Color(0xFFEDE5D8),
    surface = Color(0xFF171412),
    onSurface = Color(0xFFEDE5D8),
    surfaceVariant = Color(0xFF2A2420),
    onSurfaceVariant = Color(0xFFB3A898),
    surfaceContainerLowest = Color(0xFF120F0E),
    surfaceContainerLow = Color(0xFF1C1815),
    surfaceContainer = Color(0xFF201B18),
    surfaceContainerHigh = Color(0xFF26201C),
    surfaceContainerHighest = Color(0xFF2E2722),
    outline = Color(0xFF5A5048),
    outlineVariant = Color(0xFF3A332D),
    error = Color(0xFFE8877A),
    errorContainer = Color(0xFF4A2420),
    onErrorContainer = Color(0xFFF6D3CD),
)

private val Light = lightColorScheme(
    primary = Color(0xFFA85A1F),            // burnt amber
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFF3DCC2),
    onPrimaryContainer = Color(0xFF3A1F08),
    secondary = Color(0xFF6E604F),
    secondaryContainer = Color(0xFFEAE0D2),
    onSecondaryContainer = Color(0xFF2A2218),
    tertiary = Color(0xFF5C7357),
    background = Color(0xFFF7F2EA),         // paper
    onBackground = Color(0xFF2A2420),       // ink
    surface = Color(0xFFF7F2EA),
    onSurface = Color(0xFF2A2420),
    surfaceVariant = Color(0xFFEDE5D9),
    onSurfaceVariant = Color(0xFF6B6055),
    surfaceContainerLowest = Color(0xFFFFFCF7),
    surfaceContainerLow = Color(0xFFF3EDE3),
    surfaceContainer = Color(0xFFEFE8DD),
    surfaceContainerHigh = Color(0xFFE9E1D5),
    surfaceContainerHighest = Color(0xFFE3DACD),
    outline = Color(0xFFB3A796),
    outlineVariant = Color(0xFFDCD2C3),
)

private val Serif = FontFamily.Serif
private val base = Typography()

/** Serif for titles (like a film journal), the default sans for everything you read at length. */
private val Type = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = Serif),
    headlineLarge = base.headlineLarge.copy(fontFamily = Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
)

private val Shape = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** True when the dark palette is in use (for the few places that pick colours by hand). */
val LocalIsDark = staticCompositionLocalOf { true }

@Composable
fun LongTakeTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Shape, content = content)
    }
}

object VerdictColors {
    val human = Color(0xFF8DB386)   // sage
    val ai = Color(0xFFE07A6A)      // soft red
    val unsure = Color(0xFFE6A55E)  // amber
    val none = Color(0xFF9A8F82)
}
