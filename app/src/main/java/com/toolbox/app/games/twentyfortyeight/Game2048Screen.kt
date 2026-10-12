package com.toolbox.app.games.twentyfortyeight

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.GameFooter
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberHaptics
import kotlin.math.abs

@Composable
fun Game2048Screen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
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
        val (next, gained) = play(board, m) ?: return
        board = next
        score += gained
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
            Row(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                turn = if (won) "You made 2048! Keep going." else "Swipe to combine matching tiles",
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
    val fg = if (value in setOf(2, 4, 8, 32, 128, 512)) Color(0xFF202124) else Color.White
    Box(modifier.clip(MaterialTheme.shapes.small).background(bg), contentAlignment = Alignment.Center) {
        if (value != 0) {
            Text(
                "$value",
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = when { value < 100 -> 32.sp; value < 1000 -> 26.sp; else -> 20.sp },
            )
        }
    }
}
