package com.toolbox.app.toys.cat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val STROKE_GAIN = 0.42f // purr gained per cat-width stroked the right way
private const val WRONG_WAY = 0.12f   // cat-widths of backwards stroke before it objects
private const val IDLE_FRAME_GAP_MS = 25L // left alone, about 30 frames a second

/**
 * How the cat feels. Distances are in fractions of the cat's drawn width; positive dx runs head → tail.
 * Plain state (no Compose) so it can be unit tested.
 */
internal class CatMood {
    /** 0..1, builds while stroked the right way and fades slowly on its own. */
    var purr = 0f
        private set

    /** 1 right after a wrong-way stroke, fading back to 0 over ~1.4 s. */
    var grumble = 0f
        private set

    private var run = 0f // signed length of the current run of same-direction movement
    private var grumbledThisStroke = false

    fun startStroke() {
        run = 0f
        grumbledThisStroke = false
    }

    /** Feed one drag step. Returns true if this step made the cat grumble. */
    fun stroke(dx: Float): Boolean {
        run = if (dx >= 0f) max(run, 0f) + dx else min(run, 0f) + dx
        // Diminishing returns near the top, and no purring while it's still cross.
        if (dx > 0f && grumble < 0.5f) purr = (purr + dx * STROKE_GAIN * (1f - 0.5f * purr)).coerceAtMost(1f)
        if (run < -WRONG_WAY && !grumbledThisStroke) {
            grumbledThisStroke = true
            grumble = 1f
            purr *= 0.45f
            return true
        }
        return false
    }

    fun update(dt: Float) {
        // ~20 s from full purr to silence; slower at the bottom so a content cat lingers.
        purr = max(0f, purr - dt * (0.018f + 0.03f * purr))
        grumble = max(0f, grumble - dt / 1.4f)
    }
}

/**
 * Loudness of the purr through one breath (0..1 phase, first half breathing in): it runs on both breaths,
 * dips as the breath turns, and the out-breath is fuller. Returns 0.25..1.
 */
internal fun purrEnvelope(phase: Float): Float {
    val swell = abs(sin(2f * PI.toFloat() * phase)).pow(0.6f)
    return if (phase < 0.5f) 0.25f + 0.55f * swell else 0.25f + 0.75f * swell
}

/** Vibration strength for a purr at [purr] (0..1) at breathing [envelope]. */
internal fun purrVibration(purr: Float, envelope: Float): Float =
    if (purr < 0.03f) 0f else purr.pow(0.8f) * envelope * 0.85f

/** Animation clocks that don't need to trigger recomposition on their own. */
private class CatAnim {
    var breath = 0f // 0..1 phase
    var tail = 0f   // 0..1 phase
    var chirp = 0f  // 1 on a head tap, fading to 0
    var brush = 0f  // 0..1 stroking speed, decays quickly
    var touching = false // a finger is on the cat

    fun advance(dt: Float, mood: CatMood) {
        // Resting breaths are slow; a purring cat breathes every ~1.8 s.
        breath = (breath + dt / (3.2f - 1.4f * mood.purr)) % 1f
        tail = (tail + dt * (0.3f + 1.1f * mood.grumble)) % 1f
        chirp = max(0f, chirp - dt / 0.7f)
        brush *= exp(-dt * 7f)
    }

    /** Being stroked, or still reacting to it: worth every display frame. */
    fun lively(mood: CatMood) = touching || brush > 0.02f || chirp > 0f || mood.grumble > 0f
}

// A warm ginger tabby.
private val Fur = Color(0xFFF2A65A)
private val Stripe = Color(0xFFDA8340)
private val Outline = Color(0xFFA2561F)
private val Cream = Color(0xFFFFE9CF)
private val EarPink = Color(0xFFF6B3AE)
private val NosePink = Color(0xFFE8857F)
private val Iris = Color(0xFF9CCC65)
private val Pupil = Color(0xFF2B2522)

private const val LINE = 0.008f // outline width, in cat-widths

