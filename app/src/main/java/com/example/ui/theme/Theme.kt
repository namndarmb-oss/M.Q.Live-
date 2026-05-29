package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LuxuryColorScheme = darkColorScheme(
    primary = LuxuryPrimary,
    onPrimary = LuxuryOnBg,
    secondary = LuxurySecondary,
    onSecondary = LuxuryOnBg,
    tertiary = LuxuryTertiary,
    onTertiary = LuxuryOnBg,
    background = LuxuryBg,
    onBackground = LuxuryOnBg,
    surface = LuxurySurface,
    onSurface = LuxuryOnSurface,
    surfaceVariant = LuxurySurfaceVariant,
    onSurfaceVariant = LuxuryOnSurface
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LuxuryColorScheme,
        typography = Typography,
        content = content
    )
}
