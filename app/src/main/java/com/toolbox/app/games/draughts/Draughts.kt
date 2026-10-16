package com.toolbox.app.games.draughts

import kotlin.math.abs
import kotlin.random.Random

/** The two sides. Red starts at the bottom of the board and moves up; yellow starts at the top and moves down. */
const val RED = 1
const val YELLOW = 2

// What's on a square: nothing, a man or a king.
const val EMPTY = 0
const val RED_MAN = 1
const val YELLOW_MAN = 2
const val RED_KING = 3
const val YELLOW_KING = 4

/** The side a piece belongs to, or 0 for an empty square. */
fun owner(piece: Int): Int = if (piece == EMPTY) 0 else (piece - 1) % 2 + 1
fun isKing(piece: Int): Boolean = piece == RED_KING || piece == YELLOW_KING

/**
 * Squares are numbered row × 8 + column, with row 0 at the top (yellow's side) and column 0 on the left. Play is on
 * the dark squares, where the row and column add up to an odd number, so each side has one in its near left corner.
 */
fun row(square: Int) = square / 8
fun col(square: Int) = square % 8
fun isDark(square: Int) = (row(square) + col(square)) % 2 == 1

/** The square's name as players read it from red's side: columns A to H from the left, rows 1 to 8 from the bottom. */
fun squareName(square: Int): String = "${'A' + col(square)}${8 - row(square)}"

/**
 * A move: every square the piece stands on, from where it starts to where it ends. A step is one square
 * diagonally; a jump is two, over the piece it takes, and a run of jumps is one move.
 */
data class Move(val path: List<Int>) {
    val from: Int get() = path.first()
    val to: Int get() = path.last()
    val isCapture: Boolean get() = abs(row(path[1]) - row(path[0])) == 2

    /** The squares of the pieces it takes: the one between each pair of squares it jumps. */
    val captured: List<Int> get() = if (isCapture) path.zipWithNext { a, b -> (a + b) / 2 } else emptyList()

    fun encode(): String = path.joinToString("-")

    companion object {
        fun decode(s: String) = Move(s.split('-').map { it.toInt() })
    }
}

/** How a game ended. */
sealed interface Result {
    /** [winner] took every piece or left the other side with no move. */
    data class Win(val winner: Int) : Result

    data class Draw(val reason: DrawReason) : Result
}

enum class DrawReason {
    /** Each side made 40 moves with no capture and no man moving. */
    FortyMoves,

    /** The same position, with the same side to move, came round a third time. */
    Repetition,
}

/**
 * A game of English draughts: 12 men a side on the dark squares of an 8 × 8 board. Men move one square diagonally
 * forwards and kings in any direction. A capture jumps a neighbouring enemy piece to the empty square beyond, and
 * captures are compulsory: a piece that has jumped must jump again while it can, though any capture may be chosen
 * when there's more than one. A man reaching the far row becomes a king, and that ends its move. Taken pieces come off
 * when the move is over, so none can be jumped twice.
 *
 * Mutable with [play] and [undo], so the computer's search can walk the tree without copying.
 */
class Draughts private constructor(private val board: IntArray, toMove: Int) {
    /** Whose turn it is. */
    var toMove: Int = toMove; private set

    /** Moves (by either side) since the last capture or man's move, for the 40-move rule. */
    var quiet: Int = 0; private set

    private val played = ArrayList<Played>()
    private val counts = HashMap<String, Int>().apply { put(key(), 1) }

    private class Played(val move: Move, val piece: Int, val taken: IntArray, val quietBefore: Int)

    val moves: List<Move> get() = played.map { it.move }

    /** How many moves have been played. */
    val plies: Int get() = played.size
    val lastMove: Move? get() = played.lastOrNull()?.move

    operator fun get(square: Int): Int = board[square]

    fun pieces(side: Int): Int = board.count { owner(it) == side }

    /** Every move the side to move may make: only captures when there are any. */
    fun legalMoves(): List<Move> {
        val captures = ArrayList<Move>()
        for (s in 0 until 64) if (owner(board[s]) == toMove) jumps(s, board[s], listOf(s), emptySet(), captures)
        if (captures.isNotEmpty()) return captures
        val steps = ArrayList<Move>()
        for (s in 0 until 64) {
            val piece = board[s]
            if (owner(piece) != toMove) continue
            for (d in directions(piece)) {
                val t = step(s, d) ?: continue
                if (board[t] == EMPTY) steps += Move(listOf(s, t))
            }
        }
        return steps
    }

