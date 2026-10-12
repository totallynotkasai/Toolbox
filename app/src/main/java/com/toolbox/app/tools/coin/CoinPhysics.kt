package com.toolbox.app.tools.coin

import com.toolbox.app.ui.common.Quat
import com.toolbox.app.ui.common.Rot
import com.toolbox.app.ui.common.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A tossed coin, simulated as a rigid disc in 3D: it tumbles through the air, bounces, can land on its edge and roll,
 * knocks off the walls and wobbles down flat. The face it lands on comes out of the motion. Pure and deterministic for
 * a given [seed] and sequence of calls, so it can be tested for fairness.
 *
 * Units are coin radii and seconds. World axes as in [Rot]: x across the table, y up, z towards the viewer; the coin's
 * own axis (heads side) is its local z. The table is 2·[halfWidth] by 2·[halfDepth] with walls all round, and
 * [ceiling] is as high as a throw may go, so it stays in view. Contacts are sequential impulses at points round both
 * rims, with friction. Spin is tracked as angular momentum, so a disc's lopsided inertia gives it a real wobble.
 */
class CoinPhysics(
    seed: Long,
    startHeads: Boolean = true,
    var halfWidth: Float = 4f,
    var halfDepth: Float = 4f,
    var ceiling: Float = MAX_HEIGHT,
) {
    enum class Phase { Resting, Held, Flying }

    sealed interface Event {
        /** The coin hit the table or a wall; [intensity] 0–1 from the impact speed. */
        data class Bounce(val intensity: Float) : Event
        /** One tick of the rattle as the coin wobbles down; they come faster and softer towards the end. */
        data class Rattle(val intensity: Float) : Event
        data class Settled(val heads: Boolean) : Event
    }

    private class Contact(val offset: Vec3, val n: Vec3, val pen: Float, val friction: Float, val kn: Float) {
        var target = 0f
        var jn = 0f
        var jt = Vec3.Zero
    }

    private val random = Random(seed)

    var phase = Phase.Resting; private set
    var position = Vec3(0f, REST_Y, 0f); private set
    val x: Float get() = position.x
    val y: Float get() = position.y
    val z: Float get() = position.z

    private var orientation = restPose(startHeads, 0f)
    private var rot = orientation.toRot()
    private var v = Vec3.Zero
    /** Angular momentum (world frame); the angular velocity [w] follows from it and the orientation. */
    private var momentum = Vec3.Zero
    private var w = Vec3.Zero

    /** Face up at rest; while moving, the face more nearly up. */
    var heads = startHeads; private set
    /** Times it has turned over this throw, and the highest point reached. */
    var flips = 0; private set
    var peak = REST_Y; private set
    var bounces = 0; private set

    private var lastSide = 0
    private var time = 0f
    private var sleepTime = 0f
    private var nudges = 0
    private var knockCooldown = 0f
    private var accumulator = 0f
    private var rattleAngle = Float.NaN
    private var rattleTravel = 0f
    private val contacts = ArrayList<Contact>(24)

    /** Held: where the fingers want it, the pose it was picked up in, and how far it leans back and sideways. */
    private var holdTarget = Vec3.Zero
    private var holdBase = orientation
    private var leanBack = 0f
    private var leanSide = 0f

    fun pose(): Rot = rot

    /**
     * Keeps the coin on the table when it changes size (e.g. on rotation). A size that isn't a real table (zero,
     * infinite or NaN, from a stage squashed to nothing) is ignored, so it can't turn the coin's position into NaN.
     */
    fun resize(halfWidth: Float, halfDepth: Float, ceiling: Float) {
        if (!(halfWidth.isFinite() && halfDepth.isFinite() && ceiling.isFinite())) return
        if (halfWidth <= 0f || halfDepth <= 0f || ceiling <= 0f) return
        this.halfWidth = halfWidth
        this.halfDepth = halfDepth
        this.ceiling = ceiling
        if (phase == Phase.Resting) position = clampToTable(position)
    }

    /** Turns a resting coin over, if need be, so it shows [heads]: to match a result from the other mode. */
    fun lay(heads: Boolean) {
        if (phase != Phase.Resting || heads == this.heads) return
        this.heads = heads
        setOrientation(Quat.axisAngle(X_AXIS, PI.toFloat()) * orientation)
    }

    /**
     * Picks the resting coin up: it lifts clear of the table and tips back in the fingers, ready to be flicked
     * with [toss] or put down with [drop]. Returns false unless the coin is at rest.
     */
    fun grab(): Boolean {
        if (phase != Phase.Resting) return false
        holdBase = orientation
        holdTarget = Vec3(x, HOLD_Y, z)
        leanBack = 0f
        leanSide = 0f
        v = Vec3.Zero
        setMomentum(Vec3.Zero)
        accumulator = 0f
        phase = Phase.Held
        return true
    }

    /** Moves a held coin towards ([x], [z]) on the table; it follows smoothly, held above it. */
    fun hold(x: Float, z: Float) {
        if (phase == Phase.Held) holdTarget = Vec3(x, HOLD_Y, z)
    }

    /** Lets go of a held coin without throwing it: it drops (drifting as it was moving) and lands the way it was. */
    fun drop(): Boolean {
        if (phase != Phase.Held) return false
        v = Vec3(v.x * 0.5f, 0f, v.z * 0.5f)
        setMomentum(inertia(randomUnit() * random.nextFloat()))
        launch()
        return true
    }

    /**
     * Throws the coin with [power] 0 (a lazy flip) to 1 (a hard throw). ([alongX], [alongZ]) is the direction to
     * throw it across the table (0, −1 is away from the viewer); its length, 0–1, is how much of the throw goes
     * along the table rather than up: a flat throw stays low and goes further, so it tends to land on its edge
     * and roll. It flips end over end the way it's going. Works from rest, or out of the fingers if it's [grab]bed.
     * Returns false (and does nothing) if it's already moving.
     */
    fun toss(power: Float, alongX: Float = 0f, alongZ: Float = 0f): Boolean {
        if (phase != Phase.Resting && phase != Phase.Held) return false
        val p = power.coerceIn(0f, 1f)
        val flat = Vec3(alongX, 0f, alongZ)
        val along = min(1f, flat.length())
        val heading = if (flat.length() > 1e-3f) flat.normalized() else Vec3(0f, 0f, -1f)
        val carried = if (phase == Phase.Held) Vec3(v.x, 0f, v.z) * 0.3f else Vec3.Zero // some of the drag carries on
        position = clampToTable(Vec3(x, max(y, LAUNCH_Y), z))

        // Harder goes higher, up to the ceiling; a throw along the table stays lower.
        val top = max(position.y + 0.5f, ceiling - 0.3f)
        val rise = (top - position.y) * (0.18f + 0.82f * p) * (1f - 0.6f * along)
        val speed = along * (VH_MIN + (VH_MAX - VH_MIN) * p)
        v = heading * speed + Vec3(0f, sqrt(2 * G * rise), 0f) + carried

        // Identical gestures still vary: ±5 % of the flip, plus some absolute noise. The noise is sized so that over
        // its time in the air it spreads the landing angle over a whole number of full turns, which makes that angle
        // even all the way round. Otherwise a lazy or low flip, which only turns over a few times, would favour the
        // face it started on.
        val airTime = v.y / G + sqrt(2 * (position.y + rise - 0.5f) / G)
        val turns = max(1f, ceil(SPIN_NOISE * airTime / PI.toFloat()))
        val noise = turns * PI.toFloat() / airTime
        val rate = (W_MIN + (W_MAX - W_MIN) * p) * (1f + (random.nextFloat() * 2 - 1) * SPIN_JITTER) +
            (random.nextFloat() * 2 - 1) * noise
        val axis = rot.column(2)
        val spin = (UP cross heading) * rate + // end over end, the way it's thrown
            axis * ((random.nextFloat() * 2 - 1) * SPIN_AXIAL) + // a twist about its own axis
            heading * ((random.nextFloat() * 2 - 1) * WOBBLE) // and a little wobble
        setMomentum(inertia(spin))
        launch()
        return true
    }

    private fun launch() {
        flips = 0
        peak = y
        bounces = 0
        time = 0f
        sleepTime = 0f
        nudges = 0
        knockCooldown = 0f
        accumulator = 0f
        rattleAngle = Float.NaN
        rattleTravel = 0f
        lastSide = side()
        phase = Phase.Flying
    }

    /** Advances by [dt] seconds (in fixed sub-steps, so the outcome doesn't depend on frame rate). */
    fun step(dt: Float): List<Event> {
        if (phase == Phase.Resting) return emptyList()
        val events = ArrayList<Event>(2)
        accumulator += dt
        while (accumulator >= STEP && phase != Phase.Resting) {
            accumulator -= STEP
            if (phase == Phase.Held) held(STEP) else fly(STEP, events)
        }
        return events
    }

    private fun held(h: Float) {
        val k = 1 - exp(-HOLD_FOLLOW * h)
        val old = position
        position = position + (clampToTable(holdTarget) - position) * k
        v = (position - old) * (1f / h)
        // Tipped back in the fingers, ready to flick, and leaning into the way it's dragged.
        leanBack += (HOLD_LEAN + (v.z * 0.03f).coerceIn(-0.2f, 0.2f) - leanBack) * k
        leanSide += ((-v.x * 0.05f).coerceIn(-0.4f, 0.4f) - leanSide) * (1 - exp(-10f * h))
        setOrientation(Quat.axisAngle(Z_AXIS, leanSide) * Quat.axisAngle(X_AXIS, leanBack) * holdBase)
    }

    private fun fly(h: Float, events: MutableList<Event>) {
        time += h
        knockCooldown -= h
        v = Vec3(v.x, v.y - G * h, v.z)
        position = position + v * h
        setOrientation(orientation.integrate(w, h))

        findContacts()
        val touching = contacts.any { it.n.y > 0.5f }
        // Restitution targets come from the approach speed before any impulses, so the solver doesn't add energy.
        var hardest = 0f
        var hardestOnTable = false
        for (c in contacts) {
            val vn = pointVelocity(c.offset) dot c.n
            val e = if (c.n.y > 0.5f) RESTITUTION_TABLE else RESTITUTION_WALL
            c.target = if (vn < -BOUNCE_MIN) -e * vn else 0f
            if (-vn > hardest) { hardest = -vn; hardestOnTable = c.n.y > 0.5f }
        }
        if (hardest > KNOCK_MIN && knockCooldown <= 0f) {
            knockCooldown = 0.07f
            if (hardestOnTable) bounces++
            events += Event.Bounce(min(1f, hardest / V_REF))
        }
        repeat(ITERATIONS) { for (c in contacts) solve(c) }
        correctPositions()

        if (touching) {
            // Rolling resistance and the grind of the rim on the cloth.
            setMomentum(momentum * exp(-ROLL_DRAG * h))
            v = Vec3(v.x * exp(-SLIDE_DRAG * h), v.y, v.z * exp(-SLIDE_DRAG * h))
            rattle(events)
        } else {
            rattleAngle = Float.NaN
        }
        peak = max(peak, y)
        val s = side()
        if (s != 0 && s != lastSide) { flips++; lastSide = s }
        heads = rot.column(2).y >= 0f

        val slow = v.length() < SLEEP_V && w.length() < SLEEP_W
        sleepTime = if (slow && touching) sleepTime + h else 0f
        when {
            time > MAX_TIME -> rest(events)
            sleepTime < SLEEP_TIME -> Unit
            abs(rot.column(2).y) > FLAT -> rest(events)
            else -> nudge()
        }
    }

    /** Which face is clearly up (1 heads, −1 tails), or 0 while it's near edge-on: for counting turns. */
    private fun side(): Int {
        val ay = rot.column(2).y
        return if (ay > 0.3f) 1 else if (ay < -0.3f) -1 else 0
    }

    /**
     * Like a coin spinning down on a table: as it wobbles, the point of the rim touching the table runs round
     * the rim, faster the lower it leans. Each half-turn of that point is one tick of the rattle.
     */
    private fun rattle(events: MutableList<Event>) {
        val tilt = acosSafe(abs(rot.column(2).y))
        if (tilt < 0.015f || tilt > 0.6f) { rattleAngle = Float.NaN; return }
        val down = rot.local(Vec3(0f, -1f, 0f))
        val angle = atan2(down.y, down.x)
        if (!rattleAngle.isNaN()) {
            var d = angle - rattleAngle
            if (d > PI) d -= 2 * PI.toFloat()
            if (d < -PI) d += 2 * PI.toFloat()
            rattleTravel += abs(d)
            if (rattleTravel >= PI) {
                rattleTravel -= PI.toFloat()
                events += Event.Rattle(0.15f + 0.6f * min(1f, tilt / 0.35f))
            }
        }
        rattleAngle = angle
    }

    /** Lies it down flat on whichever face is more nearly up, and stops. */
    private fun rest(events: MutableList<Event>) {
        val axis = rot.column(2)
        heads = axis.y >= 0f
        setOrientation(Quat.between(axis, if (heads) UP else DOWN) * orientation)
        position = clampToTable(Vec3(x, REST_Y, z))
        v = Vec3.Zero
        setMomentum(Vec3.Zero)
        phase = Phase.Resting
        events += Event.Settled(heads)
    }

    /**
     * A coin that has stopped on its edge, or leaning on a wall, gets a small tip towards flat, like knocking the
     * table. Each nudge is harder than the last, so it always comes free.
     */
    private fun nudge() {
        val axis = rot.column(2)
        val target = when {
            axis.y > 0.05f -> UP
            axis.y < -0.05f -> DOWN
            random.nextBoolean() -> UP
            else -> DOWN
        }
        val tip = (axis cross target).let { if (it.length() > 1e-4f) it.normalized() else rot.column(0) }
        val k = 1f + nudges++ * 0.75f
        val toCentre = Vec3(-x, 0f, -z).let { if (it.length() > 0.1f) it.normalized() else it }
        setMomentum(momentum + inertia(tip * (NUDGE_SPIN * k)))
        v = v + UP * (NUDGE_HOP * k) + toCentre * (0.4f * k)
        sleepTime = 0f
    }

    private fun findContacts() {
        contacts.clear()
        for (local in RIM) {
            val offset = rot * local
            val point = position + offset
            // Floor, then the four walls: (normal, offset) with the inside where point · normal ≥ offset.
            addContact(offset, point.y, UP, FRICTION_TABLE)
            addContact(offset, point.x + halfWidth, X_AXIS, FRICTION_WALL)
            addContact(offset, halfWidth - point.x, -X_AXIS, FRICTION_WALL)
            addContact(offset, point.z + halfDepth, Z_AXIS, FRICTION_WALL)
            addContact(offset, halfDepth - point.z, -Z_AXIS, FRICTION_WALL)
        }
    }

    private fun addContact(offset: Vec3, clearance: Float, n: Vec3, friction: Float) {
        if (clearance >= 0f) return
        contacts += Contact(offset, n, -clearance, friction, effectiveMass(offset, n))
    }

    private fun pointVelocity(offset: Vec3) = v + (w cross offset)

    private fun effectiveMass(offset: Vec3, dir: Vec3): Float {
        val x = offset cross dir
        return 1f + (x dot inverseInertia(x))
    }

    /** One sequential-impulse pass at a contact: normal impulse (accumulated, never pulling), then clamped friction. */
    private fun solve(c: Contact) {
        val vn = pointVelocity(c.offset) dot c.n
        val old = c.jn
        c.jn = max(0f, old + (c.target - vn) / c.kn)
        applyImpulse(c.offset, c.n * (c.jn - old))

        val vrel = pointVelocity(c.offset)
        val vt = vrel - c.n * (vrel dot c.n)
        val speed = vt.length()
        if (speed < 1e-5f) return
        val t = vt * (1f / speed)
        val oldT = c.jt
        var jt = oldT - t * (speed / effectiveMass(c.offset, t))
        val limit = c.friction * c.jn
        if (jt.length() > limit) jt = jt.normalized() * limit
        c.jt = jt
        applyImpulse(c.offset, jt - oldT)
    }

    private fun applyImpulse(offset: Vec3, impulse: Vec3) {
        v = v + impulse // mass 1
        setMomentum(momentum + (offset cross impulse))
    }

    /** Pushes the coin back out of whatever it has sunk into (the deepest point per surface). */
    private fun correctPositions() {
        for (n in SURFACES) {
            val deepest = contacts.filter { it.n == n }.maxOfOrNull { it.pen } ?: continue
            position = position + n * (max(0f, deepest - SLOP) * CORRECTION)
        }
    }

    private fun clampToTable(p: Vec3): Vec3 {
        val lx = max(0f, halfWidth - 1.05f)
        val lz = max(0f, halfDepth - 1.05f)
        return Vec3(p.x.coerceIn(-lx, lx), p.y, p.z.coerceIn(-lz, lz))
    }

    private fun setOrientation(q: Quat) {
        orientation = q.normalized()
        rot = orientation.toRot()
        w = inverseInertia(momentum)
    }

    private fun setMomentum(l: Vec3) {
        momentum = l
        w = inverseInertia(l)
    }

    /** A disc spins twice as easily about a diameter as about its own axis. */
    private fun inverseInertia(l: Vec3): Vec3 {
        val b = rot.local(l)
        return rot * Vec3(b.x / I_DIAMETER, b.y / I_DIAMETER, b.z / I_AXIS)
    }

    private fun inertia(spin: Vec3): Vec3 {
        val b = rot.local(spin)
        return rot * Vec3(b.x * I_DIAMETER, b.y * I_DIAMETER, b.z * I_AXIS)
    }

    private fun randomUnit(): Vec3 {
        while (true) {
            val u = Vec3(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1)
            val l = u.length()
            if (l in 0.1f..1f) return u * (1f / l)
        }
    }

    companion object {
        const val STEP = 1f / 240f
        const val G = 44f
        const val REST_Y = COIN_THICKNESS / 2
        const val LAUNCH_Y = 1.2f
        /** Default [ceiling], and the highest any throw goes. */
        const val MAX_HEIGHT = 9f
        const val VH_MIN = 2.5f
        const val VH_MAX = 13f
        const val W_MIN = 24f
        const val W_MAX = 57f
        const val SPIN_JITTER = 0.05f
        const val SPIN_NOISE = 6f
        /**
         * Twist about the coin's own axis. Kept small: it tilts the spin towards the face that started up, so the
         * coin wobbles on that side in the air, and a big twist makes it land that way noticeably often.
         */
        const val SPIN_AXIAL = 1.5f
        const val WOBBLE = 2.5f
        const val RESTITUTION_TABLE = 0.3f
        const val RESTITUTION_WALL = 0.45f
        const val FRICTION_TABLE = 0.35f
        const val FRICTION_WALL = 0.2f
        const val BOUNCE_MIN = 1f
        const val KNOCK_MIN = 1.5f
        const val V_REF = 20f
        const val ITERATIONS = 8
        const val SLOP = 0.002f
        const val CORRECTION = 0.6f
        const val ROLL_DRAG = 0.45f
        const val SLIDE_DRAG = 0.3f
        const val SLEEP_V = 0.2f
        const val SLEEP_W = 1.2f
        const val SLEEP_TIME = 0.25f
        const val MAX_TIME = 12f
        /** |cos| of the tilt (about 8°) within which a stopped coin counts as lying flat. */
        const val FLAT = 0.99f
        const val NUDGE_SPIN = 4f
        const val NUDGE_HOP = 1f
        /** A held coin's height above the table, how fast it follows the fingers, and how far it tips back. */
        const val HOLD_Y = 1.6f
        const val HOLD_FOLLOW = 28f
        const val HOLD_LEAN = 0.5f
        /** Moments of inertia of a disc of mass 1 and radius 1: about a diameter, and about its axis. */
        private const val I_DIAMETER = 0.25f + COIN_THICKNESS * COIN_THICKNESS / 12f
        private const val I_AXIS = 0.5f

        val UP = Vec3(0f, 1f, 0f)
        private val DOWN = Vec3(0f, -1f, 0f)
        private val X_AXIS = Vec3(1f, 0f, 0f)
        private val Z_AXIS = Vec3(0f, 0f, 1f)
        private val SURFACES = listOf(UP, X_AXIS, -X_AXIS, Z_AXIS, -Z_AXIS)

        /** Contact points round both rims. */
        private val RIM = buildList {
            val n = 24
            for (face in listOf(-COIN_THICKNESS / 2, COIN_THICKNESS / 2)) for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat()
                add(Vec3(cos(a), sin(a), face))
            }
        }

        /** Lying flat on the table, heads or tails up, turned [yaw] about the vertical. */
        private fun restPose(heads: Boolean, yaw: Float): Quat =
            Quat.axisAngle(UP, yaw) * Quat.axisAngle(X_AXIS, if (heads) -PI.toFloat() / 2 else PI.toFloat() / 2)

        private fun acosSafe(c: Float) = kotlin.math.acos(c.coerceIn(-1f, 1f))
    }
}
