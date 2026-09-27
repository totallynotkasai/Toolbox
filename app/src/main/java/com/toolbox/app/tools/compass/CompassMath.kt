package com.toolbox.app.tools.compass

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Wraps any angle into [0, 360). */
fun normalizeDegrees(deg: Float): Float = ((deg % 360f) + 360f) % 360f

/** Signed shortest turn from [from] to [to], in (-180, 180]. */
fun shortestDelta(from: Float, to: Float): Float {
    val d = normalizeDegrees(to - from)
    return if (d > 180f) d - 360f else d
}

/**
 * Heading in degrees (0 = magnetic north, clockwise) from a 3×3 row-major rotation matrix that maps device
 * coordinates to world (east, north, up) coordinates, already remapped for the display rotation.
 *
 * `getOrientation`'s azimuth breaks down when the phone is held upright (the screen's Y axis points at the sky).
 * Instead this sums the horizontal parts of two device axes: the top of the screen (+Y) and the back camera (−Z).
 * Lying flat only +Y is horizontal, held upright only −Z is, and in between both point the same way, so the
 * reading stays steady through any forward tilt. Returns null if neither axis has a usable horizontal direction.
 */
fun headingFromMatrix(r: FloatArray): Float? {
    // Column j of R is device axis j expressed in world coordinates.
    val east = r[1] - r[2]
    val north = r[4] - r[5]
    if (hypot(east, north) < 0.1f) return null
    return normalizeDegrees(Math.toDegrees(atan2(east, north).toDouble()).toFloat())
}

/**
 * Low-pass filter on the heading's sine and cosine, so 359° → 0° doesn't swing the long way round.
 * [alpha] is the share of each new reading (0..1): smaller is smoother but slower.
 */
class HeadingFilter(private val alpha: Float = 0.15f) {
    private var s = 0f
    private var c = 0f
    private var primed = false

    /** Feeds one reading and returns the smoothed heading. */
    fun update(deg: Float): Float {
        val rad = Math.toRadians(deg.toDouble())
        val ns = sin(rad).toFloat()
        val nc = cos(rad).toFloat()
        if (!primed) { s = ns; c = nc; primed = true }
        else { s += (ns - s) * alpha; c += (nc - c) * alpha }
        return normalizeDegrees(Math.toDegrees(atan2(s, c).toDouble()).toFloat())
    }

    fun reset() { primed = false }
}

private val POINTS_8 = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private val POINTS_8_NAMES = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")

/** Eight-point abbreviation, e.g. 274° → "W", 30° → "NE". */
fun compassPoint(deg: Float): String = POINTS_8[pointIndex(deg)]

/** Spoken form for TalkBack, e.g. "274 degrees, west". */
fun spokenHeading(deg: Float): String = "${roundedDegrees(deg)} degrees, ${POINTS_8_NAMES[pointIndex(deg)]}"

/** Whole degrees for display, with 359.6 shown as 0 rather than 360. */
fun roundedDegrees(deg: Float): Int = normalizeDegrees(deg).roundToInt() % 360

private fun pointIndex(deg: Float) = ((normalizeDegrees(deg) + 22.5f) / 45f).toInt() % 8

/**
 * Decides when to fire a haptic tick as a cardinal point (N, E, S, W) passes the lubber line.
 * A tick needs the heading to actually cross the point, and the same point can't tick again until the heading
 * has moved [rearmDegrees] away from it, so sensor jitter while pointing due north doesn't buzz continuously.
 */
class CardinalTicker(private val rearmDegrees: Float = 6f) {
    private var last: Float? = null
    private var lastTicked: Int? = null

    /** Feeds the next heading; returns the cardinal (0, 90, 180 or 270) that was crossed, or null. */
    fun update(heading: Float): Int? {
        val prev = last
        last = heading
        lastTicked?.let { if (abs(shortestDelta(it.toFloat(), heading)) > rearmDegrees) lastTicked = null }
        if (prev == null) return null
        val d = shortestDelta(prev, heading)
        if (d == 0f) return null
        for (c in intArrayOf(0, 90, 180, 270)) {
            if (c == lastTicked) continue
            val toC = shortestDelta(prev, c.toFloat())
            val crossed = if (d > 0) toC > 0f && toC <= d else toC < 0f && toC >= d
            if (crossed) { lastTicked = c; return c }
        }
        return null
    }
}

/** Earth's field is roughly 25–65 µT; well outside that, something nearby (a magnet, a laptop, steel) is distorting it. */
fun isFieldDisturbed(microTesla: Float): Boolean = microTesla < 20f || microTesla > 70f
