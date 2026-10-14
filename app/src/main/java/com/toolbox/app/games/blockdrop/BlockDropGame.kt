package com.toolbox.app.games.blockdrop

import kotlin.math.pow
import kotlin.random.Random

const val COLS = 10
const val ROWS = 20

/** The seven pieces. [cells] is the spawn shape in a [size] × [size] box, x right and y down. */
enum class Kind(val size: Int, cells: List<Pair<Int, Int>>) {
    I(4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)),
    O(2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)),
    T(3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)),
    S(3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1)),
    Z(3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)),
    J(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)),
    L(3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1));

    /** The shape in each of the four turns, clockwise from the spawn shape. Turning the box clockwise takes (x, y) to (size − 1 − y, x). */
    val turns: List<List<Pair<Int, Int>>> = generateSequence(cells) { s -> s.map { (x, y) -> size - 1 - y to x } }.take(4).toList()
}

/** A piece in the well: its box's top-left corner is at ([x], [y]), and [rot] counts clockwise turns. */
data class Piece(val kind: Kind, val rot: Int, val x: Int, val y: Int) {
    val cells: List<Pair<Int, Int>> get() = kind.turns[rot].map { (cx, cy) -> x + cx to y + cy }

    companion object {
        /** A new piece at the top of the well, in the middle (left of middle for odd widths), on the top two rows. */
        fun spawn(kind: Kind) = Piece(kind, 0, (COLS - kind.size) / 2, if (kind == Kind.I) -1 else 0)
    }
}

/** A shuffled bag of all seven pieces, dealt in turn and refilled when empty, so no piece is ever long in coming. */
class PieceBag(private val random: Random = Random, remaining: List<Kind> = emptyList()) {
    private val bag = ArrayDeque(remaining)

    /** What's left in the current bag, in the order it will be dealt. */
    val remaining: List<Kind> get() = bag.toList()

    fun draw(): Kind {
        if (bag.isEmpty()) bag.addAll(Kind.entries.shuffled(random))
        return bag.removeFirst()
    }
}

/**
 * Block Drop, a classic falling-blocks game: a 10 × 20 well, the seven pieces from a [PieceBag], clockwise turns with
 * simple wall and floor kicks, a lock delay, and levels that speed up every 10 lines. There's no hold.
 *
 * Pure and mutable, like Stack Tower's game: the screen calls [step] every frame and the moves as the player makes
 * them, and redraws when [revision] changes.
 */
class BlockDropGame(private val random: Random = Random) {
    enum class State { Ready, Playing, Paused, Over }

    sealed interface Event {
        /** A piece locked, clearing [lines] rows (0 to 4); [levelUp] if that took the level up. */
        data class Locked(val lines: Int, val levelUp: Boolean) : Event

        /** The next piece had no room, or a piece locked above the top. */
        data object Over : Event
    }

    private val board = IntArray(COLS * ROWS) // 0 is empty, otherwise Kind.ordinal + 1
    private var bag = PieceBag(random)

    var state = State.Ready; private set
    /** The falling piece; null between games and while full rows are clearing. */
    var piece: Piece? = null; private set
    var next: Kind = Kind.T; private set
    var score = 0; private set
    var lines = 0; private set
    /** Full rows flashing before they're taken away, top first. */
    var clearing: List<Int> = emptyList(); private set
    /** How far the flash of [clearing] has got, 0 to 1. */
    val clearProgress: Float get() = (clearTime / CLEAR_SECONDS).coerceIn(0f, 1f)
    /** Goes up with every change the screen should show. */
    var revision = 0; private set

    val level: Int get() = 1 + lines / 10

    private var fall = 0f // rows of gravity owed, as a fraction
    private var lockTimer = 0f // seconds on the ground since the lock delay last reset
    private var resets = 0 // lock-delay resets used since the piece last reached a new lowest row
    private var lowest = 0 // the lowest row (largest y) the piece's box has reached
    private var clearTime = 0f

    /** What's in the cell at ([x], [y]), or null if it's empty. */
    fun cell(x: Int, y: Int): Kind? = board[y * COLS + x].let { if (it == 0) null else Kind.entries[it - 1] }

    /** For tests: fills or empties a cell. */
    internal fun setCell(x: Int, y: Int, kind: Kind?) { board[y * COLS + x] = kind?.let { it.ordinal + 1 } ?: 0 }