    /** Whether [move] makes a man a king. */
    fun crowns(move: Move): Boolean = !isKing(board[move.from]) && row(move.to) == farRow(owner(board[move.from]))

    /** Plays a legal move. */
    fun play(move: Move) {
        val piece = board[move.from]
        val captured = move.captured
        val taken = IntArray(captured.size) { board[captured[it]] }
        played += Played(move, piece, taken, quiet)
        board[move.from] = EMPTY
        for (s in captured) board[s] = EMPTY
        board[move.to] = if (crownsAt(piece, move.to)) piece + 2 else piece
        quiet = if (captured.isNotEmpty() || !isKing(piece)) 0 else quiet + 1
        toMove = 3 - toMove
        counts.merge(key(), 1, Int::plus)
    }

    /** Takes back the last move. */
    fun undo() {
        val last = played.removeLastOrNull() ?: return
        val k = key()
        counts[k] = (counts[k] ?: 1) - 1
        val move = last.move
        board[move.to] = EMPTY
        move.captured.forEachIndexed { i, s -> board[s] = last.taken[i] }
        board[move.from] = last.piece
        quiet = last.quietBefore
        toMove = 3 - toMove
    }

    /** How the game has ended, or null while it goes on. A side with no move, or no pieces, has lost. */
    fun result(): Result? = when {
        legalMoves().isEmpty() -> Result.Win(3 - toMove)
        quiet >= QUIET_LIMIT -> Result.Draw(DrawReason.FortyMoves)
        (counts[key()] ?: 0) >= 3 -> Result.Draw(DrawReason.Repetition)
        else -> null
    }

    /** A copy to search or play on, with the moves and position counts so far. */
    fun copy(): Draughts = Draughts(board.copyOf(), toMove).also {
        it.quiet = quiet
        it.played += played
        it.counts.clear()
        it.counts += counts
    }

    /** Who moved first. */
    val first: Int get() = if (played.size % 2 == 0) toMove else 3 - toMove

    /** All the jumps from [at] for [piece], having come along [path] and taken the pieces on [taken]. */
    private fun jumps(at: Int, piece: Int, path: List<Int>, taken: Set<Int>, out: MutableList<Move>) {
        // A man crowned on the far row stops there.
        if (path.size > 1 && crownsAt(piece, at)) {
            out += Move(path)
            return
        }
        var further = false
        for (d in directions(piece)) {
            val over = step(at, d) ?: continue
            val land = step(over, d) ?: continue
            if (over in taken || owner(board[over]) != 3 - owner(piece)) continue
            // The square it set off from is empty now, so a king may land on it again.
            if (board[land] != EMPTY && land != path[0]) continue
            further = true
            jumps(land, piece, path + land, taken + over, out)
        }
        if (!further && path.size > 1) out += Move(path)
    }

    private fun crownsAt(piece: Int, square: Int) = !isKing(piece) && row(square) == farRow(owner(piece))

    private fun key(): String = buildString(65) {
        for (p in board) append('0' + p)
        append('0' + toMove)
    }

    companion object {
        /** Each side's moves without a capture or a man moving before it's a draw: 40 each. */
        const val QUIET_LIMIT = 80

        private val MAN_DIRECTIONS = mapOf(RED to listOf(-1 to -1, -1 to 1), YELLOW to listOf(1 to -1, 1 to 1))
        private val KING_DIRECTIONS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

        private fun directions(piece: Int) = if (isKing(piece)) KING_DIRECTIONS else MAN_DIRECTIONS.getValue(owner(piece))

        private fun step(square: Int, d: Pair<Int, Int>): Int? {
            val r = row(square) + d.first
            val c = col(square) + d.second
            return if (r in 0..7 && c in 0..7) r * 8 + c else null
        }

        /** The row a side's men are crowned on. */
        fun farRow(side: Int) = if (side == RED) 0 else 7

        /** The starting position: yellow's men on the top three rows, red's on the bottom three, [first] to move. */
        fun start(first: Int = RED): Draughts {
            val board = IntArray(64) { s ->
                when {
                    !isDark(s) -> EMPTY
                    row(s) < 3 -> YELLOW_MAN
                    row(s) > 4 -> RED_MAN
                    else -> EMPTY
                }
            }
            return Draughts(board, first)
        }

        /** The game after [moves] from the start, or null if one of them isn't legal. */
        fun replay(first: Int, moves: List<Move>): Draughts? {
            val game = start(first)
            for (m in moves) {
                if (m !in game.legalMoves()) return null
                game.play(m)
            }
            return game
        }

        /** For moves already known to be legal, such as the game's own. */
        fun fromMoves(first: Int, moves: List<Move>): Draughts = start(first).apply { moves.forEach { play(it) } }

        /** For tests: a position with these pieces, by square, and [toMove] to move. */
        internal fun arrange(pieces: Map<Int, Int>, toMove: Int = RED, quiet: Int = 0): Draughts =
            Draughts(IntArray(64) { pieces[it] ?: EMPTY }, toMove).also { it.quiet = quiet }
    }
}

