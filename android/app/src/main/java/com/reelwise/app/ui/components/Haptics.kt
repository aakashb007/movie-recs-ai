package com.reelwise.app.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Short, distinct vibrations for the circle menu. Uses the Vibrator directly (not view haptics)
 * so the ticks are felt even on phones where "touch feedback" is set low.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** The menu opened: a firm thump. */
    fun open() = play(VibrationEffect.EFFECT_HEAVY_CLICK, 28, 220)

    /** The finger moved onto another petal: a light tick. */
    fun tick() = play(VibrationEffect.EFFECT_TICK, 10, 90)

    /** An option was chosen. */
    fun confirm() = play(VibrationEffect.EFFECT_CLICK, 18, 170)

    private fun play(predefined: Int, fallbackMs: Long, amplitude: Int) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(predefined))
            } else {
                v.vibrate(VibrationEffect.createOneShot(fallbackMs, amplitude))
            }
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}
