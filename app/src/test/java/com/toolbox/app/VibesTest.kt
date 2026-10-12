package com.toolbox.app

import com.toolbox.app.ui.common.BuzzGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibesTest {
    private fun BuzzGate.arm(nowMs: Long, a: Int) = arm(nowMs, a, gapMs = 90, minStep = 8)

    @Test fun buzzWaitsForAPulseToFinish() {
        val gate = BuzzGate()
        gate.pulse(nowMs = 1_000, lengthMs = 40) // the Zip's end-stop thud
        assertFalse(gate.arm(1_016, 120))       // the next frame's buzz would have cut it off
        assertFalse(gate.arm(1_039, 120))
        assertTrue(gate.arm(1_040, 120))
    }

    @Test fun runningBuzzIsResentOnlyAfterTheGapOrABigChange() {
        val gate = BuzzGate()
        assertTrue(gate.arm(0, 100))
        assertFalse(gate.arm(16, 104))
        assertTrue(gate.arm(32, 110))
        assertFalse(gate.arm(48, 110))
        assertTrue(gate.arm(122, 110))
    }

    @Test fun stopBuzzLeavesAPulseAlone() {
        val gate = BuzzGate()
        assertFalse(gate.stopBuzz()) // nothing running, so nothing to cancel
        gate.arm(0, 100)
        gate.pulse(nowMs = 10, lengthMs = 16)
        assertFalse(gate.stopBuzz()) // the pulse replaced the buzz; cancelling now would cut the pulse
        gate.arm(30, 100)
        assertTrue(gate.stopBuzz())
    }

    @Test fun aRepeatingPatternHoldsUntilReset() {
        val gate = BuzzGate()
        gate.pulse(nowMs = 0, lengthMs = Long.MAX_VALUE)
        assertFalse(gate.arm(60_000, 100))
        gate.reset()
        assertTrue(gate.arm(60_016, 100))
    }
}
