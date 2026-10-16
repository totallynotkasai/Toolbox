package com.toolbox.app.toys.infinitycube

import androidx.compose.ui.geometry.Offset
import com.toolbox.app.ui.common.Quat
import com.toolbox.app.ui.common.Rot
import com.toolbox.app.ui.common.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where one of the eight cubes is: how it's turned ([rot], from its own frame) and where its [centre] is. */
internal class CubePose(val rot: Rot, val centre: Vec3) {
    /** [p], given in the cube's own frame (-0.5..0.5 on each axis), in the toy's frame. */
    fun place(p: Vec3): Vec3 = centre + rot * p
}

/** One of the eight hinges: the edge cubes [a] and [b] share, from [from] to [to] in [a]'s own frame. */
internal class Hinge(val a: Int, val b: Int, val from: Vec3, val to: Vec3)

/**
 * One square face of a cube, in the cube's own frame: its outward [normal] and [corners], anticlockwise seen from
 * outside. [side] is the side of the first closed cube it's part of (0..5, see [InfinityCube.sideNormals]), or -1 for
 * the 24 squares hidden inside it, which make the outside of the second.
 */
internal class Square(val normal: Vec3, val corners: List<Vec3>, val side: Int)

/**
 * The infinity cube: eight cubes in a ring, each hinged to the next along one edge, laid out as the real toy is
 * (after CMG Lee's drawing of its six positions on Wikipedia). It folds through six positions and back to the first,
 * endlessly either way: closed, opened out flat, rolled over, closed inside out, opened out, rolled over, and closed
 * again as it started.
 *
 * Everything comes from one number, the fold given to [pose]: 0 is closed, each whole step is the next position and
 * the fractions are the folds between. Each fold turns groups of cubes about a line of hinges, so every position is
 * worked out from the fold alone and the cubes can't drift apart. Cubes are 1 unit across, and every pose is centred
 * on the middle of the eight.
 *
 * The toy's own axes: x runs from the side that opens first to the back, y from left to right, and z from top to
 * bottom (as in the drawing, where z points down). Plain Kotlin, so it can be unit tested.
 */
internal object InfinityCube {
    const val CUBES = 8

    /** Folds in a full cycle, back to where it started. */
    const val STEPS = 6

    /** Whether whole step [step] is a closed cube (the others are flat). */
    fun isClosed(step: Int): Boolean = Math.floorMod(step, 3) == 0

    /** Whether whole step [step] is the closed cube with the first one's inside out, the cream side. */
    fun isInsideOut(step: Int): Boolean = Math.floorMod(step, STEPS) == 3

    // Cube i sits in cell (x, y, z) of the closed cube, centred on the origin; hinge i joins cube i to cube i + 1.
    private val cells = listOf(
        Vec3(-0.5f, -0.5f, -0.5f), Vec3(0.5f, -0.5f, -0.5f), Vec3(0.5f, 0.5f, -0.5f), Vec3(-0.5f, 0.5f, -0.5f),
        Vec3(-0.5f, 0.5f, 0.5f), Vec3(0.5f, 0.5f, 0.5f), Vec3(0.5f, -0.5f, 0.5f), Vec3(-0.5f, -0.5f, 0.5f),
    )

    /** The hinges round the ring, each an edge of the closed cube (given here in its frame, then made local to [Hinge.a]). */
    val hinges: List<Hinge> = listOf(
        Vec3(0f, -1f, -1f) to Vec3(0f, -1f, 0f), // up the middle of the left side, top half
        Vec3(0f, 0f, -1f) to Vec3(1f, 0f, -1f),  // along the middle of the top, back half
        Vec3(0f, 1f, -1f) to Vec3(0f, 1f, 0f),   // up the middle of the right side, top half
        Vec3(-1f, 0f, 0f) to Vec3(-1f, 1f, 0f),  // across the middle of the front, right half
        Vec3(0f, 1f, 0f) to Vec3(0f, 1f, 1f),    // up the middle of the right side, bottom half
        Vec3(0f, 0f, 1f) to Vec3(1f, 0f, 1f),    // along the middle of the bottom, back half
        Vec3(0f, -1f, 0f) to Vec3(0f, -1f, 1f),  // up the middle of the left side, bottom half
        Vec3(-1f, -1f, 0f) to Vec3(-1f, 0f, 0f), // across the middle of the front, left half
    ).mapIndexed { i, (from, to) -> Hinge(i, (i + 1) % CUBES, from - cells[i], to - cells[i]) }

