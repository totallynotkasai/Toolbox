package com.toolbox.app.toys.dial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.toolbox.app.ui.common.Feel
import com.toolbox.app.ui.common.Stat
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.fixed
import com.toolbox.app.ui.common.polar
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.smoothstep
import com.toolbox.app.ui.common.turnDelta
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

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
private const val LIGHT_DEG = -40f       // where the light comes from, degrees clockwise from 12 o'clock
private const val RIDGES = 90            // knurls round the skirt, 4° apart
private const val SETTLED = 0.01f        // degrees: close enough to a detent to stop animating
private val SkirtGap = Color(0xFF26292D)
private val RidgeDark = Color(0xFF3A3E44)
private val RidgeLit = Color(0xFFD2D5DA)

/**
 * How much of the knurling's light and dark still shows (1 = all of it) when the knob turns [degreesPerFrame]. Once
 * the ridges move more than about half their pitch a frame they flicker, or seem to turn the wrong way, so they fade
 * to an even grey instead, the way fast-turning knurls blur in real life.
 */
internal fun knurlContrast(degreesPerFrame: Float): Float =
    1f - smoothstep((abs(degreesPerFrame) - 1f) / (360f / RIDGES * 0.75f - 1f))

/**
 * The magnetic detent: stays in notch [current] until [raw] (degrees) has been pulled more than [capture]
 * degrees away from it, then jumps to whichever notch is nearest. [capture] above half the [spacing] gives
 * the resist-then-jump feel, and the hysteresis stops it chattering on the boundary.
 */
