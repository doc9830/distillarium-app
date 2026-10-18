package ru.distillarium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD89A63),
    onPrimary = Color(0xFF2A1608),
    primaryContainer = Color(0xFF5A3418),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFA8C3A9),
    onSecondary = Color(0xFF142016),
    secondaryContainer = Color(0xFF344B37),
    onSecondaryContainer = Color(0xFFC5E2C6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E2DC),
    surface = Color(0xFF1B1B1B),
    onSurface = Color(0xFFE8E2DC),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCFC6BC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A4B1F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2A1608),
    secondary = Color(0xFF3F6B4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5E2C6),
    onSecondaryContainer = Color(0xFF142016),
    background = Color(0xFFFFFBF6),
    onBackground = Color(0xFF211A15),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF211A15),
    surfaceVariant = Color(0xFFF0E4D8),
    onSurfaceVariant = Color(0xFF51443A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun DistillariumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
