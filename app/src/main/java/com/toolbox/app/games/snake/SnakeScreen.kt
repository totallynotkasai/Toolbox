package com.toolbox.app.games.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.toolbox.app.ui.common.GameFooter
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.abs

private val GameSaver = Saver<SnakeGame, IntArray>(save = { it.save() }, restore = { SnakeGame.restore(it) })

@Composable
fun SnakeScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val guard = rememberGameGuard()
    var game by rememberSaveable(stateSaver = GameSaver) { mutableStateOf(SnakeGame()) }
    val (best, submit) = rememberBestScore("snake")
    // Whether the game just finished beat the best from before it, decided when it ends.
    var newBest by rememberSaveable { mutableStateOf(false) }
    var sinceStep by remember { mutableFloatStateOf(0f) }

    fun play() {
        if (game.finished || game.state == SnakeGame.State.Ready) game = SnakeGame()
        game = game.copy(state = SnakeGame.State.Playing)
        newBest = false
    }

    fun pause() {
        if (game.state == SnakeGame.State.Playing) game = game.copy(state = SnakeGame.State.Paused)
    }

    // The loop only runs while playing and the app is in view; leaving the app pauses the game too, so it waits
    // for Resume rather than carrying on the moment you're back.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { pause() }
    rememberGameLoop(enabled = game.state == SnakeGame.State.Playing) { dt ->
        sinceStep += dt
        if (sinceStep >= game.tickSeconds) {
            sinceStep = 0f
            val (next, step) = game.step()
            game = next
            when (step) {
                SnakeGame.Step.Ate -> haptics.tick()
                SnakeGame.Step.Died, SnakeGame.Step.Won -> {
                    newBest = next.score > best
                    submit(next.score)
                    haptics.heavy()
                }
                SnakeGame.Step.Moved -> Unit
            }
        }
        game.state == SnakeGame.State.Playing
    }

    val light = Color(0xFFAAD751)
    val dark = Color(0xFFA2D149)
    val underWay = game.state == SnakeGame.State.Playing || game.state == SnakeGame.State.Paused

    ToolScaffold(
        title = "Snake",
        onBack = onBack,
        actions = {
            val playing = game.state == SnakeGame.State.Playing
            if (underWay) {
                IconButton(onClick = { if (playing) pause() else play() }) {
                    if (playing) Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    else Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                }
            }
            NewGameButton(
                onClick = { pause(); guard.run(underWay && game.score > 0) { game = SnakeGame(); newBest = false } },
                enabled = game.state != SnakeGame.State.Ready,
            )
        },
    ) { padding ->
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
                                if (game.state == SnakeGame.State.Playing) {
                                    game = game.turn(
                                        if (abs(acc.x) > abs(acc.y)) { if (acc.x > 0) Dir.Right else Dir.Left }
                                        else { if (acc.y > 0) Dir.Down else Dir.Up },
                                    )
                                }
                                acc = Offset.Zero
                            }
                        },
                    )
                },
        ) {
        ToolBody(padding, scroll = false) { // a vertical swipe steers, so it mustn't scroll
            Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Score ${game.score}", style = MaterialTheme.typography.titleLarge)
                Text("Best $best", style = MaterialTheme.typography.titleLarge)
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
                Board(game, light, dark)
                when (game.state) {
                    SnakeGame.State.Ready -> Button(onClick = ::play) { Text("Play") }
                    SnakeGame.State.Paused -> {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)))
                        Button(onClick = ::play) { Text("Resume") }
                    }
                    SnakeGame.State.Over, SnakeGame.State.Won ->
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)))
                    SnakeGame.State.Playing -> Unit
                }
            }
            Spacer(Modifier.height(16.dp))
            GameFooter(
                over = when (game.state) {
                    SnakeGame.State.Over -> GameOver("Game over", scoreLine(game.score, best, newBest))
                    SnakeGame.State.Won -> GameOver("You filled the board!", scoreLine(game.score, best, newBest))
                    else -> null
                },
                turn = if (game.state == SnakeGame.State.Paused) "Paused" else "Swipe anywhere to steer",
                turnColor = null,
                hint = "Edges wrap around",
                onPlayAgain = ::play,
            )
        }
        }
    }
    GameGuardDialog(guard)
}

private fun scoreLine(score: Int, best: Int, newBest: Boolean) = if (newBest) "Score $score · New best!" else "Score $score · Best $best"

@Composable
private fun Board(game: SnakeGame, light: Color, dark: Color) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(COLS / ROWS.toFloat())) {
        val cw = size.width / COLS
        val ch = size.height / ROWS
        for (x in 0 until COLS) for (y in 0 until ROWS) {
            drawRect(if ((x + y) % 2 == 0) light else dark, Offset(x * cw, y * ch), Size(cw + 1, ch + 1))
        }
        if (game.state != SnakeGame.State.Won) {
            drawCircle(GoogleColors.Red, cw * 0.42f, Offset(game.food.x * cw + cw / 2, game.food.y * ch + ch / 2))
        }
        val n = game.body.size
        game.body.forEachIndexed { i, c ->
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
        // The eyes sit on the leading side of the head, so they look where it's going.
        val head = game.head
        val eye = cw * 0.12f
        val ahead = 0.15f
        val side = 0.2f
        val cx = head.x * cw + cw / 2
        val cy = head.y * ch + ch / 2
        for (s in listOf(-1, 1)) {
            val ex = cx + (game.dir.dx * ahead + game.dir.dy * side * s) * cw
            val ey = cy + (game.dir.dy * ahead + game.dir.dx * side * s) * ch
            drawCircle(Color.White, eye, Offset(ex, ey))
        }
    }
}
