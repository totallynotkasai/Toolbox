package com.toolbox.app.tools.meditate

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** One part of a breath, and how big the breathing circle is at the end of it. */
internal enum class Kind(val label: String, val size: Float) {
    In("Breathe in", FULL),
    HoldFull("Hold", FULL),
    Out("Breathe out", EMPTY),
    HoldEmpty("Hold", EMPTY),
}

internal const val FULL = 1f
internal const val EMPTY = 0.6f

internal data class Phase(val kind: Kind, val seconds: Int)

internal enum class Pattern(val label: String, val phases: List<Phase>) {
    Calm("Calm", listOf(Phase(Kind.In, 4), Phase(Kind.Out, 6))),
    Square("Box", listOf(Phase(Kind.In, 4), Phase(Kind.HoldFull, 4), Phase(Kind.Out, 4), Phase(Kind.HoldEmpty, 4))),
    Relax("4-7-8", listOf(Phase(Kind.In, 4), Phase(Kind.HoldFull, 7), Phase(Kind.Out, 8))),
}

/** Where a session is at a moment: which phase, how far through it (0..1), and how much of the session is left. */
internal data class Moment(val index: Int, val kind: Kind, val progress: Float, val remainingMs: Long, val phaseLeftMs: Long)

/**
 * A breathing session of [pattern], as near to [minutes] long as a whole number of breaths allows, and always ending
 * on a breath out (a hold after the last breath out is left off). Everything is worked out from the time since it
 * started, so it can't drift, pause or skip whatever the frame rate does.
 */
internal class Session(val pattern: Pattern, minutes: Int) {
    val cycles: Int
    val phases: List<Phase>
    val totalMs: Long

    init {
        val cycleMs = pattern.phases.sumOf { it.seconds } * 1000L
        val trailing = if (pattern.phases.last().kind == Kind.HoldEmpty) pattern.phases.last().seconds * 1000L else 0L
        fun length(c: Int) = c * cycleMs - trailing
        val target = minutes * 60_000L
        cycles = (1..(target / cycleMs + 2).toInt()).minBy { abs(length(it) - target) }
        phases = List(cycles) { pattern.phases }.flatten().let { if (trailing > 0) it.dropLast(1) else it }
        totalMs = length(cycles)
    }

    private val starts = LongArray(phases.size).also { s ->
        var t = 0L
        phases.forEachIndexed { i, p -> s[i] = t; t += p.seconds * 1000L }
    }

    fun at(elapsedMs: Long): Moment {
        val t = elapsedMs.coerceIn(0L, totalMs)
        var i = starts.indexOfLast { it <= t }
        if (i < 0) i = 0
        val length = phases[i].seconds * 1000L
        val into = (t - starts[i]).coerceAtMost(length)
        return Moment(i, phases[i].kind, into / length.toFloat(), totalMs - t, length - into)
    }

    /** The circle's size at [elapsedMs]: growing on the breath in, still on a hold, shrinking on the breath out. */
    fun sizeAt(elapsedMs: Long): Float {
        val m = at(elapsedMs)
        val from = if (m.index == 0) EMPTY else phases[m.index - 1].kind.size
        val ease = 0.5f - 0.5f * cos(PI.toFloat() * m.progress)
        return from + (m.kind.size - from) * ease
    }
}
