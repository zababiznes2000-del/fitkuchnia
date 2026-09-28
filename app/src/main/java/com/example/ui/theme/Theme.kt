package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FitKuchniaDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color(0xFF00390B),
    primaryContainer = NeonGreenContainer,
    onPrimaryContainer = OnNeonGreenContainer,
    secondary = NeonGreenBright,
    onSecondary = Color(0xFF003A0C),
    secondaryContainer = Color(0xFF0D2514),
    onSecondaryContainer = Color(0xFFB5FFBC),
    tertiary = TimeBlue,
    onTertiary = Color(0xFF00363D),
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun FitKuchniaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FitKuchniaDarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    FitKuchniaTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
