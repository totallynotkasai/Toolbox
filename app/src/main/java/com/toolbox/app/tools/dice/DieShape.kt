package com.toolbox.app.tools.dice

import androidx.compose.ui.geometry.Offset
import com.toolbox.app.ui.common.Quat
import com.toolbox.app.ui.common.Rot
import com.toolbox.app.ui.common.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The solid for a die with [sides] faces, in die-local coordinates (units as in [DicePhysics]: a d6 has edge 1,
 * and the others are sized to look about as big). The faces are found as the convex hull of the [corners].
 *
 * Opposite faces add up to [sides] + 1, as on real dice. A d4 has no top face, so it's numbered at the corners
 * and reads the one pointing up.
 */
class DieShape private constructor(
    val sides: Int,
    val corners: List<Vec3>,
    val faces: List<Face>,
    private val cornerValues: IntArray?,
    inertia: Float,
) {
    /**
     * One flat face: outward [normal] at [distance] from the centre, plus a frame for drawing it. [across] and
     * [down] lie in the face (across × down = normal, so it isn't drawn mirrored) with [down] pointing away from the
     * top of the number. [outline] is the face's corners in that frame in design units, 100 of which are [unit].
     */
    class Face internal constructor(
        val normal: Vec3,
        val distance: Float,
        val centre: Vec3,
        val across: Vec3,
        val down: Vec3,
        val unit: Float,
        val outline: List<Offset>,
        val cornerIndices: IntArray,
        val value: Int,
    )

    /** Height of the centre when lying on a face. */
    val inradius: Float = faces.minOf { it.distance }
    /** Distance from the centre to the furthest corner. */
    val radius: Float = corners.maxOf { it.length() }
    /** 1 / moment of inertia for a mass of 1: the regular solids spin alike about every axis (the d10 nearly so). */
    val invInertia: Float = 1f / (inertia * radius * radius)
    /** A d4: the numbers are at the corners, not the faces. */
    val numberedCorners: Boolean get() = cornerValues != null

    fun cornerValue(i: Int): Int = cornerValues?.get(i) ?: 0

    /** The number a die in orientation [r] shows. */
    fun value(r: Rot): Int {
        val cv = cornerValues ?: return faces.maxBy { worldY(r, it.normal) }.value
        return cv[corners.indices.maxBy { worldY(r, corners[it]) }]
    }

    /** The face pointing most nearly down: the one a settled die lies on. */
    fun bottomFace(r: Rot): Face = faces.minBy { worldY(r, it.normal) }

    /** Lying flat on the table showing [value], turned [yaw] radians about the vertical. */
    fun restingOn(value: Int, yaw: Float): Quat {
        val flat = if (cornerValues != null) {
            // A d4 shows the corner pointing up, so it lies on the one face that corner isn't part of.
            val corner = cornerValues.indexOf(value).coerceAtLeast(0)
            Quat.between(faces.first { corner !in it.cornerIndices }.normal, Vec3(0f, -1f, 0f))
        } else {
            Quat.between((faces.firstOrNull { it.value == value } ?: faces[0]).normal, Vec3(0f, 1f, 0f))
        }
        return (Quat.axisAngle(Vec3(0f, 1f, 0f), yaw) * flat).normalized()
    }

    companion object {
        val SIDES = listOf(4, 6, 8, 10, 12, 20)

        private val cache = HashMap<Int, DieShape>()

        fun of(sides: Int): DieShape = synchronized(cache) { cache.getOrPut(sides) { build(sides) } }

        private fun worldY(r: Rot, v: Vec3) = r[1, 0] * v.x + r[1, 1] * v.y + r[1, 2] * v.z

        private fun build(sides: Int): DieShape {
            val phi = (1f + sqrt(5f)) / 2f
            val ip = 1f / phi
            return when (sides) {
                4 -> make(4, sized(listOf(v(1, 1, 1), v(1, -1, -1), v(-1, 1, -1), v(-1, -1, 1)), 1f), inertia = 0.133f)
                6 -> make(6, signs().map { it * 0.5f }, inertia = 0.222f, cube = true)
                8 -> make(
                    8,
                    sized(listOf(v(1, 0, 0), v(-1, 0, 0), v(0, 1, 0), v(0, -1, 0), v(0, 0, 1), v(0, 0, -1)), 0.8f),
                    inertia = 0.2f,
                )
                10 -> {
                    // A pentagonal trapezohedron: two apexes over a zig-zag ring. The apex height that makes each
                    // kite flat follows from the ring's zig-zag height z.
                    val z = 0.1f
                    val c = cos(PI / 5).toFloat()
                    val apex = z * (1 + c) / (1 - c)
                    val ring = List(10) { k ->
                        val a = k * PI.toFloat() / 5
                        Vec3(cos(a), if (k % 2 == 0) z else -z, sin(a))
                    }
                    make(10, (ring + listOf(Vec3(0f, apex, 0f), Vec3(0f, -apex, 0f))).map { it * 0.8f }, inertia = 0.26f)
                }
                12 -> make(
                    12,
                    sized(
                        signs() + buildList {
                            for (a in listOf(-1f, 1f)) for (b in listOf(-1f, 1f)) {
                                add(Vec3(0f, a * ip, b * phi)); add(Vec3(a * ip, b * phi, 0f)); add(Vec3(a * phi, 0f, b * ip))
                            }
                        },
                        0.85f,
                    ),
                    inertia = 0.309f,
                )
                20 -> make(
                    20,
                    sized(
                        buildList {
                            for (a in listOf(-1f, 1f)) for (b in listOf(-1f, 1f)) {
                                add(Vec3(0f, a, b * phi)); add(Vec3(a, b * phi, 0f)); add(Vec3(a * phi, 0f, b))
                            }
                        },
                        0.88f,
                    ),
                    inertia = 0.289f,
                )
                else -> throw IllegalArgumentException("No d$sides")
            }
        }

        private fun v(x: Int, y: Int, z: Int) = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

        private fun signs() = buildList {
            for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) for (z in listOf(-1f, 1f)) add(Vec3(x, y, z))
        }

        private fun sized(raw: List<Vec3>, radius: Float): List<Vec3> {
            val k = radius / raw.maxOf { it.length() }
            return raw.map { it * k }
        }

        private const val EPS = 1e-3f

        private fun make(sides: Int, corners: List<Vec3>, inertia: Float, cube: Boolean = false): DieShape {
            // Every plane through three corners with all the others behind it is a face.
            val planes = ArrayList<Pair<Vec3, Float>>()
            for (i in corners.indices) for (j in i + 1 until corners.size) for (k in j + 1 until corners.size) {
                var n = (corners[j] - corners[i]) cross (corners[k] - corners[i])
                if (n.length() < 1e-6f) continue
                n = n.normalized()
                var d = n dot corners[i]
                if (d < 0) { n = -n; d = -d }
                if (corners.all { (it dot n) <= d + EPS } && planes.none { (it.first dot n) > 1 - EPS }) planes += n to d
            }
            check(planes.size == sides) { "d$sides hull has ${planes.size} faces" }

            // Number them: working down from the top, each face and the one opposite it add up to sides + 1.
            val order = planes.indices.sortedWith(
                compareBy({ -round(planes[it].first.y * 1000) }, { atan2(planes[it].first.z, planes[it].first.x) }),
            )
            val values = IntArray(sides)
            var next = 1
            for (i in order) {
                val n = planes[i].first
                if (cube) {
                    values[i] = cubeValue(n)
                    continue
                }
                if (values[i] != 0 || sides == 4) continue
                values[i] = next
                val opposite = planes.indices.first { (planes[it].first dot n) < -1 + EPS }
                values[opposite] = sides + 1 - next
                next++
            }

            val faces = planes.mapIndexed { i, (n, d) ->
                val idx = corners.indices.filter { abs((corners[it] dot n) - d) < EPS }
                val centre = idx.fold(Vec3.Zero) { s, c -> s + corners[c] } * (1f / idx.size)
                // Corners in order round the face, anticlockwise seen from outside.
                val e1 = (corners[idx[0]] - centre).normalized()
                val e2 = n cross e1
                val ring = idx.sortedBy { (corners[it] - centre).let { p -> atan2(p dot e2, p dot e1) } }
                val reach = ring.maxOf { (corners[it] - centre).length() }
                // Numbers point at a corner (as on d8s and d20s); a d6's pips square up with its edges instead.
                val top = if (cube) (corners[ring[0]] + corners[ring[1]]) * 0.5f
                else corners[ring.first { (corners[it] - centre).length() > reach - EPS }]
                val up = (top - centre).normalized()
                val down = -up
                val across = n cross up
                val unit = if (cube) (top - centre).length() else reach
                val outline = ring.map { (corners[it] - centre).let { p -> Offset((p dot across) * 100 / unit, (p dot down) * 100 / unit) } }
                Face(n, d, centre, across, down, unit, outline, ring.toIntArray(), values[i])
            }
            return DieShape(sides, corners, faces, if (sides == 4) IntArray(4) { it + 1 } else null, inertia)
        }

        /** The d6 layout the pips were drawn for: 1 opposite 6 on y, 2 opposite 5 on z, 3 opposite 4 on x. */
        private fun cubeValue(n: Vec3): Int = when {
            abs(n.x) > 0.5f -> if (n.x > 0) 3 else 4
            abs(n.y) > 0.5f -> if (n.y > 0) 1 else 6
            else -> if (n.z > 0) 2 else 5
        }
    }
}

/** What drawing a die needs: a physics die, or one of Quick mode's. */
interface DieLook {
    val shape: DieShape
    /** Its centre, in table units (see [DicePhysics]). */
    val position: Vec3
    fun pose(): Rot
}

/**
 * Spots for [n] dice in tidy rows [spacing] apart, none wider than [width]: as few rows as fit, with the dice shared
 * out evenly and the front rows taking any spare. Each spot is an offset from the middle of the front row, x across
 * and rows going back (−z), so every row is centred however many it holds.
 */
fun diceRows(n: Int, spacing: Float, width: Float): List<Vec3> {
    if (n <= 0) return emptyList()
    val perRow = max(1, (width / spacing).toInt() + 1)
    val rows = (n + perRow - 1) / perRow
    return buildList {
        for (r in 0 until rows) {
            val inRow = n / rows + if (r < n % rows) 1 else 0
            for (i in 0 until inRow) add(Vec3((i - (inRow - 1) / 2f) * spacing, 0f, -r * spacing))
        }
    }
}