@Composable
fun CatScreen(onBack: () -> Unit) {
    val mood = remember { CatMood() }
    val anim = remember { CatAnim() }
    var time by remember { mutableFloatStateOf(0f) }
    var hasStroked by rememberSaveable { mutableStateOf(false) }
    val hintAlpha by animateFloatAsState(if (hasStroked) 0f else 1f, label = "hint")
    val audio = rememberPcmStream { CatAudio() }
    val vibes = rememberVibes()

    // Frame loop: moods and clocks tick, the purr is handed to the synth, and the motor rumbles with each breath.
    // The cat never stops breathing, so the loop never idles, but left alone it only drifts, and about 30 frames a
    // second is plenty for that.
    val loop = rememberGameLoop { dt ->
        time += dt
        mood.update(dt)
        anim.advance(dt, mood)
        audio.purr = mood.purr
        audio.breath = anim.breath
        audio.brush = anim.brush
        vibes.buzz(purrVibration(mood.purr, purrEnvelope(anim.breath)))
        frameGapMs = if (anim.lively(mood)) 0L else IDLE_FRAME_GAP_MS
        true
    }

    ToolScaffold(title = "Purring Cat", onBack = onBack) { padding ->
        ToolBody(padding) {
            Box(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                anim.touching = true
                                loop.wake()
                                tryAwaitRelease()
                                anim.touching = false
                            },
                        ) { pos ->
                            val u = size.width.toFloat()
                            val hx = pos.x / u - 0.26f
                            val hy = pos.y / u - 0.37f
                            if (hx * hx + hy * hy < 0.17f * 0.17f) {
                                audio.chirp()
                                anim.chirp = 1f
                                vibes.pattern(longArrayOf(0, 12, 55, 14), floatArrayOf(0f, 0.35f, 0f, 0.45f))
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                mood.startStroke()
                                anim.touching = true
                                loop.wake()
                            },
                            onDragEnd = { anim.touching = false },
                            onDragCancel = { anim.touching = false },
                            onDrag = { change, d ->
                                change.consume()
                                val u = size.width.toFloat()
                                val p = change.position / u
                                if (p.x in 0.08f..0.97f && p.y in 0.12f..0.78f) {
                                    val dx = d.x / u
                                    anim.brush = (anim.brush + abs(dx) * 3f).coerceAtMost(1f)
                                    if (dx > 0f && mood.grumble < 0.5f) hasStroked = true
                                    if (mood.stroke(dx)) {
                                        audio.grumble()
                                        vibes.pattern(
                                            longArrayOf(0, 35, 25, 45, 25, 60),
                                            floatArrayOf(0f, 0.5f, 0f, 0.6f, 0f, 0.4f),
                                        )
                                    }
                                }
                            },
                        )
                    },
            ) {
                val shadow = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                Canvas(Modifier.fillMaxSize()) {
                    val t = time // read every frame so the cat redraws as it breathes
                    val purr = mood.purr
                    val grumble = mood.grumble
                    val sleepy = smoothstep((purr - 0.2f) / 0.6f)
                    val blink = if (purr < 0.35f && grumble == 0f && t % 4.6f < 0.14f) 1f else 0f
                    val closed = max(max(sleepy, blink), grumble * 0.5f) * (1f - 0.8f * anim.chirp)
                    val tailAmp = 1f + grumble
                    drawCat(
                        breathe = 0.5f - 0.5f * cos(2f * PI.toFloat() * anim.breath),
                        closed = closed,
                        earsFlat = smoothstep(grumble * 1.6f),
                        ruffle = grumble,
                        tailSwing = sin(2f * PI.toFloat() * anim.tail) * tailAmp,
                        tailLift = (0.5f + 0.5f * sin(2f * PI.toFloat() * anim.tail + 0.9f)) * tailAmp,
                        // A quizzical head tilt that rises and settles as the chirp fades.
                        headTilt = if (anim.chirp > 0f) -9f * sin(PI.toFloat() * (1f - anim.chirp)) else 0f,
                        blush = purr,
                        shadow = shadow,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Stroke from head to tail",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(hintAlpha),
            )
        }
    }
}


private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val m = 1f - t
    return p0 * (m * m * m) + p1 * (3f * m * m * t) + p2 * (3f * m * t * t) + p3 * (t * t * t)
}