    /** Some cubes turning [degrees] (right-handed) about the line of hinge [hinge], as it lies when the fold starts. */
    private class Turn(val cubes: IntArray, val hinge: Int, val degrees: Float)

    // The six folds, from each position to the next: two groups of four opening like a book, two rows of four
    // rolling over, then the two end pairs folding 180° underneath. Directions are the ones where no cube passes
    // through another.
    private val folds = listOf(
        listOf(Turn(intArrayOf(0, 1, 2, 3), 3, 90f), Turn(intArrayOf(4, 5, 6, 7), 3, -90f)),
        listOf(Turn(intArrayOf(0, 1, 6, 7), 1, 90f), Turn(intArrayOf(2, 3, 4, 5), 1, -90f)),
        listOf(Turn(intArrayOf(1, 2), 2, 180f), Turn(intArrayOf(5, 6), 4, 180f)),
        listOf(Turn(intArrayOf(0, 1, 6, 7), 1, -90f), Turn(intArrayOf(2, 3, 4, 5), 1, 90f)),
        listOf(Turn(intArrayOf(0, 1, 2, 3), 3, -90f), Turn(intArrayOf(4, 5, 6, 7), 3, 90f)),
        listOf(Turn(intArrayOf(0, 7), 0, -180f), Turn(intArrayOf(3, 4), 2, 180f)),
    )

    /** A turn ready to apply: the line it turns about, in the position the fold starts from. */
    private class Axis(val cubes: IntArray, val point: Vec3, val direction: Vec3, val radians: Float)

    private val rest: List<List<CubePose>>
    private val axes: List<List<Axis>>

    init {
        var at = cells.map { CubePose(Quat.Identity.toRot(), it) }
        val positions = ArrayList<List<CubePose>>()
        val lines = ArrayList<List<Axis>>()
        for (fold in folds) {
            positions += at
            val axis = fold.map { turn ->
                val h = hinges[turn.hinge]
                val p = at[h.a].place(h.from)
                Axis(turn.cubes, p, at[h.a].place(h.to) - p, Math.toRadians(turn.degrees.toDouble()).toFloat())
            }
            lines += axis
            // The next position is on the grid again: square it up, so float error can't build up round the cycle.
            at = centred(turned(at, axis, 1f)).map { CubePose(squared(it.rot), halves(it.centre)) }
        }
        rest = positions
        axes = lines
    }

    /** Every cube's pose at [fold]: whole numbers are the six positions, repeating every [STEPS] either way. */
    fun pose(fold: Float): List<CubePose> {
        val k = floor(fold).toInt()
        val t = fold - k
        val m = Math.floorMod(k, STEPS)
        return if (t == 0f) rest[m] else centred(turned(rest[m], axes[m], t))
    }

    /**
     * The cube that stays still while cube [grabbed] is dragged through the fold from whole step [step] to the next, as
     * if a hand held it there: the other half when two halves turn, the middle when the ends fold under, and the nearer
     * end when it's the middle that's dragged.
     */
    fun anchor(step: Int, grabbed: Int): Int {
        val m = Math.floorMod(step, STEPS)
        val turns = folds[m]
        val moving = turns.firstOrNull { grabbed in it.cubes }
        val still = (0 until CUBES).firstOrNull { c -> turns.none { c in it.cubes } }
        return when {
            moving == null -> turns.minBy { (rest[m][it.cubes[0]].centre - rest[m][grabbed].centre).length() }.cubes[0]
            still != null -> still
            else -> turns.first { it !== moving }.cubes[0]
        }
    }

