package ru.distillarium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Палитра: тёплый графит и медь. Никаких чистых чёрных и «кислотных» акцентов:
 * фон и поверхности различаются на пару шагов, акцент используется точечно.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE6B183),
    onPrimary = Color(0xFF2B1708),
    primaryContainer = Color(0xFF4A321D),
    onPrimaryContainer = Color(0xFFFFE2C6),
    secondary = Color(0xFFA9C4AF),
    onSecondary = Color(0xFF122016),
    secondaryContainer = Color(0xFF2C3E31),
    onSecondaryContainer = Color(0xFFD6E9D9),
    tertiary = Color(0xFFC7BFA8),
    onTertiary = Color(0xFF262216),
    tertiaryContainer = Color(0xFF3C392C),
    onTertiaryContainer = Color(0xFFEAE3D1),
    background = Color(0xFF0F110F),
    onBackground = Color(0xFFE9E6E0),
    surface = Color(0xFF0F110F),
    onSurface = Color(0xFFE9E6E0),
    surfaceVariant = Color(0xFF232722),
    onSurfaceVariant = Color(0xFFB8BEB5),
    surfaceContainerLowest = Color(0xFF0B0D0B),
    surfaceContainerLow = Color(0xFF171A17),
    surfaceContainer = Color(0xFF1C201C),
    surfaceContainerHigh = Color(0xFF232723),
    surfaceContainerHighest = Color(0xFF2B302B),
    outline = Color(0xFF747B73),
    outlineVariant = Color(0xFF363B36),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A1B15),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFE9E6E0),
    inverseOnSurface = Color(0xFF1C201C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C4F24),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E0CB),
    onPrimaryContainer = Color(0xFF301A0A),
    secondary = Color(0xFF48664F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBDF),
    onSecondaryContainer = Color(0xFF122016),
    tertiary = Color(0xFF655F50),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE6D7),
    onTertiaryContainer = Color(0xFF211D14),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1D1C19),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF1D1C19),
    surfaceVariant = Color(0xFFE8E5DD),
    onSurfaceVariant = Color(0xFF5B5F57),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0EEE8),
    surfaceContainerHigh = Color(0xFFE9E6DF),
    surfaceContainerHighest = Color(0xFFE2DFD7),
    outline = Color(0xFF8A8F86),
    outlineVariant = Color(0xFFD8D5CD),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF31302D),
    inverseOnSurface = Color(0xFFF4F1EB),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.05.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)


@Composable
fun DistillariumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
