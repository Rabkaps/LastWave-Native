package com.lastwave.wear.ui.rotary

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlin.math.abs

/**
 * Attaches rotary crown scroll detection with tactile hardware tick vibration
 */
fun Modifier.withRotaryHaptics(
    context: Context,
    onValueDelta: (Float) -> Unit
): Modifier {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun performTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    var accumulatedDelta = 0f
    val tickThreshold = 10f

    return this.onRotaryScrollEvent { event ->
        val delta = event.verticalScrollPixels
        accumulatedDelta += delta
        if (abs(accumulatedDelta) >= tickThreshold) {
            performTick()
            accumulatedDelta = 0f
        }
        onValueDelta(delta)
        true
    }
}
