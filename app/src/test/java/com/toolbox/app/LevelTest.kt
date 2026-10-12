package com.toolbox.app

import android.view.Surface
import com.toolbox.app.tools.level.Tilt
import com.toolbox.app.tools.level.formatDegrees
import com.toolbox.app.tools.level.levelStatus
import com.toolbox.app.tools.level.tiltOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LevelTest {
    private val g = 9.81f

    @Test fun tiltFromGravity() {
        assertEquals(Tilt(0f, 0f), tiltOf(0f, 0f, g))
        // Right edge raised 10°: gravity's reaction leans toward +x.
        val rad = Math.toRadians(10.0)
        val t = tiltOf((g * sin(rad)).toFloat(), 0f, (g * cos(rad)).toFloat())
        assertEquals(10f, t.roll, 1e-3f)
        assertEquals(0f, t.pitch, 1e-3f)
    }

    @Test fun zeroingSubtractsThePhonesOwnBias() {
        // A phone that reads 1.5° right and 0.8° top-high on a level table.
        val bias = Tilt(1.5f, 0.8f)
        assertFalse(bias.isLevel)
        assertTrue((bias - bias).isLevel)
        // After zeroing, a real 3° tilt to the left reads as 3°.
        val reading = Tilt(-1.5f, 0.8f) - bias
        assertEquals(-3f, reading.roll, 1e-6f)
        assertEquals(0f, reading.pitch, 1e-6f)
    }

    @Test fun theZeroTurnsWithTheScreen() {
        // Tilts are kept in the phone's portrait axes and turned for a landscape screen afterwards.
        assertEquals(Tilt(-5f, 2f), Tilt(2f, 5f).onDisplay(Surface.ROTATION_90))
        assertEquals(Tilt(2f, 5f), Tilt(2f, 5f).onDisplay(Surface.ROTATION_0))
    }

    @Test fun degreesAreNeverNegativeZero() {
        assertEquals("%.1f°".format(0f), formatDegrees(-0.04f))
        assertEquals("%.1f°".format(0f), formatDegrees(-0f))
        assertEquals("%.1f°".format(2.3f), formatDegrees(-2.3f))
    }

    @Test fun statusSaysWhichEdgeIsHigh() {
        assertEquals("Level", levelStatus(Tilt(0.5f, -0.9f)))
        assertEquals("Right edge is high", levelStatus(Tilt(2f, 0.2f)))
        assertEquals("Left edge is high", levelStatus(Tilt(-2f, 0f)))
        assertEquals("Bottom edge is high", levelStatus(Tilt(0f, -5f)))
        assertEquals("Top-left corner is high", levelStatus(Tilt(-2f, 3f)))
        assertEquals("Lay the phone flat to measure", levelStatus(Tilt(0f, 45f)))
    }
}
