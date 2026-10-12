package com.toolbox.app.tools.compass

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.AnnounceOnStart
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberSensorListener
import com.toolbox.app.ui.theme.GoogleColors
import com.toolbox.app.ui.theme.ink
import com.toolbox.app.ui.theme.isLight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Within this many degrees of a marked bearing counts as "on course". */
private const val ON_COURSE_DEG = 3f

/**
 * Magnetic compass. Uses the fused rotation-vector sensor where there is one (steadier, and unaffected by the phone
 * shaking), otherwise accelerometer + magnetometer. The magnetometer is always read too, for its calibration
 * accuracy and to spot magnetic interference.
 */
@Composable
fun CompassScreen(onBack: () -> Unit) {
    val view = LocalView.current
    val haptics = rememberHaptics()

    val filter = remember { HeadingFilter() }
    val ticker = remember { CardinalTicker() }
    val matrix = remember { FloatArray(9) }
    val remapped = remember { FloatArray(9) }
    val gravity = remember { FloatArray(3) }
    val geomag = remember { FloatArray(3) }
    var haveGravity by remember { mutableStateOf(false) }
    var haveGeomag by remember { mutableStateOf(false) }

    var heading by remember { mutableStateOf<Float?>(null) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var fieldStrength by remember { mutableFloatStateOf(45f) }
    var errorDeg by remember { mutableStateOf<Float?>(null) } // Rotation vector's own estimate, where reported.
    var bearing by rememberSaveable { mutableStateOf<Float?>(null) }

    fun publish() {
        val (x, y) = when (view.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, x, y, remapped)
        val raw = headingFromMatrix(remapped) ?: return
        val smooth = filter.update(raw)
        heading = smooth
        if (ticker.update(smooth) != null) haptics.tick()
    }

    val hasRotationVector = rememberSensorListener(Sensor.TYPE_ROTATION_VECTOR) { e ->
        SensorManager.getRotationMatrixFromVector(matrix, e.values)
        // values[4] is the estimated heading accuracy in radians; -1 or 0 when the device doesn't report it.
        errorDeg = e.values.getOrNull(4)?.takeIf { it > 0f }?.let { Math.toDegrees(it.toDouble()).toFloat() }
        publish()
    }
    val hasAccelerometer = rememberSensorListener(Sensor.TYPE_ACCELEROMETER, enabled = !hasRotationVector) { e ->
        lowPass(e.values, gravity, if (haveGravity) 0.15f else 1f); haveGravity = true
        if (haveGeomag && SensorManager.getRotationMatrix(matrix, null, gravity, geomag)) publish()
    }
    val hasMagnetometer = rememberSensorListener(
        Sensor.TYPE_MAGNETIC_FIELD,
        rate = if (hasRotationVector) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME,
        onAccuracyChanged = { accuracy = it },
    ) { e ->
        accuracy = e.accuracy
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        fieldStrength += (sqrt(x * x + y * y + z * z) - fieldStrength) * 0.1f
        if (!hasRotationVector) { lowPass(e.values, geomag, if (haveGeomag) 0.3f else 1f); haveGeomag = true }
    }
    val supported = hasMagnetometer && (hasRotationVector || hasAccelerometer)

    ToolScaffold(title = "Compass", onBack = onBack) { padding ->
        if (!supported) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This device has no compass sensor.", textAlign = TextAlign.Center)
            }
            return@ToolScaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Leave room under the dial for the reading and the bearing controls; shrink the dial in landscape.
            val dialSize = minOf(maxWidth - 48.dp, maxHeight - 260.dp, 360.dp).coerceAtLeast(180.dp)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CalibrationBanner(
                    visible = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                    interference = isFieldDisturbed(fieldStrength),
                )
                Spacer(Modifier.height(8.dp))
                Dial(heading ?: 0f, bearing, Modifier.size(dialSize).semantics {
                    contentDescription = heading?.let { "Compass, heading ${spokenHeading(it)}" } ?: "Compass, waiting for a reading"
                })
                Spacer(Modifier.height(16.dp))
                Reading(heading, errorDeg)
                Spacer(Modifier.height(12.dp))
                BearingControls(heading, bearing, onMark = { haptics.click(); bearing = heading?.let { roundedDegrees(it).toFloat() } },
                    onClear = { bearing = null })
            }
        }
    }
}

private fun lowPass(input: FloatArray, output: FloatArray, alpha: Float) {
    for (i in 0..2) output[i] += (input[i] - output[i]) * alpha
}

@Composable
private fun Reading(heading: Float?, errorDeg: Float?) {
    val style = MaterialTheme.typography.displayMedium
    Text(
        heading?.let { "${roundedDegrees(it)}° ${compassPoint(it)}" } ?: "—",
        style = style,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize),
    )
    val note = buildString {
        append("Magnetic north")
        if (errorDeg != null && errorDeg < 90f) append("  ·  ±${errorDeg.roundToInt()}°")
    }
    Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BearingControls(heading: Float?, bearing: Float?, onMark: () -> Unit, onClear: () -> Unit) {
    if (bearing == null) {
        FilledTonalButton(onClick = onMark, enabled = heading != null) {
            Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Mark this bearing")
        }
        return
    }
    val turn = heading?.let { shortestDelta(it, bearing) }
    val onCourse = turn != null && abs(turn) <= ON_COURSE_DEG
    AnnounceOnStart(onCourse, "On course")
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Takes what Clear leaves, so large text wraps here rather than pushing Clear off the screen.
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Bearing ${roundedDegrees(bearing)}° ${compassPoint(bearing)}", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    turn == null -> ""
                    onCourse -> "On course"
                    turn > 0 -> "Turn ${turn.roundToInt()}° right"
                    else -> "Turn ${(-turn).roundToInt()}° left"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (onCourse) GoogleColors.Green.ink() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClear, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Clear")
        }
    }
}

