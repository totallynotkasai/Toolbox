package com.toolbox.app.toys.infinitycube

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.toolbox.app.ui.common.Quat
import com.toolbox.app.ui.common.Rot
import com.toolbox.app.ui.common.Vec3
import com.toolbox.app.ui.common.drawTossShadow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How far the eye is from the middle of the toy, in cube widths: enough perspective to look solid, not to bulge. */
internal const val EYE = 10f

/**
 * How the toy is seen. [view] turns the toy's frame into the viewer's (x to the right, y up, z towards the eye), the
 * toy floats [lift] cube widths up, and its middle is drawn at [centre], [unit] pixels to a cube width.
 */
internal class CubeCamera(val view: Rot, val lift: Float, val centre: Offset, val unit: Float) {
    /** [p], in the toy's frame, in the viewer's. */
    fun world(p: Vec3): Vec3 {
        val v = view * p
        return Vec3(v.x, v.y + lift, v.z)
    }

    /** [w], in the viewer's frame, back in the toy's. */
    fun toy(w: Vec3): Vec3 = view.local(Vec3(w.x, w.y - lift, w.z))

    /** Where [w] (in the viewer's frame) is drawn: in perspective, nearer things a little bigger. */
    fun project(w: Vec3): Offset {
        val k = EYE / (EYE - w.z)
        return Offset(centre.x + w.x * unit * k, centre.y - w.y * unit * k)
    }

    /** The way from [w] to the eye. */
    fun toEye(w: Vec3) = Vec3(-w.x, -w.y, EYE - w.z)

    /** The point drawn at [screen] on the plane through [on] facing [normal] (both in the viewer's frame). */
    fun unproject(screen: Offset, on: Vec3, normal: Vec3): Vec3? {
        // Everything drawn at [screen] lies on the line (a, b, 0)·(EYE − z)/EYE + (0, 0, z) from the eye.
        val a = (screen.x - centre.x) / unit
        val b = -(screen.y - centre.y) / unit
        val k = (normal.x * a + normal.y * b) / EYE
        val den = normal.z - k
        if (abs(den) < 1e-6f) return null
        val z = ((normal dot on) - k * EYE) / den
        val s = (EYE - z) / EYE
        return Vec3(a * s, b * s, z)
    }

    /** Where [point] of cube [cube] (in its own frame) is drawn when the cubes are at [poses]. */
    fun screenOf(poses: List<CubePose>, cube: Int, point: Vec3): Offset = project(world(poses[cube].place(point)))

    companion object {
        /**
         * The usual view: the side that opens first faces you, top up, turned so the right side shows and seen a little
         * from above. The toy's z points down and its x away from you, so the first fold opens towards you.
         */
        val Usual: Rot = Quat.axisAngle(Vec3(1f, 0f, 0f), Math.toRadians(22.0).toFloat()).toRot() *
            Quat.axisAngle(Vec3(0f, 1f, 0f), Math.toRadians(-35.0).toFloat()).toRot() *
            Rot(floatArrayOf(0f, 1f, 0f, 0f, 0f, -1f, -1f, 0f, 0f))
    }
}

/** A cube a finger has hold of, and the [point] on it (in the cube's own frame) under the finger. */
internal class Grab(val cube: Int, val point: Vec3)

/** The soft colours of the first closed cube's sides, by [Square.side], with the names TalkBack uses, and the cream inside. */
internal object CubeColours {
    val sides = listOf(
        Color(0xFFA9DEC0), // back: mint
        Color(0xFFF4A9BA), // front: pink
        Color(0xFFA8CBF0), // right: sky blue
        Color(0xFFF8C59F), // left: peach
        Color(0xFFCBB8EE), // bottom: lilac
        Color(0xFFF5E197), // top: butter yellow
    )
    val names = listOf("green", "pink", "blue", "peach", "lilac", "yellow")
    val Cream = Color(0xFFF6EEDB)

    fun of(square: Square): Color = if (square.side < 0) Cream else sides[square.side]
}

/** Light from the upper left, a little in front. */
private val LIGHT = Vec3(-0.45f, 0.75f, 0.5f).normalized()

private const val INSET = 0.84f  // how much of a face the flat middle covers, inside its bevelled rim
private const val ROUND = 0.24f  // the middle's corners are rounded over this much of each side

private val facePath = Path()
private val corners = Array(4) { Offset.Zero }
private val inner = Array(4) { Offset.Zero }

/** The soft shadow on the floor under the floating toy. [lift] is how high it floats now. */
internal fun DrawScope.drawCubeShadow(cam: CubeCamera, lift: Float) {
    val c = Offset(cam.centre.x, cam.centre.y + cam.unit * 2.75f)
    drawTossShadow(c, rx = cam.unit * 1.75f, ry = cam.unit * 0.3f, height = 0.25f + lift * 2.5f)
}