    /** For tests: swaps the falling piece for [p], with a fresh lock delay. */
    internal fun place(p: Piece) {
        piece = p
        lowest = p.y
        lockTimer = 0f
        resets = 0
        fall = 0f
    }

    /** Empties the well and drops the first piece. */
    fun start() {
        board.fill(0)
        score = 0
        lines = 0
        clearing = emptyList()
        bag = PieceBag(random)
        next = bag.draw()
        state = State.Playing
        spawn()
    }

    fun pause() {
        if (state == State.Playing) { state = State.Paused; changed() }
    }

    fun resume() {
        if (state == State.Paused) { state = State.Playing; changed() }
    }

    /** Where the piece would land: straight down until it hits something. */
    fun ghost(): Piece? {
        var g = piece ?: return null
        while (fits(g.copy(y = g.y + 1))) g = g.copy(y = g.y + 1)
        return g
    }

    /** Moves the piece [dx] columns; false if something's in the way. */
    fun move(dx: Int): Boolean {
        val p = active() ?: return false
        val moved = p.copy(x = p.x + dx)
        if (!fits(moved)) return false
        shift(p, moved)
        return true
    }

    /** Turns the piece clockwise, nudging it sideways or up out of a wall, the floor or the stack if it has to. */
    fun rotate(): Boolean {
        val p = active() ?: return false
        if (p.kind == Kind.O) return false
        val turned = p.copy(rot = (p.rot + 1) % 4)
        for ((dx, dy) in if (p.kind == Kind.I) I_KICKS else KICKS) {
            val kicked = turned.copy(x = turned.x + dx, y = turned.y + dy)
            if (fits(kicked)) {
                shift(p, kicked)
                return true
            }
        }
        return false
    }

    /** Moves the piece down a row, for a point; false once it's on the ground (the lock delay still applies). */
    fun softDrop(): Boolean {
        val p = active() ?: return false
        val down = p.copy(y = p.y + 1)
        if (!fits(down)) return false
        piece = down
        fall = 0f
        score++
        reachedRow(down.y)
        changed()
        return true
    }

    /** Drops the piece straight to the bottom and locks it, for a point a row. */
    fun hardDrop(): List<Event> {
        val p = active() ?: return emptyList()
        val landed = ghost() ?: p
        score += landed.y - p.y
        piece = landed
        return lock()
    }

    /** Advances the game [dt] seconds: gravity, the lock delay and the flash of cleared rows. */
    fun step(dt: Float): List<Event> {
        if (state != State.Playing) return emptyList()
        if (clearing.isNotEmpty()) {
            clearTime += dt
            changed()
            if (clearTime < CLEAR_SECONDS) return emptyList()
            removeRows(clearing)
            clearing = emptyList()
            return spawn()
        }
        val p = piece ?: return emptyList()
        if (!fits(p.copy(y = p.y + 1))) {
            fall = 0f
            lockTimer += dt
            return if (lockTimer >= LOCK_DELAY || resets >= MAX_RESETS) lock() else emptyList()
        }
        fall += dt / gravity(level)
        var current = p
        while (fall >= 1f) {
            fall -= 1f
            val down = current.copy(y = current.y + 1)
            if (!fits(down)) { fall = 0f; break }
            current = down
        }
        if (current != p) {
            piece = current
            reachedRow(current.y)
            changed()
        }
        return emptyList()
    }

    /**
     * The game as ints for saved state: state, score, lines, next piece, the piece (kind, turn, x, y; kind −1 for
     * none), the rows clearing and what's left in the bag (each a count then the values), then the well row by row.
     * A game in play comes back paused, and the timers start again.
     */
    fun save(): IntArray {
        val p = piece
        return intArrayOf(state.ordinal, score, lines, next.ordinal, p?.kind?.ordinal ?: -1, p?.rot ?: 0, p?.x ?: 0, p?.y ?: 0) +
            intArrayOf(clearing.size) + clearing.toIntArray() +
            intArrayOf(bag.remaining.size) + bag.remaining.map { it.ordinal }.toIntArray() +
            board
    }

    private fun active(): Piece? = if (state == State.Playing && clearing.isEmpty()) piece else null