/** Tells the user to wave the phone in a figure-8, with a small animated figure-8 to show the motion. */
@Composable
private fun CalibrationBanner(visible: Boolean, interference: Boolean) {
    AnimatedVisibility(visible || interference, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FigureEight(MaterialTheme.colorScheme.onTertiaryContainer, Modifier.size(width = 56.dp, height = 32.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        if (visible) "Compass needs calibrating" else "Magnetic interference",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        if (visible) "Move your phone in a figure-8 a few times."
                        else "Move away from magnets, speakers, laptops or metal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun FigureEight(color: Color, modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "fig8").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(modifier) {
        // Lemniscate of Gerono: x = sin(u), y = sin(u)cos(u).
        fun point(u: Double) = Offset(
            center.x + (size.width / 2 - 4.dp.toPx()) * sin(u).toFloat(),
            center.y + (size.height - 8.dp.toPx()) * (sin(u) * cos(u)).toFloat(),
        )
        val path = Path()
        for (i in 0..64) {
            val p = point(2 * PI * i / 64)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color.copy(alpha = 0.4f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), point(2 * PI * t))
    }
}

@Composable
private fun Dial(heading: Float, bearing: Float?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val face = scheme.surfaceContainerHigh
    val rim = scheme.outlineVariant
    val ink = scheme.onSurface
    val minor = scheme.onSurfaceVariant
    val bearingColor = GoogleColors.Blue
    // A darker grey in light mode, so the south half of the needle stands out from the face.
    val south = if (scheme.isLight) Color(0xFF6B7177) else Color(0xFF9AA0A6)
    // The labels are part of the drawing and sized to the card, so they don't grow with the font size and overlap;
    // the reading under the dial is the text that does.
    val density = LocalDensity.current
    val labels = remember(measurer, ink, minor, density) {
        (0 until 360 step 30).associateWith { deg ->
            val cardinal = deg % 90 == 0
            val text = when (deg) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "$deg" }
            measurer.measure(
                text,
                TextStyle(
                    color = if (deg == 0) GoogleColors.Red else if (cardinal) ink else minor,
                    fontSize = with(density) { (if (cardinal) 22.dp else 13.dp).toSp() },
                    fontWeight = if (cardinal) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }

    Canvas(modifier) {
        val margin = 16.dp.toPx() // Room above the dial for the lubber triangle.
        val r = size.minDimension / 2 - margin
        drawCircle(face, r)
        drawCircle(rim, r, style = Stroke(1.5.dp.toPx()))

        rotate(-heading) {
            for (deg in 0 until 360 step 5) {
                val len = when { deg % 90 == 0 -> 16.dp; deg % 30 == 0 -> 12.dp; else -> 6.dp }.toPx()
                rotate(deg.toFloat()) {
                    drawLine(
                        if (deg == 0) GoogleColors.Red else if (deg % 30 == 0) ink else minor,
                        Offset(center.x, center.y - r + 4.dp.toPx()),
                        Offset(center.x, center.y - r + 4.dp.toPx() + len),
                        strokeWidth = (if (deg % 30 == 0) 2.dp else 1.dp).toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            labels.forEach { (deg, layout) -> drawLabel(deg.toFloat(), layout, r - 24.dp.toPx()) }
            if (bearing != null) {
                rotate(bearing) {
                    val tip = center.y - r + 3.dp.toPx()
                    val w = 7.dp.toPx()
                    drawPath(Path().apply {
                        moveTo(center.x, tip + 12.dp.toPx()); lineTo(center.x - w, tip); lineTo(center.x + w, tip); close()
                    }, bearingColor)
                }
            }
            drawNeedle(r * 0.5f, south)
        }

        // Fixed lubber line: a red triangle above the dial and a line across the rim.
        val top = center.y - r
        drawPath(Path().apply {
            moveTo(center.x, top + 2.dp.toPx())
            lineTo(center.x - 9.dp.toPx(), top - margin + 2.dp.toPx())
            lineTo(center.x + 9.dp.toPx(), top - margin + 2.dp.toPx())
            close()
        }, GoogleColors.Red)
        drawLine(GoogleColors.Red, Offset(center.x, top), Offset(center.x, top + 22.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
    }
}

/** Draws a label on the dial's radius at [deg], reading outward like a real compass card. */
private fun DrawScope.drawLabel(deg: Float, layout: TextLayoutResult, radius: Float) {
    rotate(deg) {
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - radius))
    }
}

/** Classic two-tone needle: red half points to north on the card, the grey half to south. */
private fun DrawScope.drawNeedle(length: Float, southColor: Color) {
    val w = length * 0.12f
    val north = Path().apply { moveTo(center.x, center.y - length); lineTo(center.x - w, center.y); lineTo(center.x + w, center.y); close() }
    val south = Path().apply { moveTo(center.x, center.y + length); lineTo(center.x - w, center.y); lineTo(center.x + w, center.y); close() }
    drawPath(north, GoogleColors.Red)
    drawPath(south, southColor)
    drawCircle(Color.White, w * 0.7f, center)
    drawCircle(Color(0x55000000), w * 0.7f, center, style = Stroke(1.dp.toPx()))
}
