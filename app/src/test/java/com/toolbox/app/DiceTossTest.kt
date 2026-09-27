package com.toolbox.app

import com.toolbox.app.tools.dicetoss.DicePhysics
import com.toolbox.app.tools.dicetoss.DieShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DiceTossTest {
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