    /**
     * Which way each side of the first closed cube faces, by number: 0 +x (the back), 1 −x (the side that opens first),
     * 2 +y (right), 3 −y (left), 4 +z (bottom) and 5 −z (top).
     */
    val sideNormals = listOf(Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f))

    /** The squares, six per cube (cube i's are 6i..6i+5), one facing each of [sideNormals] in the cube's own frame. */
    val squares: List<Square> = run {
        val closed = rest[0]
        closed.indices.flatMap { c ->
            sideNormals.mapIndexed { side, n ->
                // Two edge directions in the face, with u × v = n so the corners run anticlockwise from outside.
                val u = if (abs(n.x) > 0f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
                val v = n cross u
                val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (a, b) -> (n + u * a + v * b) * 0.5f }
                val beside = closed[c].centre + n
                val inside = closed.any { (it.centre - beside).length() < 0.01f }
                Square(n, corners, if (inside) -1 else side)
            }
        }
    }

    private fun turned(start: List<CubePose>, axis: List<Axis>, t: Float): List<CubePose> {
        val out = start.toMutableList()
        for (a in axis) {
            val r = Quat.axisAngle(a.direction, a.radians * t).toRot()
            for (c in a.cubes) {
                val s = start[c]
                out[c] = CubePose(r * s.rot, a.point + r * (s.centre - a.point))
            }
        }
        return out
    }

    private fun centred(poses: List<CubePose>): List<CubePose> {
        var mid = Vec3.Zero
        for (p in poses) mid += p.centre
        mid *= 1f / poses.size
        return poses.map { CubePose(it.rot, it.centre - mid) }
    }

    // Adding 0 turns a rounded −0 into 0, so equal positions compare equal.
    private fun squared(r: Rot): Rot = Rot(FloatArray(9) { round(r[it / 3, it % 3]) + 0f })

    private fun halves(v: Vec3) = Vec3(round(v.x * 2f) / 2f + 0f, round(v.y * 2f) / 2f + 0f, round(v.z * 2f) / 2f + 0f)
}

/** The whole toy turned by [rot] and then moved by [shift], in its own frame: where a hand has put it. */
internal class Placement(val rot: Rot, val shift: Vec3) {
    fun place(p: Vec3): Vec3 = rot * p + shift

    /** The cubes at [poses], placed. */
    fun place(poses: List<CubePose>): List<CubePose> = poses.map { CubePose(rot * it.rot, place(it.centre)) }

    companion object {
        val Home = Placement(Quat.Identity.toRot(), Vec3.Zero)
    }
}

/**
 * A finger folding the toy while the rest of it is held, so the part dragged is the part that moves: [placementAt]
 * says where the toy must be put, at a given fold, for the held cube ([InfinityCube.anchor]) to stay where it is at
 * fold [from], placed by [placement]. Past a whole step, the next fold's held cube takes over from there.
 */
internal class Hold(private val grabbed: Int, private val from: Float, private val placement: Placement) {
    fun placementAt(fold: Float): Placement {
        var s = from
        var at = placement
        while (s != fold) {
            val up = fold > s
            val step = if (up) floor(s).toInt() else ceil(s).toInt() - 1
            val end = if (up) min(fold, step + 1f) else max(fold, step.toFloat())
            val held = InfinityCube.anchor(step, grabbed)
            val a = InfinityCube.pose(s)[held]
            val b = InfinityCube.pose(end)[held]
            // Turn and move the toy so the held cube goes back from where it is at end to where it was at s.
            val turn = a.rot * b.rot.transposed()
            at = Placement(at.rot * turn, at.rot * (a.centre - turn * b.centre) + at.shift)
            s = end
        }
        return at
    }
}

internal fun Rot.transposed(): Rot = Rot(FloatArray(9) { this[it % 3, it / 3] })

/** The rotation [this] as a quaternion. */
internal fun Rot.toQuat(): Quat {
    val t = this[0, 0] + this[1, 1] + this[2, 2]
    return when {
        t > 0f -> {
            val s = sqrt(t + 1f) * 2f
            Quat(0.25f * s, (this[2, 1] - this[1, 2]) / s, (this[0, 2] - this[2, 0]) / s, (this[1, 0] - this[0, 1]) / s)
        }
        this[0, 0] > this[1, 1] && this[0, 0] > this[2, 2] -> {
            val s = sqrt(1f + this[0, 0] - this[1, 1] - this[2, 2]) * 2f
            Quat((this[2, 1] - this[1, 2]) / s, 0.25f * s, (this[0, 1] + this[1, 0]) / s, (this[0, 2] + this[2, 0]) / s)
        }
        this[1, 1] > this[2, 2] -> {
            val s = sqrt(1f + this[1, 1] - this[0, 0] - this[2, 2]) * 2f
            Quat((this[0, 2] - this[2, 0]) / s, (this[0, 1] + this[1, 0]) / s, 0.25f * s, (this[1, 2] + this[2, 1]) / s)
        }
        else -> {
            val s = sqrt(1f + this[2, 2] - this[0, 0] - this[1, 1]) * 2f
            Quat((this[1, 0] - this[0, 1]) / s, (this[0, 2] + this[2, 0]) / s, (this[1, 2] + this[2, 1]) / s, 0.25f * s)
        }
    }.normalized()
}

