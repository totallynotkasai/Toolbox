package com.toolbox.app.games.connectfour

import kotlin.random.Random

const val COLS = 7
const val ROWS = 6

/**
 * A Connect Four position. Cells are indexed `row * COLS + col` with row 0 at the bottom; 0 is empty,
 * 1 the first player (red) and 2 the second (yellow). Player 1 always moves first, so whose turn it is
 * follows from the move count.
 *
 * Mutable with [play]/[undo] so the search can walk the tree without copying.
 */
class ConnectFour private constructor(private val cells: IntArray, private val heights: IntArray, moves: Int) {
    constructor() : this(IntArray(COLS * ROWS), IntArray(COLS), 0)

    var moves = moves; private set
    val toMove get() = if (moves % 2 == 0) 1 else 2
    val isFull get() = moves == COLS * ROWS

    operator fun get(col: Int, row: Int) = cells[row * COLS + col]
    fun height(col: Int) = heights[col]
    fun canPlay(col: Int) = col in 0 until COLS && heights[col] < ROWS

    /** Drops a disc for the player to move; returns the row it lands in. */
    fun play(col: Int): Int {
        require(canPlay(col)) { "column $col is full" }
        val row = heights[col]++
        cells[row * COLS + col] = toMove
        moves++
        return row
    }

    fun undo(col: Int) {
        val row = --heights[col]
        cells[row * COLS + col] = 0
        moves--
    }

    fun copy() = ConnectFour(cells.copyOf(), heights.copyOf(), moves)

    /** The four (or more) cells in a line through ([col], [row]) with its colour, or null. */
    fun lineThrough(col: Int, row: Int): List<Int>? {
        val p = this[col, row]
        if (p == 0) return null
        for ((dc, dr) in DIRECTIONS) {
            val line = ArrayList<Int>(7)
            var c = col - dc; var r = row - dr
            while (inside(c, r) && this[c, r] == p) { c -= dc; r -= dr }
            c += dc; r += dr
            while (inside(c, r) && this[c, r] == p) { line += r * COLS + c; c += dc; r += dr }
            if (line.size >= 4) return line
        }
        return null
    }

    /** Any winning line on the board (used after restoring a game). */
    fun winLine(): List<Int>? {
        for (col in 0 until COLS) for (row in 0 until heights[col]) lineThrough(col, row)?.let { return it }
        return null
    }

    /** Whether the player to move would win by playing [col]. */
    fun isWinningMove(col: Int): Boolean {
        if (!canPlay(col)) return false
        val row = play(col)
        val wins = lineThrough(col, row) != null
        undo(col)
        return wins
    }

    /**
     * Heuristic value of the position for [player]: every window of four that only one side can still use scores
     * by how full it is, plus a bonus for the centre column.
     */
    fun evaluate(player: Int): Int {
        val opponent = 3 - player
        var score = 0
        for (row in 0 until ROWS) {
            if (cells[row * COLS + 3] == player) score += 3 else if (cells[row * COLS + 3] == opponent) score -= 3
        }
        var i = 0
        while (i < WINDOWS.size) {
            var mine = 0; var theirs = 0
            for (k in 0 until 4) {
                when (cells[WINDOWS[i + k]]) { player -> mine++; opponent -> theirs++ }
            }
            if (theirs == 0) score += WINDOW_SCORE[mine]
            else if (mine == 0) score -= WINDOW_SCORE[theirs] + if (theirs == 3) 1 else 0 // threats count a bit more
            i += 4
        }
        return score
    }

    fun encode(): String = buildString { for (c in cells) append(c) }

    companion object {
        /** Search order: centre first, since central columns take part in the most lines. */
        val ORDER = intArrayOf(3, 2, 4, 1, 5, 0, 6)
        private val DIRECTIONS = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        private val WINDOW_SCORE = intArrayOf(0, 1, 4, 20, 0)
        private fun inside(c: Int, r: Int) = c in 0 until COLS && r in 0 until ROWS

        /** Every window of four cells in a line, flattened: 69 windows × 4 indices. */
        private val WINDOWS: IntArray = buildList {
            for (row in 0 until ROWS) for (col in 0 until COLS) for ((dc, dr) in DIRECTIONS) {
                val endC = col + 3 * dc; val endR = row + 3 * dr
                if (inside(endC, endR)) for (k in 0 until 4) add((row + k * dr) * COLS + col + k * dc)
            }
        }.toIntArray()

        /** Replays a list of columns from an empty board, ignoring any that aren't legal. */
        fun fromMoves(moves: List<Int>) = ConnectFour().also { b -> moves.forEach { if (b.canPlay(it)) b.play(it) } }
    }
}

private const val WIN = 1_000_000

/**
 * Negamax with alpha-beta pruning: the value of the position for the player to move, searching [depth] plies.
 * Wins score higher the sooner they come, so the AI takes the quickest win and puts off a loss.
 * Assumes the last move didn't end the game.
 */
private fun negamax(b: ConnectFour, depth: Int, alphaIn: Int, beta: Int): Int {
    if (b.isFull) return 0
    for (c in ConnectFour.ORDER) if (b.isWinningMove(c)) return WIN - b.moves - 1
    if (depth == 0) return b.evaluate(b.toMove)
    var alpha = alphaIn
    var best = -WIN
    for (c in ConnectFour.ORDER) {
        if (!b.canPlay(c)) continue
        b.play(c)
        val score = -negamax(b, depth - 1, -beta, -alpha)
        b.undo(c)
        if (score > best) best = score
        if (score > alpha) alpha = score
        if (alpha >= beta) break
    }
    return best
}

/** The best column for the player to move, searching [depth] plies. Ties go to the more central column. */
fun bestMove(board: ConnectFour, depth: Int): Int {
    val b = board.copy()
    for (c in ConnectFour.ORDER) if (b.isWinningMove(c)) return c
    var bestCol = ConnectFour.ORDER.first { b.canPlay(it) }
    var alpha = -WIN - 1
    for (c in ConnectFour.ORDER) {
        if (!b.canPlay(c)) continue
        b.play(c)
        val score = -negamax(b, depth - 1, -WIN - 1, -alpha)
        b.undo(c)
        if (score > alpha) { alpha = score; bestCol = c }
    }
    return bestCol
}

/** The computer's move: a random column 30% of the time, and otherwise the best move looking 2 plies ahead. */
fun aiMove(board: ConnectFour, random: Random = Random): Int {
    if (random.nextFloat() < 0.3f) {
        return (0 until COLS).filter { board.canPlay(it) }.random(random)
    }
    return bestMove(board, 2)
}
