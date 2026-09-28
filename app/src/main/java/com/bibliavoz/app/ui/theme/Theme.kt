package com.bibliavoz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta "pergamino": cálida y de bajo contraste para leer largo rato sin cansar,
// con un bronce como color de acento.
private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5230),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E2CB),
    onPrimaryContainer = Color(0xFF2A1707),
    secondary = Color(0xFF4A6360),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E7E3),
    onSecondaryContainer = Color(0xFF0A211F),
    tertiary = Color(0xFF6B5B8E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFBF7F0),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE3D4),
    onSurfaceVariant = Color(0xFF4E4539),
    surfaceContainer = Color(0xFFF2EBE0),
    surfaceContainerHigh = Color(0xFFECE4D8),
    outline = Color(0xFF80766A),
    outlineVariant = Color(0xFFD3C8B8),
    error = Color(0xFF8F2F2A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410004),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8C08E),
    onPrimary = Color(0xFF432B10),
    primaryContainer = Color(0xFF5E4122),
    onPrimaryContainer = Color(0xFFF3E2CB),
    secondary = Color(0xFFB2CCC7),
    onSecondary = Color(0xFF1D3532),
    secondaryContainer = Color(0xFF334B48),
    onSecondaryContainer = Color(0xFFD6E7E3),
    tertiary = Color(0xFFD2C2F5),
    onTertiary = Color(0xFF3A2C5D),
    background = Color(0xFF14120E),
    onBackground = Color(0xFFE9E1D6),
    surface = Color(0xFF14120E),
    onSurface = Color(0xFFE9E1D6),
    surfaceVariant = Color(0xFF4E4539),
    onSurfaceVariant = Color(0xFFD2C4B2),
    surfaceContainer = Color(0xFF201D18),
    surfaceContainerHigh = Color(0xFF2B2721),
    outline = Color(0xFF9A8F81),
    outlineVariant = Color(0xFF4E4539),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690004),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD5),
)

/** @param themeMode 0 = seguir al sistema, 1 = claro, 2 = oscuro. */
@Composable
fun BibliaVozTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
