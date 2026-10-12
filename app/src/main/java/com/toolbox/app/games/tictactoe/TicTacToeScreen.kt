package com.toolbox.app.games.tictactoe

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameMode
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val X = 1
private const val O = 2
private val LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

private val X_COLOR = GoogleColors.Red
private val O_COLOR = GoogleColors.Blue

internal fun winLine(b: List<Int>): List<Int>? = LINES.firstOrNull { (a, c, d) -> b[a] != 0 && b[a] == b[c] && b[a] == b[d] }

/** Full minimax; the board is tiny so no pruning needed. Prefers faster wins / slower losses via depth. */
private fun minimax(b: MutableList<Int>, turn: Int, depth: Int): Int {
    winLine(b)?.let { return if (b[it[0]] == O) 10 - depth else depth - 10 }
    if (b.none { it == 0 }) return 0
    var best = if (turn == O) Int.MIN_VALUE else Int.MAX_VALUE
    for (i in 0 until 9) if (b[i] == 0) {
        b[i] = turn
        val s = minimax(b, if (turn == O) X else O, depth + 1)
        b[i] = 0
        best = if (turn == O) maxOf(best, s) else minOf(best, s)
    }
    return best
}

/** O's best square by full minimax. */
internal fun bestMove(b: List<Int>): Int {
    val work = b.toMutableList()
    return b.indices.filter { b[it] == 0 }.maxBy { i -> work[i] = O; val s = minimax(work, X, 1); work[i] = 0; s }
}

/** The computer (O) plays the best move only 40% of the time, and otherwise any free square. */
internal fun aiMove(b: List<Int>, random: Random = Random): Int {
    val empty = b.indices.filter { b[it] == 0 }
    return if (random.nextFloat() > 0.4f) empty.random(random) else bestMove(b)
}

/** Who moves first: X, unless it's the computer's (O's) turn to start a solo game. */
internal fun firstTurn(vsComputer: Boolean, cpuFirst: Boolean) = if (vsComputer && cpuFirst) O else X

@Composable
fun TicTacToeScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val guard = rememberGameGuard()
    var mode by rememberGameMode("tictactoe_mode")
    val tally = rememberTally("tictactoe_tally_${mode.key}")
    var board by rememberSaveable { mutableStateOf(List(9) { 0 }) }
    // You are always X. Against the computer, who starts alternates from game to game.
    var cpuFirst by rememberSaveable { mutableStateOf(false) }
    var turn by rememberSaveable { mutableIntStateOf(X) }

    val line = winLine(board)
    val over = line != null || board.none { it == 0 }
    val cpuTurn = mode.vsComputer && turn == O && !over
    // X always moves first in two-player games and you are X against the computer, so an X on the board means
    // you have played.
    val underWay = !over && board.any { it == X }

    fun play(i: Int) {
        if (board[i] != 0 || over) return
        val next = board.toMutableList().also { it[i] = turn }
        board = next
        turn = if (turn == X) O else X
        haptics.click()
        val l = winLine(next)
        when {
            // In the tally, side a is you (or X) and side b the computer (or O).
            l != null -> { tally.record(next[l[0]]); haptics.heavy() }
            next.none { it == 0 } -> tally.record(0)
        }
    }

    /** A tap on the board: ignored while the computer is choosing, so you can't make its move for it. */
    fun tap(i: Int) {
        if (!cpuTurn) play(i)
    }

    fun newGame(nextMode: GameMode = mode) {
        cpuFirst = when {
            nextMode != mode -> false
            nextMode.vsComputer && board.any { it != 0 } -> !cpuFirst
            else -> cpuFirst
        }
        mode = nextMode
        board = List(9) { 0 }
        turn = firstTurn(nextMode.vsComputer, cpuFirst)
    }

    LaunchedEffect(board, turn, mode) {
        if (cpuTurn) {
            delay(450)
            play(aiMove(board))
        }
    }

    val names = if (mode.vsComputer) "You (X)" to "CPU (O)" else "X" to "O"
    ToolScaffold(
        title = "Tic-Tac-Toe",
        onBack = onBack,
        actions = {
            NewGameButton(onClick = { guard.run(underWay) { newGame() } }, enabled = board.any { it != 0 })
            ScoresMenu(onReset = tally::reset)
        },
    ) { padding ->
        ToolBody(padding) {
            GameModeChips(mode, onSelect = { m -> guard.run(underWay) { newGame(m) } })
            Spacer(Modifier.height(16.dp))
            val t = tally.value
            ScoreRow(listOf(Score(names.first, t.a, X_COLOR), Score("Draws", t.draws), Score(names.second, t.b, O_COLOR)))
            Spacer(Modifier.height(24.dp))
            Board(board, line, enabled = !over && !cpuTurn, onTap = ::tap)
            Spacer(Modifier.height(24.dp))
            val result = when {
                !over -> null
                line == null -> "It's a draw"
                !mode.vsComputer -> "${if (board[line[0]] == X) "X" else "O"} wins!"
                board[line[0]] == X -> "You win!"
                else -> "The computer wins"
            }
            GameFooter(
                over = result?.let {
                    // cpuFirst flips when the next game starts.
                    GameOver(it, if (!mode.vsComputer) null else if (cpuFirst) "You start the next game" else "The computer starts the next game")
                },
                turn = when {
                    cpuTurn -> "Thinking…"
                    !mode.vsComputer -> "${if (turn == X) "X" else "O"}'s turn"
                    board.none { it != 0 } -> "You go first"
                    else -> "Your turn"
                },
                turnColor = if (turn == X) X_COLOR else O_COLOR,
                onPlayAgain = { newGame() },
            )
        }
    }
    GameGuardDialog(guard)
}

