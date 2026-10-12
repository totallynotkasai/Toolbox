package com.toolbox.app

import androidx.compose.ui.geometry.Offset
import com.toolbox.app.toys.fidget.SpinnerShape
import com.toolbox.app.toys.fidget.addSpin
import com.toolbox.app.toys.fidget.blurFor
import com.toolbox.app.toys.fidget.brakeFor
import com.toolbox.app.toys.fidget.spinDown
import com.toolbox.app.ui.common.polar
import org.junit.Assert.assertFalse
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

    private fun secondsToStop(start: Float, brake: Float): Float {
        var omega = start
        var t = 0f
        while (omega != 0f && t < 60f) {
            omega = spinDown(omega, 1f / 60f, brake)
            t += 1f / 60f
        }
        return t
    }

    @Test fun aFreeSpinRunsDownSlowlyAndAFingerStopsItInAQuarterSecond() {
        assertTrue(secondsToStop(7200f, 0f) > 10f)
        assertEquals(0.25f, secondsToStop(7200f, brakeFor(7200f)), 0.05f)
        assertTrue(secondsToStop(300f, brakeFor(300f)) <= 0.27f)
        assertEquals(0f, spinDown(-3f, 1f / 60f), 0f) // a crawl just stops
    }

    @Test fun theSpinnerBlursOnlyWhenItTurnsTooFarAFrameToFollow() {
        assertEquals(0f, blurFor(5f), 0f)
        assertEquals(1f, blurFor(30f), 0f)
        assertEquals(1f, blurFor(-30f), 0f)
        assertTrue(blurFor(17f) in 0.3f..0.7f)
    }

    @Test fun theBodyHasLobesAHubAndPinchedSides() {
        for (i in 0 until 3) assertTrue(SpinnerShape.contains(SpinnerShape.lobe(i)))
        assertTrue(SpinnerShape.contains(Offset.Zero))
        // Between two lobes the concave curve pinches it to the waist.
        assertTrue(SpinnerShape.contains(polar(Offset.Zero, SpinnerShape.WAIST - 0.01f, 60f)))
        assertFalse(SpinnerShape.contains(polar(Offset.Zero, SpinnerShape.WAIST + 0.01f, 60f)))
        // The lobe tips reach ARM + LOBE out, and no further.
        assertTrue(SpinnerShape.contains(polar(Offset.Zero, SpinnerShape.ARM + SpinnerShape.LOBE - 0.01f, 120f)))
        assertFalse(SpinnerShape.contains(polar(Offset.Zero, SpinnerShape.ARM + SpinnerShape.LOBE + 0.01f, 120f)))
    }

    @Test fun theBlurDiscIsSolidNearTheHubAndThinsTowardsTheTips() {
        val cover = FloatArray(SpinnerShape.RINGS) { SpinnerShape.bodyCover[it] + SpinnerShape.steelCover[it] }
        assertTrue(cover.all { it in 0f..1f })
        assertEquals(1f, cover.first(), 0.01f)
        assertTrue(cover.last() < 0.2f)
        assertTrue(SpinnerShape.steelCover.any { it > 0f })
    }
}