/**
 * The eight cubes at [poses], seen through [cam]: back to front, each face that looks towards the eye drawn as a
 * bevelled square in its colour, shaded by the light, with a fine [edge] line round it so neighbours stay apart.
 */
internal fun DrawScope.drawInfinityCube(poses: List<CubePose>, cam: CubeCamera, edge: Color) {
    val order = poses.indices.sortedByDescending { cam.toEye(cam.world(poses[it].centre)).length() }
    val line = Stroke(width = cam.unit * 0.014f, join = StrokeJoin.Round)
    for (c in order) {
        val pose = poses[c]
        for (f in 0 until 6) {
            val square = InfinityCube.squares[c * 6 + f]
            val n = cam.view * (pose.rot * square.normal)
            if ((n dot cam.toEye(cam.world(pose.place(square.normal * 0.5f)))) <= 0f) continue
            val mid = square.normal * 0.5f
            for (i in 0 until 4) {
                val p = square.corners[i]
                corners[i] = cam.project(cam.world(pose.place(p)))
                inner[i] = cam.project(cam.world(pose.place(mid + (p - mid) * INSET)))
            }
            // Gently shaded, so the colours stay soft: faces turned from the light are a little darker, and those facing
            // it squarely catch a touch of shine.
            val light = max(0f, n dot LIGHT)
            val base = CubeColours.of(square)
            val face = lerp(Color.Black, base, 0.8f + 0.2f * light).let { if (light > 0.85f) lerp(it, Color.White, (light - 0.85f) * 0.8f) else it }

            facePath.reset()
            facePath.moveTo(corners[0].x, corners[0].y)
            for (i in 1 until 4) facePath.lineTo(corners[i].x, corners[i].y)
            facePath.close()
            drawPath(facePath, lerp(face, Color.Black, 0.1f))
            drawPath(facePath, edge, style = line)

            roundedQuad(inner)
            drawPath(facePath, face)
        }
    }
}

/** [facePath] as the quad [q] with its corners rounded off, drawn in perspective from the projected corners. */
private fun roundedQuad(q: Array<Offset>) {
    facePath.reset()
    for (i in 0 until 4) {
        val prev = q[(i + 3) % 4]
        val next = q[(i + 1) % 4]
        val a = q[i] + (prev - q[i]) * ROUND
        val b = q[i] + (next - q[i]) * ROUND
        if (i == 0) facePath.moveTo(a.x, a.y) else facePath.lineTo(a.x, a.y)
        facePath.quadraticTo(q[i].x, q[i].y, b.x, b.y)
    }
    facePath.close()
}

/**
 * The cube drawn at [screen] and the point on it there, nearest first. A touch just off the toy, within [slop]
 * pixels of a face, takes hold of that face's middle. Null if it's further off.
 */
internal fun cubeAt(screen: Offset, poses: List<CubePose>, cam: CubeCamera, slop: Float): Grab? {
    val order = poses.indices.sortedBy { cam.toEye(cam.world(poses[it].centre)).length() }
    var near: Grab? = null
    var nearDistance = slop
    for (c in order) {
        val pose = poses[c]
        for (f in 0 until 6) {
            val square = InfinityCube.squares[c * 6 + f]
            val n = cam.view * (pose.rot * square.normal)
            val mid = cam.world(pose.place(square.normal * 0.5f))
            if ((n dot cam.toEye(mid)) <= 0f) continue
            val quad = square.corners.map { cam.project(cam.world(pose.place(it))) }
            val off = distanceOutside(screen, quad)
            if (off == 0f) {
                val w = cam.unproject(screen, mid, n) ?: continue
                return Grab(c, pose.rot.local(cam.toy(w) - pose.centre))
            }
            if (off < nearDistance) {
                nearDistance = off
                near = Grab(c, square.normal * 0.5f)
            }
        }
    }
    return near
}

/** How far [p] is outside the convex polygon [quad] (either winding): 0 inside or on it. */
internal fun distanceOutside(p: Offset, quad: List<Offset>): Float {
    var sign = 0f
    var inside = true
    var nearest = Float.MAX_VALUE
    for (i in quad.indices) {
        val a = quad[i]
        val b = quad[(i + 1) % quad.size]
        val e = b - a
        val cross = e.x * (p.y - a.y) - e.y * (p.x - a.x)
        if (cross != 0f) {
            if (sign == 0f) sign = cross else if (cross * sign < 0f) inside = false
        }
        val len2 = e.getDistanceSquared()
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * e.x + (p.y - a.y) * e.y) / len2).coerceIn(0f, 1f)
        nearest = min(nearest, (p - (a + e * t)).getDistance())
    }
    return if (inside) 0f else nearest
}