    private fun fits(p: Piece): Boolean = p.cells.all { (x, y) -> x in 0 until COLS && y < ROWS && (y < 0 || board[y * COLS + x] == 0) }

    private fun grounded(p: Piece) = !fits(p.copy(y = p.y + 1))

    /**
     * Puts the moved or turned piece in place. A move made on the ground starts the lock delay again, up to
     * [MAX_RESETS] times; after that the piece locks the moment it's on the ground, so it can't be kept up for ever.
     */
    private fun shift(from: Piece, to: Piece) {
        piece = to
        if ((grounded(from) || grounded(to)) && resets < MAX_RESETS) {
            lockTimer = 0f
            resets++
        }
        reachedRow(to.y)
        changed()
    }

    /** Reaching a new lowest row gives the piece a fresh lock delay and all its resets back. */
    private fun reachedRow(y: Int) {
        if (y > lowest) {
            lowest = y
            lockTimer = 0f
            resets = 0
        }
    }

    private fun lock(): List<Event> {
        val p = piece ?: return emptyList()
        piece = null
        changed()
        val cells = p.cells
        for ((x, y) in cells) if (y >= 0) board[y * COLS + x] = p.kind.ordinal + 1
        if (cells.any { (_, y) -> y < 0 }) {
            state = State.Over
            return listOf(Event.Locked(0, false), Event.Over)
        }
        val full = (0 until ROWS).filter { y -> (0 until COLS).all { x -> board[y * COLS + x] != 0 } }
        if (full.isEmpty()) return listOf(Event.Locked(0, false)) + spawn()
        val before = level
        score += LINE_SCORES[full.size] * before
        lines += full.size
        clearing = full
        clearTime = 0f
        return listOf(Event.Locked(full.size, level > before))
    }

    private fun removeRows(rows: List<Int>) {
        val kept = (0 until ROWS).filter { it !in rows }
        val copy = board.copyOf()
        board.fill(0)
        // Rows that stay fall to the bottom, keeping their order.
        kept.reversed().forEachIndexed { i, y -> copy.copyInto(board, (ROWS - 1 - i) * COLS, y * COLS, y * COLS + COLS) }
        changed()
    }

    private fun spawn(): List<Event> {
        val p = Piece.spawn(next)
        next = bag.draw()
        fall = 0f
        lockTimer = 0f
        resets = 0
        lowest = p.y
        changed()
        if (!fits(p)) {
            state = State.Over
            return listOf(Event.Over)
        }
        piece = p
        return emptyList()
    }

    private fun changed() { revision++ }

    companion object {
        /** Seconds a piece may sit on the ground before it locks, and how many moves there may restart that. */
        const val LOCK_DELAY = 0.5f
        const val MAX_RESETS = 15

        /** How long full rows flash before they go. */
        const val CLEAR_SECONDS = 0.3f

        /** Points for clearing 1 to 4 rows at once, times the level. */
        val LINE_SCORES = intArrayOf(0, 100, 300, 500, 800)

        /** Turns try these nudges in order: in place, a column either way, then up a row, for a turn against the floor. */
        private val KICKS = listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1)

        /** The long I piece can need two columns, or two rows to stand up on the floor. */
        private val I_KICKS = listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1, 0 to -2)

        /** Gravity stops speeding up at this level. */
        const val TOP_SPEED_LEVEL = 15

        /** Seconds a piece takes to fall one row at [level]: 1 s at level 1, getting faster up to level 15. */
        fun gravity(level: Int): Float {
            val l = level.coerceIn(1, TOP_SPEED_LEVEL) - 1
            return (0.8f - l * 0.007f).pow(l)
        }

        fun restore(saved: IntArray, random: Random = Random): BlockDropGame = BlockDropGame(random).apply {
            var i = 0
            fun take() = saved[i++]
            state = State.entries[take()].let { if (it == State.Playing) State.Paused else it }
            score = take()
            lines = take()
            next = Kind.entries[take()]
            val kind = take()
            val rot = take()
            val x = take()
            val y = take()
            piece = if (kind < 0) null else Piece(Kind.entries[kind], rot, x, y)
            lowest = y
            clearing = List(take()) { take() }
            bag = PieceBag(random, List(take()) { Kind.entries[take()] })
            saved.copyInto(board, 0, i, i + board.size)
        }
    }
}
