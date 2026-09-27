package com.toolbox.app.toys.rainstick

import android.hardware.Sensor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.VibesToggleButton
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberSensorListener
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val EARTH = 9.81f
private const val G_SCALE = 0.9f // m/s² → tube widths/s²; grains take a few seconds to fall the length

/** Vibration strength for a collision rate (summed hit intensity per second; a full pour is ~300-500). */
internal fun rainVibration(rate: Float): Float = (rate / 450f).coerceIn(0f, 1f).pow(0.7f) * 0.75f

/**
 * Gravity in the tube's frame, from gravity on screen ([sx], [sy]: x right, y down) and the tube's rotation
 * [degrees] (clockwise on screen).
 */
internal fun toTubeFrame(sx: Float, sy: Float, degrees: Float): Pair<Float, Float> {
    val a = Math.toRadians(degrees.toDouble())
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    return (c * sx + s * sy) to (-s * sx + c * sy)
}

// Seeds, beans and grit.
private val GrainColours = listOf(
    Color(0xFFD7B98E), Color(0xFFB5651D), Color(0xFF8D6E63), Color(0xFFE8D5B0),
    Color(0xFF6B4F3A), Color(0xFFA0522D), Color(0xFF9E8F4A),
)
private val Wood = Color(0xFF8D5A3B)
private val WoodDark = Color(0xFF5D3A26)
private val Peg = Color(0xFF6D4C41)
private val TubeTint = Color(0xFFC8A27A)

@Composable
fun RainStickScreen(onBack: () -> Unit) {
    val sim = remember { RainStickSim() }
    val audio = rememberPcmStream { RainAudio() }
    val vibes = rememberVibes()
    var tick by remember { mutableLongStateOf(0L) }
    var flips by rememberSaveable { mutableIntStateOf(0) }
    val tubeAngle by animateFloatAsState(
        targetValue = flips * 180f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "flip",
    )

    // In-plane gravity from the accelerometer, in screen axes (x right, y down), lightly smoothed. Raw
    // accelerometer rather than TYPE_GRAVITY on purpose: a shake then rattles the grains too.
    val gravity = remember { FloatArray(2) }
    val hasSensor = rememberSensorListener(Sensor.TYPE_ACCELEROMETER) { e ->
        gravity[0] += (-e.values[0] - gravity[0]) * 0.3f
        gravity[1] += (e.values[1] - gravity[1]) * 0.3f
    }

    LaunchedEffect(sim) {
        var last = 0L
        var rate = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                if (dt > 0f) {
                    // Lying flat on a table there's no useful tilt, so fall back to "down the screen";
                    // the Flip button still works then because it turns the tube itself.
                    val tilt = if (hasSensor) ((hypot(gravity[0], gravity[1]) - 1.5f) / 3f).coerceIn(0f, 1f) else 0f
                    val sx = gravity[0] * tilt
                    val sy = gravity[1] * tilt + EARTH * (1f - tilt)
                    val (gx, gy) = toTubeFrame(sx, sy, tubeAngle)
                    sim.step(dt, gx * G_SCALE, gy * G_SCALE)
                    if (sim.hitCount > 0) audio.post(sim.hits.copyOf(sim.hitCount))
                    rate += (sim.hitEnergy / dt - rate) * min(1f, dt * 10f)
                    vibes.buzz(rainVibration(rate), now / 1_000_000L)
                    tick++
                }
            }
        }
    }

    val glass = MaterialTheme.colorScheme.onSurface
    ToolScaffold(title = "Rain Stick", onBack = onBack, actions = { VibesToggleButton() }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                if (tick < 0L) return@Canvas // reading tick redraws the grains every frame
                drawRainStick(sim, tubeAngle, glass)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (hasSensor) "Tilt your phone, or flip the stick" else "Flip the stick to make it rain",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { flips++; vibes.click(0.3f) }) {
                Icon(Icons.Filled.SwapVert, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Flip")
            }
        }
    }
}

/**
 * The stick, centred and as tall as fits, rotated by [angle]. While it's turning it shrinks a little so it
 * stays on screen when it's sideways.
 */
private fun DrawScope.drawRainStick(sim: RainStickSim, angle: Float, glass: Color) {
    val len = RainStickSim.LENGTH
    val cap = 0.3f
    val total = len + 2 * cap
    val unit = min(size.height / (total + 0.1f), size.width * 0.3f) // px per tube width
    val sideways = abs(sin(Math.toRadians(angle.toDouble()))).toFloat()
    val fit = min(1f, size.width * 0.96f / (total * unit))
    val shrink = 1f + (fit - 1f) * sideways
    val origin = Offset(center.x - 0.5f * unit, center.y - 0.5f * len * unit)
    fun p(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)

    rotate(angle, pivot = center) {
        scale(shrink, pivot = center) {
            // Wooden end caps with painted bands.
            for (top in listOf(true, false)) {
                val y0 = if (top) -cap - 0.02f else len - 0.02f
                val tl = p(-0.08f, y0)
                val capSize = Size(1.16f * unit, (cap + 0.04f) * unit)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(WoodDark, Wood, WoodDark), startX = tl.x, endX = tl.x + capSize.width),
                    topLeft = tl,
                    size = capSize,
                    cornerRadius = CornerRadius(0.14f * unit),
                )
                GoogleColors.all.forEachIndexed { i, c ->
                    val by = y0 + 0.07f + i * 0.05f
                    drawLine(c.copy(alpha = 0.85f), p(-0.06f, by), p(1.06f, by), strokeWidth = 0.025f * unit)
                }
            }

            // Tube interior, shaded like a cylinder.
            val tubeTl = p(0f, 0f)
            val tubeSize = Size(unit, len * unit)
            drawRect(
                Brush.horizontalGradient(
                    listOf(TubeTint.copy(alpha = 0.32f), TubeTint.copy(alpha = 0.12f), TubeTint.copy(alpha = 0.28f)),
                    startX = tubeTl.x,
                    endX = tubeTl.x + unit,
                ),
                topLeft = tubeTl,
                size = tubeSize,
            )

            // Pegs on the far side, then the grains, then the near pegs over them.
            val pegR = RainStickSim.PEG_R * unit
            for (k in 0 until sim.pegCount) {
                if (sim.pegDepth[k] >= 0f) continue
                drawCircle(Peg.copy(alpha = 0.35f), pegR * 0.85f, p(sim.pegX[k], sim.pegY[k]))
            }
            val grainR = RainStickSim.GRAIN_R * unit
            for (i in 0 until sim.count) {
                drawCircle(GrainColours[sim.shade[i]], grainR * sim.size[i], p(sim.x[i], sim.y[i]))
            }
            for (k in 0 until sim.pegCount) {
                if (sim.pegDepth[k] < 0f) continue
                val c = p(sim.pegX[k], sim.pegY[k])
                drawCircle(Peg, pegR, c)
                drawCircle(Color.White.copy(alpha = 0.35f), pegR * 0.35f, c - Offset(pegR * 0.3f, pegR * 0.3f))
            }

            // Glass sheen and walls.
            drawLine(
                Color.White.copy(alpha = 0.16f),
                p(0.22f, 0.12f),
                p(0.22f, len - 0.12f),
                strokeWidth = 0.07f * unit,
            )
            drawRect(glass.copy(alpha = 0.35f), topLeft = tubeTl, size = tubeSize, style = Stroke(0.02f * unit))
        }
    }
}
