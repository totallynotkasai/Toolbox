package com.toolbox.app.toys.fidget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.Stat
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.smoothstep
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

private const val MAX_SPEED = 7200f       // deg/s = 1200 rpm
private const val BEARING_FRICTION = 18f // deg/s² constant drag from the bearing
private const val AIR_DRAG = 0.11f       // 1/s, proportional drag; dominates at high speed
private const val STOP_BELOW = 4f        // deg/s
private const val SPIN_CEILING = MAX_SPEED * 1.05f // flicks approach this asymptotically
private const val BRAKE_SECONDS = 0.25f  // a finger resting on it stops it in about this long
private const val MIN_BRAKE = 200f       // deg/s², so a slow spin still stops promptly

// The shape repeats every 120°, so once it turns more than a few tens of degrees a frame the eye can't follow it
// (it seems to stop, or turn backwards). From BLUR_FROM degrees a frame it fades into a smeared disc, gone
// completely by BLUR_FULL, and the drawn turn per frame is capped at MAX_DRAWN_STEP so what's left of it always
// reads as turning forwards.
private const val BLUR_FROM = 8f
private const val BLUR_FULL = 26f
private const val MAX_DRAWN_STEP = 24f

private val ColourNames = listOf("Blue", "Red", "Yellow", "Green")

/**
 * Adds a flick of [push] deg/s to a spin already at [current] deg/s, with diminishing returns: gentle
 * flicks behave much as before, but each push near the top counts for less, so reaching [MAX_SPEED]
 * takes several hard flicks in a row. Both arguments are magnitudes.
 */
internal fun addSpin(current: Float, push: Float): Float {
    val effort = -SPIN_CEILING * ln(1f - current.coerceAtMost(MAX_SPEED) / SPIN_CEILING)
    return (SPIN_CEILING * (1f - exp(-(effort + push) / SPIN_CEILING))).coerceAtMost(MAX_SPEED)
}

/**
 * One frame of a free spin at [omega] deg/s: the bearing and the air slow it, and a finger resting on it ([brake]
 * deg/s², 0 for none) slows it much harder. Returns the new speed, 0 once it has stopped.
 */
internal fun spinDown(omega: Float, dt: Float, brake: Float = 0f): Float {
    val slow = (BEARING_FRICTION + AIR_DRAG * abs(omega) + brake) * dt
    return if (abs(omega) <= slow || abs(omega) < STOP_BELOW) 0f else omega - sign(omega) * slow
}

/** How hard a finger brakes a spin of [omega] deg/s, so it stops in about [BRAKE_SECONDS] whatever the speed. */
internal fun brakeFor(omega: Float): Float = max(abs(omega) / BRAKE_SECONDS, MIN_BRAKE)

/** How far into the blur disc a spin turning [degreesPerFrame] is, 0..1. */
internal fun blurFor(degreesPerFrame: Float): Float = smoothstep((abs(degreesPerFrame) - BLUR_FROM) / (BLUR_FULL - BLUR_FROM))

/** What the touch handlers and the frame loop share that never needs a recomposition on its own. */
private class Hand {
    var touching = false
    var dragging = false
    var momentum = 0f // the spin when the finger landed, so a same-way flick adds to it
    var brake = 0f
}

