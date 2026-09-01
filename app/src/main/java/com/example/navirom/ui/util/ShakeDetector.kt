package com.example.navirom.ui.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

class ShakeDetector(
    private val onShake2Seconds: () -> Unit
) : SensorEventListener {

    private var firstShakeTime: Long = 0L
    private var lastShakeTime: Long = 0L
    private var isTriggered: Boolean = false

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.1f
        private const val SHAKE_SLOP_TIME_MS = 600L
        private const val REQUIRED_SHAKE_DURATION_MS = 1000L // ~1 second of sustained shaking
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // gForce will be close to 1 when there is no movement.
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        val now = System.currentTimeMillis()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            if (firstShakeTime == 0L || (now - lastShakeTime > SHAKE_SLOP_TIME_MS)) {
                // Start a new shaking sequence
                firstShakeTime = now
                isTriggered = false
            }

            lastShakeTime = now

            // Check if shaking has continued for at least 2 seconds
            val duration = now - firstShakeTime
            if (duration >= REQUIRED_SHAKE_DURATION_MS && !isTriggered) {
                isTriggered = true
                firstShakeTime = 0L
                lastShakeTime = 0L
                onShake2Seconds()
            }
        } else {
            // If user stopped shaking for more than the slop window, reset
            if (firstShakeTime != 0L && (now - lastShakeTime > SHAKE_SLOP_TIME_MS)) {
                firstShakeTime = 0L
                lastShakeTime = 0L
                isTriggered = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun RememberShakeDetector(
    enabled: Boolean = true,
    onShake2Seconds: () -> Unit
) {
    val context = LocalContext.current
    val currentOnShake = rememberUpdatedState(onShake2Seconds)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(enabled, context, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val detector = ShakeDetector {
            currentOnShake.value()
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (sensorManager != null && accelerometer != null) {
                    sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_UI)
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                if (sensorManager != null) {
                    sensorManager.unregisterListener(detector)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (sensorManager != null) {
                sensorManager.unregisterListener(detector)
            }
        }
    }
}

