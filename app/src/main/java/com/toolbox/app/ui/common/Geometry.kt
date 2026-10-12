package com.toolbox.app.ui.common

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Small helpers shared by the toys that turn round a centre (the Dial, the Ratchet, the Fidget Spinner). Angles are
// in degrees, clockwise from 12 o'clock, the way a finger going round a knob sees them.

/** Shortest signed difference for an angle change, -180..180. */
fun wrapDelta(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/** Angle of [p] around [c], degrees clockwise from 12 o'clock. */
fun angleOf(p: Offset, c: Offset): Float =
    Math.toDegrees(atan2((p.x - c.x).toDouble(), (c.y - p.y).toDouble())).toFloat()

/** The point [r] from [c] at [deg] degrees clockwise from 12 o'clock. */
fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
}

/**
 * How far a drag from [from] to [to] turns something centred on [c], or 0 if either point is within [hub] of the
 * centre, where the angle swings wildly for tiny moves. A fast flick straight through the middle would otherwise
 * read as a half turn in a random direction, so each step is also capped at [maxStep] degrees.
 */
fun turnDelta(from: Offset, to: Offset, c: Offset, hub: Float, maxStep: Float = 45f): Float {
    if ((from - c).getDistance() <= hub || (to - c).getDistance() <= hub) return 0f
    return wrapDelta(angleOf(to, c) - angleOf(from, c)).coerceIn(-maxStep, maxStep)
}

/** 0 below 0, 1 above 1, and an S-curve between: a soft fade with no corners. */
fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * [value] to [decimals] places, never "-0.00": a reading that rounds to zero loses its minus sign, so a knob
 * settling just short of zero doesn't flicker between "-0.00" and "0.00".
 */
fun fixed(value: Float, decimals: Int): String {
    var scale = 1f
    repeat(decimals) { scale *= 10f }
    val v = if (abs(value * scale).roundToInt() == 0) 0f else value
    return "%.${decimals}f".format(v)
}
