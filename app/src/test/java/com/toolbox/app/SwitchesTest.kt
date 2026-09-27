package com.toolbox.app

import com.toolbox.app.toys.switches.DETENTS
import com.toolbox.app.toys.switches.magnetPos
import com.toolbox.app.toys.switches.nearestDetent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchesTest {
    @Test fun detentsCoverTheEnds() {
        assertEquals(0, nearestDetent(0f))
        assertEquals(DETENTS - 1, nearestDetent(1f))
        assertEquals(0, nearestDetent(-0.3f))
        assertEquals(DETENTS - 1, nearestDetent(1.4f))
    }

    @Test fun positionRoundsToTheNearestNotch() {
        val step = 1f / (DETENTS - 1)
        assertEquals(3, nearestDetent(step * 3 + step * 0.4f))
        assertEquals(4, nearestDetent(step * 3 + step * 0.6f))
    }

    @Test fun magnetPullsTowardTheNotchWithoutPassingIt() {
        val step = 1f / (DETENTS - 1)
        val pos = step * 2 + step * 0.3f
        val shown = magnetPos(pos)
        assertTrue(shown < pos && shown > step * 2)
        assertEquals(step * 5, magnetPos(step * 5), 1e-6f)
    }
}
