package com.toolbox.app.games.twentyfortyeight

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.GameFooter
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.accessibilityActions
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberScreenReaderOn
import kotlin.math.abs

/** The board as TalkBack reads it, row by row: "Row 1: 2, empty, 4, empty. Row 2: …". */
internal fun boardSpoken(board: List<Int>): String =
    (0 until N).joinToString(". ") { r ->
        "Row ${r + 1}: " + (0 until N).joinToString(", ") { c -> board[r * N + c].let { if (it == 0) "empty" else "$it" } }
    }

/**
 * What TalkBack says after a move from [before]: the biggest tile a merge made and the new [score] if it scored, then
 * where the new tile came in. [slid] is the board after sliding and before the new tile, [next] after it.
 */
internal fun moveSpoken(before: List<Int>, slid: List<Int>, next: List<Int>, score: Int, gained: Int): String {
    val parts = mutableListOf<String>()
    if (gained > 0) {
        // The biggest tile made is always one more of its value than before: a merge that used one up would have
        // made a bigger one still.
        val had = before.filter { it != 0 }.groupingBy { it }.eachCount()
        slid.filter { it != 0 }.groupingBy { it }.eachCount().filter { (v, n) -> n > (had[v] ?: 0) }.keys.maxOrNull()
            ?.let { parts += "Made $it" }
        parts += "Score $score"
    }
    next.indices.firstOrNull { slid[it] == 0 && next[it] != 0 }?.let { parts += "New ${next[it]} in row ${it / N + 1}, column ${it % N + 1}" }
    return parts.joinToString(". ")
}

@Composable
fun Game2048Screen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()
    val readerOn = rememberScreenReaderOn()
    val guard = rememberGameGuard()
    var board by rememberSaveable { mutableStateOf(newBoard()) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    val (best, submit) = rememberBestScore("2048")
    // The best from before this game, so the end can say whether it was beaten. The saved best itself is updated
    // after every move, so leaving mid-game never loses it.
    var bestBefore by rememberSaveable { mutableIntStateOf(best) }
    val over = !canMove(board)
    val won = board.any { it >= 2048 }

    fun newGame() {
        board = newBoard(); score = 0; bestBefore = best
    }

    // Called from a long-lived pointerInput, so read state fresh rather than via captured locals.
    fun swipe(m: Move) {
        if (!canMove(board)) return
        val before = board
        val (next, gained) = play(before, m) ?: run { say("Nothing moves that way"); return }
        board = next
        score += gained
        say(moveSpoken(before, move(before, m).first, next, score, gained))
        if (gained > 0) {
            haptics.tick()
            submit(score)
        }
        if (!canMove(next)) haptics.heavy()
    }

    ToolScaffold(
        title = "2048",
        onBack = onBack,
        actions = { NewGameButton(onClick = { guard.run(!over && score > 0) { newGame() } }, enabled = score > 0 || over) },
    ) { padding ->
        ToolBody(padding) {
            FlowRow(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Score $score", style = MaterialTheme.typography.titleLarge)
                Text("Best $best", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .pointerInput(Unit) {
                        var acc = Offset.Zero
                        var fired = false
                        detectDragGestures(
                            onDragStart = { acc = Offset.Zero; fired = false },
                            onDrag = { change, amount ->
                                change.consume()
                                acc += amount
                                if (!fired && acc.getDistance() > 32.dp.toPx()) {
                                    fired = true
                                    swipe(
                                        if (abs(acc.x) > abs(acc.y)) { if (acc.x > 0) Move.Right else Move.Left }
                                        else { if (acc.y > 0) Move.Down else Move.Up },
                                    )
                                }
                            },
                        )
                    }
                    // TalkBack reads the whole board at once, and moves through its actions menu.
                    .clearAndSetSemantics {
                        contentDescription = boardSpoken(board)
                        customActions = accessibilityActions(
                            "Move up" to { swipe(Move.Up) },
                            "Move down" to { swipe(Move.Down) },
                            "Move left" to { swipe(Move.Left) },
                            "Move right" to { swipe(Move.Right) },
                        )
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until N) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until N) Tile(board[r * N + c], Modifier.weight(1f).fillMaxSize())
                    }
                }
                if (over) {
                    Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)))
                }
            }
            Spacer(Modifier.height(16.dp))
            GameFooter(
                over = if (!over) null else GameOver(
                    if (won) "You made 2048!" else "No more moves",
                    if (score > bestBefore) "Score $score · New best!" else "Score $score · Best $best",
                ),
                turn = when {
                    won -> "You made 2048! Keep going."
                    readerOn -> "Use the actions menu to move tiles"
                    else -> "Swipe to combine matching tiles"
                },
                turnColor = null,
                onPlayAgain = ::newGame,
            )
        }
    }
    GameGuardDialog(guard)
}

private val TILE_COLORS = mapOf(
    2 to Color(0xFFE8F0FE), 4 to Color(0xFFD2E3FC), 8 to Color(0xFFFDE293), 16 to Color(0xFFFBBC04),
    32 to Color(0xFFF6AEA9), 64 to Color(0xFFEA4335), 128 to Color(0xFFA8DAB5), 256 to Color(0xFF34A853),
    512 to Color(0xFF8AB4F8), 1024 to Color(0xFF4285F4), 2048 to Color(0xFF1A73E8),
)

@Composable
private fun Tile(value: Int, modifier: Modifier) {
    val bg by animateColorAsState(
        if (value == 0) MaterialTheme.colorScheme.surfaceContainerHighest else TILE_COLORS[value] ?: Color(0xFF174EA6),
        label = "tile",
    )
    // Dark numbers on the pale tiles and the yellow 16; white on the rest, which all reach 3:1 for large bold text.
    val fg = if (value in setOf(2, 4, 8, 16, 32, 128, 512)) Color(0xFF202124) else Color.White
    Box(modifier.clip(MaterialTheme.shapes.small).background(bg), contentAlignment = Alignment.Center) {
        if (value != 0) {
            val size = when { value < 100 -> 32.sp; value < 1000 -> 26.sp; else -> 20.sp }
            // Large text grows the number only as far as the tile has room.
            Text(
                "$value",
                color = fg,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = size),
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
