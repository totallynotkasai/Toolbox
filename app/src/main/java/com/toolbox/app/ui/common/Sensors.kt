package com.toolbox.app.ui.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Listens to [type] (or [fallbackType] if the device lacks it) while this composable is on screen and the app is
 * started, so sensors stop when the user leaves the screen or backgrounds the app.
 *
 * Callbacks arrive on the main thread; check `event.sensor.type` if a fallback is in play.
 * Returns `false` if neither sensor exists, so the caller can show a "not supported" message.
 */
@Composable
fun rememberSensorListener(
    type: Int,
    rate: Int = SensorManager.SENSOR_DELAY_GAME,
    fallbackType: Int? = null,
    enabled: Boolean = true,
    onAccuracyChanged: (accuracy: Int) -> Unit = {},
    onEvent: (SensorEvent) -> Unit,
): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember(manager, type, fallbackType) {
        manager.getDefaultSensor(type) ?: fallbackType?.let { manager.getDefaultSensor(it) }
    }
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnAccuracy by rememberUpdatedState(onAccuracyChanged)

    if (sensor != null && enabled) {
        LifecycleStartEffect(manager, sensor, rate) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) = currentOnEvent(e)
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = currentOnAccuracy(accuracy)
            }
            manager.registerListener(listener, sensor, rate)
            onStopOrDispose { manager.unregisterListener(listener) }
        }
    }
    return sensor != null
}

/**
 * Turns an accelerometer or gravity reading's [x] and [y] (device axes: x to the right of the portrait screen, y up
 * it) into the same axes on the screen as shown, for a display turned by [rotation], a `Surface.ROTATION_*` value.
 * Only matters where the portrait lock is ignored, as on screens 600dp and wider; a portrait screen passes through.
 */
fun toDisplayAxes(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
    Surface.ROTATION_90 -> -y to x
    Surface.ROTATION_180 -> -x to -y
    Surface.ROTATION_270 -> y to -x
    else -> x to y
}
