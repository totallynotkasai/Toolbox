package com.toolbox.app

import com.toolbox.app.tools.fidget.addSpin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FidgetTest {
    @Test fun gentleFlickIsBarelyDamped() {
        assertEquals(500f, addSpin(0f, 500f), 40f)
    }

    @Test fun oneHardFlickFallsShortOfTheTop() {
        assertTrue(addSpin(0f, 7200f) < 5000f)
    }

    @Test fun repeatedFlicksStackUpToTheCap() {
        var omega = 0f
        repeat(4) { omega = addSpin(omega, 7200f) }
        assertEquals(7200f, omega, 0.01f)
    }

    @Test fun stackingNeverSlowsTheSpinner() {
        var omega = 0f
        repeat(20) {
            val next = addSpin(omega, 300f)
            assertTrue(next >= omega)
            omega = next
        }
    }
}
