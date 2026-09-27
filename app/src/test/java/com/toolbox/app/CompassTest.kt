package com.toolbox.app

import com.toolbox.app.tools.compass.CardinalTicker
import com.toolbox.app.tools.compass.HeadingFilter
import com.toolbox.app.tools.compass.compassPoint
import com.toolbox.app.tools.compass.headingFromMatrix
import com.toolbox.app.tools.compass.isFieldDisturbed
import com.toolbox.app.tools.compass.roundedDegrees
import com.toolbox.app.tools.compass.shortestDelta
import com.toolbox.app.tools.compass.spokenHeading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CompassTest {
    /** Row-major matrix for a phone yawed [yawDeg] clockwise from north and pitched [pitchDeg] (top edge up). */
    private fun matrix(yawDeg: Double, pitchDeg: Double): FloatArray {
        val y = Math.toRadians(yawDeg); val p = Math.toRadians(pitchDeg)
        // Pitch about the device X axis, then yaw about world up (clockwise seen from above).
        val pitch = arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, cos(p), -sin(p)), doubleArrayOf(0.0, sin(p), cos(p)))
        val yaw = arrayOf(doubleArrayOf(cos(y), sin(y), 0.0), doubleArrayOf(-sin(y), cos(y), 0.0), doubleArrayOf(0.0, 0.0, 1.0))
        return FloatArray(9) { i -> (0..2).sumOf { k -> yaw[i / 3][k] * pitch[k][i % 3] }.toFloat() }
    }

    @Test fun headingLyingFlat() {
        for (yaw in listOf(0.0, 45.0, 90.0, 180.0, 274.0)) {
            assertEquals("yaw $yaw", yaw.toFloat(), headingFromMatrix(matrix(yaw, 0.0))!!, 0.01f)
        }
    }

    @Test fun headingStaysPutThroughTiltToUpright() {
        for (pitch in listOf(0.0, 30.0, 45.0, 60.0, 89.0, 90.0, 110.0)) {
            assertEquals("pitch $pitch", 120f, headingFromMatrix(matrix(120.0, pitch))!!, 0.01f)
        }
    }

    @Test fun degenerateMatrixHasNoHeading() {
        // Device Y and −Z both horizontal and cancelling: impossible in practice, but must not crash or guess.
        val m = floatArrayOf(0f, 0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertNull(headingFromMatrix(m))
        assertNotNull(headingFromMatrix(matrix(10.0, 0.0)))
    }

    @Test fun shortestDeltaWraps() {
        assertEquals(2f, shortestDelta(359f, 1f), 1e-4f)
        assertEquals(-2f, shortestDelta(1f, 359f), 1e-4f)
        assertEquals(180f, shortestDelta(0f, 180f), 1e-4f)
    }

    @Test fun filterDoesNotSwingTheLongWayRound() {
        val f = HeadingFilter(alpha = 0.5f)
        f.update(350f)
        val h = f.update(10f)
        assertTrue("got $h", h > 355f || h < 5f)
        assertEquals(0f, shortestDelta(0f, h), 0.5f)
    }

    @Test fun compassPointsAndRounding() {
        assertEquals("W", compassPoint(274f))
        assertEquals("N", compassPoint(359f))
        assertEquals("NE", compassPoint(30f))
        assertEquals(0, roundedDegrees(359.6f))
        assertEquals("274 degrees, west", spokenHeading(274f))
    }

    @Test fun tickerFiresOncePerCrossing() {
        val t = CardinalTicker(rearmDegrees = 6f)
        assertNull(t.update(355f))
        assertEquals(0, t.update(2f))
        // Jitter across north doesn't tick again until the heading moves well away.
        assertNull(t.update(359f))
        assertNull(t.update(1f))
        assertNull(t.update(20f))
        assertEquals(0, t.update(358f))
        assertEquals(0, t.update(80f)) // 358 → 80 crosses north again; 80° is far enough away to re-arm it
        assertEquals(90, t.update(95f))
    }

    @Test fun tickerIgnoresMovesThatDontCross() {
        val t = CardinalTicker()
        t.update(10f)
        assertNull(t.update(40f))
        assertNull(t.update(80f))
        assertEquals(90, t.update(91f))
        assertEquals(180, t.update(185f))
    }

    @Test fun fieldDisturbance() {
        assertFalse(isFieldDisturbed(48f))
        assertTrue(isFieldDisturbed(150f))
        assertTrue(isFieldDisturbed(5f))
    }
}
