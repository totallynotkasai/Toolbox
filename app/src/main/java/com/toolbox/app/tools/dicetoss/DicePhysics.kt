package com.toolbox.app.tools.dicetoss

import com.toolbox.app.tools.coin.Quat
import com.toolbox.app.tools.coin.Rot
import com.toolbox.app.tools.coin.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Dice thrown onto a walled table, simulated as rigid convex solids (d4 to d20, see [DieShape]): the numbers that
 * come up are whatever the tumbling produces. Pure and deterministic for a given [seed] and sequence of calls,
 * so it can be tested for fairness.
 *
 * Units are d6 edges and seconds. World axes as in [Rot]: x across the table, y up, z towards the viewer.
 * The table is 2·[halfWidth] by 2·[halfDepth] with walls all round. Collisions use sequential impulses at the
 * dice's corners (against the table, the walls and the other dice), with friction, so dice roll, skid and
 * knock each other about. A die that has nearly stopped goes to sleep; once they all have, the roll is over.
 */
class DicePhysics(
    seed: Long,
    count: Int = 2,
    sides: Int = 6,
    var halfWidth: Float = 3f,
    var halfDepth: Float = 4.5f,
) {
    enum class Phase { Resting, Held, Rolling }

    sealed interface Event {
        /** A die hit the table, a wall or another die hard enough to hear; [intensity] 0–1. */
        data class Knock(val intensity: Float) : Event
        data class Settled(val values: List<Int>) : Event
    }

    class Die internal constructor(val shape: DieShape, p: Vec3, q: Quat) {
        var position = p; internal set
        var orientation = q; internal set
        internal var v = Vec3.Zero
        internal var w = Vec3.Zero
        internal var asleep = true
        internal var sleepTime = 0f
        internal var touching = false
        internal var knockCooldown = 0f
        internal var nudges = 0

        /** The number showing: on the top face, or a d4's top corner. */
        val value: Int get() = shape.value(orientation.toRot())
        fun pose(): Rot = orientation.toRot()
    }

    private class Contact(
        val a: Die,
        val b: Die?, // null for the table or a wall
        val point: Vec3,
        val n: Vec3, // from b (or the surface) towards a
        val pen: Float,
        val friction: Float,
        var target: Float = 0f,
        var jn: Float = 0f,
        var jt: Vec3 = Vec3.Zero,
    )

    private val random = Random(seed)
    private val planes = ArrayList<Pair<Vec3, Float>>(5)

    var phase = Phase.Resting; private set
    var shape = DieShape.of(sides); private set
    var dice: List<Die> = emptyList(); private set
    private var rollTime = 0f
    private var holdCentre = Vec3.Zero
    private var holdTarget = Vec3.Zero
    private var accumulator = 0f

    val values: List<Int> get() = dice.map { it.value }

    init { setCount(count) }

    /** Lays [n] dice out in a row near the thrower, on random faces. Ignored mid-roll. */
    fun setCount(n: Int) {
        if (phase != Phase.Resting) return
        val size = shape.radius
        val spacing = min(1.85f * size, (2 * halfWidth - 1.4f * size) / max(1, n))
        dice = List(n) { i ->
            val x = (i - (n - 1) / 2f) * spacing
            val face = shape.faces[random.nextInt(shape.faces.size)]
            val flat = Quat.between(face.normal, DOWN)
            val yaw = Quat.axisAngle(UP, (random.nextFloat() - 0.5f) * 0.8f)
            Die(shape, Vec3(x, shape.inradius, max(0f, halfDepth - 1.6f * size)), (yaw * flat).normalized())
        }
    }

    /** Swaps every die for one with [sides] faces (4, 6, 8, 10, 12 or 20). Ignored mid-roll. */
    fun setSides(sides: Int) {
        if (phase != Phase.Resting || sides == shape.sides) return
        shape = DieShape.of(sides)
        setCount(dice.size)
    }

    /** Keeps resting dice on the table when it changes size (e.g. on rotation). */
    fun resize(halfWidth: Float, halfDepth: Float) {
        this.halfWidth = halfWidth
        this.halfDepth = halfDepth
        for (d in dice) {
            val lx = max(0f, halfWidth - shape.radius); val lz = max(0f, halfDepth - shape.radius)
            d.position = Vec3(d.position.x.coerceIn(-lx, lx), d.position.y, d.position.z.coerceIn(-lz, lz))
        }
    }

    /**
     * Throws every die with [power] 0 (a gentle roll) to 1 (a hard throw) along ([dirX], [dirZ]) on the table
     * (0, −1 is away from the viewer). Returns false (and does nothing) unless the dice are at rest.
     */
    fun toss(power: Float, dirX: Float = 0f, dirZ: Float = -1f): Boolean {
        if (phase != Phase.Resting && phase != Phase.Held || dice.isEmpty()) return false
        val fromHand = phase == Phase.Held
        val p = power.coerceIn(0f, 1f)
        val dir = Vec3(dirX, 0f, dirZ).let { if (it.length() < 1e-3f) Vec3(0f, 0f, -1f) else it.normalized() }
        val side = UP cross dir // tumbling forwards spins about this axis
        for (d in dice) {
            val speed = (V_MIN + (V_MAX - V_MIN) * p) * (1f + (random.nextFloat() * 2 - 1) * 0.15f)
            val spread = (random.nextFloat() * 2 - 1) * 0.18f
            val horizontal = (dir + (dir cross UP) * spread).normalized() * speed
            val carried = if (fromHand) Vec3(d.v.x, 0f, d.v.z) * 0.3f else Vec3.Zero // some of the drag carries on
            d.position = Vec3(d.position.x, max(d.position.y, shape.radius + LAUNCH_CLEARANCE), d.position.z)
            d.v = Vec3(horizontal.x, VY_MIN + (VY_MAX - VY_MIN) * p + random.nextFloat() * 1.5f, horizontal.z) + carried
            val spin = SPIN_MIN + (SPIN_MAX - SPIN_MIN) * p
            d.w = side * (spin * 0.6f) + randomUnit() * (spin * (0.5f + 0.5f * random.nextFloat()))
        }
        startRolling()
        return true
    }

    /**
     * Picks all the resting dice up: they gather into a loose handful above the table, turning over in the fingers,
     * ready to be flicked with [toss] or let fall with [drop]. Returns false unless the dice are at rest.
     */
    fun grab(): Boolean {
        if (phase != Phase.Resting || dice.isEmpty()) return false
        val centre = dice.fold(Vec3.Zero) { s, d -> s + d.position } * (1f / dice.size)
        holdCentre = clampHold(Vec3(centre.x, holdHeight(), centre.z))
        holdTarget = holdCentre
        for (d in dice) {
            d.v = Vec3.Zero
            d.w = randomUnit() * (1f + random.nextFloat())
        }
        accumulator = 0f
        phase = Phase.Held
        return true
    }

    /** Moves the held dice towards ([x], [z]) on the table; they follow smoothly, held above it. */
    fun hold(x: Float, z: Float) {
        if (phase == Phase.Held) holdTarget = Vec3(x, holdHeight(), z)
    }

    /** Where the held handful is (or would be, if picked up now): its middle, above the table. */
    val handPosition: Vec3 get() = holdCentre

    /** Lets go of the held dice without throwing: they fall, tumbling a little, and that's the roll. */
    fun drop(): Boolean {
        if (phase != Phase.Held) return false
        for (d in dice) {
            d.v = Vec3(d.v.x * 0.5f, 0f, d.v.z * 0.5f)
            d.w = randomUnit() * (4f + 6f * random.nextFloat())
        }
        startRolling()
        return true
    }

    private fun startRolling() {
        for (d in dice) {
            d.asleep = false
            d.sleepTime = 0f
            d.knockCooldown = 0f
            d.nudges = 0
        }
        rollTime = 0f
        accumulator = 0f
        phase = Phase.Rolling
    }

    private fun holdHeight() = shape.radius + 1.3f

    /** Where each die sits in the hand: a ring round the middle, spaced so they don't overlap. */
    private fun handOffset(i: Int): Vec3 {
        val n = dice.size
        if (n == 1) return Vec3.Zero
        val ring = shape.radius * 1.1f / sin(PI.toFloat() / n)
        val a = 2 * PI.toFloat() * i / n + 0.4f
        return Vec3(cos(a) * ring, if (i % 2 == 0) 0.15f else -0.15f, sin(a) * ring)
    }

    private fun clampHold(p: Vec3): Vec3 {
        val reach = if (dice.size > 1) shape.radius * 1.1f / sin(PI.toFloat() / dice.size) else 0f
        val lx = max(0f, halfWidth - reach - shape.radius)
        val lz = max(0f, halfDepth - reach - shape.radius)
        return Vec3(p.x.coerceIn(-lx, lx), p.y, p.z.coerceIn(-lz, lz))
    }

    private fun held(h: Float) {
        val k = 1 - exp(-HOLD_FOLLOW * h)
        holdCentre = holdCentre + (clampHold(holdTarget) - holdCentre) * k
        for ((i, d) in dice.withIndex()) {
            val old = d.position
            d.position = d.position + (holdCentre + handOffset(i) - d.position) * k
            d.v = (d.position - old) * (1f / h)
            d.orientation = d.orientation.integrate(d.w, h)
        }
    }

    /** Advances by [dt] seconds (in fixed sub-steps, so the outcome doesn't depend on frame rate). */
    fun step(dt: Float): List<Event> {
        if (phase == Phase.Resting) return emptyList()
        val events = ArrayList<Event>(2)
        accumulator += dt
        while (accumulator >= STEP && phase != Phase.Resting) {
            accumulator -= STEP
            if (phase == Phase.Held) held(STEP) else substep(STEP, events)
        }
        return events
    }

    private fun substep(h: Float, events: MutableList<Event>) {
        rollTime += h
        for (d in dice) {
            if (d.asleep) continue
            d.v = Vec3(d.v.x, d.v.y - G * h, d.v.z) * exp(-LINEAR_DRAG * h)
            d.w = d.w * exp(-ANGULAR_DRAG * h)
            d.position = d.position + d.v * h
            d.orientation = d.orientation.integrate(d.w, h)
            d.knockCooldown -= h
            d.touching = false
        }

        val contacts = findContacts()
        // Restitution targets come from the approach speed before any impulses, so the solver doesn't add energy.
        for (c in contacts) {
            val vn = relativeVelocity(c) dot c.n
            val e = if (c.b == null) (if (c.n.y > 0.5f) RESTITUTION_TABLE else RESTITUTION_WALL) else RESTITUTION_DICE
            c.target = if (vn < -BOUNCE_MIN) -e * vn else 0f
            if (-vn > KNOCK_MIN && c.a.knockCooldown <= 0f) {
                c.a.knockCooldown = 0.08f
                events += Event.Knock(min(1f, (-vn - KNOCK_MIN) / (V_MAX * 0.8f)))
            }
        }
        repeat(ITERATIONS) { for (c in contacts) solve(c) }
        correctPositions(contacts)

        for (d in dice) {
            if (d.asleep) continue
            val slow = d.v.length() < SLEEP_V && d.w.length() < SLEEP_W
            d.sleepTime = if (slow && d.touching) d.sleepTime + h else 0f
            when {
                rollTime > MAX_ROLL_TIME -> sleep(d, force = true)
                d.sleepTime <= SLEEP_TIME -> Unit
                tiltFromFlat(d) < COCKED -> sleep(d, force = false)
                else -> nudge(d)
            }
        }
        if (dice.all { it.asleep }) {
            phase = Phase.Resting
            events += Event.Settled(values)
        }
    }

    /** Angle between the face the die is lying on and level, in radians. */
    private fun tiltFromFlat(d: Die): Float {
        val r = d.orientation.toRot()
        val down = r * shape.bottomFace(r).normal
        return atan2((down cross DOWN).length(), down dot DOWN)
    }

    /**
     * A die that has stopped cocked, leaning on a wall or another die, gets a small tip towards flat,
     * like knocking the table. Real dice do this too; a game just needs a clear answer. Each nudge is harder than
     * the last, so a die wedged between others always gets free.
     */
    private fun nudge(d: Die) {
        val r = d.orientation.toRot()
        val tip = (r * shape.bottomFace(r).normal) cross DOWN
        val k = 1f + d.nudges++ * 0.75f
        // Hop towards the middle of the table, where there's most likely room to land.
        val toCentre = Vec3(-d.position.x, 0f, -d.position.z).let { if (it.length() > 0.1f) it.normalized() else it }
        d.w = d.w + tip.normalized() * (NUDGE_SPIN * k)
        d.v = d.v + UP * (NUDGE_HOP * k) + toCentre * (0.5f * k)
        d.sleepTime = 0f
    }

    /** Stops [d]. A die lying on the table (or any die, if [force]d at the time limit) is squared up exactly. */
    private fun sleep(d: Die, force: Boolean) {
        d.asleep = true
        d.v = Vec3.Zero
        d.w = Vec3.Zero
        val rest = shape.inradius
        val onTable = d.position.y < rest + 0.08f
        if (onTable || force) {
            val r = d.orientation.toRot()
            val down = r * shape.bottomFace(r).normal
            val cross = down cross DOWN
            val angle = atan2(cross.length(), down dot DOWN)
            if (angle > 1e-4f) d.orientation = (Quat.axisAngle(cross, angle) * d.orientation).normalized()
        }
        if (onTable || force && d.position.y < rest + 0.6f) d.position = Vec3(d.position.x, rest, d.position.z)
    }

    private fun findContacts(): List<Contact> {
        planes.clear()
        planes += UP to 0f
        planes += Vec3(1f, 0f, 0f) to -halfWidth
        planes += Vec3(-1f, 0f, 0f) to -halfWidth
        planes += Vec3(0f, 0f, 1f) to -halfDepth
        planes += Vec3(0f, 0f, -1f) to -halfDepth
        val out = ArrayList<Contact>()
        val rots = dice.map { it.orientation.toRot() }
        for ((i, d) in dice.withIndex()) {
            if (d.asleep) continue
            val r = rots[i]
            for (corner in shape.corners) {
                val point = d.position + r * corner
                for ((n, offset) in planes) {
                    val pen = offset - (point dot n)
                    if (pen > 0f) {
                        out += Contact(d, null, point, n, pen, if (n.y > 0.5f) FRICTION_TABLE else FRICTION_WALL)
                        d.touching = true
                    }
                }
            }
        }
        for (i in dice.indices) for (j in i + 1 until dice.size) {
            val a = dice[i]; val b = dice[j]
            if (a.asleep && b.asleep) continue
            val gap = a.position - b.position
            if (gap.length() > 2 * shape.radius) continue // further apart than two corners can reach
            val before = out.size
            cornersInside(a, rots[i], b, rots[j], out)
            cornersInside(b, rots[j], a, rots[i], out)
            val touch = 2 * shape.inradius
            if (out.size == before && gap.length() < touch) {
                // Edge against edge, which the corner test misses: treat the inscribed spheres as touching.
                val n = if (gap.length() > 1e-4f) gap.normalized() else UP
                out += Contact(a, b, b.position + gap * 0.5f, n, touch - gap.length(), FRICTION_DICE)
            }
            if (out.size > before) {
                a.touching = true; b.touching = true
                // A sleeping die that gets hit wakes up and joins in.
                val hit = (a.v - b.v).length() + (a.w - b.w).length() * 0.3f
                if (hit > WAKE_SPEED) { a.asleep = false; b.asleep = false; a.sleepTime = 0f; b.sleepTime = 0f }
            }
        }
        return out
    }

    /** Corners of [a] inside [b] become contacts pushing [a] out through [b]'s nearest face. */
    private fun cornersInside(a: Die, ra: Rot, b: Die, rb: Rot, out: MutableList<Contact>) {
        val bx = rb.column(0); val by = rb.column(1); val bz = rb.column(2)
        for (corner in shape.corners) {
            val point = a.position + ra * corner
            val rel = point - b.position
            val local = Vec3(rel dot bx, rel dot by, rel dot bz)
            var pen = Float.MAX_VALUE
            var face: DieShape.Face? = null
            for (f in shape.faces) {
                val depth = f.distance - (local dot f.normal)
                if (depth <= 0f) { face = null; break }
                if (depth < pen) { pen = depth; face = f }
            }
            if (face != null) out += Contact(a, b, point, rb * face.normal, pen, FRICTION_DICE)
        }
    }

    private fun relativeVelocity(c: Contact): Vec3 {
        val va = pointVelocity(c.a, c.point)
        return if (c.b == null) va else va - pointVelocity(c.b, c.point)
    }

    private fun pointVelocity(d: Die, point: Vec3): Vec3 =
        if (d.asleep) Vec3.Zero else d.v + (d.w cross (point - d.position))

    /** One sequential-impulse pass at a contact: normal impulse (accumulated, never pulling), then clamped friction. */
    private fun solve(c: Contact) {
        val ra = c.point - c.a.position
        val rb = c.b?.let { c.point - it.position }
        val aMoves = !c.a.asleep
        val bMoves = c.b != null && !c.b.asleep
        if (!aMoves && !bMoves) return

        fun effectiveMass(dir: Vec3): Float {
            var k = 0f
            if (aMoves) { val x = ra cross dir; k += INV_MASS + shape.invInertia * (x dot x) }
            if (bMoves) { val x = rb!! cross dir; k += INV_MASS + shape.invInertia * (x dot x) }
            return k
        }

        fun apply(impulse: Vec3) {
            if (aMoves) {
                c.a.v = c.a.v + impulse * INV_MASS
                c.a.w = c.a.w + (ra cross impulse) * shape.invInertia
            }
            if (bMoves) {
                val b = c.b!!
                b.v = b.v - impulse * INV_MASS
                b.w = b.w - (rb!! cross impulse) * shape.invInertia
            }
        }

        val vn = relativeVelocity(c) dot c.n
        val old = c.jn
        c.jn = max(0f, old + (c.target - vn) / effectiveMass(c.n))
        apply(c.n * (c.jn - old))

        val vrel = relativeVelocity(c)
        val vt = vrel - c.n * (vrel dot c.n)
        val speed = vt.length()
        if (speed < 1e-5f) return
        val t = vt * (1f / speed)
        val oldT = c.jt
        var jt = oldT - t * (speed / effectiveMass(t))
        val limit = c.friction * c.jn
        if (jt.length() > limit) jt = jt.normalized() * limit
        c.jt = jt
        apply(jt - oldT)
    }

    /** Pushes dice back out of whatever they've sunk into (the deepest contact per die and surface). */
    private fun correctPositions(contacts: List<Contact>) {
        val deepest = HashMap<Pair<Die, Any>, Contact>()
        for (c in contacts) {
            val key = c.a to (c.b ?: c.n)
            if ((deepest[key]?.pen ?: 0f) < c.pen) deepest[key] = c
        }
        for (c in deepest.values) {
            val push = max(0f, c.pen - SLOP) * CORRECTION
            val bMoves = c.b != null && !c.b.asleep
            if (c.b == null || !bMoves) {
                if (!c.a.asleep) c.a.position = c.a.position + c.n * push
            } else {
                val aShare = if (c.a.asleep) 0f else if (bMoves) 0.5f else 1f
                c.a.position = c.a.position + c.n * (push * aShare)
                c.b.position = c.b.position - c.n * (push * (1f - aShare))
            }
        }
    }

    private fun randomUnit(): Vec3 {
        while (true) {
            val v = Vec3(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1)
            val l = v.length()
            if (l in 0.1f..1f) return v * (1f / l)
        }
    }

    companion object {
        const val STEP = 1f / 240f
        const val G = 40f
        /** How fast held dice follow the fingers. */
        const val HOLD_FOLLOW = 22f
        /** How far above its furthest corner a die leaves the hand. */
        const val LAUNCH_CLEARANCE = 0.25f
        const val V_MIN = 4f
        const val V_MAX = 14f
        const val VY_MIN = 3f
        const val VY_MAX = 7.5f
        const val SPIN_MIN = 10f
        const val SPIN_MAX = 32f
        const val LINEAR_DRAG = 0.1f
        const val ANGULAR_DRAG = 0.25f
        const val RESTITUTION_TABLE = 0.35f
        const val RESTITUTION_WALL = 0.5f
        const val RESTITUTION_DICE = 0.35f
        const val FRICTION_TABLE = 0.45f
        const val FRICTION_WALL = 0.2f
        const val FRICTION_DICE = 0.25f
        const val BOUNCE_MIN = 1.2f
        const val KNOCK_MIN = 1f
        const val WAKE_SPEED = 0.8f
        const val ITERATIONS = 8
        const val SLOP = 0.003f
        const val CORRECTION = 0.6f
        const val SLEEP_V = 0.25f
        const val SLEEP_W = 0.9f
        const val SLEEP_TIME = 0.3f
        const val MAX_ROLL_TIME = 10f
        /** Tilt (radians, about 8°) beyond which a stopped die counts as cocked and gets nudged. */
        const val COCKED = 0.14f
        const val NUDGE_SPIN = 5f
        const val NUDGE_HOP = 1.2f
        /** Mass 1; the moment of inertia depends on the solid (see [DieShape.invInertia]). */
        const val INV_MASS = 1f

        val UP = Vec3(0f, 1f, 0f)
        private val DOWN = Vec3(0f, -1f, 0f)
    }
}
