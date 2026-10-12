package com.toolbox.app.games.tictactoe

import kotlin.random.Random

/** Tic-Tac-Toe rules. The board is 9 squares in reading order: 0 empty, [X] or [O]. */
const val X = 1
const val O = 2

private val LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

fun winLine(b: List<Int>): List<Int>? = LINES.firstOrNull { (a, c, d) -> b[a] != 0 && b[a] == b[c] && b[a] == b[d] }

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
fun bestMove(b: List<Int>): Int {
    val work = b.toMutableList()
    return b.indices.filter { b[it] == 0 }.maxBy { i -> work[i] = O; val s = minimax(work, X, 1); work[i] = 0; s }
}

/** The computer (O) plays the best move only 40% of the time, and otherwise any free square. */
fun aiMove(b: List<Int>, random: Random = Random): Int {
    val empty = b.indices.filter { b[it] == 0 }
    return if (random.nextFloat() > 0.4f) empty.random(random) else bestMove(b)
}

/** Who moves first: X, unless it's the computer's (O's) turn to start a solo game. */
fun firstTurn(vsComputer: Boolean, cpuFirst: Boolean) = if (vsComputer && cpuFirst) O else X

/** How the game stands: [X] or [O] once someone has a line, 0 for a full board with no line, null while it's on. */
fun outcome(b: List<Int>): Int? {
    winLine(b)?.let { return b[it[0]] }
    return if (b.none { it == 0 }) 0 else null
}
