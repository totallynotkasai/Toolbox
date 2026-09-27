package com.toolbox.app.ui.common

import android.hardware.Sensor
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

/**
 * Calls [onShake] when the phone is shaken, with the peak linear acceleration of the shake in m/s²
 * (about 12 for a light shake, 30+ for a hard one), so callers like the physics coin can scale their throw.
 *
 * Uses the linear-acceleration sensor, or the accelerometer minus a low-passed gravity estimate where that is missing.
 * Does nothing on devices without either sensor.
 */
@Composable
fun OnShake(enabled: Boolean = true, onShake: (strength: Float) -> Unit) {
    val currentOnShake by rememberUpdatedState(onShake)
    // Fresh state each time shaking is re-enabled, so a stale gravity estimate can't fire a false shake.
    val detector = remember(enabled) { ShakeDetector() }
    val gravity = remember(enabled) { GravityFilter() }

    rememberSensorListener(
        type = Sensor.TYPE_LINEAR_ACCELERATION,
        fallbackType = Sensor.TYPE_ACCELEROMETER,
        enabled = enabled,
    ) { e ->
        var (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity.update(x, y, z)
            x -= gravity.x; y -= gravity.y; z -= gravity.z
        }
        detector.onSample(sqrt(x * x + y * y + z * z), e.timestamp / 1_000_000)?.let(currentOnShake)
    }
}

/**
 * Pure shake logic, fed one acceleration magnitude per sensor sample.
 *
 * Once a sample crosses [threshold] it keeps watching for up to [peakWindowMs] (or until the reading drops back
 * below the threshold) to capture the peak, then reports it. Further shakes are ignored for [cooldownMs].
 */
class ShakeDetector(
    private val threshold: Float = 12f,
    private val cooldownMs: Long = 800,
    private val peakWindowMs: Long = 120,
) {
    private var peak = 0f
    private var peakStart = NONE
    private var lastFired = NONE

    /** Returns the shake's peak strength when a shake completes, otherwise null. */
    fun onSample(magnitude: Float, timeMs: Long): Float? {
        if (peakStart != NONE) {
            if (magnitude > peak) peak = magnitude
            if (magnitude < threshold || timeMs - peakStart >= peakWindowMs) {
                peakStart = NONE
                lastFired = timeMs
                return peak
            }
            return null
        }
        if (lastFired != NONE && timeMs - lastFired < cooldownMs) return null
        if (magnitude >= threshold) {
            peakStart = timeMs
            peak = magnitude
        }
        return null
    }

    private companion object { const val NONE = Long.MIN_VALUE }
}

/** Low-pass estimate of gravity from raw accelerometer readings; seeded by the first sample. */
private class GravityFilter(private val alpha: Float = 0.8f) {
    var x = 0f; var y = 0f; var z = 0f
    private var seeded = false

    fun update(ax: Float, ay: Float, az: Float) {
        if (!seeded) { x = ax; y = ay; z = az; seeded = true; return }
        x = alpha * x + (1 - alpha) * ax
        y = alpha * y + (1 - alpha) * ay
        z = alpha * z + (1 - alpha) * az
    }
}

/** The shared "shake to decide" switch (default on), so every screen with a shake toggle stays in step. */
@Composable
fun rememberShakeEnabled(): MutableState<Boolean> = rememberPref(Prefs.SHAKE_ENABLED, true)

/**
 * Top-bar toggle for [rememberShakeEnabled]. [action] finishes the sentence "Shake to …" (e.g. "roll"),
 * which is used for the accessibility label and the confirmation toast.
 */
@Composable
fun ShakeToggleButton(shake: MutableState<Boolean>, action: String) {
    val context = LocalContext.current
    IconToggleButton(
        checked = shake.value,
        onCheckedChange = {
            shake.value = it
            Toast.makeText(context, "Shake to $action: ${if (it) "on" else "off"}", Toast.LENGTH_SHORT).show()
        },
    ) {
        Icon(
            // The motion sensor, not a phone, so it isn't mistaken for the vibration switch beside it.
            if (shake.value) Icons.Filled.Sensors else Icons.Filled.SensorsOff,
            contentDescription = "Shake to $action",
        )
    }
}
