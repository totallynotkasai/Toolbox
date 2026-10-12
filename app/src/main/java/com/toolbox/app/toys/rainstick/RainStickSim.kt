package com.toolbox.app.toys.rainstick

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Grains falling through a rain stick, in the tube's own frame: x runs 0..1 across the tube, y 0..[LENGTH]
 * along it (y grows towards the end drawn at the bottom when the stick is upright). Units are tube widths.
 *
 * Plain arrays and no allocation per step. Each [step] also records the collisions it produced, for the sound:
 * [hits] holds up to [MAX_HITS] codes (`type * 10 + intensity`, see [hitType] and [hitIntensity]), [hitEnergy] the
 * summed intensity of all of them, including any that didn't fit in [hits].
 */
internal class RainStickSim(val count: Int = GRAINS, seed: Int = 7) {
    private val rng = Random(seed)

    val x = FloatArray(count)
    val y = FloatArray(count)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)
    private val ox = FloatArray(count) // positions at the start of the substep
    private val oy = FloatArray(count)
    private val bx = FloatArray(count) // bounce to add back after the velocity update
    private val by = FloatArray(count)
    private val stuck = FloatArray(count) // seconds spent resting on pins
    private val slip = FloatArray(count)  // seconds left ignoring pins
    /** Per-grain drag multiplier: light seeds drift, heavy ones drop, which spreads the flow out over time. */
    private val drag = FloatArray(count) { 0.75f + 0.55f * rng.nextFloat() }
    /** Drawn size relative to [GRAIN_R]. */
    val size = FloatArray(count) { 0.8f + 0.4f * rng.nextFloat() }
    /** Colour index, 0 until [SHADES]. */
    val shade = IntArray(count) { rng.nextInt(SHADES) }

    /** Pegs follow a helix around the tube; seen from the side they zigzag, [pegDepth] > 0 being nearer the viewer. */
    val pegCount = ((LENGTH - 2 * PEG_FIRST) / PEG_STEP).toInt() + 1
    val pegX = FloatArray(pegCount)
    val pegY = FloatArray(pegCount)
    val pegDepth = FloatArray(pegCount)

    val hits = FloatArray(MAX_HITS)
    var hitCount = 0
        private set
    var hitEnergy = 0f
        private set
    /** Collisions loud enough to hear since the sim was made (for tests). */
    var totalHits = 0L
        private set

    init {
        for (k in 0 until pegCount) {
            val a = k * HELIX_STEP
            pegX[k] = 0.5f + 0.4f * cos(a) // keeps a grain-wide gap to the wall so nothing wedges
            pegY[k] = PEG_FIRST + k * PEG_STEP
            pegDepth[k] = sin(a)
        }
        // Start settled in a loose pile at the far end.
        val perRow = 22
        for (i in 0 until count) {
            x[i] = 0.04f + (i % perRow) * 0.042f + rng.nextFloat() * 0.01f
            y[i] = LENGTH - GRAIN_R - 0.005f - (i / perRow) * 0.038f
        }
    }

    /** The fastest grain's speed in tube widths/s: close to zero once everything has settled. */
    fun fastest(): Float {
        var most = 0f
        for (i in 0 until count) most = max(most, vx[i] * vx[i] + vy[i] * vy[i])
        return sqrt(most)
    }

    /** Advances [dt] seconds under gravity ([gx], [gy]) in tube units/s², in tube coordinates. */
    fun step(dt: Float, gx: Float, gy: Float) {
        hitCount = 0
        hitEnergy = 0f
        if (dt <= 0f) return
        val n = ceil(dt / MAX_SUBSTEP).toInt().coerceIn(1, 12)
        val h = dt / n
        repeat(n) { substep(h, gx, gy) }
    }

    /**
     * Position-based: move every grain, push grains out of walls, pegs and each other, then take the velocity
     * from how far each actually moved. That keeps a resting pile still (no stored-up velocity to rattle it)
     * and makes grain-on-grain contact soft like sand. Pegs and walls then add a little bounce back.
     */
    private fun substep(h: Float, gx: Float, gy: Float) {
        val r = GRAIN_R
        for (i in 0 until count) {
            var ux = vx[i] + gx * h
            var uy = vy[i] + gy * h
            val damp = 1f - DRAG * drag[i] * h
            ux *= damp
            uy *= damp
            val sp2 = ux * ux + uy * uy
            if (sp2 > MAX_SPEED * MAX_SPEED) {
                val k = MAX_SPEED / sqrt(sp2)
                ux *= k
                uy *= k
            }
            vx[i] = ux
            vy[i] = uy
            ox[i] = x[i]
            oy[i] = y[i]
            x[i] += ux * h
            y[i] += uy * h
            bx[i] = 0f
            by[i] = 0f
        }

        for (i in 0 until count) {
            // A grain wedged between two pins would, in a real (round) tube, just roll around them; here it slips
            // past the pins for a moment instead.
            if (slip[i] > 0f) {
                slip[i] -= h
                continue
            }
            var px = x[i]
            var py = y[i]
            val ux = vx[i]
            val uy = vy[i]
            var touching = false
            // Pegs: evenly spaced along y, so only the nearest few can touch.
            val kc = ((py - PEG_FIRST) / PEG_STEP).roundToInt()
            for (k in kc - 1..kc + 1) {
                if (k < 0 || k >= pegCount) continue
                val dx = px - pegX[k]
                val dy = py - pegY[k]
                val d2 = dx * dx + dy * dy
                if (d2 >= PEG_REACH * PEG_REACH || d2 < 1e-10f) continue
                touching = true
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                px = pegX[k] + nx * PEG_REACH
                py = pegY[k] + ny * PEG_REACH
                val impact = -(ux * nx + uy * ny)
                if (impact > 0f) {
                    // Bounce off and scatter a little sideways so grains don't all take one path; the constant
                    // part of the kick tips a grain off the top of a pin.
                    val kick = (rng.nextFloat() - 0.5f) * (impact * 0.6f + 0.05f)
                    bx[i] += nx * impact * PEG_BOUNCE - ny * kick
                    by[i] += ny * impact * PEG_BOUNCE + nx * kick
                    hit(TYPE_PEG, impact)
                }
            }
            stuck[i] = if (touching && ux * ux + uy * uy < STUCK_SPEED * STUCK_SPEED) stuck[i] + h else 0f
            if (stuck[i] > STUCK_TIME) {
                stuck[i] = 0f
                slip[i] = SLIP_TIME
            }
            x[i] = px
            y[i] = py
        }

        // Grain against grain: brute force with cheap rejects; a couple of hundred grains is fine.
        val d0 = 2f * r
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val dy = y[j] - y[i]
                if (dy >= d0 || dy <= -d0) continue
                val dx = x[j] - x[i]
                if (dx >= d0 || dx <= -d0) continue
                val d2 = dx * dx + dy * dy
                if (d2 >= d0 * d0 || d2 < 1e-10f) continue
                val d = sqrt(d2)
                val nx = dx / d
                val ny = dy / d
                val push = (d0 - d) * 0.5f
                x[i] -= nx * push
                y[i] -= ny * push
                x[j] += nx * push
                y[j] += ny * push
                val closing = -((vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny)
                if (closing > GRAIN_HIT_MIN) hit(TYPE_GRAIN, closing * 0.6f)
            }
        }

        // Walls and end caps last, so nothing is left outside the tube.
        for (i in 0 until count) {
            var px = x[i]
            var py = y[i]
            val ux = vx[i]
            val uy = vy[i]
            if (px < r) { px = r; if (ux < 0f) wall(i, 1f, 0f, -ux) }
            if (px > 1f - r) { px = 1f - r; if (ux > 0f) wall(i, -1f, 0f, ux) }
            if (py < r) { py = r; if (uy < 0f) wall(i, 0f, 1f, -uy) }
            if (py > LENGTH - r) { py = LENGTH - r; if (uy > 0f) wall(i, 0f, -1f, uy) }
            x[i] = px
            y[i] = py
            vx[i] = (px - ox[i]) / h + bx[i]
            vy[i] = (py - oy[i]) / h + by[i]
        }
    }

    private fun wall(i: Int, nx: Float, ny: Float, impact: Float) {
        bx[i] += nx * impact * WALL_BOUNCE
        by[i] += ny * impact * WALL_BOUNCE
        hit(TYPE_WALL, impact)
    }

    private fun hit(type: Int, speed: Float) {
        if (speed < HIT_MIN) return
        val intensity = (speed / HIT_FULL).coerceAtMost(1f)
        hitEnergy += intensity
        totalHits++
        if (hitCount < MAX_HITS) hits[hitCount++] = type * 10f + intensity
    }

    companion object {
        const val LENGTH = 5.2f
        const val GRAIN_R = 0.02f
        const val PEG_R = 0.028f
        const val GRAINS = 200
        const val SHADES = 7
        const val MAX_HITS = 64
        /** Below this ([fastest]), with no collisions, the grains are at rest. */
        const val SETTLED_SPEED = 0.05f

        const val TYPE_PEG = 0
        const val TYPE_GRAIN = 1
        const val TYPE_WALL = 2

        fun hitType(code: Float): Int = (code / 10f).toInt()
        fun hitIntensity(code: Float): Float = code - hitType(code) * 10f

        private const val PEG_FIRST = 0.45f  // pegs stop short of the ends, leaving room for the pile
        private const val PEG_STEP = 0.06f
        private const val HELIX_STEP = 0.95f // radians between neighbouring pegs
        private const val PEG_REACH = PEG_R + GRAIN_R
        private const val MAX_SUBSTEP = 1f / 240f
        private const val DRAG = 5.5f        // 1/s; with ~9 u/s² gravity grains drift down at ~1.5 tube widths/s
        private const val MAX_SPEED = 2.6f
        private const val PEG_BOUNCE = 0.35f
        private const val WALL_BOUNCE = 0.25f
        private const val HIT_MIN = 0.18f     // slower touches are silent, so a resting pile is quiet
        private const val GRAIN_HIT_MIN = 0.3f
        private const val STUCK_SPEED = 0.08f
        private const val STUCK_TIME = 0.4f
        private const val SLIP_TIME = 0.12f
        private const val HIT_FULL = 1.8f
    }
}
