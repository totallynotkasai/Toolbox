package com.toolbox.app

import com.toolbox.app.ui.common.FrameClock
import org.junit.Assert.assertEquals
import org.junit.Test

class GameLoopTest {
    private val ms = 1_000_000L

    @Test fun firstFrameIsAZeroStep() {
        val clock = FrameClock(0.05f)
        assertEquals(0f, clock.step(5_000 * ms), 0f)
        assertEquals(0.016f, clock.step(5_016 * ms), 1e-6f)
    }

    @Test fun longFramesAreCapped() {
        val clock = FrameClock(0.05f)
        clock.step(0)
        assertEquals(0.05f, clock.step(400 * ms), 0f)
    }

    @Test fun resetStartsAgainFromZero() {
        val clock = FrameClock(0.05f)
        clock.step(0)
        clock.step(16 * ms)
        clock.reset()
        // After a pause the gap is ignored, not just capped.
        assertEquals(0f, clock.step(60_000 * ms), 0f)
        assertEquals(0.02f, clock.step(60_020 * ms), 1e-6f)
    }

    @Test fun clockGoingBackwardsIsNeverANegativeStep() {
        val clock = FrameClock(0.05f)
        clock.step(100 * ms)
        assertEquals(0f, clock.step(90 * ms), 0f)
    }
}
