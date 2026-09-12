package com.makemission.folio.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Ambient light sensor reader (§5 Colorimetric Contrast Optimization).
 *
 * Reads device's ambient light sensor (TYPE_LIGHT) and exposes smoothed lux.
 * Handles devices without sensor gracefully — returns null (fallback to fixed
 * FolioTheme colors, no crash).
 *
 * Smoothing: exponential moving average (alpha 0.15) + throttle to avoid
 * jarring flicker as spec requires smooth/gradual shifts.
 */
@Composable
fun rememberAmbientLightLux(
    enabled: Boolean,
    smoothingAlpha: Float = 0.15f,
): State<Float?> {
    val context = LocalContext.current
    val luxState = remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            luxState.value = null
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (sensorManager == null || lightSensor == null) {
            // Graceful fallback — no sensor on this device (emulator, etc.)
            luxState.value = null
            return@DisposableEffect onDispose {}
        }

        var currentSmoothed: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.sensor.type != Sensor.TYPE_LIGHT) return
                val raw = event.values.firstOrNull() ?: return
                // Clamp to valid range and apply EMA for smoothness
                val clamped = raw.coerceIn(0f, 10000f)
                currentSmoothed = if (currentSmoothed == null) {
                    clamped
                } else {
                    // EMA: smoothed = alpha*new + (1-alpha)*old
                    smoothingAlpha * clamped + (1f - smoothingAlpha) * (currentSmoothed ?: clamped)
                }
                // Performance: throttle tiny fluctuations — only publish if change exceeds
                // perceptible threshold (~2% or ~5 lux) to avoid retriggering adaptivePair
                // + animateColorAsState on every sensor tick (SENSOR_DELAY_NORMAL ~200ms).
                val prev = luxState.value
                val next = currentSmoothed
                if (prev == null || next == null || kotlin.math.abs(next - prev) > 5f || kotlin.math.abs(next - prev) / (prev.coerceAtLeast(1f)) > 0.02f) {
                    luxState.value = next
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        try {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (_: Exception) {
            // Some devices throw on register — fallback gracefully
            luxState.value = null
        }

        onDispose {
            try {
                sensorManager.unregisterListener(listener)
            } catch (_: Exception) { }
        }
    }

    return luxState
}

/**
 * Non-composable helper for ViewModel or imperative checks.
 * Returns true if device has ambient light sensor.
 */
fun hasAmbientLightSensor(context: Context): Boolean {
    return try {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
    } catch (_: Exception) {
        false
    }
}
