package com.toolbox.app

import com.toolbox.app.tools.dice.DicePhysics
import com.toolbox.app.tools.dice.DieLook
import com.toolbox.app.tools.dice.DieShape
import com.toolbox.app.tools.dice.QuickDice
import com.toolbox.app.tools.dice.diceRows
import com.toolbox.app.ui.common.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DicePhysicsTest {
    /** Runs one throw to the end at 60 fps; returns the settled values and how long it took. */
    private fun DicePhysics.rollOut(power: Float, dirX: Float = 0f, dirZ: Float = -1f): Pair<List<Int>, Float> {
        assertTrue(toss(power, dirX, dirZ))
        var t = 0f
        while (phase == DicePhysics.Phase.Rolling) {
            for (e in step(1 / 60f)) if (e is DicePhysics.Event.Settled) return e.values to t
            t += 1 / 60f
            check(t < 30f) { "never settled" }
        }
        error("settled without an event")
    }

    private fun DicePhysics.Die.downY(): Float {
        val r = pose()
        val n = shape.bottomFace(r).normal
        return r[1, 0] * n.x + r[1, 1] * n.y + r[1, 2] * n.z
    }

    @Test fun settlesFlatOnTheTableAndInsideTheWalls() {
        for (sides in DieShape.SIDES) {
            val p = DicePhysics(seed = sides.toLong(), count = 3, sides = sides)
            val rest = p.shape.inradius
            repeat(if (sides == 6) 40 else 15) { i ->
                val (values, time) = p.rollOut(power = (i % 5) / 4f, dirX = (i % 3 - 1) * 0.4f)
                assertEquals(3, values.size)
                assertTrue("d$sides roll $i took ${time}s", time < DicePhysics.MAX_ROLL_TIME)
                assertTrue("d$sides values $values", values.all { it in 1..sides })
                for (d in p.dice) {
                    assertTrue("d$sides roll $i (${time}s) not flat: ${d.downY()} at ${d.position}", d.downY() < -0.97f)
                    assertTrue(abs(d.position.x) < p.halfWidth && abs(d.position.z) < p.halfDepth)
                    assertTrue("d$sides sunk or floating: ${d.position.y}", d.position.y in rest - 0.05f..rest + 1.1f)
                }
            }
        }
    }

    @Test fun restingDiceNeverOverlap() {
        // Phone-sized tables (half a stage width of 6.5 dice, and wider), straight away and after a resize.
        for (sides in DieShape.SIDES) for (n in 1..6) for (halfWidth in listOf(3f, 3.25f, 4.5f)) {
            val p = DicePhysics(seed = n.toLong(), count = n, sides = sides)
            p.resize(halfWidth, 3.9f)
            assertApart(p.dice, "d$sides × $n on ${halfWidth}w")
            for (d in p.dice) {
                assertTrue(abs(d.position.x) + p.shape.radius <= halfWidth + 1e-3f)
                assertTrue(abs(d.position.z) + p.shape.radius <= 3.9f + 1e-3f)
            }
        }
    }

    @Test fun showingValuesLaysTheDiceOnThem() {
        for (sides in DieShape.SIDES) {
            val p = DicePhysics(seed = sides.toLong(), count = 1, sides = sides)
            for (v in 1..sides) {
                p.show(listOf(v))
                assertEquals("d$sides showing $v", listOf(v), p.values)
                assertTrue(p.dice.single().downY() < -0.999f)
            }
            val many = List(5) { 1 + it % sides }
            p.show(many) // more dice than it had
            assertEquals(many, p.values)
            assertApart(p.dice, "d$sides shown")
        }
    }

    @Test fun anEmptyTableIsIgnored() {
        // A stage squashed to nothing gives an infinitely wide table with a NaN depth.
        val p = DicePhysics(seed = 8, count = 3)
        p.resize(Float.POSITIVE_INFINITY, Float.NaN)
        p.resize(0f, 0f)
        assertEquals(3f, p.halfWidth)
        assertEquals(4.5f, p.halfDepth)
        p.setCount(4)
        val (values, _) = p.rollOut(0.5f)
        assertEquals(4, values.size)
        for (d in p.dice) assertTrue("${d.position}", d.position.x.isFinite() && d.position.z.isFinite())
    }

    @Test fun rowsShareTheDiceOutEvenly() {
        fun rows(n: Int) = diceRows(n, 1f, 2f).groupBy { it.z }.values.map { it.size }
        assertEquals(listOf(1), rows(1))
        assertEquals(listOf(3), rows(3))
        assertEquals(listOf(2, 2), rows(4))
        assertEquals(listOf(3, 2), rows(5))
        assertEquals(listOf(3, 3), rows(6))
        assertEquals(listOf(1, 1), diceRows(2, 1f, -5f).groupBy { it.z }.values.map { it.size }) // no room: one each
        for (row in diceRows(5, 1f, 2f).groupBy { it.z }.values) assertEquals(0f, row.sumOf { it.x.toDouble() }.toFloat(), 1e-5f)
    }

    @Test fun ignoresThrowsAndSideChangesMidRoll() {
        val p = DicePhysics(seed = 2, count = 1)
        assertTrue(p.toss(0.5f))
        p.step(0.1f)
        assertFalse(p.toss(1f))
        p.setSides(20)
        assertEquals(6, p.shape.sides)
    }

    @Test fun pickingUpGathersEveryDieAndFlickingThrowsThem() {
        val p = DicePhysics(seed = 4, count = 4, sides = 12, halfWidth = 4f, halfDepth = 5f)
        assertTrue(p.grab())
        assertFalse(p.grab())
        p.hold(1f, -1f)
        repeat(60) { p.step(1 / 60f) }
        assertEquals(DicePhysics.Phase.Held, p.phase)
        for (d in p.dice) {
            assertTrue("held die at ${d.position}", abs(d.position.x - 1f) < 2.5f && abs(d.position.z + 1f) < 2.5f)
            assertTrue(d.position.y > p.shape.radius)
        }
        for (i in p.dice.indices) for (j in i + 1 until p.dice.size) {
            val gap = (p.dice[i].position - p.dice[j].position).length()
            assertTrue("dice overlap in the hand: $gap", gap > 2 * p.shape.radius)
        }
        val (values, _) = p.rollOut(0.6f, 0.3f, -1f)
        assertEquals(4, values.size)
    }

    @Test fun droppingHeldDiceRollsThem() {
        val p = DicePhysics(seed = 6, count = 3)
        repeat(10) {
            assertTrue(p.grab())
            repeat(20) { p.step(1 / 60f) }
            assertTrue(p.drop())
            var t = 0f
            while (p.phase != DicePhysics.Phase.Resting) { p.step(1 / 60f); t += 1 / 60f; check(t < 20f) }
            for (d in p.dice) assertTrue(d.downY() < -0.97f)
        }
    }

    @Test fun shapesHaveTheRightFacesAndNumbering() {
        for (sides in DieShape.SIDES) {
            val s = DieShape.of(sides)
            assertEquals(sides, s.faces.size)
            if (sides == 4) {
                assertEquals((1..4).toSet(), s.corners.indices.map { s.cornerValue(it) }.toSet())
                continue
            }
            assertEquals("d$sides", (1..sides).toSet(), s.faces.map { it.value }.toSet())
            for (f in s.faces) {
                val opposite = s.faces.single { (it.normal dot f.normal) < -0.999f }
                assertEquals("d$sides opposite faces", sides + 1, f.value + opposite.value)
                assertEquals(s.inradius, f.distance, 1e-4f)
                // Drawn from outside, not mirrored.
                assertTrue(((f.across cross f.down) dot f.normal) > 0.999f)
            }
        }
        val cube = DieShape.of(6)
        assertEquals(1, cube.faces.single { it.normal.y > 0.9f }.value)
        assertEquals(6, cube.faces.single { it.normal.y < -0.9f }.value)
    }

    private fun assertFair(sides: Int, n: Int, critical: Double) {
        val counts = IntArray(sides + 1)
        val rng = Random(7)
        val p = DicePhysics(seed = 3, count = 1, sides = sides)
        repeat(n) {
            val (values, _) = p.rollOut(power = rng.nextFloat(), dirX = rng.nextFloat() - 0.5f)
            counts[values[0]]++
        }
        val expected = n.toDouble() / sides
        val chi2 = (1..sides).sumOf { (counts[it] - expected).let { d -> d * d / expected } }
        assertTrue("d$sides counts ${counts.drop(1)}, chi² $chi2", chi2 < critical)
    }

    // Critical values at 0.1 %, so a fair die essentially never fails these.
    @Test fun d6FacesComeUpEvenly() = assertFair(6, 1200, 20.5)
    @Test fun d4CornersComeUpEvenly() = assertFair(4, 800, 16.3)
    @Test fun d8FacesComeUpEvenly() = assertFair(8, 1200, 24.3)
    @Test fun d10FacesComeUpEvenly() = assertFair(10, 1500, 27.9)
    @Test fun d12FacesComeUpEvenly() = assertFair(12, 1800, 31.3)
    @Test fun d20FacesComeUpEvenly() = assertFair(20, 2400, 43.8)
}

