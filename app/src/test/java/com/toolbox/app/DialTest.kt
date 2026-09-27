package com.toolbox.app

import com.toolbox.app.toys.dial.detentAfter
import com.toolbox.app.toys.dial.wrapDelta
import org.junit.Assert.assertEquals
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
}
