package com.toolbox.app.games.reaction

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay
import kotlin.math.roundToLong
import kotlin.random.Random

const val REACTION_ROUNDS = 5

/** Faster than this is a guess, not a reaction (human visual reaction time bottoms out around 150 ms). */
const val MIN_REACTION_MS = 100L

/**
 * Reaction time from the vsync at which green reaches the screen to the finger going down.
 * Choreographer frame times use `System.nanoTime()`, and pointer events use `SystemClock.uptimeMillis()`;
 * both are CLOCK_MONOTONIC on Android, so they can be subtracted directly.
 */
fun reactionMs(goFrameNanos: Long, tapUptimeMs: Long): Long = tapUptimeMs - goFrameNanos / 1_000_000

fun reactionRating(ms: Long): String = when {
    ms < 200 -> "Lightning fast!"
    ms < 250 -> "Great"
    ms < 300 -> "Good"
    ms < 400 -> "Not bad"
    else -> "Keep practising"
}

private enum class Phase { Ready, Waiting, Go, Result, TooSoon, Done }

private val DARK_TEXT = Color(0xFF202124)
private val WAIT_RED = Color(0xFFC5221F)

@Composable
fun ReactionScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val (best, submit) = rememberBestScore("reaction-avg", lowerIsBetter = true)
    val guard = rememberGameGuard()
    var phase by rememberSaveable { mutableStateOf(Phase.Ready) }
    var times by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var attempt by rememberSaveable { mutableIntStateOf(0) } // restarts the wait even when phase is unchanged
    var newBest by rememberSaveable { mutableStateOf(false) }
    // Not saved: after a rotation mid-round, 0 makes the next tap count as "too soon" instead of a bogus time.
    var goNanos by remember { mutableLongStateOf(0L) }

    fun startRound() { goNanos = 0L; attempt++; phase = Phase.Waiting }

    fun onTap(uptimeMs: Long) {
        when (phase) {
            Phase.Ready, Phase.Result, Phase.TooSoon -> startRound()
            Phase.Done -> { times = emptyList(); newBest = false; startRound() }
            Phase.Waiting -> { phase = Phase.TooSoon; haptics.heavy() }
            Phase.Go -> {
                val ms = if (goNanos == 0L) 0L else reactionMs(goNanos, uptimeMs)
                if (ms < MIN_REACTION_MS) { phase = Phase.TooSoon; haptics.heavy(); return }
                haptics.click()
                times = times + ms
                if (times.size < REACTION_ROUNDS) {
                    phase = Phase.Result
                } else {
                    val avg = times.average().roundToLong().toInt()
                    newBest = best == 0 || avg < best
                    submit(avg)
                    phase = Phase.Done
                }
            }
        }
    }
    val currentOnTap by rememberUpdatedState(::onTap)

    // Keyed on attempt only: keying on phase would cancel this effect the moment it switches to Go.
    LaunchedEffect(attempt) {
        if (phase != Phase.Waiting) return@LaunchedEffect
        delay(Random.nextLong(1500, 4001))
        if (phase != Phase.Waiting) return@LaunchedEffect // tapped too soon in the meantime
        phase = Phase.Go
        // The first frame drawn green only starts rendering at this vsync; it reaches the screen a frame later. So
        // the clock starts at the next vsync, when green actually appears, rather than reading a frame slow.
        // A tap before then still reads as "too soon", since it can only be a guess.
        withFrameNanos { }
        withFrameNanos { goNanos = it }
    }

    val round = (times.size + if (phase == Phase.Result) 0 else 1).coerceAtMost(REACTION_ROUNDS)
    // Colours switch instantly: animating to green would blur the moment being measured. The red and green are darker
    // than Google's, so the white text on them is easy to read.
    val (panel, ink) = when (phase) {
        Phase.Waiting -> WAIT_RED to Color.White
        Phase.Go -> GoogleColors.GreenInk to Color.White
        Phase.TooSoon -> GoogleColors.Yellow to DARK_TEXT
        else -> GoogleColors.Blue to Color.White
    }

    ToolScaffold(
        title = "Reaction Time",
        onBack = onBack,
        actions = {
            NewGameButton(
                onClick = {
                    guard.run(times.isNotEmpty() && phase != Phase.Done, title = "Start over?", text = "The rounds so far will be lost.", confirm = "Start over") {
                        times = emptyList(); newBest = false; phase = Phase.Ready
                    }
                },
                enabled = phase != Phase.Ready || times.isNotEmpty(),
                description = "Start over",
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(panel)
                // React on finger-down, not on release, like a real reaction test.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        currentOnTap(down.uptimeMillis)
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "Reaction panel"
                    val label = when (phase) {
                        Phase.Ready -> "Start"
                        Phase.Waiting, Phase.Go -> "Tap"
                        Phase.TooSoon -> "Try again"
                        Phase.Result -> "Next round"
                        Phase.Done -> "Play again"
                    }
                    onClick(label) { currentOnTap(SystemClock.uptimeMillis()); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Read as one, so each change of phase is said whole.
            Column(
                Modifier.padding(24.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (phase) {
                    Phase.Ready -> {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = ink, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(16.dp))
                        Big("Tap to start", ink)
                        Small("When the screen turns green, tap as fast as you can. $REACTION_ROUNDS rounds.", ink)
                        if (best > 0) Small("Best average: $best ms", ink)
                    }
                    Phase.Waiting -> {
                        Big("Wait for green…", ink)
                        Small("Round $round of $REACTION_ROUNDS", ink)
                    }
                    Phase.Go -> Huge("TAP!", ink)
                    Phase.TooSoon -> {
                        Big("Too soon!", ink)
                        Small("Wait for green. Tap to try round $round again.", ink)
                    }
                    Phase.Result -> {
                        Huge("${times.last()} ms", ink)
                        Small(reactionRating(times.last()), ink)
                        Spacer(Modifier.height(16.dp))
                        Small("Tap for round ${times.size + 1} of $REACTION_ROUNDS", ink)
                    }
                    Phase.Done -> {
                        Small("Average", ink)
                        Huge("${times.average().roundToLong()} ms", ink)
                        Small(if (newBest) "New best!" else "Best average: $best ms", ink)
                        Spacer(Modifier.height(8.dp))
                        Small(times.joinToString("  ·  "), ink)
                        Spacer(Modifier.height(16.dp))
                        Small("Tap to play again", ink)
                    }
                }
                if (phase != Phase.Ready) {
                    Spacer(Modifier.height(24.dp))
                    RoundDots(done = times.size, ink = ink)
                }
            }
        }
    }
    GameGuardDialog(guard)
}

@Composable
private fun Huge(text: String, color: Color) =
    Text(text, color = color, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

@Composable
private fun Big(text: String, color: Color) =
    Text(text, color = color, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)

@Composable
private fun Small(text: String, color: Color) =
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )

@Composable
private fun RoundDots(done: Int, ink: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics { contentDescription = "$done of $REACTION_ROUNDS rounds done" },
    ) {
        repeat(REACTION_ROUNDS) { i ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (i < done) ink else ink.copy(alpha = 0.3f)),
            )
        }
    }
}
