package com.example.navirom.ui.util

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

class NaviromHaptics(
    private val hapticFeedback: HapticFeedback,
    private val vibrator: Vibrator?
) {
    /**
     * Ultra-light tick for tab changes, filter chips, segmented buttons, slider steps.
     */
    fun tick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(10, 30))
                }
            } else {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Exception) {
            try {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }

    /**
     * Crisp click for primary selections (track click, album click, button tap).
     */
    fun click() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Exception) {
            try {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }

    /**
     * Medium tactile feedback for playback controls (Play/Pause, Shuffle, Repeat toggle).
     */
    fun toggle() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {
            try {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
        }
    }

    /**
     * Satisfying double-click haptic for favoriting a track, playlist creation, connection success.
     */
    fun success() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                } else {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 15, 40, 20), -1))
                }
            } else {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {
            try {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
        }
    }

    /**
     * Strong feedback for long-press menus, drag actions.
     */
    fun longPress() {
        try {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Alert double-pulse for connection failure or errors.
     */
    fun error() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1))
            } else {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun rememberNaviromHaptics(): NaviromHaptics {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    return remember(context, hapticFeedback) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        NaviromHaptics(hapticFeedback, vibrator)
    }
}