@Composable
fun FidgetSpinnerScreen(onBack: () -> Unit) {
    var angle by remember { mutableFloatStateOf(0f) }   // as drawn, degrees
    var omega by remember { mutableFloatStateOf(0f) }   // deg/s, positive = clockwise
    var blur by remember { mutableFloatStateOf(0f) }
    var spinSeconds by remember { mutableFloatStateOf(0f) }
    var lastSpinSeconds by remember { mutableFloatStateOf(0f) }
    var hasSpun by rememberSaveable { mutableStateOf(false) }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    val hand = remember { Hand() }
    val (bestRpm, submitRpm) = rememberBestScore("fidget-rpm")

    val audio = rememberPcmStream { FidgetAudio(MAX_SPEED) }
    val vibes = rememberVibes()

    // Physics: friction, the finger's brake, the blur, and the speed fed to the synth and the motor. It goes idle
    // once the spinner has stopped and nothing is touching it.
    val loop = rememberGameLoop { dt ->
        if (!hand.dragging && omega != 0f) {
            omega = spinDown(omega, dt, if (hand.touching) hand.brake else 0f)
            spinSeconds += dt
            if (omega == 0f && !hand.touching) lastSpinSeconds = spinSeconds
        }
        val step = omega * dt
        if (dt > 0f) {
            angle = (angle + step.coerceIn(-MAX_DRAWN_STEP, MAX_DRAWN_STEP)) % 360f
            blur += (blurFor(step) - blur) * min(1f, dt * 10f)
            if (blur < 0.003f) blur = 0f
        }
        audio.speed = omega
        vibes.buzz(abs(omega) / MAX_SPEED)
        omega != 0f || hand.touching || blur > 0f
    }

    val bodyColor = GoogleColors.all[colorIndex]

    ToolScaffold(title = "Fidget Spinner", onBack = onBack) { padding ->
        ToolBody(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Rpm { omega }
                Stat("Best", "$bestRpm")
                Stat("Last spin", if (lastSpinSeconds > 0f) "%.1fs".format(lastSpinSeconds) else "–", widest = "00.0s")
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics { contentDescription = "Fidget spinner. Swipe across it to spin it." }
                    .pointerInput(Unit) {
                        // A finger landing on it brakes it straight away, without waiting to see if it's a drag.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            hand.touching = true
                            hand.momentum = omega
                            hand.brake = brakeFor(omega)
                            if (abs(omega) > STOP_BELOW) {
                                audio.grab(abs(omega) / MAX_SPEED)
                                vibes.tick(0.4f)
                            }
                            loop.wake()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                            } while (event.changes.any { it.pressed })
                            hand.touching = false
                        }
                    }
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker1D(isDataDifferential = true)
                        var lever = Offset.Zero
                        detectDragGestures(
                            onDragStart = { pos ->
                                // The finger holds it now; the spin it had is kept in hand.momentum.
                                omega = 0f
                                hand.dragging = true
                                tracker.resetTracking()
                                // Lever arm from the hub. Each axis is kept at least 40% of the radius so a
                                // straight up/down (or left/right) swipe through the middle still spins it.
                                val radius = size.width / 2f
                                val r = pos - Offset(size.width / 2f, size.height / 2f)
                                fun arm(v: Float) = (if (v >= 0f) 1f else -1f) * abs(v).coerceIn(radius * 0.4f, radius)
                                lever = Offset(arm(r.x), arm(r.y))
                            },
                            onDrag = { change, d ->
                                change.consume()
                                val radius = size.width / 2f
                                // Torque-style mapping: angular delta = (lever × drag) / R².
                                val dTheta = Math.toDegrees(((lever.x * d.y - lever.y * d.x) / (radius * radius)).toDouble()).toFloat()
                                angle = (angle + dTheta) % 360f
                                tracker.addDataPoint(change.uptimeMillis, dTheta)
                            },
                            onDragEnd = {
                                hand.dragging = false
                                val fling = tracker.calculateVelocity() * 1.4f
                                val momentum = hand.momentum
                                omega = when {
                                    abs(fling) < 30f -> 0f
                                    sign(fling) == sign(momentum) -> sign(fling) * addSpin(abs(momentum), abs(fling))
                                    else -> sign(fling) * addSpin(0f, abs(fling))
                                }
                                hand.momentum = 0f
                                if (omega != 0f) {
                                    audio.flick(abs(omega) / MAX_SPEED)
                                    spinSeconds = 0f
                                    hasSpun = true
                                    vibes.click(0.6f)
                                    submitRpm((abs(omega) / 6f).toInt())
                                }
                                loop.wake()
                            },
                            onDragCancel = { hand.dragging = false },
                        )
                    }
                    .drawWithCache {
                        val outline = SpinnerShape.path(size.center, size.minDimension / 2f)
                        onDrawBehind { drawFidgetSpinner(outline, angle, blur, bodyColor) }
                    },
            )
            Spacer(Modifier.height(16.dp))
            // Fades out once you've spun it, but keeps its space so the spinner doesn't jump.
            val hintAlpha by animateFloatAsState(if (hasSpun) 0f else 1f, label = "hint")
            Text(
                "Swipe up or down on the spinner — faster swipes spin it faster",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(hintAlpha),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GoogleColors.all.forEachIndexed { i, c ->
                    // A 36 dp swatch in a 48 dp touch target.
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .selectable(selected = i == colorIndex, role = Role.RadioButton, onClick = { colorIndex = i })
                            .semantics { contentDescription = ColourNames[i] },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (i == colorIndex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

/** The live RPM, in its own scope so a spin only recomposes this one number each frame, not the whole screen. */
@Composable
private fun Rpm(omega: () -> Float) {
    val rpm by remember { derivedStateOf { (abs(omega()) / 6f).toInt() } }
    Stat("RPM", "$rpm", widest = "0000")
}
