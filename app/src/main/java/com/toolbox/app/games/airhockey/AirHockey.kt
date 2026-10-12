package com.toolbox.app.games.airhockey

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * Air hockey for two players on one screen. Player 0 defends the bottom goal, player 1 the top.
 *
 * Units are table widths: the table is 1 wide and [height] tall, y down. Players move their mallets by setting
 * [setTarget]; each mallet follows its target within its own half, and its speed comes from how far it moved,
 * so a quick flick hits the puck hard. Pure and deterministic for a given [seed], so it can be tested.
 */
class AirHockey(val height: Float = 1.7f, seed: Long = 0L) {
    data class Point(val x: Float, val y: Float)

    sealed interface Event {
        /** Puck off a wall or post; [intensity] 0–1. */
        data class Wall(val intensity: Float) : Event
        data class Hit(val player: Int, val intensity: Float) : Event
        data class Goal(val scorer: Int) : Event
    }

    private val random = Random(seed)

    var puckX = 0.5f; private set
    var puckY = height / 2; private set
    private var puckVx = 0f
    private var puckVy = 0f

    private val malletX = floatArrayOf(0.5f, 0.5f)
    private val malletY = floatArrayOf(height - 0.18f, 0.18f)
    private val malletVx = FloatArray(2)
    private val malletVy = FloatArray(2)
    private val targetX = floatArrayOf(0.5f, 0.5f)
    private val targetY = floatArrayOf(height - 0.18f, 0.18f)
    /** Top speed of each mallet (table widths per second); lower it for a computer player. */
    val malletSpeed = floatArrayOf(MALLET_MAX_SPEED, MALLET_MAX_SPEED)
    /** How far behind the puck the computer's idea of it trails, in seconds, like a reaction time. 0 is perfect. */
    var aiReaction = 0f

    // Where the computer thinks the puck is.
    private var seenX = puckX
    private var seenY = puckY

    val scores = IntArray(2)
    /** Seconds until the puck is back in play after a goal. */
    var serveDelay = 0f; private set
    private var accumulator = 0f
    // Seconds the puck has sat nearly still.
    private var stalled = 0f

    val winner: Int? get() = scores.indices.firstOrNull { scores[it] >= WIN_SCORE }

    fun mallet(player: Int) = Point(malletX[player], malletY[player])
    val puckSpeed get() = hypot(puckVx, puckVy)

    fun setTarget(player: Int, x: Float, y: Float) {
        targetX[player] = x
        targetY[player] = y
    }

    /** Starts a fresh match with the puck on [serveTo]'s side. */
    fun reset(serveTo: Int = 0) {
        scores.fill(0)
        for (p in 0..1) {
            malletX[p] = 0.5f; malletY[p] = restY(p); targetX[p] = 0.5f; targetY[p] = restY(p)
            malletVx[p] = 0f; malletVy[p] = 0f
        }
        serve(toward = serveTo)
        serveDelay = 0f
    }

    /** Carries on a match at [blue]–[red], with everything back in its starting place and the puck on blue's side. */
    fun resume(blue: Int, red: Int) {
        reset()
        scores[0] = blue
        scores[1] = red
    }

    /** Advances by [dt] seconds in fixed sub-steps, so fast pucks don't pass through mallets. */
    fun step(dt: Float): List<Event> {
        val events = ArrayList<Event>(2)
        accumulator += dt.coerceAtMost(0.1f)
        while (accumulator >= STEP) {
            accumulator -= STEP
            substep(STEP, events)
        }
        return events
    }

