package com.lastwave.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val WearPrimary = Color(0xFFC6F100)      // LastWave Neon Lime
val WearOnPrimary = Color(0xFF003800)
val WearSecondary = Color(0xFFFFB4A2)    // Warm Peach Accent
val WearOnSecondary = Color(0xFF561E14)
val WearBackground = Color(0xFF000000)   // Pure OLED Black
val WearOnBackground = Color(0xFFEEEEEE)
val WearSurface = Color(0xFF141414)
val WearOnSurface = Color(0xFFF0F0F0)
val WearSurfaceVariant = Color(0xFF242424)
val WearOnSurfaceVariant = Color(0xFFBDBDBD)

val WearColorScheme = ColorScheme(
    primary = WearPrimary,
    onPrimary = WearOnPrimary,
    secondary = WearSecondary,
    onSecondary = WearOnSecondary,
    background = WearBackground,
    onBackground = WearOnBackground,
    surfaceContainer = WearSurface,
    onSurface = WearOnSurface,
    surfaceContainerHigh = WearSurfaceVariant,
    onSurfaceVariant = WearOnSurfaceVariant
)

@Composable
fun LastWaveWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content
    )
}
