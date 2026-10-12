package com.toolbox.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Who a two-sided game is played against. [key] is what's saved: Solo keeps "Easy", its name from when there were
 * difficulty levels, so the modes and win tallies saved under it carry on.
 */
enum class GameMode(val key: String, val label: String) {
    Solo("Easy", "Solo"),
    TwoPlayer("TwoPlayer", "2 players");

    val vsComputer: Boolean get() = this == Solo
}

/** The saved [GameMode] for a game, Solo until another is chosen. */
@Composable
fun rememberGameMode(key: String, default: GameMode = GameMode.Solo): MutableState<GameMode> =
    rememberEnumPref(key, default, GameMode.entries, GameMode::key)

/** "Solo" and "2 players" chips. [onSelect] is only called for the mode that isn't already chosen. */
@Composable
fun GameModeChips(mode: GameMode, onSelect: (GameMode) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in GameMode.entries) {
            FilterChip(
                selected = mode == m,
                onClick = { if (mode != m) onSelect(m) },
                label = { Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                enabled = enabled,
            )
        }
    }
}

/** The top-bar button that starts a new game. */
@Composable
fun NewGameButton(onClick: () -> Unit, enabled: Boolean = true, description: String = "New game") {
    IconButton(onClick = onClick, enabled = enabled) { Icon(Icons.Filled.RestartAlt, contentDescription = description) }
}

/** Whose turn it is, with their colour as a dot when there is one. Read out by TalkBack as it changes, hint and all. */
@Composable
fun TurnStatus(text: String, color: Color?, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
            if (color != null) {
                Box(Modifier.size(16.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** How a game ended: [title] such as "You win!", and an optional line under it. */
data class GameOver(val title: String, val detail: String? = null)

/** The result of a finished game and its button ("Play again" unless [action] says otherwise). */
@Composable
fun GameOverCard(over: GameOver, onAction: () -> Unit, modifier: Modifier = Modifier, action: String = "Play again", enabled: Boolean = true) {
    Surface(modifier.widthIn(max = 400.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // The title and detail are read out together when the card appears ("You lose. The word was CAT").
            Column(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(over.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (over.detail != null) {
                    Text(over.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAction, enabled = enabled) { Text(action) }
        }
    }
}

/**
 * The space under a board: the [TurnStatus] during a game, and the [GameOverCard] once it's [over]. The space always
 * keeps the size of a full card, so the board never jumps when a game ends or a new one starts.
 */
@Composable
fun GameFooter(
    over: GameOver?,
    turn: String,
    turnColor: Color?,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    action: String = "Play again",
) {
    val shown by animateFloatAsState(if (over != null) 1f else 0f, tween(220), label = "gameOver")
    Box(modifier.widthIn(max = 400.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // An invisible card with a detail line sets the height, whether or not the real one has a detail.
        GameOverCard(GameOver(" ", " "), onAction = {}, enabled = false, action = action, modifier = Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { })
        if (over != null) {
            GameOverCard(over, onAction = onPlayAgain, action = action, modifier = Modifier.graphicsLayer { alpha = shown })
        }
        TurnStatus(
            turn, turnColor, hint = hint,
            modifier = Modifier
                .graphicsLayer { alpha = 1f - shown }
                .then(if (over != null) Modifier.clearAndSetSemantics { } else Modifier),
        )
    }
}

/**
 * Asks before throwing away a game that's under way. Starting a new game, or changing the mode or board, goes
 * through [run]; [GameGuardDialog] shows the question.
 */
@Stable
class GameGuard internal constructor() {
    internal class Ask(val title: String, val text: String, val confirm: String, val action: () -> Unit)

    internal var asking by mutableStateOf<Ask?>(null)

    /** Whether the question is on screen, for a real-time game to hold still behind it. */
    val isAsking: Boolean get() = asking != null

    /** Runs [action] at once if nothing would be lost, or once the player agrees when the game is [underWay]. */
    fun run(
        underWay: Boolean,
        title: String = "Start a new game?",
        text: String = "The game in progress will be lost.",
        confirm: String = "New game",
        action: () -> Unit,
    ) {
        if (underWay) asking = Ask(title, text, confirm, action) else action()
    }
}

@Composable
fun rememberGameGuard(): GameGuard = remember { GameGuard() }

@Composable
fun GameGuardDialog(guard: GameGuard) {
    val ask = guard.asking ?: return
    AlertDialog(
        onDismissRequest = { guard.asking = null },
        title = { Text(ask.title) },
        text = { Text(ask.text) },
        confirmButton = { TextButton(onClick = { guard.asking = null; ask.action() }) { Text(ask.confirm) } },
        dismissButton = { TextButton(onClick = { guard.asking = null }) { Text("Cancel") } },
    )
}

/** The top-bar "More" menu of a game that keeps a tally, holding "Reset scores". */
@Composable
fun ScoresMenu(onReset: () -> Unit, label: String = "Reset scores") {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(label) }, onClick = { onReset(); open = false })
        }
    }
}