    private fun substep(h: Float, events: MutableList<Event>) {
        for (p in 0..1) moveMallet(p, h)
        val follow = if (aiReaction > 0f) 1 - exp(-h / aiReaction) else 1f
        seenX += (puckX - seenX) * follow
        seenY += (puckY - seenY) * follow
        if (winner != null) return
        if (serveDelay > 0f) {
            serveDelay -= h
            return
        }
        val drag = exp(-FRICTION * h)
        puckVx *= drag; puckVy *= drag
        puckX += puckVx * h
        puckY += puckVy * h

        // Side walls.
        if (puckX < PUCK_R) { puckX = PUCK_R; bounceX(events) }
        if (puckX > 1 - PUCK_R) { puckX = 1 - PUCK_R; bounceX(events) }

        // End walls, except across the mouth of each goal.
        val inMouth = abs(puckX - 0.5f) < GOAL_WIDTH / 2
        if (!inMouth) {
            if (puckY < PUCK_R) { puckY = PUCK_R; bounceY(events) }
            if (puckY > height - PUCK_R) { puckY = height - PUCK_R; bounceY(events) }
        }
        for (postX in floatArrayOf(0.5f - GOAL_WIDTH / 2, 0.5f + GOAL_WIDTH / 2)) {
            for (postY in floatArrayOf(0f, height)) bounceOffPoint(postX, postY, events)
        }
        // A puck fully past the end line is in the goal.
        if (puckY < -PUCK_R) { goal(scorer = 0, events); return }
        if (puckY > height + PUCK_R) { goal(scorer = 1, events); return }

        for (p in 0..1) collideMallet(p, events)

        val speed = puckSpeed
        if (speed > MAX_SPEED) { puckVx *= MAX_SPEED / speed; puckVy *= MAX_SPEED / speed }

        // A puck that dies against the boards can be out of reach (the computer can't dig it out of a corner, and the
        // other player can't cross the centre line), so after a while it's served again on that side. One sitting
        // in the open, like a serve nobody has hit yet, is left alone.
        val margin = PUCK_R + 0.02f
        val onBoards = puckX < margin || puckX > 1 - margin || puckY < margin || puckY > height - margin
        stalled = if (onBoards && speed < STALL_SPEED) stalled + h else 0f
        if (stalled > STALL_TIME) {
            serve(toward = if (puckY > height / 2) 0 else 1)
            serveDelay = SERVE_DELAY
        }
    }

    /** The mallet chases its target at up to [MALLET_MAX_SPEED], kept inside its own half. */
    private fun moveMallet(p: Int, h: Float) {
        val (minY, maxY) = if (p == 0) height / 2 + MALLET_R to height - MALLET_R else MALLET_R to height / 2 - MALLET_R
        val tx = targetX[p].coerceIn(MALLET_R, 1 - MALLET_R)
        val ty = targetY[p].coerceIn(minY, maxY)
        var dx = tx - malletX[p]
        var dy = ty - malletY[p]
        val dist = hypot(dx, dy)
        val maxStep = malletSpeed[p] * h
        if (dist > maxStep) { dx *= maxStep / dist; dy *= maxStep / dist }
        malletX[p] += dx; malletY[p] += dy
        // Smoothed so one jumpy touch sample doesn't produce a huge hit.
        malletVx[p] += (dx / h - malletVx[p]) * 0.35f
        malletVy[p] += (dy / h - malletVy[p]) * 0.35f
    }

    private fun collideMallet(p: Int, events: MutableList<Event>) {
        val dx = puckX - malletX[p]
        val dy = puckY - malletY[p]
        val dist = hypot(dx, dy)
        val minDist = PUCK_R + MALLET_R
        if (dist >= minDist || dist < 1e-6f) return
        val nx = dx / dist; val ny = dy / dist
        puckX = malletX[p] + nx * minDist
        puckY = malletY[p] + ny * minDist
        // The mallet is effectively immovable: reflect the puck's velocity relative to it.
        val rvx = puckVx - malletVx[p]
        val rvy = puckVy - malletVy[p]
        val along = rvx * nx + rvy * ny
        if (along < 0f) {
            puckVx -= (1 + MALLET_BOUNCE) * along * nx
            puckVy -= (1 + MALLET_BOUNCE) * along * ny
            events += Event.Hit(p, min(1f, -along / MAX_SPEED))
        }
        // Keep a pinned puck from being pushed through a wall.
        puckX = puckX.coerceIn(PUCK_R, 1 - PUCK_R)
    }

    private fun bounceX(events: MutableList<Event>) {
        if (abs(puckVx) > 0.05f) events += Event.Wall(min(1f, abs(puckVx) / MAX_SPEED))
        puckVx = -puckVx * WALL_BOUNCE
        if (puckX <= PUCK_R) puckVx = abs(puckVx) else if (puckX >= 1 - PUCK_R) puckVx = -abs(puckVx)
    }

