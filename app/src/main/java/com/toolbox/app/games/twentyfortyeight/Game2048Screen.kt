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
import androidx.compose.material3.Button
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
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberHaptics
import kotlin.math.abs

private const val N = 4

private fun spawn(board: List<Int>): List<Int> {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return board
    return board.toMutableList().also { it[empty.random()] = if (Math.random() < 0.9) 2 else 4 }
}

private fun newBoard() = spawn(spawn(List(N * N) { 0 }))

/** Slides one row toward index 0, merging equal neighbours once. Returns the new row and points scored. */
internal fun slide(row: List<Int>): Pair<List<Int>, Int> {
    val tiles = row.filter { it != 0 }
    val out = mutableListOf<Int>()
    var gained = 0
    var i = 0
    while (i < tiles.size) {
        if (i + 1 < tiles.size && tiles[i] == tiles[i + 1]) {
            out += tiles[i] * 2; gained += tiles[i] * 2; i += 2
        } else { out += tiles[i]; i++ }
    }
    while (out.size < N) out += 0
    return out to gained
}

internal enum class Move { Left, Right, Up, Down }

internal fun move(board: List<Int>, m: Move): Pair<List<Int>, Int> {
    val out = MutableList(N * N) { 0 }
    var gained = 0
    for (line in 0 until N) {
        // Index of the k-th cell along this line, ordered in the direction of travel.
        val idx = (0 until N).map { k ->
            when (m) {
                Move.Left -> line * N + k
                Move.Right -> line * N + (N - 1 - k)
                Move.Up -> k * N + line
                Move.Down -> (N - 1 - k) * N + line
            }
        }
        val (slid, g) = slide(idx.map { board[it] })
        gained += g
        idx.forEachIndexed { k, cell -> out[cell] = slid[k] }
    }
    return out to gained
}

private fun canMove(b: List<Int>) = Move.entries.any { move(b, it).first != b }

@Composable
fun Game2048Screen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    var board by rememberSaveable { mutableStateOf(newBoard()) }
    var score by rememberSaveable { mutableIntStateOf(0) }
    val (best, submit) = rememberBestScore("2048")
    val over = !canMove(board)
    val won = board.any { it >= 2048 }

    // Called from a long-lived pointerInput, so read state fresh rather than via captured locals.
    fun play(m: Move) {
        if (!canMove(board)) return
        val (next, gained) = move(board, m)
        if (next == board) return
        board = spawn(next)
        score += gained
        if (gained > 0) haptics.tick()
        if (!canMove(board)) submit(score)
    }

    ToolScaffold(title = "2048", onBack = onBack) { padding ->
        ToolBody(padding) {
            Row(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Score $score", style = MaterialTheme.typography.titleLarge)
                Text("Best ${maxOf(best, score)}", style = MaterialTheme.typography.titleLarge)
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
                                    play(
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
                    Box(
                        Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Game over", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(if (won) "You made 2048! Keep going." else " ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { board = newBoard(); score = 0 }) { Text("New game") }
            Spacer(Modifier.height(8.dp))
            Text("Swipe to combine matching tiles", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
