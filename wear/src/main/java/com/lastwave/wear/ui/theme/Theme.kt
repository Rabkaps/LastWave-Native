package com.lastwave.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val WearPrimary = Color(0xFFC6F100)               // LastWave Neon Lime
val WearOnPrimary = Color(0xFF142200)
val WearPrimaryContainer = Color(0xFF243300)
val WearOnPrimaryContainer = Color(0xFFD4FA48)

val WearSecondary = Color(0xFF82B1FF)             // Companion / Blue Phone Accent
val WearOnSecondary = Color(0xFF002F6C)
val WearSecondaryContainer = Color(0xFF173663)
val WearOnSecondaryContainer = Color(0xFFCEE5FF)

val WearTertiary = Color(0xFFFFB4A2)              // Lossless Coral Accent
val WearOnTertiary = Color(0xFF561E14)
val WearTertiaryContainer = Color(0xFF4C2720)
val WearOnTertiaryContainer = Color(0xFFFFDAD3)

val WearBackground = Color(0xFF000000)            // Pure OLED Black
val WearOnBackground = Color(0xFFEDEDED)

val WearSurfaceContainer = Color(0xFF181A1D)       // M3 Surface container
val WearSurfaceContainerHigh = Color(0xFF23272D)   // M3 High surface (cards/buttons)
val WearSurfaceContainerLow = Color(0xFF111315)    // M3 Low surface

val WearOnSurface = Color(0xFFF2F2F2)
val WearOnSurfaceVariant = Color(0xFFA5ABB2)
val WearOutline = Color(0xFF383C44)

val WearColorScheme = ColorScheme(
    primary = WearPrimary,
    onPrimary = WearOnPrimary,
    primaryContainer = WearPrimaryContainer,
    onPrimaryContainer = WearOnPrimaryContainer,
    secondary = WearSecondary,
    onSecondary = WearOnSecondary,
    secondaryContainer = WearSecondaryContainer,
    onSecondaryContainer = WearOnSecondaryContainer,
    tertiary = WearTertiary,
    onTertiary = WearOnTertiary,
    tertiaryContainer = WearTertiaryContainer,
    onTertiaryContainer = WearOnTertiaryContainer,
    background = WearBackground,
    onBackground = WearOnBackground,
    surfaceContainer = WearSurfaceContainer,
    surfaceContainerHigh = WearSurfaceContainerHigh,
    surfaceContainerLow = WearSurfaceContainerLow,
    onSurface = WearOnSurface,
    onSurfaceVariant = WearOnSurfaceVariant,
    outline = WearOutline
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
