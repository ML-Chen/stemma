package app.stemma.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.stemma.data.ThemeChoice

/** The concrete theme in use after resolving [ThemeChoice.SYSTEM]. Entry pages use it to pick their CSS theme. */
enum class ReadingTheme(val cssClass: String, val isDark: Boolean) {
    LIGHT("wh-light", false),
    SEPIA("wh-sepia", false),
    DARK("wh-dark", true),
    BLACK("wh-black", true),
}

val LocalReadingTheme = staticCompositionLocalOf { ReadingTheme.LIGHT }

@Composable
fun ThemeChoice.resolve(): ReadingTheme = when (this) {
    ThemeChoice.SYSTEM -> if (isSystemInDarkTheme()) ReadingTheme.DARK else ReadingTheme.LIGHT
    ThemeChoice.LIGHT -> ReadingTheme.LIGHT
    ThemeChoice.SEPIA -> ReadingTheme.SEPIA
    ThemeChoice.DARK -> ReadingTheme.DARK
    ThemeChoice.BLACK -> ReadingTheme.BLACK
}

// Colours follow the Wikimedia palette used by the Wikipedia app.
private val Light = lightColorScheme(
    primary = Color(0xFF3366CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF3FF),
    onPrimaryContainer = Color(0xFF1A3D80),
    secondary = Color(0xFF54595D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAECF0),
    onSecondaryContainer = Color(0xFF202122),
    tertiary = Color(0xFFAC6600),
    tertiaryContainer = Color(0xFFFEF6E7),
    onTertiaryContainer = Color(0xFF5C3600),
    background = Color.White,
    onBackground = Color(0xFF202122),
    surface = Color.White,
    onSurface = Color(0xFF202122),
    surfaceVariant = Color(0xFFF8F9FA),
    onSurfaceVariant = Color(0xFF54595D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF8F9FA),
    surfaceContainerHigh = Color(0xFFF1F3F5),
    surfaceContainerHighest = Color(0xFFEAECF0),
    outline = Color(0xFFA2A9B1),
    outlineVariant = Color(0xFFEAECF0),
    error = Color(0xFFD73333),
)

private val Sepia = lightColorScheme(
    primary = Color(0xFF3366CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E0CF),
    onPrimaryContainer = Color(0xFF1A3D80),
    secondary = Color(0xFF646059),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1DAD1),
    onSecondaryContainer = Color(0xFF222222),
    tertiary = Color(0xFF9A5B00),
    tertiaryContainer = Color(0xFFF0E2C8),
    onTertiaryContainer = Color(0xFF4E2F00),
    background = Color(0xFFF8F1E3),
    onBackground = Color(0xFF222222),
    surface = Color(0xFFF8F1E3),
    onSurface = Color(0xFF222222),
    surfaceVariant = Color(0xFFF0E6D6),
    onSurfaceVariant = Color(0xFF646059),
    surfaceContainerLowest = Color(0xFFFBF6EC),
    surfaceContainerLow = Color(0xFFF3EBDC),
    surfaceContainer = Color(0xFFF0E6D6),
    surfaceContainerHigh = Color(0xFFEADFCC),
    surfaceContainerHighest = Color(0xFFE1D6C3),
    outline = Color(0xFFB4ADA2),
    outlineVariant = Color(0xFFE1DAD1),
    error = Color(0xFFD73333),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF88A3E8),
    onPrimary = Color(0xFF0F2252),
    primaryContainer = Color(0xFF233666),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFA2A9B1),
    onSecondary = Color(0xFF202122),
    secondaryContainer = Color(0xFF3A3D42),
    onSecondaryContainer = Color(0xFFEAECF0),
    tertiary = Color(0xFFE8B058),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFFCE3B8),
    background = Color(0xFF1F2023),
    onBackground = Color(0xFFEAECF0),
    surface = Color(0xFF1F2023),
    onSurface = Color(0xFFEAECF0),
    surfaceVariant = Color(0xFF27292D),
    onSurfaceVariant = Color(0xFFA2A9B1),
    surfaceContainerLowest = Color(0xFF18191B),
    surfaceContainerLow = Color(0xFF232427),
    surfaceContainer = Color(0xFF27292D),
    surfaceContainerHigh = Color(0xFF2E3034),
    surfaceContainerHighest = Color(0xFF36393E),
    outline = Color(0xFF72777D),
    outlineVariant = Color(0xFF3A3D42),
    error = Color(0xFFFF6E6E),
)

private val Black = Dark.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121314),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0D),
    surfaceContainer = Color(0xFF121314),
    surfaceContainerHigh = Color(0xFF1A1B1D),
    surfaceContainerHighest = Color(0xFF222326),
    outlineVariant = Color(0xFF2A2C30),
)

fun ReadingTheme.colorScheme(): ColorScheme = when (this) {
    ReadingTheme.LIGHT -> Light
    ReadingTheme.SEPIA -> Sepia
    ReadingTheme.DARK -> Dark
    ReadingTheme.BLACK -> Black
}

/** Serif for headwords and screen titles, like the Wikipedia app's article titles. */
val Serif = FontFamily.Serif

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.Normal),
    headlineLarge = base.headlineLarge.copy(fontFamily = Serif),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif),
    titleLarge = base.titleLarge.copy(fontFamily = Serif, fontSize = 24.sp),
)

/** Style for a word in a list row. */
val WordRowStyle = TextStyle(fontFamily = Serif, fontSize = 20.sp, lineHeight = 26.sp)

@Composable
fun StemmaTheme(theme: ReadingTheme, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalReadingTheme provides theme) {
        MaterialTheme(colorScheme = theme.colorScheme(), typography = AppTypography, content = content)
    }
}
