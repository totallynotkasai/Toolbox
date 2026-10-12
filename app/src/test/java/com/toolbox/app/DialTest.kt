package com.toolbox.app

import androidx.compose.ui.geometry.Offset
import com.toolbox.app.toys.dial.detentAfter
import com.toolbox.app.toys.dial.knurlContrast
import com.toolbox.app.ui.common.fixed
import com.toolbox.app.ui.common.turnDelta
import com.toolbox.app.ui.common.wrapDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialTest {
    @Test fun wrapTakesTheShortWayRound() {
        assertEquals(10f, wrapDelta(10f), 0.001f)
        assertEquals(-10f, wrapDelta(350f), 0.001f)
        assertEquals(10f, wrapDelta(-350f), 0.001f)
    }

    @Test fun detentHoldsUntilPulledPastCapture() {
        assertEquals(0, detentAfter(18f, 0, 30f, 19f))
        assertEquals(1, detentAfter(20f, 0, 30f, 19f))
        assertEquals(-1, detentAfter(-20f, 0, 30f, 19f))
    }

    @Test fun detentHasHysteresis() {
        // Just past the halfway point it stays in whichever notch it was already in.
        assertEquals(0, detentAfter(16f, 0, 30f, 19f))
        assertEquals(1, detentAfter(14f, 1, 30f, 19f))
    }

    @Test fun fastSwingCanSkipSeveralNotches() {
        assertEquals(3, detentAfter(92f, 0, 30f, 19f))
    }

    private val c = Offset(100f, 100f)

    @Test fun aDragRoundTheKnobTurnsIt() {
        // From 12 o'clock to 3 o'clock is a quarter turn, but one step is capped at 45°.
        assertEquals(45f, turnDelta(Offset(100f, 20f), Offset(180f, 100f), c, hub = 16f), 0.01f)
        assertEquals(-10f, turnDelta(Offset(100f, 20f), Offset(100f, 20f).rotated(-10f), c, hub = 16f), 0.01f)
    }

    @Test fun aFlickThroughTheHubDoesNotSpinIt() {
        // Either end inside the hub counts for nothing, rather than half a turn in a random direction.
        assertEquals(0f, turnDelta(Offset(100f, 40f), Offset(102f, 105f), c, hub = 16f), 0f)
        assertEquals(0f, turnDelta(Offset(101f, 98f), Offset(100f, 160f), c, hub = 16f), 0f)
        // Straight across, both ends outside: capped, not 180°.
        assertEquals(45f, kotlin.math.abs(turnDelta(Offset(100f, 70f), Offset(101f, 130f), c, hub = 16f)), 0.01f)
    }

    @Test fun theKnurlingBlursOnlyAtSpeed() {
        assertEquals(1f, knurlContrast(0.5f), 0f)
        assertEquals(0f, knurlContrast(-4f), 0f)
        assertTrue(knurlContrast(2f) in 0.01f..0.99f)
    }

    @Test fun fixedNeverShowsMinusZero() {
        assertEquals("0.00", fixed(-0.004f, 2))
        assertEquals("-0.01", fixed(-0.006f, 2))
        assertEquals("1.25", fixed(1.25f, 2))
    }

    private fun Offset.rotated(deg: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        val d = this - c
        return c + Offset((d.x * kotlin.math.cos(a) - d.y * kotlin.math.sin(a)).toFloat(), (d.x * kotlin.math.sin(a) + d.y * kotlin.math.cos(a)).toFloat())
    }
}