internal fun detentAfter(raw: Float, current: Int, spacing: Float, capture: Float): Int {
    val off = raw - current * spacing
    return if (abs(off) <= capture) current else (raw / spacing).roundToInt()
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
    var contrast by remember { mutableFloatStateOf(1f) } // of the knurling, faded at speed
    var dragging by remember { mutableStateOf(false) }
    val feel = remember { Feel() }
    val prev = remember { FloatArray(1) } // where the knob was drawn last frame

    val audio = rememberPcmStream { DialAudio() }
    val vibes = rememberVibes()

    // Frame loop: Free-mode momentum, settling into detents, and speed-driven whirr/buzz. It goes idle once the
    // knob has settled and nothing is holding it.
    val loop = rememberGameLoop { dt ->
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
        shown = when {
            direct -> target
            abs(target - shown) < SETTLED -> target
            else -> shown + (target - shown) * (1f - exp(-SNAP_RATE * dt))
        }
        if (dt > 0f) {
            speed += ((shown - prev[0]) / dt - speed) * min(1f, dt * 14f)
            contrast = knurlContrast(shown - prev[0])
        }
        if (abs(speed) < 0.5f && !dragging && omega == 0f) speed = 0f
        prev[0] = shown

        val notchRate = if (m.notches > 0) abs(speed) / m.spacing else 0f
        feel.fast = notchRate > FAST_NOTCHES
        audio.whirr = if (m == DialMode.Free) speed else 0f
        when {
            m == DialMode.Free -> vibes.buzz(min(1f, abs(speed) / MAX_FREE_SPEED) * 0.3f)
            feel.fast -> vibes.buzz(0.2f + 0.4f * min(1f, (notchRate - FAST_NOTCHES) / 150f))
            else -> vibes.stopBuzz()
        }
        dragging || omega != 0f || speed != 0f || shown != target
    }

    fun onNotch(m: DialMode) {
        if (m == DialMode.Chunky) {
            audio.clunk(1f)
            feel.space { vibes.click(0.55f) }
        } else {
            audio.tick(if (feel.fast) 0.6f else 1f)
            // Past ~30 notches a second the frame loop's buzz takes over, so ticks don't smear together.
            if (!feel.fast) feel.space { vibes.tick(0.35f) }
        }
    }

    fun switchTo(m: DialMode) {
        if (m == mode) return
        loop.wake()
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
        loop.wake()
        omega = 0f
        val home = (shown / 360f).roundToInt() * 360f
        zero = home
        raw = home
        if (mode.notches > 0) detent = (home / mode.spacing).roundToInt()
        audio.clunk(0.7f)
        vibes.click(0.6f)
    }


    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    ToolScaffold(title = "Rotary Dial", onBack = onBack) { padding ->
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
            DialStats(mode, { detent }, { shown }, { speed }, { zero })
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        // A finger landing on a spinning knob stops it dead, without waiting to see if it's a drag.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (omega != 0f) {
                                audio.grab(abs(omega) / MAX_FREE_SPEED)
                                vibes.tick(0.4f)
                                omega = 0f
                                raw = shown
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker1D(isDataDifferential = true)
                        detectDragGestures(
                            onDragStart = {
                                if (currentMode != DialMode.Chunky) raw = shown
                                dragging = true
                                tracker.resetTracking()
                                loop.wake()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val c = Offset(size.width / 2f, size.height / 2f)
                                // Near the hub the angle swings wildly for tiny moves, and a flick through the
                                // middle would read as half a turn, so turnDelta ignores that zone and caps the step.
                                val d = turnDelta(change.previousPosition, change.position, c, size.width * 0.08f)
                                if (d != 0f) {
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
                                loop.wake()
                            },
                            onDragCancel = {
                                dragging = false
                                val m = currentMode
                                if (m.notches > 0) raw = detent * m.spacing
                                loop.wake()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) { drawDial(shown, mode, tickColor, accent, contrast) }
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

/** The readings, in their own scope so a turning knob recomposes just these numbers, not the whole screen. */
@Composable
private fun DialStats(mode: DialMode, detent: () -> Int, shown: () -> Float, speed: () -> Float, zero: () -> Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        if (mode.notches > 0) {
            Stat("Notch", "${detent() - (zero() / mode.spacing).roundToInt()}", widest = "-000")
        } else {
            Stat("RPM", "${(abs(speed()) / 6f).roundToInt()}", widest = "000")
        }
        val turns = (shown() - zero()) / 360f
        Stat("Turns", fixed(turns, 2), widest = "-00.00")
        val deg = ((shown() - zero()) % 360f + 360f) % 360f
        Stat("Angle", "${deg.roundToInt() % 360}°", widest = "000°")
    }
}

/**
 * A machined aluminium knob on a printed scale: a dial of notch marks (the current notch lit), a soft drop
 * shadow, a knurled skirt whose ridges catch a fixed light as they turn, a bevel, a brushed top face and a
 * red pointer groove. Only the ridges and pointer rotate; the lighting stays put, which is what sells it.
 */
private fun DrawScope.drawDial(angle: Float, mode: DialMode, tickColor: Color, accent: Color, contrast: Float) {
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
    drawCircle(SkirtGap, knob, c)
    fun ridgeColor(deg: Float) = lerp(RidgeDark, RidgeLit, 0.5f + 0.5f * cos(Math.toRadians((deg - LIGHT_DEG).toDouble())).toFloat())
    if (contrast < 1f) {
        // Turning fast, the ridges smear into an even band, shaded only by the light.
        val band = List(25) { k -> lerp(SkirtGap, ridgeColor(90f + 15f * k), 0.5f) } // sweeps start at 3 o'clock
        drawCircle(
            Brush.sweepGradient(band, center = c), knob * 0.9175f, c,
            alpha = 1f - contrast, style = Stroke(knob * 0.135f),
        )
    }
    if (contrast > 0f) {
        val ridgeWidth = (2f * PI.toFloat() * knob * 0.92f / RIDGES) * 0.5f
        for (i in 0 until RIDGES) {
            val a = angle + 360f * i / RIDGES
            drawLine(
                ridgeColor(a).copy(alpha = contrast),
                polar(c, knob * 0.85f, a), polar(c, knob * 0.985f, a),
                strokeWidth = ridgeWidth,
            )
        }
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
