package com.toolbox.app.tools.dice

import com.toolbox.app.ui.common.Quat
import com.toolbox.app.ui.common.Rot
import com.toolbox.app.ui.common.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Quick mode's dice. The numbers are picked as soon as they're rolled, evenly, and the dice tumble where they sit
 * for a moment and land showing them. They sit in tidy rows, three to a row, in the middle of the table (units and
 * axes as in [DicePhysics]). Pure, so it can be tested without Compose.
 */
class QuickDice(seed: Long, count: Int = 2, sides: Int = 6) {
    private class Spin(val axis: Vec3, val turns: Int, val delay: Float)

    private class Pose(override val shape: DieShape, override val position: Vec3, private val q: Quat) : DieLook {
        override fun pose(): Rot = q.toRot()
    }

    private val random = Random(seed)

    var shape = DieShape.of(sides); private set
    var values: List<Int> = emptyList(); private set

    /** Each die's pose when the roll began, and the pose it lands in. */
    private var from: List<Quat> = emptyList()
    private var to: List<Quat> = emptyList()
    private var spins: List<Spin> = emptyList()
    private var spots: List<Vec3> = emptyList()

    /** How far the rows reach from the middle of the table, across and front to back, including the dice. */
    var halfWidth = 0f; private set
    var halfDepth = 0f; private set

    init { setCount(count) }

    /** [n] dice on random faces. */
    fun setCount(n: Int) = show(List(n) { random.nextInt(1, shape.sides + 1) })

    /** Swaps every die for one with [sides] faces, on random faces. */
    fun setSides(sides: Int) {
        if (sides == shape.sides) return
        shape = DieShape.of(sides)
        setCount(values.size)
    }

    /** Lays the dice out at rest showing [values]: a result from the other mode, or after a recreation. */
    fun show(values: List<Int>) {
        this.values = values
        to = values.map { shape.restingOn(it, randomYaw()) }
        from = to
        spins = emptyList()
        layOut()
    }

    /** Rolls every die: picks the new numbers (returned) and sets up the tumble from where they are now. */
    fun roll(): List<Int> {
        from = to
        values = List(values.size) { random.nextInt(1, shape.sides + 1) }
        to = values.map { shape.restingOn(it, randomYaw()) }
        spins = values.map {
            val a = random.nextFloat() * 2 * PI.toFloat()
            Spin(Vec3(cos(a), 0f, sin(a)), 1 + random.nextInt(2), random.nextFloat() * MAX_DELAY)
        }
        return values
    }

    /**
     * The dice [t] of the way through a roll, from 0 (as they were) to 1 (landed on [values]), for drawing. Each one
     * hops, turns end over end a whole number of times while turning to its new face, and slows as it lands.
     */
    fun dice(t: Float): List<DieLook> = values.indices.map { i ->
        val spin = spins.getOrNull(i) ?: return@map Pose(shape, spots[i] + Vec3(0f, shape.inradius, 0f), to[i])
        val u = ((t - spin.delay) / (1f - spin.delay)).coerceIn(0f, 1f)
        val e = 1f - (1f - u) * (1f - u) * (1f - u)
        val tumble = Quat.axisAngle(spin.axis, 2 * PI.toFloat() * spin.turns * (1f - e))
        val lift = shape.inradius + HOP * sin(PI.toFloat() * u)
        Pose(shape, spots[i] + Vec3(0f, lift, 0f), (tumble * from[i].slerp(to[i], e)).normalized())
    }

    private fun layOut() {
        val spacing = 2 * shape.radius + DicePhysics.ROW_GAP
        val rows = diceRows(values.size, spacing, 2 * spacing)
        val back = rows.minOfOrNull { it.z } ?: 0f
        // The first row at the back (the top of the screen), so the dice read in the order of [values], like text.
        spots = rows.map { Vec3(it.x, 0f, back / 2 - it.z) }
        halfWidth = (spots.maxOfOrNull { it.x } ?: 0f) + shape.radius
        halfDepth = max(0f, -back / 2) + shape.radius
    }

    private fun randomYaw() = (random.nextFloat() - 0.5f) * 0.8f

    companion object {
        /** How high a die hops in the middle of its tumble, in die units. */
        const val HOP = 1f
        /** The most a die's tumble starts late, as a fraction of the roll, so they don't all move as one. */
        const val MAX_DELAY = 0.15f
    }
}