    private fun bounceY(events: MutableList<Event>) {
        if (abs(puckVy) > 0.05f) events += Event.Wall(min(1f, abs(puckVy) / MAX_SPEED))
        puckVy = if (puckY <= PUCK_R) abs(puckVy) * WALL_BOUNCE else -abs(puckVy) * WALL_BOUNCE
    }

    private fun bounceOffPoint(x: Float, y: Float, events: MutableList<Event>) {
        val dx = puckX - x; val dy = puckY - y
        val dist = hypot(dx, dy)
        if (dist >= PUCK_R || dist < 1e-6f) return
        val nx = dx / dist; val ny = dy / dist
        puckX = x + nx * PUCK_R; puckY = y + ny * PUCK_R
        val along = puckVx * nx + puckVy * ny
        if (along < 0f) {
            puckVx -= (1 + WALL_BOUNCE) * along * nx
            puckVy -= (1 + WALL_BOUNCE) * along * ny
            events += Event.Wall(min(1f, -along / MAX_SPEED))
        }
    }

    private fun goal(scorer: Int, events: MutableList<Event>) {
        scores[scorer]++
        events += Event.Goal(scorer)
        // The player who conceded serves.
        serve(toward = 1 - scorer)
        serveDelay = SERVE_DELAY
    }

    private fun serve(toward: Int) {
        stalled = 0f
        puckX = 0.5f + (random.nextFloat() - 0.5f) * 0.2f
        puckY = if (toward == 0) height * 0.68f else height * 0.32f
        puckVx = 0f; puckVy = 0f
    }

    /** Puts the puck somewhere, for tests. */
    internal fun placePuck(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f) {
        puckX = x; puckY = y; puckVx = vx; puckVy = vy
    }

    private fun restY(p: Int) = if (p == 0) height - 0.18f else 0.18f

    /**
     * Where a computer-controlled player 1 wants its mallet: guarding the goal mouth while the puck is in the other
     * half, and when it comes over, getting on the goal side of it and driving through it. It goes by where it
     * last saw the puck ([aiReaction]). Pair with a lower [malletSpeed] so it can be beaten.
     */
    fun aiTarget(): Point {
        val x = seenX; val y = seenY
        val serving = serveDelay > 0f
        if (serving || y > height / 2) return Point(0.5f + (x - 0.5f) * 0.5f, 0.16f)
        return if (malletY[1] < y - PUCK_R) {
            // Goal side of the puck: strike through it along the line from the mallet, leaning a little towards
            // the middle. Aiming at a fixed point past the puck could slide by it, flip to going round, and dither.
            val dx = x - malletX[1] + (0.5f - x) * 0.15f
            val dy = y - malletY[1]
            val d = hypot(dx, dy)
            Point(x + dx / d * 0.14f, y + dy / d * 0.14f)
        } else if (y - (PUCK_R + MALLET_R) < MALLET_R + 0.01f && puckSpeed < 0.5f) {
            // Too near our own end to get behind it: shove it out sideways, or it would sit there for good.
            Point(x, y)
        } else {
            // Wrong side: go round it rather than knocking it towards our own goal.
            val side = if (x > malletX[1]) -1f else 1f
            Point(x + side * (PUCK_R + MALLET_R + 0.02f), y - (PUCK_R + MALLET_R))
        }
    }

    companion object {
        const val STEP = 1f / 240f
        const val PUCK_R = 0.045f
        const val MALLET_R = 0.075f
        const val GOAL_WIDTH = 0.36f
        const val FRICTION = 0.35f
        const val MAX_SPEED = 3.2f
        const val MALLET_MAX_SPEED = 6f
        const val WALL_BOUNCE = 0.88f
        const val MALLET_BOUNCE = 0.8f
        const val SERVE_DELAY = 0.9f
        const val STALL_SPEED = 0.05f
        const val STALL_TIME = 3f
        const val WIN_SCORE = 7
    }
}
