package com.toolbox.app.games.tictactoe

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
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

@Composable
fun TicTacToeScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    var board by rememberSaveable { mutableStateOf(List(9) { 0 }) }
    var turn by rememberSaveable { mutableIntStateOf(X) }
    var vsComputer by rememberSaveable { mutableStateOf(true) }
    var xWins by rememberSaveable { mutableIntStateOf(0) }
    var oWins by rememberSaveable { mutableIntStateOf(0) }
    var draws by rememberSaveable { mutableIntStateOf(0) }

    val line = winLine(board)
    val over = line != null || board.none { it == 0 }

    fun play(i: Int) {
        if (board[i] != 0 || over) return
        board = board.toMutableList().also { it[i] = turn }
        turn = if (turn == X) O else X
        haptics.click()
        val l = winLine(board)
        when {
            l != null -> { if (board[l[0]] == X) xWins++ else oWins++; haptics.heavy() }
            board.none { it == 0 } -> draws++
        }
    }
    fun reset() { board = List(9) { 0 }; turn = X }

    LaunchedEffect(board, turn, vsComputer) {
        if (vsComputer && turn == O && !over) {
            delay(450)
            play(aiMove(board))
        }
    }

    ToolScaffold(title = "Tic-Tac-Toe", onBack = onBack) { padding ->
        ToolBody(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vsComputer, onClick = { vsComputer = true; reset() }, label = { Text("Solo") })
                FilterChip(selected = !vsComputer, onClick = { vsComputer = false; reset() }, label = { Text("2 players") })
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Score(if (vsComputer) "You (X)" else "X", xWins)
                Score("Draw", draws)
                Score(if (vsComputer) "CPU (O)" else "O", oWins)
            }
            Spacer(Modifier.height(24.dp))
            Board(board, line) { play(it) }
            Spacer(Modifier.height(24.dp))
            Text(
                when {
                    line != null -> "${if (board[line[0]] == X) "X" else "O"} wins!"
                    over -> "It's a draw"
                    vsComputer && turn == O -> "Thinking…"
                    else -> "${if (turn == X) "X" else "O"}'s turn"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = ::reset) { Text(if (over) "Play again" else "Restart") }
        }
    }
}

@Composable
private fun Score(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Board(board: List<Int>, line: List<Int>?, onTap: (Int) -> Unit) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val strike = MaterialTheme.colorScheme.onSurface
    val strikeProgress by animateFloatAsState(if (line != null) 1f else 0f, tween(400, delayMillis = 200), label = "strike")
    Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f)) {
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 3) Row(Modifier.weight(1f)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    val progress by animateFloatAsState(if (board[i] != 0) 1f else 0f, tween(250), label = "cell$i")
                    Canvas(Modifier.weight(1f).fillMaxSize().clickable { onTap(i) }) {
                        val pad = size.minDimension * 0.24f
                        val w = 10.dp.toPx()
                        when (board[i]) {
                            X -> {
                                val p1 = (progress * 2).coerceAtMost(1f)
                                val p2 = (progress * 2 - 1).coerceAtLeast(0f)
                                drawLine(GoogleColors.Red, Offset(pad, pad), Offset(pad + (size.width - 2 * pad) * p1, pad + (size.height - 2 * pad) * p1), w, StrokeCap.Round)
                                if (p2 > 0) drawLine(GoogleColors.Red, Offset(size.width - pad, pad), Offset(size.width - pad - (size.width - 2 * pad) * p2, pad + (size.height - 2 * pad) * p2), w, StrokeCap.Round)
                            }
                            O -> drawArc(
                                GoogleColors.Blue, -90f, 360f * progress, false,
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
