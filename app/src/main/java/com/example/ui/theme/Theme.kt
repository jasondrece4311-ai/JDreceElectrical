package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AiGemini,
    onPrimary = Color.White,
    primaryContainer = CadSurfaceVariant,
    onPrimaryContainer = Color.White,
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    tertiary = ElectricAmber,
    background = CadDarkBackground,
    onBackground = TextPrimary,
    surface = CadSurface,
    onSurface = TextPrimary,
    surfaceVariant = CadSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = IndustrialStopRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