/** No two dice closer than their full widths, so however they're turned they can't overlap. */
private fun assertApart(dice: List<DieLook>, what: String) {
    for (i in dice.indices) for (j in i + 1 until dice.size) {
        val a = dice[i].position; val b = dice[j].position
        val gap = Vec3(a.x - b.x, 0f, a.z - b.z).length()
        assertTrue("$what: dice $i and $j only $gap apart", gap >= 2 * dice[i].shape.radius)
    }
}

class QuickDiceTest {
    private fun DieLook.showing() = shape.value(pose())

    @Test fun landsOnTheNumbersItRolled() {
        for (sides in DieShape.SIDES) {
            val q = QuickDice(seed = sides.toLong(), count = 4, sides = sides)
            repeat(20) {
                val values = q.roll()
                assertEquals(values, q.values)
                assertTrue(values.all { it in 1..sides })
                assertEquals("d$sides", values, q.dice(1f).map { it.showing() })
                for (d in q.dice(1f)) assertEquals(d.shape.inradius, d.position.y, 1e-5f)
            }
        }
    }

    @Test fun aRollStartsFromWhereTheDiceWere() {
        val q = QuickDice(seed = 5, count = 3, sides = 20)
        val before = q.dice(1f).map { it.pose() }
        q.roll()
        val start = q.dice(0f)
        for ((d, r) in start.zip(before)) {
            for (c in 0 until 3) {
                val a = d.pose().column(c); val b = r.column(c)
                assertEquals(0f, (a - b).length(), 1e-3f)
            }
        }
        // Partway through, they're in the air.
        assertTrue(q.dice(0.6f).all { it.position.y > it.shape.inradius + 0.3f })
    }

