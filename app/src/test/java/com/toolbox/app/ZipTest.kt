package com.toolbox.app

import com.toolbox.app.toys.zip.ZIP_TEETH
import com.toolbox.app.toys.zip.boltDelta
import com.toolbox.app.toys.zip.pawlNotch
import com.toolbox.app.toys.zip.teethPassed
import com.toolbox.app.toys.zip.zipSpread
import com.toolbox.app.toys.zip.zipTeeth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipTest {
    @Test fun sliderPassesEveryToothOnBothSides() {
        assertEquals(0, teethPassed(0f))
        assertEquals(ZIP_TEETH * 2, teethPassed(1f))
        assertEquals(ZIP_TEETH * 2, teethPassed(1.5f))
        assertEquals(40, teethPassed(1f, teeth = 20))
    }

    @Test fun teethKeepARoughlyEvenSizeWhateverTheZipsHeight() {
        assertEquals(ZIP_TEETH, zipTeeth(484f))  // a phone in portrait
        assertEquals(16, zipTeeth(110f))         // a short zip keeps teeth big enough to see
        assertEquals(64, zipTeeth(2000f))        // and a tall one doesn't get hundreds
        assertTrue(zipTeeth(700f) in 60..64)
    }

    @Test fun zipIsShutBelowTheSliderAndSpreadsAbove() {
        assertEquals(0f, zipSpread(0f, 100f), 0.001f)
        assertEquals(0f, zipSpread(-50f, 100f), 0.001f)
        assertTrue(zipSpread(40f, 100f) < zipSpread(200f, 100f))
        assertTrue(zipSpread(10_000f, 100f) <= 100f)
    }

    @Test fun clickingDirectionLeavesTheBoltAlone() {
        assertEquals(0f, boltDelta(5f, clicksClockwise = true), 0f)
        assertEquals(-5f, boltDelta(-5f, clicksClockwise = true), 0f)
        assertEquals(5f, boltDelta(5f, clicksClockwise = false), 0f)
        assertEquals(0f, boltDelta(-5f, clicksClockwise = false), 0f)
    }

    @Test fun onlySlippingClicks() {
        // Slipping clockwise by 45° passes three teeth; driving back leaves the pawl where it was.
        var handle = 0f
        var bolt = 0f
        val start = pawlNotch(handle, bolt)
        handle += 45f
        bolt += boltDelta(45f, clicksClockwise = true)
        assertEquals(3, pawlNotch(handle, bolt) - start)
        val mid = pawlNotch(handle, bolt)
        handle -= 90f
        bolt += boltDelta(-90f, clicksClockwise = true)
        assertEquals(mid, pawlNotch(handle, bolt))
    }
}
