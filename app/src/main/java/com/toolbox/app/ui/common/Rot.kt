package com.toolbox.app.ui.common

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A 3×3 rotation (row-major) giving a coin's or die's orientation in the world.
 *
 * World axes: x to the right, y up (height above the table), z towards the viewer.
 * The columns are where the body's local axes point; for a coin, u (design right), v (design up) and w (the
 * heads-side normal). Pure Kotlin so the coin and dice physics and their tests don't need Compose.
 */
class Rot(private val m: FloatArray) {
    operator fun get(row: Int, col: Int) = m[row * 3 + col]

    operator fun times(o: Rot): Rot {
        val r = FloatArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            r[i * 3 + j] = this[i, 0] * o[0, j] + this[i, 1] * o[1, j] + this[i, 2] * o[2, j]
        }
        return Rot(r)
    }

    fun column(c: Int) = Vec3(this[0, c], this[1, c], this[2, c])

    /** Rotates a body-local vector into the world. */
    operator fun times(v: Vec3) = Vec3(
        this[0, 0] * v.x + this[0, 1] * v.y + this[0, 2] * v.z,
        this[1, 0] * v.x + this[1, 1] * v.y + this[1, 2] * v.z,
        this[2, 0] * v.x + this[2, 1] * v.y + this[2, 2] * v.z,
    )

    /** Rotates a world vector into the body's frame (the inverse of [times]). */
    fun local(v: Vec3) = Vec3(
        this[0, 0] * v.x + this[1, 0] * v.y + this[2, 0] * v.z,
        this[0, 1] * v.x + this[1, 1] * v.y + this[2, 1] * v.z,
        this[0, 2] * v.x + this[1, 2] * v.y + this[2, 2] * v.z,
    )

    companion object {
        /** Rotation about the world x axis; a positive angle tips +y towards +z. */
        fun x(rad: Float): Rot {
            val c = cos(rad); val s = sin(rad)
            return Rot(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c))
        }

        /** A coin lying flat, heads up, with the top of the design pointing away from the viewer. */
        val Rest = x(-(Math.PI / 2).toFloat())
    }
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    companion object { val Zero = Vec3(0f, 0f, 0f) }

    fun normalized(): Vec3 { val l = sqrt(this dot this); return if (l == 0f) this else this * (1 / l) }
}
