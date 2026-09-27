package com.toolbox.app.tools.level

import android.hardware.Sensor
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberSensorListener
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Surface level: lay the phone flat and centre the bubble. Uses gravity (or raw accelerometer) with light smoothing. */
@Composable
fun LevelScreen(onBack: () -> Unit) {
    var pitch by remember { mutableFloatStateOf(0f) } // degrees, front/back tilt
    var roll by remember { mutableFloatStateOf(0f) }  // degrees, left/right tilt

    val available = rememberSensorListener(Sensor.TYPE_GRAVITY, fallbackType = Sensor.TYPE_ACCELEROMETER) { e ->
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val r = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
        val p = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
        roll += (r - roll) * 0.2f
        pitch += (p - pitch) * 0.2f
    }

    val level = abs(pitch) < 1f && abs(roll) < 1f
    val bubbleColor by animateColorAsState(if (level) GoogleColors.Green else MaterialTheme.colorScheme.primary, label = "bubble")
    val ring = MaterialTheme.colorScheme.outlineVariant

    ToolScaffold(title = "Bubble Level", onBack = onBack) { padding ->
        ToolBody(padding) {
            if (!available) {
                Text("This device has no accelerometer.")
            } else Canvas(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f)) {
                val r = size.minDimension / 2
                drawCircle(ring, r, style = Stroke(2.dp.toPx()))
                drawCircle(ring, r * 0.5f, style = Stroke(2.dp.toPx()))
                drawCircle(ring, r * 0.12f, style = Stroke(2.dp.toPx()))
                drawLine(ring, Offset(center.x - r, center.y), Offset(center.x + r, center.y))
                drawLine(ring, Offset(center.x, center.y - r), Offset(center.x, center.y + r))
                // The accelerometer reports "up" in device axes, so the bubble drifts toward the raised edge.
                // 20° of tilt reaches the rim.
                var dx = roll / 20f * r
                var dy = -pitch / 20f * r
                val dist = hypot(dx, dy)
                val max = r * 0.88f
                if (dist > max) { dx *= max / dist; dy *= max / dist }
                drawCircle(bubbleColor.copy(alpha = 0.85f), r * 0.11f, Offset(center.x + dx, center.y + dy))
            }
            Spacer(Modifier.height(32.dp))
            Text("%.1f°  ·  %.1f°".format(roll, pitch), style = MaterialTheme.typography.displayMedium)
            Text(if (level) "Level" else "Lay your phone on a surface", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