/**
 * A loaf-pose tabby, side-on facing left with its face turned to us. Drawn in "cat-width" units: the canvas is
 * scaled so x runs 0..1 and y 0..0.8.
 */
private fun DrawScope.drawCat(
    breathe: Float,
    closed: Float,
    earsFlat: Float,
    ruffle: Float,
    tailSwing: Float,
    tailLift: Float,
    headTilt: Float,
    blush: Float,
    shadow: Color,
) {
    val u = size.width
    scale(scaleX = u, scaleY = u, pivot = Offset.Zero) {
        drawOval(shadow, topLeft = Offset(0.13f, 0.68f), size = Size(0.83f, 0.07f))

        // The whole cat rises a little with each breath.
        scale(scaleX = 1f + 0.006f * breathe, scaleY = 1f + 0.02f * breathe, pivot = Offset(0.55f, 0.71f)) {
            if (ruffle > 0.01f) drawRuffle(ruffle)

            val body = Path().apply {
                moveTo(0.26f, 0.70f)
                lineTo(0.80f, 0.70f)
                cubicTo(0.90f, 0.70f, 0.94f, 0.57f, 0.89f, 0.48f)
                cubicTo(0.84f, 0.38f, 0.72f, 0.33f, 0.58f, 0.33f)
                cubicTo(0.44f, 0.33f, 0.30f, 0.36f, 0.23f, 0.47f)
                cubicTo(0.18f, 0.56f, 0.19f, 0.70f, 0.26f, 0.70f)
                close()
            }
            drawPath(body, Fur)
            clipPath(body) {
                for (x0 in floatArrayOf(0.42f, 0.52f, 0.62f, 0.72f, 0.81f)) {
                    val stripe = Path().apply {
                        moveTo(x0, 0.29f)
                        quadraticTo(x0 + 0.035f, 0.39f, x0 + 0.01f, 0.45f + (x0 - 0.4f) * 0.1f)
                    }
                    drawPath(stripe, Stripe, style = Stroke(0.03f, cap = StrokeCap.Round))
                }
                drawOval(Cream, topLeft = Offset(0.17f, 0.55f), size = Size(0.2f, 0.18f))
            }
            drawPath(body, Outline, style = Stroke(LINE, join = StrokeJoin.Round))

            // Haunch: one line is enough to read the folded back leg.
            val haunch = Path().apply {
                moveTo(0.64f, 0.695f)
                cubicTo(0.62f, 0.56f, 0.76f, 0.47f, 0.87f, 0.55f)
            }
            drawPath(haunch, Outline.copy(alpha = 0.55f), style = Stroke(LINE * 0.8f, cap = StrokeCap.Round))

            drawPaw(Offset(0.19f, 0.655f))
            drawPaw(Offset(0.30f, 0.662f))
            drawTail(tailSwing, tailLift)

            rotate(headTilt, pivot = Offset(0.26f, 0.52f)) { drawHead(closed, earsFlat, blush) }
        }
    }
}

/** Fur standing on end along the back, drawn behind the body so only the spikes show. */
private fun DrawScope.drawRuffle(amount: Float) {
    val cx = 0.58f
    val cy = 0.585f
    val rx = 0.33f
    val ry = 0.255f
    val a0 = 1.2f * PI.toFloat()
    val a1 = 1.93f * PI.toFloat()
    val points = 26
    val spikes = Path().apply {
        moveTo(cx, cy)
        for (i in 0..points) {
            val a = a0 + (a1 - a0) * i / points
            // Odd points are the tips; vary their length so it looks tufty rather than sawn.
            val out = if (i % 2 == 1) 1f + amount * (0.12f + 0.06f * ((i * 37) % 5) / 4f) else 0.96f
            lineTo(cx + rx * out * cos(a), cy + ry * out * sin(a))
        }
        close()
    }
    drawPath(spikes, Outline, style = Stroke(LINE, join = StrokeJoin.Round))
    drawPath(spikes, Fur)
}

