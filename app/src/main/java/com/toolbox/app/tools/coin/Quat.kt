package com.toolbox.app.tools.coin

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Unit quaternion for a tumbling coin or die; integrates spin without drifting away from a pure rotation. */
data class Quat(val w: Float, val x: Float, val y: Float, val z: Float) {
    operator fun times(o: Quat) = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun normalized(): Quat {
        val l = sqrt(w * w + x * x + y * y + z * z)
        return Quat(w / l, x / l, y / l, z / l)
    }

    /** Advances by angular velocity [omega] (world frame, rad/s) for [h] seconds. */
    fun integrate(omega: Vec3, h: Float): Quat {
        val d = Quat(0f, omega.x, omega.y, omega.z) * this
        return Quat(w + 0.5f * h * d.w, x + 0.5f * h * d.x, y + 0.5f * h * d.y, z + 0.5f * h * d.z).normalized()
    }

    fun toRot(): Rot = Rot(
        floatArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
            2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
            2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y),
        ),
    )

    companion object {
        val Identity = Quat(1f, 0f, 0f, 0f)

        fun axisAngle(axis: Vec3, rad: Float): Quat {
            val a = axis.normalized()
            val s = sin(rad / 2)
            return Quat(cos(rad / 2), a.x * s, a.y * s, a.z * s)
        }

        /** The shortest rotation taking direction [from] to direction [to]. */
        fun between(from: Vec3, to: Vec3): Quat {
            val a = from.normalized(); val b = to.normalized()
            val axis = a cross b
            return when {
                axis.length() > 1e-5f -> axisAngle(axis, atan2(axis.length(), a dot b))
                (a dot b) > 0f -> Identity
                else -> axisAngle(if (abs(a.x) < 0.9f) a cross Vec3(1f, 0f, 0f) else a cross Vec3(0f, 1f, 0f), PI.toFloat())
            }
        }
    }
}