@Composable
private fun Board(board: List<Int>, line: List<Int>?, enabled: Boolean, onTap: (Int) -> Unit) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val strike = MaterialTheme.colorScheme.onSurface
    val strikeProgress by animateFloatAsState(if (line != null) 1f else 0f, tween(400, delayMillis = 200), label = "strike")
    Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f)) {
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 3) Row(Modifier.weight(1f)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    val progress by animateFloatAsState(if (board[i] != 0) 1f else 0f, tween(250), label = "cell$i")
                    val mark = when (board[i]) { X -> "X"; O -> "O"; else -> "empty" }
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .semantics { contentDescription = "Row ${r + 1}, column ${c + 1}, $mark" }
                            .clickable(enabled = enabled && board[i] == 0, role = Role.Button, onClickLabel = "Play here") { onTap(i) },
                    ) {
                        val pad = size.minDimension * 0.24f
                        val w = 10.dp.toPx()
                        when (board[i]) {
                            X -> {
                                val p1 = (progress * 2).coerceAtMost(1f)
                                val p2 = (progress * 2 - 1).coerceAtLeast(0f)
                                drawLine(X_COLOR, Offset(pad, pad), Offset(pad + (size.width - 2 * pad) * p1, pad + (size.height - 2 * pad) * p1), w, StrokeCap.Round)
                                if (p2 > 0) drawLine(X_COLOR, Offset(size.width - pad, pad), Offset(size.width - pad - (size.width - 2 * pad) * p2, pad + (size.height - 2 * pad) * p2), w, StrokeCap.Round)
                            }
                            O -> drawArc(
                                O_COLOR, -90f, 360f * progress, false,
                                topLeft = Offset(pad, pad), size = Size(size.width - 2 * pad, size.height - 2 * pad),
                                style = Stroke(w, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val third = size.width / 3
            val w = 4.dp.toPx()
            for (k in 1..2) {
                drawLine(grid, Offset(third * k, 8.dp.toPx()), Offset(third * k, size.height - 8.dp.toPx()), w, StrokeCap.Round)
                drawLine(grid, Offset(8.dp.toPx(), third * k), Offset(size.width - 8.dp.toPx(), third * k), w, StrokeCap.Round)
            }
            if (line != null && strikeProgress > 0f) {
                fun centre(i: Int) = Offset(third * (i % 3) + third / 2, third * (i / 3) + third / 2)
                val a = centre(line[0]); val b = centre(line[2])
                drawLine(strike, a, a + (b - a) * strikeProgress, 8.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
