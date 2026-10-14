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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.accessibilityActions
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
private const val AUTO_STROKE = 0.6f       // cat-widths, head to tail, of a stroke done through TalkBack
private const val AUTO_STROKE_SPEED = 1.2f // cat-widths a second, an unhurried hand

/**
 * How the cat feels. Distances are in cat-widths (the width of its box, [CatShape.VIEW_W]); positive dx runs
 * head → tail.
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

/** How the cat seems, for TalkBack: in broad steps, so it isn't re-read as the purr fades by the frame. */
internal fun catMoodState(purr: Float, grumble: Float): String = when {
    grumble > 0.3f -> "Grumpy"
    purr > 0.6f -> "Purring loudly"
    purr >= 0.05f -> "Purring"
    else -> "Calm"
}

/** Animation clocks that don't need to trigger recomposition on their own. */
private class CatAnim {
    var breath = 0f // 0..1 phase
    var tail = 0f   // 0..1 phase
    var chirp = 0f  // 1 on a head tap, fading to 0
    var brush = 0f  // 0..1 stroking speed, decays quickly
    var touching = false // a finger is on the cat
    var autoStroke = 0f  // cat-widths still to go of a stroke asked for through TalkBack

    fun advance(dt: Float, mood: CatMood) {
        // Resting breaths are slow; a purring cat breathes every ~1.8 s.
        breath = (breath + dt / (3.2f - 1.4f * mood.purr)) % 1f
        tail = (tail + dt * (0.3f + 1.1f * mood.grumble)) % 1f
        chirp = max(0f, chirp - dt / 0.7f)
        brush *= exp(-dt * 7f)
    }

    /** Being stroked, or still reacting to it: worth every display frame. */
    fun lively(mood: CatMood) = touching || autoStroke > 0f || brush > 0.02f || chirp > 0f || mood.grumble > 0f
}

@Composable
fun CatScreen(onBack: () -> Unit) {
    val mood = remember { CatMood() }
    val anim = remember { CatAnim() }
    var time by remember { mutableFloatStateOf(0f) }
    var hasStroked by rememberSaveable { mutableStateOf(false) }
    var moodState by remember { mutableStateOf(catMoodState(0f, 0f)) }
    val hintAlpha by animateFloatAsState(if (hasStroked) 0f else 1f, label = "hint")
    val audio = rememberPcmStream { CatAudio() }
    val vibes = rememberVibes()

    // Frame loop: moods and clocks tick, the purr is handed to the synth, and the motor rumbles with each breath.
    // The cat never stops breathing, so the loop never idles, but left alone it only drifts, and about 30 frames a
    // second is plenty for that.
    val loop = rememberGameLoop { dt ->
        time += dt
        if (anim.autoStroke > 0f) {
            // A TalkBack stroke runs head to tail over a few frames, as a hand would.
            val dx = min(anim.autoStroke, dt * AUTO_STROKE_SPEED)
            anim.autoStroke -= dx
            anim.brush = (anim.brush + dx * 3f).coerceAtMost(1f)
            if (mood.grumble < 0.5f) hasStroked = true
            mood.stroke(dx)
        }
        mood.update(dt)
        // Set every frame, but an unchanged value doesn't recompose: only a step to another mood does.
        moodState = catMoodState(mood.purr, mood.grumble)
        anim.advance(dt, mood)
        audio.purr = mood.purr
        audio.breath = anim.breath
        audio.brush = anim.brush
        vibes.buzz(purrVibration(mood.purr, purrEnvelope(anim.breath)))
        frameGapMs = if (anim.lively(mood)) 0L else IDLE_FRAME_GAP_MS
        true
    }

    fun chirp() {
        audio.chirp()
        anim.chirp = 1f
        vibes.pattern(longArrayOf(0, 12, 55, 14), floatArrayOf(0f, 0.35f, 0f, 0.45f))
        loop.wake()
    }

    ToolScaffold(title = "Purring Cat", onBack = onBack) { padding ->
        ToolBody(padding) {
            val catState = moodState // read here, not in the semantics block, so a new mood reaches TalkBack
            Box(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .semantics {
                        contentDescription = "Cat"
                        stateDescription = catState
                        onClick(label = "Stroke") {
                            mood.startStroke()
                            anim.autoStroke = AUTO_STROKE
                            loop.wake()
                            true
                        }
                        customActions = accessibilityActions("Tap head" to { chirp() })
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                anim.touching = true
                                loop.wake()
                                tryAwaitRelease()
                                anim.touching = false
                            },
                        ) { pos ->
                            if (CatShape.onHead(CatShape.toCat(pos, size.width.toFloat(), size.height.toFloat()))) chirp()
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
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (CatShape.onCat(CatShape.toCat(change.position, w, h))) {
                                    val dx = d.x / (CatShape.VIEW_W * CatShape.scale(w, h))
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
                // Once faded out it isn't read either.
                modifier = Modifier.alpha(hintAlpha).then(if (hasStroked) Modifier.clearAndSetSemantics {} else Modifier),
            )
        }
    }
}