    @Test fun showAndChangesKeepTidyRows() {
        val q = QuickDice(seed = 2, count = 2)
        q.show(listOf(6, 1, 3, 5, 2))
        assertEquals(listOf(6, 1, 3, 5, 2), q.dice(1f).map { it.showing() })
        assertApart(q.dice(1f), "shown")
        q.setSides(12)
        assertEquals(5, q.values.size)
        assertTrue(q.values.all { it in 1..12 })
        q.setCount(6)
        assertEquals(6, q.dice(1f).size)
        assertApart(q.dice(1f), "six d12")
        // The rows are centred, read like text from the top (the back, −z), and the reported extent covers every die.
        val dice = q.dice(1f)
        assertTrue(dice.first().position.z < 0f && dice.last().position.z > 0f)
        assertTrue(dice[0].position.x < dice[1].position.x)
        assertEquals(0f, dice.sumOf { it.position.z.toDouble() }.toFloat(), 1e-4f)
        assertTrue(dice.all { abs(it.position.x) + it.shape.radius <= q.halfWidth + 1e-4f })
        assertTrue(dice.all { abs(it.position.z) + it.shape.radius <= q.halfDepth + 1e-4f })
    }

    @Test fun numbersComeUpEvenly() {
        val q = QuickDice(seed = 11, count = 6, sides = 6)
        val counts = IntArray(7)
        repeat(1000) { q.roll().forEach { counts[it]++ } }
        val expected = 6000.0 / 6
        val chi2 = (1..6).sumOf { (counts[it] - expected).let { d -> d * d / expected } }
        assertTrue("counts ${counts.drop(1)}, chi² $chi2", chi2 < 20.5)
    }
}
