package com.toolbox.app.games.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val COLS = 17
private const val ROWS = 15

internal data class Cell(val x: Int, val y: Int)
internal enum class Dir(val dx: Int, val dy: Int) { Up(0, -1), Down(0, 1), Left(-1, 0), Right(1, 0) }

/** [Paused] is a game brought back after the screen was recreated: the board is kept, waiting for Resume. */
internal enum class State { Ready, Playing, Paused, Over }

internal class SnakeGame {
    var snake by mutableStateOf(listOf(Cell(4, ROWS / 2), Cell(3, ROWS / 2), Cell(2, ROWS / 2)))
    var food by mutableStateOf(Cell(12, ROWS / 2))
    var dir = Dir.Right
    val queued = ArrayDeque<Dir>() // buffered swipes so quick turns aren't lost between ticks
    var score by mutableIntStateOf(0)
    var state by mutableStateOf(State.Ready)

    fun reset() {
        snake = listOf(Cell(4, ROWS / 2), Cell(3, ROWS / 2), Cell(2, ROWS / 2))
        food = Cell(12, ROWS / 2); dir = Dir.Right; queued.clear(); score = 0
    }

    fun turn(d: Dir) {
        val last = queued.lastOrNull() ?: dir
        if (d != last && (d.dx + last.dx != 0 || d.dy + last.dy != 0) && queued.size < 3) queued.addLast(d)
    }

    /**
     * The board as ints for saved state: direction, state, score, food, then the snake from the head. Buffered turns
     * aren't kept, and a game in play comes back [State.Paused].
     */
    fun save(): IntArray = intArrayOf(dir.ordinal, state.ordinal, score, food.x, food.y) + snake.flatMap { listOf(it.x, it.y) }

    fun restore(saved: IntArray) {
        dir = Dir.entries[saved[0]]
        state = State.entries[saved[1]].let { if (it == State.Playing) State.Paused else it }
        score = saved[2]
        food = Cell(saved[3], saved[4])
        snake = (5 until saved.size step 2).map { Cell(saved[it], saved[it + 1]) }
        queued.clear()
    }

    /** Returns true if food was eaten. */
    fun step(): Boolean {
        queued.removeFirstOrNull()?.let { dir = it }
        val head = snake.first()
        // No walls: leaving one edge re-enters from the opposite side. Only biting yourself ends the game.
        val next = Cell((head.x + dir.dx + COLS) % COLS, (head.y + dir.dy + ROWS) % ROWS)
        val ate = next == food
        val body = if (ate) snake else snake.dropLast(1)
        if (next in body) { state = State.Over; return false }
        snake = listOf(next) + body
        if (ate) {
            score++
            val free = (0 until COLS).flatMap { x -> (0 until ROWS).map { y -> Cell(x, y) } } - snake.toSet()
            if (free.isEmpty()) state = State.Over else food = free.random()
        }
        return ate
    }
}

@Composable
fun SnakeScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val game = rememberSaveable(saver = Saver(save = { it.save() }, restore = { SnakeGame().apply { restore(it) } })) { SnakeGame() }
    val (best, submit) = rememberBestScore("snake")

    LaunchedEffect(game.state) {
        while (game.state == State.Playing) {
            delay((150 - game.score * 3).coerceAtLeast(70).toLong())
            if (game.step()) haptics.tick()
            if (game.state == State.Over) { haptics.heavy(); submit(game.score) }
        }
    }

    val light = Color(0xFFAAD751)
    val dark = Color(0xFFA2D149)

    ToolScaffold(title = "Snake", onBack = onBack) { padding ->
        // Swipes are read across the whole screen, not just the board, so a thumb anywhere can steer.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var acc = Offset.Zero
                    detectDragGestures(
                        onDragStart = { acc = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            acc += amount
                            if (acc.getDistance() > 24.dp.toPx()) {
                                game.turn(
                                    if (abs(acc.x) > abs(acc.y)) { if (acc.x > 0) Dir.Right else Dir.Left }
                                    else { if (acc.y > 0) Dir.Down else Dir.Up },
                                )
                                acc = Offset.Zero
                            }
                        },
                    )
                },
        ) {
        ToolBody(padding, scroll = false) { // a vertical swipe steers, so it mustn't scroll
            Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🍎 ${game.score}", style = MaterialTheme.typography.titleLarge)
                Text("🏆 $best", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .aspectRatio(COLS / ROWS.toFloat())
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(COLS / ROWS.toFloat())) {
                    val cw = size.width / COLS
                    val ch = size.height / ROWS
                    for (x in 0 until COLS) for (y in 0 until ROWS) {
                        drawRect(if ((x + y) % 2 == 0) light else dark, Offset(x * cw, y * ch), Size(cw + 1, ch + 1))
                    }
                    drawCircle(GoogleColors.Red, cw * 0.42f, Offset(game.food.x * cw + cw / 2, game.food.y * ch + ch / 2))
                    val n = game.snake.size
                    game.snake.forEachIndexed { i, c ->
                        // Head is Google blue; the body fades slightly toward the tail.
                        val shade = GoogleColors.Blue.copy(alpha = 1f - 0.35f * i / n.coerceAtLeast(1))
                        val inset = if (i == 0) 0f else cw * 0.06f
                        drawRoundRect(
                            shade,
                            Offset(c.x * cw + inset, c.y * ch + inset),
                            Size(cw - inset * 2, ch - inset * 2),
                            CornerRadius(cw * 0.3f),
                        )
                    }
                    val head = game.snake.first()
                    val eye = cw * 0.1f
                    drawCircle(Color.White, eye * 1.6f, Offset(head.x * cw + cw * 0.35f, head.y * ch + ch * 0.35f))
                    drawCircle(Color.White, eye * 1.6f, Offset(head.x * cw + cw * 0.65f, head.y * ch + ch * 0.35f))
                }
                if (game.state != State.Playing) {
                    Button(onClick = { if (game.state != State.Paused) game.reset(); game.state = State.Playing }) {
                        Text(
                            when (game.state) {
                                State.Over -> "Game over · Play again"
                                State.Paused -> "Resume"
                                else -> "Play"
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Swipe anywhere to steer · edges wrap around", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}
