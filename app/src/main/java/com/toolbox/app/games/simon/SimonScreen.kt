package com.toolbox.app.games.simon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay

private enum class Phase { Idle, Showing, Input, Over }

private val PAD_COLORS = listOf(GoogleColors.Green, GoogleColors.Red, GoogleColors.Yellow, GoogleColors.Blue)

/** How long a pad sounds when the player presses it. */
private const val PRESS_TONE_MS = 280

/** A round's pads as TalkBack says them while they play: "Green, red, blue". */
internal fun sequenceSpoken(sequence: List<Int>): String =
    sequence.mapIndexed { i, pad -> if (i == 0) PAD_NAMES[pad] else PAD_NAMES[pad].lowercase() }.joinToString(", ")

@Composable
fun SimonScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()
    val synth = rememberToneSynth()
    val (best, submitBest) = rememberBestScore("simon-round")
    val guard = rememberGameGuard()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var phase by rememberSaveable { mutableStateOf(Phase.Idle) }
    var encoded by rememberSaveable { mutableStateOf("") }
    val game = SimonGame.decode(encoded)
    var lit by remember { mutableIntStateOf(-1) } // pad lit by playback or the game-over hint
    var held by remember { mutableIntStateOf(-1) } // pad under the player's finger
    var missed by rememberSaveable { mutableIntStateOf(-1) }

    fun start() {
        synth.silence()
        haptics.click()
        encoded = SimonGame.start().encode()
        missed = -1
        phase = Phase.Showing
    }

    fun press(pad: Int) {
        val g = game ?: return
        if (phase != Phase.Input) return
        val (next, result) = g.press(pad)
        when (result) {
            PressResult.Wrong -> {
                synth.silence()
                synth.buzz(600)
                haptics.heavy()
                missed = g.expected ?: -1
                submitBest(g.completed)
                phase = Phase.Over
            }
            PressResult.RoundComplete -> {
                synth.play(PAD_TONES_HZ[pad], PRESS_TONE_MS)
                haptics.click()
                submitBest(g.round)
                encoded = next.extended().encode()
                phase = Phase.Showing
            }
            PressResult.Correct -> {
                synth.play(PAD_TONES_HZ[pad], PRESS_TONE_MS)
                haptics.tick()
                encoded = next.encode()
            }
        }
    }

    // Leaving the app mid-round means the sequence is shown again on return, and you repeat it from the start.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val g = game
        if (g != null && (phase == Phase.Showing || phase == Phase.Input)) {
            encoded = g.copy(inputIndex = 0).encode()
            phase = Phase.Showing
            lit = -1
            held = -1
        }
    }

    // Playback. Input is locked (phase != Input) until the whole sequence has been shown.
    // Keyed on the encoded game too, so a restored or restarted game replays from the top. It only plays while the
    // app is in view: leaving stops it, and coming back starts it again from the beginning. The same goes for the
    // "Start a new game?" question, so cancelling it doesn't leave you having missed the sequence.
    LaunchedEffect(phase, encoded, lifecycle, guard.isAsking) {
        val g = game
        if (guard.isAsking) lit = -1
        if (phase != Phase.Showing || g == null || guard.isAsking) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            lit = -1
            delay(if (g.round == 1) 500 else 900)
            held = -1 // in case a finger is still resting on the last pad
            // The lights can't be seen with TalkBack, so it says the whole round once as it starts: one announcement
            // keeps up where one per pad would trip over each other at speed.
            say(sequenceSpoken(g.sequence))
            val tone = SimonGame.toneMs(g.round)
            val gap = SimonGame.gapMs(g.round)
            for (pad in g.sequence) {
                lit = pad
                synth.play(PAD_TONES_HZ[pad], tone)
                delay(tone.toLong())
                lit = -1
                delay(gap.toLong())
            }
            phase = Phase.Input
        }
    }

    // After a mistake, blink the pad that should have been pressed.
    LaunchedEffect(phase, missed) {
        if (phase != Phase.Over || missed < 0) return@LaunchedEffect
        delay(500)
        held = -1
        repeat(3) {
            lit = missed; delay(260)
            lit = -1; delay(200)
        }
    }

    val round = game?.round ?: 0
    ToolScaffold(
        title = "Simon",
        onBack = onBack,
        actions = {
            NewGameButton(
                onClick = { guard.run((phase == Phase.Showing || phase == Phase.Input) && round > 1) { start() } },
                enabled = phase != Phase.Idle,
            )
        },
    ) { padding ->
        ToolBody(padding) {
            // The best, like the end message, counts rounds repeated in full: reaching round 5 means 4 repeated.
            Text(
                when (phase) {
                    Phase.Idle -> if (best > 0) "Best $best" else "Simon says…"
                    Phase.Over -> repeatedText(game?.completed ?: 0) + if (best > 0) "  ·  Best $best" else ""
                    else -> "Round $round" + if (best > 0) "  ·  Best $best" else ""
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                when (phase) {
                    Phase.Idle -> "Watch the pattern, then repeat it"
                    Phase.Showing -> if (SimonGame.speedsUpAt(round)) "Faster now! Watch…" else "Watch…"
                    Phase.Input -> "Your turn: ${game?.inputIndex ?: 0} of $round"
                    Phase.Over -> if (missed >= 0) "It was ${PAD_NAMES[missed].lowercase()}" else " "
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (phase == Phase.Over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                    // The score is in the line above, which isn't read out as it changes, so the end says it here.
                    if (phase == Phase.Over) {
                        contentDescription = listOfNotNull(
                            if (missed >= 0) "It was ${PAD_NAMES[missed].lowercase()}" else null,
                            repeatedText(game?.completed ?: 0),
                        ).joinToString(". ")
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
            Board(
                lit = if (held >= 0) held else lit,
                enabled = phase == Phase.Input,
                hub = {
                    Hub(phase, round, onStart = ::start)
                },
                onDown = { pad ->
                    held = pad
                    press(pad)
                },
                onUp = { held = -1 },
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            )
        }
    }
    GameGuardDialog(guard)
}

private fun repeatedText(completed: Int) = when (completed) {
    0 -> "No rounds repeated"
    1 -> "You repeated 1 round"
    else -> "You repeated $completed rounds"
}

@Composable
private fun Board(
    lit: Int,
    enabled: Boolean,
    hub: @Composable () -> Unit,
    onDown: (Int) -> Unit,
    onUp: () -> Unit,
    modifier: Modifier,
) {
    val gap = 10.dp
    val outer = 100
    val inner = 10
    // Each pad is a quarter of a disc: fully rounded on its outer corner.
    val shapes = listOf(
        RoundedCornerShape(outer, inner, inner, inner),
        RoundedCornerShape(inner, outer, inner, inner),
        RoundedCornerShape(inner, inner, inner, outer),
        RoundedCornerShape(inner, inner, outer, inner),
    )
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until 2) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until 2) {
                        val pad = r * 2 + c
                        Pad(
                            color = PAD_COLORS[pad],
                            name = PAD_NAMES[pad],
                            lit = lit == pad,
                            enabled = enabled,
                            shape = shapes[pad],
                            onDown = { onDown(pad) },
                            onUp = onUp,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize(0.4f)) { hub() }
    }
}

@Composable
private fun Pad(
    color: Color,
    name: String,
    lit: Boolean,
    enabled: Boolean,
    shape: RoundedCornerShape,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier,
) {
    // Light instantly, fade out slowly, like the original's lamps.
    val glow by animateFloatAsState(if (lit) 1f else 0f, tween(if (lit) 30 else 220), label = "glow")
    // Dark enough that a lit pad stands out from it by 3:1, blue included.
    val dim = lerp(color, Color.Black, 0.65f)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDown by rememberUpdatedState(onDown)
    val currentUp by rememberUpdatedState(onUp)
    Box(
        modifier
            .graphicsLayer {
                val s = 1f - 0.025f * glow
                scaleX = s; scaleY = s
            }
            .clip(shape)
            .drawBehind {
                drawRect(lerp(dim, color, glow))
                if (glow > 0f) {
                    drawRect(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * glow), Color.Transparent), center, size.maxDimension * 0.7f),
                    )
                }
            }
            .semantics {
                contentDescription = name
                role = Role.Button
                if (!enabled) disabled()
                onClick(label = "Press $name") { onDown(); onUp(); true }
            }
            // Keyed on Unit so a gesture survives the phase change its own press causes (the finger can
            // still be down when the round ends); the latest callbacks are read through updated state.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    if (!currentEnabled) return@awaitEachGesture
                    currentDown()
                    waitForUpOrCancellation()
                    currentUp()
                }
            },
    )
}

@Composable
private fun Hub(phase: Phase, round: Int, onStart: () -> Unit) {
    val canStart = phase == Phase.Idle || phase == Phase.Over
    Surface(
        onClick = onStart,
        enabled = canStart,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .border(6.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
    ) {
        // The hub is part of the toy and doesn't grow, so its words keep their size at large font scales (the round
        // is also in the line above the board, which does grow).
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (phase) {
                        Phase.Idle -> Text("Start", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Phase.Over -> {
                            Text("Game over", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            Text("Again", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        else -> {
                            Text("ROUND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$round", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
