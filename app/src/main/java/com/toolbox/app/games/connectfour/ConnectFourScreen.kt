package com.toolbox.app.games.connectfour

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.GameFooter
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameMode
import com.toolbox.app.ui.common.GameModeChips
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.Score
import com.toolbox.app.ui.common.ScoreRow
import com.toolbox.app.ui.common.ScoresMenu
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameMode
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import kotlin.math.sqrt

private val RED = GoogleColors.Red
private val YELLOW = GoogleColors.Yellow
private fun discColor(player: Int) = if (player == 1) RED else YELLOW
private fun colorName(player: Int) = if (player == 1) "Red" else "Yellow"

@Composable
fun ConnectFourScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val guard = rememberGameGuard()
    var mode by rememberGameMode("connect4_mode")
    // Per mode, as "a,b,draws": you/CPU against the computer, red/yellow for two players.
    val tally = rememberTally("connect4_tally_${mode.key}")

    var moves by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var cpuFirst by rememberSaveable { mutableStateOf(false) } // alternates each game against the computer
    val board = remember(moves) { ConnectFour.fromMoves(moves) }
    val win = remember(board) { board.winLine() }
    val over = win != null || board.isFull
    val cpuPlayer = if (!mode.vsComputer) 0 else if (cpuFirst) 1 else 2
    val cpuTurn = !over && board.toMove == cpuPlayer
    // You have a disc on the board: any move in two-player games, any but the computer's opening one otherwise.
    val underWay = !over && moves.size > (if (cpuPlayer == 1) 1 else 0)

    // The newest disc falls in; everything else is drawn in place.
    val drop = remember { Animatable(Float.MAX_VALUE) } // ms since the drop started
    var dropTotal by remember { mutableFloatStateOf(0f) }
    var dropping by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(-1) }

    /** Adds a finished game to the tally: side a is you (or red), side b the computer (or yellow). */
    fun record(finished: ConnectFour, line: List<Int>?) {
        if (line == null) { tally.record(0); return }
        val winner = finished[line[0] % COLS, line[0] / COLS]
        val first = if (!mode.vsComputer) winner == 1 else winner != cpuPlayer
        tally.record(if (first) 1 else 2)
    }

    fun play(col: Int) {
        if (over || dropping || !board.canPlay(col)) return
        val rowsFallen = ROWS - board.height(col) // from the row above the board down to its landing row
        val next = moves + col
        moves = next
        // The result is counted straight away, so leaving during the last drop can't lose it. Only the thud waits.
        val after = ConnectFour.fromMoves(next)
        val line = after.winLine()
        val finished = line != null || after.isFull
        if (finished) record(after, line)
        dropping = true
        scope.launch {
            val (impact, total) = dropTimes(rowsFallen)
            dropTotal = total
            drop.snapTo(0f)
            drop.animateTo(impact, tween(impact.toInt(), easing = LinearEasing))
            haptics.click()
            drop.animateTo(total, tween((total - impact).toInt(), easing = LinearEasing))
            dropping = false
            if (finished && moves == next) haptics.heavy()
        }
    }

    fun clearDrop() {
        scope.launch { drop.snapTo(Float.MAX_VALUE) }
        dropping = false
    }

    fun newGame(nextMode: GameMode = mode) {
        cpuFirst = when {
            nextMode != mode -> false
            nextMode.vsComputer && moves.isNotEmpty() -> !cpuFirst
            else -> cpuFirst
        }
        mode = nextMode
        moves = emptyList()
        clearDrop()
    }

    fun undo() {
        if (over || moves.isEmpty()) return
        var m = moves.dropLast(1)
        // Against the computer, go back to your own turn.
        while (m.isNotEmpty() && cpuPlayer != 0 && (if (m.size % 2 == 0) 1 else 2) == cpuPlayer) m = m.dropLast(1)
        clearDrop()
        moves = m
    }

    // The computer's move, searched off the main thread once the last disc has landed.
    LaunchedEffect(moves, mode, cpuFirst) {
        if (!cpuTurn) return@LaunchedEffect
        snapshotFlow { dropping }.first { !it }
        thinking = true
        val col = try {
            val started = System.currentTimeMillis()
            val best = withContext(Dispatchers.Default) { aiMove(board.copy()) }
            delay((450 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            best
        } finally {
            thinking = false // also when Undo, New game or a mode change cancels the search
        }
        play(col)
    }

    // Not during the computer's turn either, as its move would land straight after.
    val canUndo = !over && !dropping && !cpuTurn && moves.any() && (cpuPlayer == 0 || moves.size > if (cpuFirst) 1 else 0)
    ToolScaffold(
        title = "Connect Four",
        onBack = onBack,
        actions = {
            IconButton(onClick = ::undo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            NewGameButton(onClick = { guard.run(underWay) { newGame() } }, enabled = moves.isNotEmpty() && !dropping)
            ScoresMenu(onReset = tally::reset)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameModeChips(mode, onSelect = { m -> guard.run(underWay) { newGame(m) } })
            Spacer(Modifier.height(12.dp))
            val t = tally.value
            ScoreRow(
                if (mode.vsComputer) listOf(Score("You", t.a, discColor(3 - cpuPlayer)), Score("Draws", t.draws), Score("CPU", t.b, discColor(cpuPlayer)))
                else listOf(Score("Red", t.a, RED), Score("Draws", t.draws), Score("Yellow", t.b, YELLOW)),
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val inputEnabled = !over && !dropping && !cpuTurn
                BoardView(
                    board = board,
                    last = moves.lastOrNull(),
                    dropMs = drop.value,
                    dropTotal = dropTotal,
                    win = if (dropping) null else win,
                    hover = if (inputEnabled) hover else -1,
                    enabled = inputEnabled,
                    onHover = { hover = it },
                    onDrop = { hover = -1; play(it) },
                    modifier = Modifier.widthIn(max = 480.dp).aspectRatio(COLS / (ROWS + 1f)),
                )
            }
            Spacer(Modifier.height(12.dp))
            // The result shows once the last disc has landed.
            val result = when {
                dropping -> null
                win != null -> {
                    val winner = board[win[0] % COLS, win[0] / COLS]
                    when {
                        !mode.vsComputer -> "${colorName(winner)} wins!"
                        winner == cpuPlayer -> "The computer wins"
                        else -> "You win!"
                    }
                }
                board.isFull -> "It's a draw"
                else -> null
            }
            GameFooter(
                over = result?.let {
                    GameOver(it, if (!mode.vsComputer) null else if (cpuFirst) "You start the next game" else "The computer starts the next game")
                },
                turn = when {
                    dropping || over -> " "
                    thinking || cpuTurn -> "Thinking…"
                    !mode.vsComputer -> "${colorName(board.toMove)}'s turn"
                    moves.isEmpty() -> "You go first"
                    else -> "Your turn"
                },
                turnColor = if (dropping || over) null else discColor(board.toMove),
                onPlayAgain = { newGame() },
            )
        }
    }
    GameGuardDialog(guard)
}

/** Gravity for a falling disc, in cells per ms²: six cells in about 360 ms. */
private const val DROP_G = 2 * 6f / (360f * 360f)

/** Time to first impact and to rest (ms) for a disc falling [rows] cells, with two small bounces. */
private fun dropTimes(rows: Int): Pair<Float, Float> {
    val fall = sqrt(2 * rows / DROP_G)
    val h1 = minOf(0.2f, 0.06f * rows)
    val b1 = 2 * sqrt(2 * h1 / DROP_G)
    val b2 = 2 * sqrt(2 * h1 * 0.3f / DROP_G)
    return fall to fall + b1 + b2
}

/** How far above its slot (in cells) a disc falling [rows] cells is [t] ms after release. */
private fun dropHeight(t: Float, rows: Int): Float {
    val fall = sqrt(2 * rows / DROP_G)
    if (t < fall) return rows - 0.5f * DROP_G * t * t
    val h1 = minOf(0.2f, 0.06f * rows)
    var tau = t - fall
    for (h in floatArrayOf(h1, h1 * 0.3f)) {
        val v = sqrt(2 * DROP_G * h)
        val len = 2 * v / DROP_G
        if (tau < len) return v * tau - 0.5f * DROP_G * tau * tau
        tau -= len
    }
    return 0f
}

@Composable
private fun BoardView(
    board: ConnectFour,
    last: Int?,
    dropMs: Float,
    dropTotal: Float,
    win: List<Int>?,
    hover: Int,
    enabled: Boolean,
    onHover: (Int) -> Unit,
    onDrop: (Int) -> Unit,
    modifier: Modifier,
) {
    val frameColor = GoogleColors.Blue
    val surface = MaterialTheme.colorScheme.surface
    val pulse by rememberInfiniteTransition(label = "win").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    val currentEnabled by rememberUpdatedState(enabled)
    val currentHover by rememberUpdatedState(onHover)
    val currentDrop by rememberUpdatedState(onDrop)
    val lastRow = last?.let { board.height(it) - 1 }
    val winSet = win?.toSet() ?: emptySet()

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Press to preview a column, slide to change it, release to drop.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!currentEnabled) return@awaitEachGesture
                        fun colAt(x: Float) = (x / (size.width / COLS.toFloat())).toInt().coerceIn(0, COLS - 1)
                        var col = colAt(down.position.x)
                        currentHover(col)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val inside = change.position.y in -size.height * 0.2f..size.height * 1.2f
                                if (inside && currentEnabled) currentDrop(col) else currentHover(-1)
                                return@awaitEachGesture
                            }
                            col = colAt(change.position.x)
                            currentHover(col)
                            change.consume()
                        }
                        currentHover(-1)
                    }
                },
        ) {
            val cell = size.width / COLS
            val r = cell * 0.4f
            fun centre(col: Int, row: Int) = Offset(cell * (col + 0.5f), cell * (ROWS - row + 0.5f))

            if (hover >= 0 && board.canPlay(hover)) {
                drawDisc(centre(hover, ROWS), r, discColor(board.toMove), alpha = 0.55f)
            }
            for (col in 0 until COLS) for (row in 0 until board.height(col)) {
                val index = row * COLS + col
                val animating = col == last && row == lastRow && dropMs < dropTotal
                val base = centre(col, row)
                val c = if (animating) base - Offset(0f, dropHeight(dropMs, ROWS - row) * cell) else base
                val dim = win != null && index !in winSet
                drawDisc(c, r, lerp(discColor(board[col, row]), surface, if (dim) 0.45f else 0f))
                if (index in winSet) {
                    drawCircle(Color.White.copy(alpha = pulse), r * 0.62f, c, style = Stroke(r * 0.16f))
                }
            }

            // The frame, with a hole for every slot so discs show through from behind.
            val frame = Path().apply {
                fillType = PathFillType.EvenOdd
                addRoundRect(RoundRect(0f, cell, size.width, size.height, CornerRadius(cell * 0.3f)))
                for (col in 0 until COLS) for (row in 0 until ROWS) {
                    val c = centre(col, row)
                    addOval(androidx.compose.ui.geometry.Rect(c, r * 1.06f))
                }
            }
            drawPath(frame, Brush.verticalGradient(listOf(lerp(frameColor, Color.White, 0.12f), lerp(frameColor, Color.Black, 0.2f)), startY = cell, endY = size.height))
            for (col in 0 until COLS) for (row in 0 until ROWS) {
                drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.06f, centre(col, row), style = Stroke(cell * 0.03f))
            }

            if (win != null) {
                val a = centre(win.first() % COLS, win.first() / COLS)
                val b = centre(win.last() % COLS, win.last() / COLS)
                drawLine(Color.White.copy(alpha = 0.85f), a, b, r * 0.18f, StrokeCap.Round)
            }
        }
        // One TalkBack target per column.
        Row(Modifier.fillMaxSize()) {
            for (col in 0 until COLS) {
                val free = ROWS - board.height(col)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Button
                            contentDescription = "Column ${col + 1}, " + if (free == 0) "full" else "$free free"
                            if (!enabled || free == 0) disabled()
                            onClick(label = "Drop a disc") { onDrop(col); true }
                        },
                )
            }
        }
    }
}

private fun DrawScope.drawDisc(c: Offset, r: Float, color: Color, alpha: Float = 1f) {
    drawCircle(lerp(color, Color.Black, 0.18f), r, c, alpha = alpha)
    drawCircle(
        Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color), c - Offset(r * 0.3f, r * 0.3f), r),
        r * 0.8f, c, alpha = alpha,
    )
    drawCircle(lerp(color, Color.Black, 0.12f), r * 0.55f, c, alpha = alpha, style = Stroke(r * 0.06f))
}
