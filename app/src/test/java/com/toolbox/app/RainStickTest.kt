package com.toolbox.app

import com.toolbox.app.toys.rainstick.RainStickSim
import com.toolbox.app.toys.rainstick.rainVibration
import com.toolbox.app.toys.rainstick.toTubeFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RainStickTest {
    private val g = 8.8f
    private val frame = 1f / 60f

    private fun RainStickSim.run(seconds: Float, gx: Float, gy: Float): Long {
        val before = totalHits
        repeat((seconds / frame).toInt()) { step(frame, gx, gy) }
        return totalHits - before
    }

    @Test fun grainsStayInsideTheTube() {
        val sim = RainStickSim()
        sim.run(3f, 0f, -g)
        sim.run(1f, g, 0f)
        sim.run(3f, 0f, g)
        for (i in 0 until sim.count) {
            assertTrue(sim.x[i] in 0f..1f)
            assertTrue(sim.y[i] in 0f..RainStickSim.LENGTH)
        }
    }

    @Test fun flippedGrainsPourToTheOtherEndAndRattleOnTheWay() {
        val sim = RainStickSim()
        val hits = sim.run(10f, 0f, -g)
        val arrived = sim.y.count { it < 0.8f }
        assertTrue("only $arrived arrived", arrived > sim.count * 0.95f)
        assertTrue(hits > 1000)
    }

    @Test fun theFlowTakesAFewSeconds() {
        val sim = RainStickSim()
        sim.run(1.5f, 0f, -g)
        assertTrue(sim.y.count { it < 0.8f } == 0) // nothing arrives straight away
    }

    @Test fun aRestingPileIsSilent() {
        val sim = RainStickSim()
        sim.run(5f, 0f, g)
        assertEquals(0L, sim.run(1f, 0f, g))
    }

    @Test fun hitCodesRoundTrip() {
        val code = RainStickSim.TYPE_WALL * 10f + 0.37f
        assertEquals(RainStickSim.TYPE_WALL, RainStickSim.hitType(code))
        assertEquals(0.37f, RainStickSim.hitIntensity(code), 1e-4f)
    }

    @Test fun flippingTheTubeReversesGravity() {
        val (gx, gy) = toTubeFrame(0f, 9.8f, 180f)
        assertEquals(0f, gx, 1e-4f)
        assertEquals(-9.8f, gy, 1e-4f)
        // Tube turned a quarter clockwise: its +x side now faces down the screen.
        val (qx, qy) = toTubeFrame(0f, 9.8f, 90f)
        assertEquals(9.8f, qx, 1e-4f)
        assertEquals(0f, qy, 1e-4f)
    }

    @Test fun vibrationFollowsTheRain() {
        assertEquals(0f, rainVibration(0f), 0f)
        assertTrue(rainVibration(400f) > rainVibration(100f))
        assertTrue(rainVibration(10_000f) <= 1f)
    }
}
