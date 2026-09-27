package com.toolbox.app.toys.dial

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.VibesToggleButton
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private enum class DialMode(val label: String, val notches: Int) {
    Fine("Fine", 60),
    Chunky("Chunky", 12),
    Free("Free", 0);

    /** Degrees between detents (0 in Free mode). */
    val spacing: Float get() = if (notches == 0) 0f else 360f / notches
}

private const val CHUNKY_CAPTURE = 19f   // deg a chunky detent holds on for before letting go (spacing is 30)
private const val MAGNET_GIVE = 0.3f     // how far a held chunky knob yields toward the finger
private const val SNAP_RATE = 38f        // 1/s, how quickly the knob settles into a detent
private const val FREE_FRICTION = 60f    // deg/s², constant bearing drag in Free mode
private const val FREE_DRAG = 0.35f      // 1/s, proportional drag; dominates at speed
private const val MAX_FREE_SPEED = 2880f // deg/s (480 rpm), cap on a flick
private const val FAST_NOTCHES = 32f     // notches/s above which separate ticks would smear; buzz instead
private const val MIN_VIBE_GAP_MS = 25L
private const val LIGHT_DEG = -40f       // where the light comes from, degrees clockwise from 12 o'clock

/** Shortest signed difference for an angle change, -180..180. */
internal fun wrapDelta(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/**
 * The magnetic detent: stays in notch [current] until [raw] (degrees) has been pulled more than [capture]
 * degrees away from it, then jumps to whichever notch is nearest. [capture] above half the [spacing] gives
 * the resist-then-jump feel, and the hysteresis stops it chattering on the boundary.
 */
internal fun detentAfter(raw: Float, current: Int, spacing: Float, capture: Float): Int {
    val off = raw - current * spacing
    return if (abs(off) <= capture) current else (raw / spacing).roundToInt()
}

/** Angle of [p] around [c], degrees clockwise from 12 o'clock. */
private fun angleOf(p: Offset, c: Offset): Float =
    Math.toDegrees(atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())).toFloat()

/** The point [r] from [c] at [deg] degrees clockwise from 12 o'clock. */
private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
}

/** Bookkeeping the gesture and frame loop share that never needs a recomposition. */
private class Feel {
    var lastVibeMs = 0L
    var fast = false
}