/** The position a cube let go at [fold] eases to: the nearest whole step. */
internal fun snapStep(fold: Float): Int = fold.roundToInt()

/**
 * The whole step "Fold" ([direction] 1) or "Fold back" (−1) goes to from [fold]: the next one that way, so from a
 * position it's the next position, and part-way through a fold it finishes or undoes that fold.
 */
internal fun stepFrom(fold: Float, direction: Int): Int =
    if (direction > 0) floor(fold + SAME_STEP).toInt() + 1 else ceil(fold - SAME_STEP).toInt() - 1

/** What TalkBack says of the cube at rest at [step]; [top] names the colour on top of the first closed cube. */
internal fun cubeStateText(step: Int, top: String?): String = when (Math.floorMod(step, InfinityCube.STEPS)) {
    0 -> if (top != null) "Closed, $top on top" else "Closed"
    3 -> "Closed, inside out"
    1, 4 -> "Open"
    else -> "Open, rolled over"
}

/**
 * Finds the fold near [from] that brings a grabbed point closest to the finger at [target]; [at] says where the point
 * is on screen at a given fold. It walks downhill from [from] in whichever direction brings the point nearer, at most
 * [maxStep] either way, so the fold follows the finger continuously, through one position into the next, and never
 * jumps to some other fold that happens to put the point in the same place. Where neither way brings it nearer, the
 * finger is pulling a way the cube can't fold, and it stays put.
 */
internal fun followFold(from: Float, target: Offset, maxStep: Float = 0.25f, at: (Float) -> Offset): Float {
    fun miss(f: Float) = (at(f) - target).getDistanceSquared()
    var best = from
    var bestMiss = miss(from)
    for (dir in intArrayOf(1, -1)) {
        var x = from
        var e = miss(from)
        while (abs(x - from) < maxStep) {
            val next = x + dir * FOLLOW_STEP
            val en = miss(next)
            if (en >= e) break
            x = next
            e = en
        }
        if (e < bestMiss) {
            best = x
            bestMiss = e
        }
    }
    if (best == from) return from
    // Settle between the walk's last steps on the parabola through the three nearest.
    val lo = miss(best - FOLLOW_STEP)
    val hi = miss(best + FOLLOW_STEP)
    val curve = lo - 2f * bestMiss + hi
    val shift = if (curve > 0f) (FOLLOW_STEP * (lo - hi) / (2f * curve)).coerceIn(-FOLLOW_STEP, FOLLOW_STEP) else 0f
    return best + shift
}

/**
 * The positions a moving fold reaches, for its clicks: [move] returns each whole step passed or landed on since the
 * last call, in order. A step it has just clicked into doesn't click again until the fold has moved [CLEAR] away, so
 * a finger wobbling on a position, or a fold that overshoots and settles back, clicks once.
 */
internal class FoldClicks(private var last: Float = 0f) {
    private var latched: Int? = snapStep(last).takeIf { abs(last - it) < SAME_STEP }

    fun move(to: Float, onStep: (Int) -> Unit) {
        // A finger can leave the fold a hair short of a position, which still counts as reaching it.
        val fold = snapStep(to).let { if (abs(to - it) < NEAR) it.toFloat() else to }
        val steps = if (fold > last) (floor(last).toInt() + 1)..floor(fold).toInt()
        else (ceil(last).toInt() - 1) downTo ceil(fold).toInt()
        for (n in steps) {
            if (n == latched) continue
            latched = n
            onStep(n)
        }
        latched?.let { if (abs(fold - it) > CLEAR) latched = null }
        last = fold
    }

    private companion object {
        const val CLEAR = 0.2f
        const val NEAR = 0.01f // about 1° of a 90° fold
    }
}

private const val SAME_STEP = 1e-3f   // folds this close to a whole step count as on it
private const val FOLLOW_STEP = 0.004f // the walk's stride, about a degree of a 90° fold
