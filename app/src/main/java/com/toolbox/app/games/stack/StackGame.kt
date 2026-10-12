package com.toolbox.app.games.stack

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stack Tower: a block slides back and forth above the tower and each tap drops it. Whatever hangs over the edge of
 * the block below is sliced off, so the tower narrows, until a block misses completely.
 * A drop within [PERFECT] of the block below (or a quarter of its width, if that's less) counts as perfect and loses
 * nothing; several in a row widen it again.
 *
 * Pure, so it can be unit-tested. Horizontal units are fractions of the play area's width (0 to 1).
 */
class StackGame {
    enum class State { Ready, Playing, Over }

    data class Block(val x: Float, val width: Float) {
        val right get() = x + width
    }

    /** A sliced-off piece, for the screen to animate falling. [level] is the row it was cut from. */
    data class Offcut(val x: Float, val width: Float, val level: Int)

    sealed interface Drop {
        /** Landed with nothing cut off; [streak] counts consecutive perfect drops. */
        data class Perfect(val streak: Int, val grew: Boolean) : Drop
        data class Cut(val offcut: Offcut) : Drop
        /** Missed the tower entirely; the whole block falls. */
        data class Miss(val offcut: Offcut) : Drop
    }

    var state = State.Ready; private set
    /** Placed blocks, bottom first. The first is the base and doesn't score. */
    var tower = listOf(Block((1 - BASE_WIDTH) / 2, BASE_WIDTH)); private set
    /** The sliding block (not part of [tower] until dropped). */
    var moving = Block(0f, BASE_WIDTH); private set
    var streak = 0; private set
    private var direction = 1f

    val score get() = tower.size - 1
    val top get() = tower.last()

    /** Field widths per second: speeds up as the tower grows. */
    val speed get() = min(SPEED_MAX, SPEED_START + SPEED_GAIN * score)

    fun start() {
        reset()
        state = State.Playing
        spawn()
    }

    /** Back to just the base, waiting for the first tap. */
    fun reset() {
        tower = listOf(Block((1 - BASE_WIDTH) / 2, BASE_WIDTH))
        streak = 0
        state = State.Ready
    }

    /** Slides the moving block for [dt] seconds, bouncing between the limits of its travel. */
    fun step(dt: Float) {
        if (state != State.Playing) return
        var x = moving.x + direction * speed * dt
        val (lo, hi) = travel(moving.width)
        if (x > hi) { x = hi - (x - hi); direction = -1f }
        if (x < lo) { x = lo + (lo - x); direction = 1f }
        moving = moving.copy(x = x.coerceIn(lo, hi))
    }

    fun drop(): Drop? {
        if (state != State.Playing) return null
        val below = top
        val level = tower.size
        // On a very narrow tower the allowance shrinks with it, so a "perfect" block always overlaps most of the one below.
        if (abs(moving.x - below.x) <= min(PERFECT, below.width * 0.25f)) {
            streak++
            val grow = streak >= GROW_AFTER && below.width < BASE_WIDTH
            val width = if (grow) min(BASE_WIDTH, below.width + GROW_BY) else below.width
            val x = (below.x - (width - below.width) / 2).coerceIn(0f, 1f - width)
            tower = tower + Block(x, width)
            spawn()
            return Drop.Perfect(streak, grow)
        }
        streak = 0
        val left = max(moving.x, below.x)
        val right = min(moving.right, below.right)
        if (right - left <= 0f) {
            state = State.Over
            return Drop.Miss(Offcut(moving.x, moving.width, level))
        }
        val offcut = if (moving.x < below.x) Offcut(moving.x, below.x - moving.x, level)
        else Offcut(below.right, moving.right - below.right, level)
        tower = tower + Block(left, right - left)
        spawn()
        return Drop.Cut(offcut)
    }

    /**
     * Everything needed to carry on after the screen is recreated, as floats: state, streak, direction, the moving
     * block, then each tower block from the base.
     */
    fun save(): FloatArray = floatArrayOf(state.ordinal.toFloat(), streak.toFloat(), direction, moving.x, moving.width) +
        tower.flatMap { listOf(it.x, it.width) }

    fun restore(saved: FloatArray) {
        state = State.entries[saved[0].toInt()]
        streak = saved[1].toInt()
        direction = saved[2]
        moving = Block(saved[3], saved[4])
        tower = (5 until saved.size step 2).map { Block(saved[it], saved[it + 1]) }
    }

    /** The next block comes in from alternating sides, at the width of the top of the tower. */
    private fun spawn() {
        val w = top.width
        val (lo, hi) = travel(w)
        direction = if (tower.size % 2 == 0) 1f else -1f
        moving = Block(if (direction > 0) lo else hi, w)
    }

    /**
     * From just clear of the tower on one side to just clear on the other, so each end of the swing is a miss,
     * but kept mostly on screen.
     */
    private fun travel(width: Float): Pair<Float, Float> {
        val lo = max(top.x - width - OVERSHOOT, -width * 0.8f)
        val hi = min(top.right + OVERSHOOT, 1f - width * 0.2f)
        return lo to hi
    }

    companion object {
        const val BASE_WIDTH = 0.6f
        const val PERFECT = 0.012f
        const val GROW_AFTER = 3
        const val GROW_BY = 0.03f
        const val OVERSHOOT = 0.05f
        const val SPEED_START = 0.55f
        const val SPEED_GAIN = 0.018f
        const val SPEED_MAX = 1.4f
    }
}
