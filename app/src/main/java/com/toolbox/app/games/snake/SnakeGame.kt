package com.toolbox.app.games.snake

import kotlin.random.Random

const val COLS = 17
const val ROWS = 15

data class Cell(val x: Int, val y: Int)

enum class Dir(val dx: Int, val dy: Int) { Up(0, -1), Down(0, 1), Left(-1, 0), Right(1, 0) }

/**
 * Snake: the snake moves one cell per tick, edges wrap around, and only biting yourself ends the game. Filling the
 * whole board is a win. Immutable, so it can be unit-tested and kept in saved state.
 */
data class SnakeGame(
    /** Head first. */
    val body: List<Cell> = START,
    val food: Cell = Cell(12, ROWS / 2),
    val dir: Dir = Dir.Right,
    /** Buffered swipes, so quick turns aren't lost between ticks. */
    val queued: List<Dir> = emptyList(),
    val score: Int = 0,
    val state: State = State.Ready,
) {
    /** [Paused] keeps the board waiting for Resume, after leaving the app or the Pause button. */
    enum class State { Ready, Playing, Paused, Over, Won }

    enum class Step { Moved, Ate, Died, Won }

    val head: Cell get() = body.first()
    val finished: Boolean get() = state == State.Over || state == State.Won

    /** How long each step takes: it speeds up as the snake grows. */
    val tickSeconds: Float get() = (150 - score * 3).coerceAtLeast(70) / 1000f

    /** Queues a turn. A turn back on yourself, or the way you're already going, is ignored. */
    fun turn(d: Dir): SnakeGame {
        val last = queued.lastOrNull() ?: dir
        val reverse = d.dx + last.dx == 0 && d.dy + last.dy == 0
        return if (d == last || reverse || queued.size >= 3) this else copy(queued = queued + d)
    }

    /** One tick. [random] places the next food. */
    fun step(random: Random = Random): Pair<SnakeGame, Step> {
        val dir = queued.firstOrNull() ?: dir
        val next = Cell((head.x + dir.dx + COLS) % COLS, (head.y + dir.dy + ROWS) % ROWS)
        val ate = next == food
        // Moving into the cell the tail is just leaving is fine.
        val rest = if (ate) body else body.dropLast(1)
        val turned = copy(dir = dir, queued = queued.drop(1))
        if (next in rest) return turned.copy(state = State.Over) to Step.Died
        val moved = turned.copy(body = listOf(next) + rest)
        if (!ate) return moved to Step.Moved
        val taken = moved.body.toSet()
        val free = ALL.filter { it !in taken }
        return if (free.isEmpty()) moved.copy(score = score + 1, state = State.Won) to Step.Won
        else moved.copy(score = score + 1, food = free.random(random)) to Step.Ate
    }

    /**
     * The game as ints for saved state: direction, state, score, food, then the snake from the head. Buffered turns
     * aren't kept, and a game in play comes back [State.Paused].
     */
    fun save(): IntArray = intArrayOf(dir.ordinal, state.ordinal, score, food.x, food.y) + body.flatMap { listOf(it.x, it.y) }

    companion object {
        val START = listOf(Cell(4, ROWS / 2), Cell(3, ROWS / 2), Cell(2, ROWS / 2))
        private val ALL = (0 until COLS).flatMap { x -> (0 until ROWS).map { y -> Cell(x, y) } }

        fun restore(saved: IntArray) = SnakeGame(
            body = (5 until saved.size step 2).map { Cell(saved[it], saved[it + 1]) },
            food = Cell(saved[3], saved[4]),
            dir = Dir.entries[saved[0]],
            score = saved[2],
            state = State.entries[saved[1]].let { if (it == State.Playing) State.Paused else it },
        )
    }
}
