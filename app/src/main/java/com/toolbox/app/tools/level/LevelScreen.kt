package com.toolbox.app.tools.level

import android.hardware.Sensor
import android.view.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.AnnounceOnStart
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberSensorListener
import com.toolbox.app.ui.common.toDisplayAxes
import com.toolbox.app.ui.theme.GoogleColors
import com.toolbox.app.ui.theme.ink
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Within this many degrees on both axes counts as level. */
private const val LEVEL_DEG = 1f
/** Tilt that puts the bubble on the rim; beyond it the phone isn't lying on a surface, so it can't be zeroed. */
private const val RIM_DEG = 20f
private const val PREF_ZERO_ROLL = "level_zero_roll"
private const val PREF_ZERO_PITCH = "level_zero_pitch"

/** Left–right ([roll], positive when the right edge is high) and front–back ([pitch], top edge high) tilt in degrees. */
data class Tilt(val roll: Float = 0f, val pitch: Float = 0f) {
    operator fun minus(other: Tilt) = Tilt(roll - other.roll, pitch - other.pitch)

    val isLevel: Boolean get() = abs(roll) < LEVEL_DEG && abs(pitch) < LEVEL_DEG
    val steepest: Float get() = maxOf(abs(roll), abs(pitch))

    /** This tilt, measured in the phone's portrait axes, as seen on a screen turned by `rotation` (a `Surface.ROTATION_*`). */
    fun onDisplay(rotation: Int): Tilt = toDisplayAxes(roll, pitch, rotation).let { (r, p) -> Tilt(r, p) }
}

/** Tilt from a gravity or accelerometer reading, which points up, out of the raised edge. */
fun tiltOf(x: Float, y: Float, z: Float) = Tilt(
    roll = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat(),
    pitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat(),
)

/** "1.2°" for the size of a tilt; the status line gives its direction. Unsigned, so never "−0.0°". */
fun formatDegrees(deg: Float): String = "%.1f°".format(abs(deg))

/** What the bubble shows, in words: level, which edge or corner is high, or that the phone isn't lying flat. */
fun levelStatus(t: Tilt): String {
    if (t.steepest > RIM_DEG) return "Lay the phone flat to measure"
    if (t.isLevel) return "Level"
    val side = if (abs(t.roll) >= LEVEL_DEG) (if (t.roll > 0) "right" else "left") else null
    val end = if (abs(t.pitch) >= LEVEL_DEG) (if (t.pitch > 0) "Top" else "Bottom") else null
    return when {
        side != null && end != null -> "$end-$side corner is high"
        end != null -> "$end edge is high"
        else -> "${side!!.replaceFirstChar(Char::uppercase)} edge is high"
    }
}

/**
 * Surface level: lay the phone flat and centre the bubble. Uses gravity (or raw accelerometer) with light smoothing.
 * "Set as level" saves the current reading as zero, which cancels the 0.5–2° bias phone sensors commonly have.
 */
@Composable
fun LevelScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    var raw by remember { mutableStateOf(Tilt()) } // smoothed, in the phone's own portrait axes
    var rotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    var zeroRoll by rememberPref(PREF_ZERO_ROLL, 0f)
    var zeroPitch by rememberPref(PREF_ZERO_PITCH, 0f)
    val zeroed = zeroRoll != 0f || zeroPitch != 0f

    val view = LocalView.current
    val available = rememberSensorListener(Sensor.TYPE_GRAVITY, fallbackType = Sensor.TYPE_ACCELEROMETER) { e ->
        // Read per reading: a large screen can turn 180° without the screen being recreated.
        rotation = view.display?.rotation ?: Surface.ROTATION_0
        val t = tiltOf(e.values[0], e.values[1], e.values[2])
        raw = Tilt(raw.roll + (t.roll - raw.roll) * 0.2f, raw.pitch + (t.pitch - raw.pitch) * 0.2f)
    }
    // The zero is kept in the phone's axes, so it stays right if a large screen turns.
    val tilt = (raw - Tilt(zeroRoll, zeroPitch)).onDisplay(rotation)

    val levelColor = GoogleColors.Green.ink()
    val bubbleColor by animateColorAsState(if (tilt.isLevel) levelColor else MaterialTheme.colorScheme.primary, label = "bubble")
    val ring = MaterialTheme.colorScheme.outlineVariant
    val say = rememberAnnouncer()

    ToolScaffold(title = "Bubble Level", onBack = onBack) { padding ->
        ToolBody(padding) {
            if (!available) {
                Text("This device has no accelerometer.")
                return@ToolBody
            }
            AnnounceOnStart(tilt.isLevel, "Level")
            // Drawing only: the readings and status line below say what it shows.
            Canvas(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f).clearAndSetSemantics {}) {
                val r = size.minDimension / 2
                drawCircle(ring, r, style = Stroke(2.dp.toPx()))
                drawCircle(ring, r * 0.5f, style = Stroke(2.dp.toPx()))
                drawCircle(ring, r * 0.12f, style = Stroke(2.dp.toPx()))
                drawLine(ring, Offset(center.x - r, center.y), Offset(center.x + r, center.y))
                drawLine(ring, Offset(center.x, center.y - r), Offset(center.x, center.y + r))
                // The bubble drifts toward the raised edge. RIM_DEG of tilt reaches the rim.
                var dx = tilt.roll / RIM_DEG * r
                var dy = -tilt.pitch / RIM_DEG * r
                val dist = hypot(dx, dy)
                val max = r * 0.88f
                if (dist > max) { dx *= max / dist; dy *= max / dist }
                drawCircle(bubbleColor.copy(alpha = 0.85f), r * 0.11f, Offset(center.x + dx, center.y + dy))
            }
            Spacer(Modifier.height(24.dp))
            // The two readings go one above the other when large text makes them too wide to sit side by side.
            FlowRow(
                Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Axis("Left–right", tilt.roll)
                Axis("Front–back", tilt.pitch)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                levelStatus(tilt),
                style = MaterialTheme.typography.titleMedium,
                color = if (tilt.isLevel) levelColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { haptics.click(); zeroRoll = raw.roll; zeroPitch = raw.pitch; say("Calibrated") },
                    enabled = raw.steepest <= RIM_DEG,
                ) { Text("Set as level") }
                if (zeroed) {
                    TextButton(onClick = { haptics.click(); zeroRoll = 0f; zeroPitch = 0f; say("Calibration reset") }) { Text("Reset") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (zeroed) "Calibrated on a level surface. Reset to use the sensor as it is."
                else "Phone sensors are often a degree or two out. On a surface you know is level, tap Set as level.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}

@Composable
private fun Axis(label: String, deg: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val style = MaterialTheme.typography.displaySmall
        Text(
            formatDegrees(deg),
            style = style,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize),
        )
    }
}