private fun DrawScope.drawPaw(topLeft: Offset) {
    val size = Size(0.1f, 0.052f)
    drawOval(Outline, topLeft, size, style = Stroke(LINE))
    drawOval(Cream, topLeft, size)
    for (f in floatArrayOf(0.37f, 0.63f)) {
        val x = topLeft.x + size.width * f
        drawLine(
            Outline.copy(alpha = 0.6f),
            Offset(x, topLeft.y + size.height * 0.55f),
            Offset(x, topLeft.y + size.height * 0.92f),
            strokeWidth = LINE * 0.7f,
            cap = StrokeCap.Round,
        )
    }
}

/** Tail: out from behind the rump, around along the ground in front, tip curling up and swishing. */
private fun DrawScope.drawTail(swing: Float, lift: Float) {
    val tip = Offset(0.575f + 0.035f * swing, 0.72f - 0.035f * lift)
    val a0 = Offset(0.86f, 0.74f)
    val a1 = Offset(0.76f, 0.755f)
    val a2 = Offset(tip.x + 0.07f, 0.75f)
    val tail = Path().apply {
        moveTo(0.86f, 0.625f)
        cubicTo(0.93f, 0.64f, 0.96f, 0.72f, a0.x, a0.y)
        cubicTo(a1.x, a1.y, a2.x, a2.y, tip.x, tip.y)
    }
    // No outline where the tail leaves the body, so it seems to come from behind the rump.
    val root = Path().apply { addOval(Rect(center = Offset(0.86f, 0.625f), radius = 0.045f)) }
    clipPath(root, clipOp = ClipOp.Difference) {
        drawPath(tail, Outline, style = Stroke(0.05f + 2 * LINE, cap = StrokeCap.Round))
    }
    drawPath(tail, Fur, style = Stroke(0.05f, cap = StrokeCap.Round))
    // Darker tabby tip.
    val m = cubic(a0, a1, a2, tip, 0.72f)
    val n = cubic(a0, a1, a2, tip, 0.86f)
    val tipPath = Path().apply {
        moveTo(m.x, m.y)
        quadraticTo(n.x, n.y, tip.x, tip.y)
    }
    drawPath(tipPath, Stripe, style = Stroke(0.05f, cap = StrokeCap.Round))
}