// The computer.

private const val WIN_SCORE = 100_000

/** Material, from [side]'s point of view: a man is worth 100 and a king 160, with a little for men that have advanced. */
fun evaluate(game: Draughts, side: Int): Int {
    var score = 0
    for (s in 0 until 64) {
        val p = game[s]
        if (p == EMPTY) continue
        val value = if (isKing(p)) 160 else 100 + 3 * (if (owner(p) == RED) 7 - row(s) else row(s))
        score += if (owner(p) == side) value else -value
    }
    return score
}

/** Negamax with alpha-beta pruning: the value of the position for the side to move, searching [depth] moves. */
private fun negamax(game: Draughts, depth: Int, alphaIn: Int, beta: Int): Int {
    val legal = game.legalMoves()
    if (legal.isEmpty()) return -WIN_SCORE + game.plies // losing later is better
    if (depth == 0) return evaluate(game, game.toMove)
    var alpha = alphaIn
    var best = -WIN_SCORE * 2
    for (m in legal) {
        game.play(m)
        val score = -negamax(game, depth - 1, -beta, -alpha)
        game.undo()
        if (score > best) best = score
        if (score > alpha) alpha = score
        if (alpha >= beta) break
    }
    return best
}

/** The best move for the side to move, searching [depth] moves ahead. */
fun bestMove(game: Draughts, depth: Int): Move {
    val g = game.copy()
    val legal = g.legalMoves()
    var best = legal.first()
    var alpha = -WIN_SCORE * 2
    for (m in legal) {
        g.play(m)
        val score = -negamax(g, depth - 1, -WIN_SCORE * 2, -alpha)
        g.undo()
        if (score > alpha) {
            alpha = score
            best = m
        }
    }
    return best
}

/** Whether, after [move], the other side could take a piece straight back. */
fun leavesAPiece(game: Draughts, move: Move): Boolean {
    game.play(move)
    val exposed = game.legalMoves().firstOrNull()?.isCapture == true
    game.undo()
    return exposed
}

/**
 * The computer's move, pitched at a casual player rather than played to win. Among captures it usually takes the
 * one that takes most, and it likes to crown a man. Otherwise it only sometimes checks whether a move leaves a piece
 * to be taken, and only now and then searches properly.
 */
fun aiMove(game: Draughts, random: Random = Random, search: Float = AI_SEARCH, careful: Float = AI_CAREFUL): Move {
    val legal = game.legalMoves()
    if (legal.size == 1) return legal.first()
    if (random.nextFloat() < search) return bestMove(game, 4)
    if (legal.first().isCapture) {
        val most = legal.maxOf { it.captured.size }
        return if (random.nextFloat() < 0.8f) legal.filter { it.captured.size == most }.random(random) else legal.random(random)
    }
    val crowning = legal.filter { game.crowns(it) }
    if (crowning.isNotEmpty() && random.nextFloat() < 0.7f) return crowning.random(random)
    if (random.nextFloat() < careful) {
        val g = game.copy()
        val safe = legal.filter { !leavesAPiece(g, it) }
        if (safe.isNotEmpty()) return safe.random(random)
    }
    return legal.random(random)
}

/**
 * How often the computer searches properly, and how often it checks a move doesn't leave a piece to be taken. Tuned
 * against a simulated casual player (who takes the biggest capture, crowns when it can and nearly always avoids
 * leaving a piece): over 500 games it won 47%, the computer 32%, and 20% were drawn.
 */
internal const val AI_SEARCH = 0.2f
internal const val AI_CAREFUL = 0.75f
