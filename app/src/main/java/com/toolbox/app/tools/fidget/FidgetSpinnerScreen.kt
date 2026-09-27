package com.toolbox.app.tools.fidget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.VibesToggleButton
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.sin

private const val MAX_SPEED = 7200f       // deg/s = 1200 rpm
private const val BEARING_FRICTION = 18f // deg/s² constant drag from the bearing
private const val AIR_DRAG = 0.11f       // 1/s, proportional drag; dominates at high speed
private const val STOP_BELOW = 4f        // deg/s
private const val SPIN_CEILING = MAX_SPEED * 1.05f // flicks approach this asymptotically

/**
 * Adds a flick of [push] deg/s to a spin already at [current] deg/s, with diminishing returns: gentle
 * flicks behave much as before, but each push near the top counts for less, so reaching [MAX_SPEED]
 * takes several hard flicks in a row. Both arguments are magnitudes.
 */
internal fun addSpin(current: Float, push: Float): Float {
    val effort = -SPIN_CEILING * ln(1f - current.coerceAtMost(MAX_SPEED) / SPIN_CEILING)
    return (SPIN_CEILING * (1f - exp(-(effort + push) / SPIN_CEILING))).coerceAtMost(MAX_SPEED)
}

@Composable
fun FidgetSpinnerScreen(onBack: () -> Unit) {
    var angle by remember { mutableFloatStateOf(0f) }
    var omega by remember { mutableFloatStateOf(0f) } // deg/s, positive = clockwise
    var dragging by remember { mutableStateOf(false) }
    var spinStartMs by remember { mutableFloatStateOf(0f) }
    var lastSpinSeconds by remember { mutableFloatStateOf(0f) }
    var hasSpun by rememberSaveable { mutableStateOf(false) }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var clockMs by remember { mutableFloatStateOf(0f) }
    val (bestRpm, submitRpm) = rememberBestScore("fidget-rpm")

    val audio = remember { FidgetAudio(MAX_SPEED) }
    DisposableEffect(Unit) { audio.start(); onDispose { audio.stop() } }
    val vibes = rememberVibes()

    // Physics loop: integrate spin and friction every frame; feed speed to the synth.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                clockMs += dt * 1000f
                if (!dragging && omega != 0f) {
                    val slow = (BEARING_FRICTION + AIR_DRAG * abs(omega)) * dt
                    omega = if (abs(omega) <= slow || abs(omega) < STOP_BELOW) 0f else omega - sign(omega) * slow
                    angle = (angle + omega * dt) % 360f
                    if (omega == 0f) lastSpinSeconds = (clockMs - spinStartMs) / 1000f
                }
                audio.speed = omega
                vibes.buzz(abs(omega) / MAX_SPEED, clockMs.toLong())
            }
        }
    }

    val rpm = (abs(omega) / 6f).toInt()
    val bodyColor = GoogleColors.all[colorIndex]

    ToolScaffold(title = "Fidget Spinner", onBack = onBack, actions = { VibesToggleButton() }) { padding ->
        ToolBody(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Stat("RPM", "$rpm")
                Stat("Best", "$bestRpm")
                Stat("Last spin", if (lastSpinSeconds > 0f) "%.1fs".format(lastSpinSeconds) else "–")
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker1D(isDataDifferential = true)
                        var momentum = 0f
                        var lever = Offset.Zero
                        detectDragGestures(
                            onDragStart = { pos ->
                                // A finger on the spinner acts as a brake; remember the spin so a
                                // same-direction flick can add to it rather than starting from zero.
                                momentum = omega
                                audio.grab(abs(omega) / MAX_SPEED)
                                omega = 0f
                                dragging = true
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
                                dragging = false
                                val fling = tracker.calculateVelocity() * 1.4f
                                omega = when {
                                    abs(fling) < 30f -> 0f
                                    sign(fling) == sign(momentum) -> sign(fling) * addSpin(abs(momentum), abs(fling))
                                    else -> sign(fling) * addSpin(0f, abs(fling))
                                }
                                if (omega != 0f) {
                                    audio.flick(abs(omega) / MAX_SPEED)
                                    spinStartMs = clockMs
                                    hasSpun = true
                                    vibes.click(0.6f)
                                    submitRpm((abs(omega) / 6f).toInt())
                                }
                            },
                            onDragCancel = { dragging = false },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // Cheap motion blur: faint trailing copies once it's moving fast.
                    val blur = abs(omega) > 900f
                    if (blur) {
                        for (k in 3 downTo 1) {
                            rotate(angle - omega * 0.006f * k) { drawSpinner(bodyColor, alpha = 0.12f) }
                        }
                    }
                    rotate(angle) { drawSpinner(bodyColor, alpha = 1f) }
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(visible = !hasSpun, exit = fadeOut()) {
                Text("Swipe up or down on the spinner — faster swipes spin it faster", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GoogleColors.all.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(if (i == colorIndex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { colorIndex = i },
                    )
                }
            }
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

/** Classic tri-spinner: three weighted lobes on a rounded body, metal bearings, centre cap. */
private fun DrawScope.drawSpinner(color: Color, alpha: Float) {
    val r = size.minDimension / 2
    val arm = r * 0.56f
    val lobe = r * 0.3f
    val lobes = (0 until 3).map { i ->
        val a = Math.toRadians(-90.0 + 120.0 * i)
        Offset(center.x + arm * cos(a).toFloat(), center.y + arm * sin(a).toFloat())
    }
    val darker = color.copy(red = color.red * 0.75f, green = color.green * 0.75f, blue = color.blue * 0.75f, alpha = alpha)
    val body = color.copy(alpha = alpha)
    lobes.forEach { p -> drawLine(body, center, p, strokeWidth = lobe * 1.25f) }
    lobes.forEach { p ->
        drawCircle(body, lobe, p)
        drawCircle(darker, lobe, p, style = Stroke(lobe * 0.08f))
        // Bearing: steel ring with a sheen.
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFFF1F3F4), Color(0xFF9AA0A6)), start = p - Offset(lobe, lobe), end = p + Offset(lobe, lobe)),
            lobe * 0.55f, p, alpha = alpha,
        )
        drawCircle(Color(0xFF5F6368).copy(alpha = alpha), lobe * 0.55f, p, style = Stroke(lobe * 0.05f))
        drawCircle(Color(0xFF3C4043).copy(alpha = alpha), lobe * 0.22f, p)
    }
    drawCircle(body, lobe * 1.1f, center)
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color(0xFFDADCE0)), center = center - Offset(lobe * 0.2f, lobe * 0.2f), radius = lobe * 0.7f),
        lobe * 0.6f, center, alpha = alpha,
    )
    drawCircle(darker, lobe * 0.6f, center, style = Stroke(lobe * 0.06f))
}