private fun DrawScope.drawHead(closed: Float, earsFlat: Float, blush: Float) {
    // Ears, drawn first so the head overlaps their bases. Flattened ears swing out sideways.
    val leftEar = listOf(Offset(0.125f, 0.33f), lerp(Offset(0.13f, 0.155f), Offset(0.035f, 0.265f), earsFlat), Offset(0.225f, 0.265f))
    val rightEar = listOf(Offset(0.295f, 0.265f), lerp(Offset(0.39f, 0.155f), Offset(0.485f, 0.265f), earsFlat), Offset(0.395f, 0.33f))
    val ears = listOf(leftEar, rightEar).map { pts ->
        Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            close()
        }
    }
    ears.forEach { drawPath(it, Outline, style = Stroke(2 * LINE, join = StrokeJoin.Round)) }
    ears.forEach { drawPath(it, Fur) }
    listOf(leftEar, rightEar).forEach { pts ->
        val c = Offset((pts[0].x + pts[1].x + pts[2].x) / 3f, (pts[0].y + pts[1].y + pts[2].y) / 3f)
        val inner = Path().apply {
            val p = pts.map { c + (it - c) * 0.55f + Offset(0f, 0.008f) }
            moveTo(p[0].x, p[0].y)
            lineTo(p[1].x, p[1].y)
            lineTo(p[2].x, p[2].y)
            close()
        }
        drawPath(inner, EarPink)
        drawPath(inner, EarPink, style = Stroke(LINE, join = StrokeJoin.Round))
    }

    // Head: a wide oval plus two cheek tufts. Outline everything first, then fill, so only the union is outlined.
    val headTl = Offset(0.11f, 0.25f)
    val headSize = Size(0.30f, 0.26f)
    val cheekSize = Size(0.12f, 0.13f)
    val cheeks = listOf(Offset(0.095f, 0.36f), Offset(0.305f, 0.36f))
    drawOval(Outline, headTl, headSize, style = Stroke(2 * LINE))
    cheeks.forEach { drawOval(Outline, it, cheekSize, style = Stroke(2 * LINE)) }
    drawOval(Fur, headTl, headSize)
    cheeks.forEach { drawOval(Fur, it, cheekSize) }

    // Tabby "M" on the forehead.
    for ((x0, x1) in listOf(0.24f to 0.245f, 0.26f to 0.26f, 0.28f to 0.275f)) {
        drawLine(Stripe, Offset(x0, 0.262f), Offset(x1, 0.302f), strokeWidth = 0.012f, cap = StrokeCap.Round)
    }

    // Muzzle, chin and a blush that deepens as it relaxes.
    drawOval(Cream, Offset(0.215f, 0.4275f), Size(0.05f, 0.045f))
    drawOval(Cream, Offset(0.255f, 0.4275f), Size(0.05f, 0.045f))
    drawOval(Cream, Offset(0.235f, 0.465f), Size(0.05f, 0.03f))
    if (blush > 0.05f) {
        drawCircle(EarPink.copy(alpha = 0.45f * blush), 0.02f, Offset(0.175f, 0.435f))
        drawCircle(EarPink.copy(alpha = 0.45f * blush), 0.02f, Offset(0.345f, 0.435f))
    }

    drawEye(0.205f, 0.385f, closed)
    drawEye(0.315f, 0.385f, closed)

    val nose = Path().apply {
        moveTo(0.245f, 0.428f)
        lineTo(0.275f, 0.428f)
        lineTo(0.26f, 0.444f)
        close()
    }
    drawPath(nose, NosePink)
    drawPath(nose, NosePink, style = Stroke(0.008f, join = StrokeJoin.Round))

    // "ω" mouth.
    val mouth = Path().apply {
        moveTo(0.26f, 0.446f)
        lineTo(0.26f, 0.456f)
        quadraticTo(0.252f, 0.47f, 0.24f, 0.461f)
        moveTo(0.26f, 0.456f)
        quadraticTo(0.268f, 0.47f, 0.28f, 0.461f)
    }
    drawPath(mouth, Outline, style = Stroke(0.006f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Whiskers, mirrored about the nose.
    val whisker = Outline.copy(alpha = 0.55f)
    for ((y0, y1) in listOf(0.445f to 0.425f, 0.452f to 0.452f, 0.459f to 0.48f)) {
        drawLine(whisker, Offset(0.215f, y0), Offset(0.105f, y1), strokeWidth = 0.004f, cap = StrokeCap.Round)
        drawLine(whisker, Offset(0.305f, y0), Offset(0.415f, y1), strokeWidth = 0.004f, cap = StrokeCap.Round)
    }
}

/** An almond-green eye with a slit pupil; [closed] 0..1 lowers a furry lid, ending in a content "‿". */
private fun DrawScope.drawEye(cx: Float, cy: Float, closed: Float) {
    val w = 0.052f
    val h = 0.062f
    val tl = Offset(cx - w / 2, cy - h / 2)
    val size = Size(w, h)
    if (closed < 0.97f) {
        val eye = Path().apply { addOval(Rect(tl, size)) }
        clipPath(eye) {
            drawRect(Iris, tl, size)
            drawOval(Pupil, Offset(cx - w * 0.19f, cy - h * 0.42f), Size(w * 0.38f, h * 0.84f))
            drawCircle(Color.White, w * 0.13f, Offset(cx - w * 0.17f, cy - h * 0.2f))
            if (closed > 0f) {
                drawRect(Fur, tl, Size(w, h * closed))
                val lidY = tl.y + h * closed
                drawLine(Outline, Offset(cx - w, lidY), Offset(cx + w, lidY), strokeWidth = 0.007f)
            }
        }
        val rim = (1f - closed).pow(2)
        drawOval(Outline, tl, size, alpha = rim, style = Stroke(0.005f))
    }
    val shut = ((closed - 0.75f) / 0.25f).coerceIn(0f, 1f)
    if (shut > 0f) {
        val arc = Path().apply {
            moveTo(cx - w * 0.6f, cy + h * 0.1f)
            quadraticTo(cx, cy + h * 0.55f, cx + w * 0.6f, cy + h * 0.1f)
        }
        drawPath(arc, Outline, alpha = shut, style = Stroke(0.008f, cap = StrokeCap.Round))
    }
}
