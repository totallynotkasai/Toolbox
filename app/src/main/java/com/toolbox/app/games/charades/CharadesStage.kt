package com.toolbox.app.games.charades

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.theme.GoogleColors
import com.toolbox.app.ui.theme.ink

// A round is white on a deep blue in both themes, so it reads the same across a room, and flashes green for got it
// and orange for a pass.
private val ROUND = Color(0xFF1A3D8F)
private val GOT = Color(0xFF1E8E3E)
private val PASS = Color(0xFFD35400)
private val LAST_SECONDS = Color(0xFFFFD54F)

/** The sideways part of the game: getting ready, the count-in, the cards, a pause and the results. */
@Composable
internal fun RoundStage(
    stage: Stage,
    round: CharadesRound,
    deckName: String,
    count: Int,
    flash: Boolean?,
    tiltWorks: Boolean,
    onStart: () -> Unit,
    onAnswer: (got: Boolean) -> Unit,
    onPause: () -> Unit,
    onClose: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onPlayAgain: () -> Unit,
    onChangeDeck: () -> Unit,
) {
    when (stage) {
        Stage.Ready -> Ready(round, deckName, tiltWorks, onStart, onClose)
        Stage.CountIn -> Backdrop(ROUND, "Get ready") {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Get ready", style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
                Text(
                    "$count",
                    fontSize = 160.sp,
                    lineHeight = 170.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Stage.Playing -> Playing(round, flash, onAnswer, onPause)
        Stage.Paused -> Backdrop(ROUND, "Paused") {
            Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Paused", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(
                    "${secondsLeft(round.secondsLeft)} · ${round.score} got",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onEnd, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("End round") }
                    Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ROUND)) { Text("Resume") }
                }
            }
        }
        Stage.Results -> Results(round, deckName, onPlayAgain, onChangeDeck)
        Stage.Setup, Stage.Editor -> Unit
    }
}

/**
 * A full-screen colour with its content clear of any camera cut-out, and a [title] for TalkBack to say on arriving.
 * [underlay] goes under the content and fills the whole screen, cut-out and all.
 */
@Composable
private fun Backdrop(
    color: Color,
    title: String,
    modifier: Modifier = Modifier,
    underlay: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(color).semantics { paneTitle = title }.then(modifier)) {
        underlay()
        Box(Modifier.fillMaxSize().safeDrawingPadding(), content = content)
    }
}

internal fun secondsLeft(s: Int) = if (s == 1) "1 second left" else "$s seconds left"

/** "Hold the phone on your forehead": the round counts in once it's held upright, or on a tap anywhere. */
@Composable
private fun Ready(round: CharadesRound, deckName: String, tiltWorks: Boolean, onStart: () -> Unit, onClose: () -> Unit) {
    Backdrop(ROUND, "Get ready", Modifier.clickable(onClickLabel = "Start", onClick = onStart)) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(
                if (round.started) Icons.Filled.Pause else Icons.Filled.Close,
                contentDescription = if (round.started) "Pause" else "Back to decks",
                tint = Color.White,
            )
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (round.started) "$deckName · ${secondsLeft(round.secondsLeft)}" else deckName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hold the phone on your forehead",
                style = MaterialTheme.typography.displaySmall.copy(lineBreak = LineBreak.Heading),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (tiltWorks) "Sideways, with the screen facing out. It starts when you hold it still, or tap to start."
                else "Sideways, with the screen facing out. Tap to start.",
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The card, as big as it fits, and the time left. The left half of the screen is pass and the right half got it, as
 * seen from the front, which is also how TalkBack finds them. After each card the screen flashes for a moment.
 */
@Composable
private fun Playing(round: CharadesRound, flash: Boolean?, onAnswer: (Boolean) -> Unit, onPause: () -> Unit) {
    val color = when (flash) {
        true -> GOT
        false -> PASS
        null -> ROUND
    }
    // The halves reach right to the edges, so a tap by the camera cut-out still counts.
    val halves = @Composable {
        Row(Modifier.fillMaxSize()) {
            Half("Pass", Modifier.weight(1f)) { onAnswer(false) }
            Half("Got it", Modifier.weight(1f)) { onAnswer(true) }
        }
    }
    Backdrop(color, "Round", underlay = halves) {
        val card = round.current ?: ""
        Text(
            when (flash) {
                true -> "Got it!"
                false -> "Pass"
                null -> card
            },
            // A line height in ems, so it grows with the auto-sized text and the lines never overlap.
            style = MaterialTheme.typography.displayLarge.copy(lineBreak = LineBreak.Heading, lineHeight = 1.1.em),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 120.sp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 56.dp)
                // Each new card is read out, and the flash isn't, since "Got it" or "Passed" is said for it.
                .then(if (flash == null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier.clearAndSetSemantics { }),
        )
        val s = round.secondsLeft
        Text(
            "${s / 60}:${(s % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (s <= CharadesRound.TICKS) LAST_SECONDS else Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clearAndSetSemantics { contentDescription = secondsLeft(s) },
        )
        IconButton(onClick = onPause, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
        }
        Text(
            "‹ Pass",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).clearAndSetSemantics { },
        )
        Text(
            "Got it ›",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).clearAndSetSemantics { },
        )
    }
}

/** Half the screen as one big button, with no ripple: the flash that follows is the feedback. */
@Composable
private fun Half(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
    )
}

/** The score and every card in the order it came up, beside Play again; stacked instead on a screen that's upright. */
@Composable
private fun Results(round: CharadesRound, deckName: String, onPlayAgain: () -> Unit, onChangeDeck: () -> Unit) {
    Surface(Modifier.fillMaxSize().semantics { paneTitle = "Results" }, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val summary = @Composable { modifier: Modifier ->
                Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        if (round.timeUp) "Time's up!" else "Round over",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "You got ${round.score}",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                    Text(
                        "${round.passed} passed · $deckName",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onPlayAgain) { Text("Play again") }
                    TextButton(onClick = onChangeDeck) { Text("Change deck") }
                }
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    summary(Modifier.weight(0.45f).fillMaxHeight())
                    Spacer(Modifier.width(16.dp))
                    CardList(round.played, Modifier.weight(0.55f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    summary(Modifier.padding(vertical = 16.dp))
                    CardList(round.played, Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CardList(played: List<Played>, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        if (played.isEmpty()) {
            item { Text("No cards this round", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }
        itemsIndexed(played) { _, p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clearAndSetSemantics { contentDescription = "${p.card}, ${if (p.got) "got it" else "passed"}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (p.got) Icons.Filled.Check else Icons.Filled.SkipNext,
                    contentDescription = null,
                    tint = if (p.got) GoogleColors.Green.ink() else GoogleColors.Yellow.ink(),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    p.card,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (p.got) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
