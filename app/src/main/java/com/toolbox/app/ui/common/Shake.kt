package com.toolbox.app.ui.common

import android.hardware.Sensor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.sqrt

/**
 * Calls [onShake] when the phone is shaken, with the peak linear acceleration of the shake in m/s²
 * (about 12 for a light shake, 30+ for a hard one), so callers like the physics coin can scale their throw.
 *
 * Uses the linear-acceleration sensor, or [AccelShakeDetector] on the raw accelerometer where that is missing.
 * Does nothing on devices without either sensor.
 */
@Composable
fun OnShake(enabled: Boolean = true, onShake: (strength: Float) -> Unit) {
    val currentOnShake by rememberUpdatedState(onShake)
    // Fresh state each time shaking is re-enabled, so a stale gravity estimate can't fire a false shake.
    val detector = remember(enabled) { ShakeDetector() }
    val accelDetector = remember(enabled) { AccelShakeDetector() }

    rememberSensorListener(
        type = Sensor.TYPE_LINEAR_ACCELERATION,
        fallbackType = Sensor.TYPE_ACCELEROMETER,
        enabled = enabled,
    ) { e ->
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val timeMs = e.timestamp / 1_000_000
        val strength = if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelDetector.onSample(x, y, z, timeMs)
        } else {
            detector.onSample(sqrt(x * x + y * y + z * z), timeMs)
        }
        strength?.let(currentOnShake)
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

/**
 * Shake logic for phones with only an accelerometer, fed one raw reading (m/s², gravity included) per sample.
 *
 * Gravity is estimated with a low-pass filter and taken away, which leaves the phone's movement. Turning the phone
 * over moves gravity faster than the filter follows, though, so what's left looks like a shake too, and used to fire
 * one. A shake swings back and forth while turning over pushes one way, so a shake here needs two swings: the
 * movement rises past [threshold], falls back below half of it, then rises past it again pointing the other way
 * within [gapMs]. That second swing reports the stronger swing's peak, and further shakes are ignored for [cooldownMs].
 *
 * Tuned on simulated turns (150 ms to half a second, with a lift and the sensor off the turning axis) and shakes
 * (12 to 30 m/s² at 2 to 5 Hz): no turn fires and every shake does, about one swing later than the sensor path.
 */
class AccelShakeDetector(
    private val threshold: Float = 10f,
    private val gapMs: Long = 300,
    private val cooldownMs: Long = 800,
    private val alpha: Float = 0.9f,
) {
    private val gravity = FloatArray(3)
    private var seeded = false

    private var inSwing = false
    private val swing = FloatArray(3) // Direction at the current swing's peak.
    private var swingPeak = 0f
    private val last = FloatArray(3)  // Direction at the last finished swing's peak.
    private var lastPeak = 0f
    private var lastEnd = NONE
    private var lastFired = NONE

    /** Returns the shake's strength when the second swing of a shake starts, otherwise null. */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long): Float? {
        if (!seeded) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            seeded = true
            return null
        }
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z
        val mx = x - gravity[0]
        val my = y - gravity[1]
        val mz = z - gravity[2]
        val size = sqrt(mx * mx + my * my + mz * mz)

        if (inSwing) {
            if (size > swingPeak) setSwing(mx, my, mz, size)
            if (size < threshold / 2) {
                inSwing = false
                swing.copyInto(last)
                lastPeak = swingPeak
                lastEnd = timeMs
            }
            return null
        }
        if (size < threshold) return null
        inSwing = true
        setSwing(mx, my, mz, size)
        val reversed = lastEnd != NONE && timeMs - lastEnd <= gapMs &&
            swing[0] * last[0] + swing[1] * last[1] + swing[2] * last[2] < 0
        if (!reversed || (lastFired != NONE && timeMs - lastFired < cooldownMs)) return null
        lastFired = timeMs
        return maxOf(lastPeak, size)
    }

    private fun setSwing(x: Float, y: Float, z: Float, size: Float) {
        swing[0] = x / size; swing[1] = y / size; swing[2] = z / size
        swingPeak = size
    }

    private companion object { const val NONE = Long.MIN_VALUE }
}

/** The app-wide "shake to decide" switch (default on), set in the home screen's Settings sheet. */
@Composable
fun rememberShakeEnabled(): MutableState<Boolean> = rememberPref(Prefs.SHAKE_ENABLED, true)

