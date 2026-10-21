package com.lastwave.wear.ui.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Ambient mode state indicating whether the watch screen is in low-power Always-On Display mode.
 */
data class AmbientState(
    val isAmbient: Boolean = false,
    val burnInProtection: Boolean = false
)

val LocalAmbientState = compositionLocalOf { AmbientState() }

@Composable
fun isAmbientMode(): Boolean = LocalAmbientState.current.isAmbient