@Composable
fun DialScreen(onBack: () -> Unit) {
    var modeName by rememberPref("dial_mode", DialMode.Fine.name)
    val mode = DialMode.entries.firstOrNull { it.name == modeName } ?: DialMode.Fine
    val currentMode by rememberUpdatedState(mode)

    var raw by remember { mutableFloatStateOf(0f) }     // where the finger (or momentum) has put the knob, degrees
    var shown by remember { mutableFloatStateOf(0f) }   // where it's drawn: raw, pulled into detents
    var detent by remember { mutableIntStateOf(0) }     // current notch index, in the current mode's spacing
    var zero by remember { mutableFloatStateOf(0f) }    // the angle "0" was last set at (a whole turn)
    var omega by remember { mutableFloatStateOf(0f) }   // Free-mode spin, deg/s, positive = clockwise
    var speed by remember { mutableFloatStateOf(0f) }   // smoothed deg/s of the drawn knob
    var dragging by remember { mutableStateOf(false) }
    val feel = remember { Feel() }

    val audio = rememberPcmStream { DialAudio() }
    val vibes = rememberVibes()

    fun onNotch(m: DialMode) {
        val now = SystemClock.uptimeMillis()
        if (m == DialMode.Chunky) {
            audio.clunk(1f)
            if (now - feel.lastVibeMs >= MIN_VIBE_GAP_MS) { vibes.click(0.55f); feel.lastVibeMs = now }
        } else {
            audio.tick(if (feel.fast) 0.6f else 1f)
            // Past ~30 notches a second the frame loop's buzz takes over, so ticks don't smear together.
            if (!feel.fast && now - feel.lastVibeMs >= MIN_VIBE_GAP_MS) { vibes.tick(0.35f); feel.lastVibeMs = now }
        }
    }

    fun switchTo(m: DialMode) {
        if (m == mode) return
        modeName = m.name
        omega = 0f
        if (m.notches > 0) {
            detent = (shown / m.spacing).roundToInt()
            raw = detent * m.spacing
        } else {
            raw = shown
        }
        audio.tick(0.8f)
        vibes.tick(0.4f)
    }

    fun reset() {
        // Wind back to the nearest whole turn so the pointer lands at 12 o'clock, and count from there.
        omega = 0f
        val home = (shown / 360f).roundToInt() * 360f
        zero = home
        raw = home
        if (mode.notches > 0) detent = (home / mode.spacing).roundToInt()
        audio.clunk(0.7f)
        vibes.click(0.6f)
    }

    // Frame loop: Free-mode momentum, settling into detents, and speed-driven whirr/buzz.
    LaunchedEffect(Unit) {
        var last = 0L
        var prev = shown
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val m = currentMode
                if (m == DialMode.Free && !dragging && omega != 0f) {
                    val slow = (FREE_FRICTION + FREE_DRAG * abs(omega)) * dt
                    omega = if (abs(omega) <= slow) 0f else omega - sign(omega) * slow
                    raw += omega * dt
                }
                val target = if (m == DialMode.Chunky) {
                    val notch = detent * m.spacing
                    notch + if (dragging) (raw - notch) * MAGNET_GIVE else 0f
                } else {
                    raw
                }
                val direct = (dragging && m != DialMode.Chunky) || (m == DialMode.Free && omega != 0f)
                shown = if (direct) target else shown + (target - shown) * (1f - exp(-SNAP_RATE * dt))
                if (dt > 0f) speed += ((shown - prev) / dt - speed) * min(1f, dt * 14f)
                prev = shown

                val nowMs = SystemClock.uptimeMillis()
                val notchRate = if (m.notches > 0) abs(speed) / m.spacing else 0f
                feel.fast = notchRate > FAST_NOTCHES
                audio.whirr = if (m == DialMode.Free) speed else 0f
                when {
                    m == DialMode.Free -> vibes.buzz(min(1f, abs(speed) / MAX_FREE_SPEED) * 0.3f, nowMs)
                    feel.fast -> vibes.buzz(0.2f + 0.4f * min(1f, (notchRate - FAST_NOTCHES) / 150f), nowMs)
                    else -> vibes.stopBuzz()
                }
            }
        }
    }

    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    ToolScaffold(title = "Rotary Dial", onBack = onBack, actions = { VibesToggleButton() }) { padding ->
        ToolBody(padding) {
            SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                DialMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = m == mode,
                        onClick = { switchTo(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, DialMode.entries.size),
                    ) { Text(m.label, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                if (mode.notches > 0) {
                    Stat("Notch", "${detent - (zero / mode.spacing).roundToInt()}")
                } else {
                    Stat("RPM", "${(abs(speed) / 6f).roundToInt()}")
                }
                Stat("Turns", "%.2f".format((shown - zero) / 360f))
                val deg = ((shown - zero) % 360f + 360f) % 360f
                Stat("Angle", "${deg.roundToInt() % 360}°")
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker1D(isDataDifferential = true)
                        detectDragGestures(
                            onDragStart = {
                                // A finger on a spinning knob stops it dead.
                                if (omega != 0f) {
                                    audio.grab(abs(omega) / MAX_FREE_SPEED)
                                    vibes.tick(0.4f)
                                    omega = 0f
                                }
                                if (currentMode != DialMode.Chunky) raw = shown
                                dragging = true
                                tracker.resetTracking()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val c = Offset(size.width / 2f, size.height / 2f)
                                // Near the hub the angle swings wildly for tiny moves; ignore that zone.
                                if ((change.position - c).getDistance() > size.width * 0.08f) {
                                    val d = wrapDelta(angleOf(change.position, c) - angleOf(change.previousPosition, c))
                                    raw += d
                                    tracker.addDataPoint(change.uptimeMillis, d)
                                    val m = currentMode
                                    val next = when (m) {
                                        DialMode.Fine -> (raw / m.spacing).roundToInt()
                                        DialMode.Chunky -> detentAfter(raw, detent, m.spacing, CHUNKY_CAPTURE)
                                        DialMode.Free -> detent
                                    }
                                    if (next != detent) {
                                        detent = next
                                        onNotch(m)
                                    }
                                }
                            },
                            onDragEnd = {
                                dragging = false
                                val m = currentMode
                                if (m.notches > 0) {
                                    raw = detent * m.spacing // settle into the nearest notch
                                } else {
                                    val fling = tracker.calculateVelocity()
                                    omega = if (abs(fling) < 40f) 0f else fling.coerceIn(-MAX_FREE_SPEED, MAX_FREE_SPEED)
                                    if (omega != 0f) vibes.tick(0.25f)
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                val m = currentMode
                                if (m.notches > 0) raw = detent * m.spacing
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) { drawDial(shown, mode, tickColor, accent) }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { reset() }) { Text("Reset to 0") }
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    DialMode.Fine -> "Drag around the knob: 60 light clicks a turn"
                    DialMode.Chunky -> "Drag around the knob: 12 heavy detents a turn"
                    DialMode.Free -> "Flick it round and let it spin down"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A machined aluminium knob on a printed scale: a dial of notch marks (the current notch lit), a soft drop
 * shadow, a knurled skirt whose ridges catch a fixed light as they turn, a bevel, a brushed top face and a
 * red pointer groove. Only the ridges and pointer rotate; the lighting stays put, which is what sells it.
 */
private fun DrawScope.drawDial(angle: Float, mode: DialMode, tickColor: Color, accent: Color) {
    val r = size.minDimension / 2f
    val c = center

    // Scale.
    val count = if (mode.notches > 0) mode.notches else 72
    val lit = if (mode.notches > 0) Math.floorMod((angle / mode.spacing).roundToInt(), mode.notches) else -1
    for (i in 0 until count) {
        val a = 360f * i / count
        val major = when (mode) {
            DialMode.Fine -> i % 5 == 0
            DialMode.Chunky -> true
            DialMode.Free -> i % 6 == 0
        }
        val width = when {
            mode == DialMode.Chunky -> r * 0.035f
            major -> r * 0.018f
            else -> r * 0.01f
        }
        val color = when {
            i == lit -> accent
            mode == DialMode.Free -> tickColor.copy(alpha = if (major) 0.45f else 0.2f)
            major -> tickColor.copy(alpha = 0.9f)
            else -> tickColor.copy(alpha = 0.5f)
        }
        val inner = r * if (major) 0.86f else 0.9f
        drawLine(color, polar(c, inner, a), polar(c, r * 0.97f, a), strokeWidth = width, cap = StrokeCap.Round)
    }

    val knob = r * 0.78f
    // Drop shadow, cast down and slightly right of the light.
    val shadowAt = c + Offset(knob * 0.03f, knob * 0.08f)
    drawCircle(
        Brush.radialGradient(
            0.8f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Transparent,
            center = shadowAt,
            radius = knob * 1.1f,
        ),
        knob * 1.1f, shadowAt,
    )

    // Knurled skirt: each ridge is lit by how squarely it faces the light.
    drawCircle(Color(0xFF26292D), knob, c)
    val ridges = 90
    val ridgeWidth = (2f * PI.toFloat() * knob * 0.92f / ridges) * 0.5f
    for (i in 0 until ridges) {
        val a = angle + 360f * i / ridges
        val light = 0.5f + 0.5f * cos(Math.toRadians((a - LIGHT_DEG).toDouble())).toFloat()
        drawLine(
            lerp(Color(0xFF3A3E44), Color(0xFFD2D5DA), light),
            polar(c, knob * 0.85f, a), polar(c, knob * 0.985f, a),
            strokeWidth = ridgeWidth,
        )
    }

    // Bevel between skirt and face.
    val lightDir = polar(Offset.Zero, knob, LIGHT_DEG)
    drawCircle(Brush.linearGradient(listOf(Color(0xFFF1F3F4), Color(0xFF5F6368)), start = c + lightDir, end = c - lightDir), knob * 0.85f, c)

    // Top face: brushed metal with faint machining rings.
    val face = knob * 0.78f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFDADCE0), Color(0xFF8A8F96)), center = c + lightDir * 0.4f, radius = face * 1.7f),
        face, c,
    )
    for (k in 1..12) {
        val ring = if (k % 2 == 0) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
        drawCircle(ring, face * k / 12.5f, c, style = Stroke(face * 0.02f))
    }
    drawCircle(Color.Black.copy(alpha = 0.25f), face, c, style = Stroke(face * 0.02f))

    // Pointer: a dark groove with the red fill inset.
    drawLine(Color(0xFF202124), polar(c, face * 0.25f, angle), polar(c, face * 0.92f, angle), strokeWidth = knob * 0.07f, cap = StrokeCap.Round)
    drawLine(GoogleColors.Red, polar(c, face * 0.27f, angle), polar(c, face * 0.9f, angle), strokeWidth = knob * 0.04f, cap = StrokeCap.Round)

    // Centre dimple, shaded opposite to the face so it reads as a dip.
    drawCircle(
        Brush.linearGradient(listOf(Color(0xFF80868B), Color(0xFFE8EAED)), start = c + lightDir * 0.14f, end = c - lightDir * 0.14f),
        face * 0.13f, c,
    )
}
