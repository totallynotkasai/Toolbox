package com.toolbox.app

import com.toolbox.app.tools.compass.DeclinationSource
import com.toolbox.app.tools.compass.applyDeclination
import com.toolbox.app.tools.compass.chooseDeclination
import com.toolbox.app.tools.compass.declinationFieldText
import com.toolbox.app.tools.compass.declinationNeedsRefresh
import com.toolbox.app.tools.compass.formatDeclination
import com.toolbox.app.tools.compass.normalizeDegrees
import com.toolbox.app.tools.compass.parseDeclination
import com.toolbox.app.tools.compass.roundedDegrees
import com.toolbox.app.tools.compass.spokenDeclination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclinationTest {
    @Test fun eastDeclinationAddsAndWraps() {
        assertEquals(101.2f, applyDeclination(100f, 1.2f), 1e-4f)
        assertEquals(1f, applyDeclination(359f, 2f), 1e-4f)
        assertEquals(0f, applyDeclination(350f, 10f), 1e-4f)
    }

    @Test fun westDeclinationSubtractsAndWraps() {
        assertEquals(98.8f, applyDeclination(100f, -1.2f), 1e-4f)
        assertEquals(358f, applyDeclination(1f, -3f), 1e-4f)
        assertEquals(190f, applyDeclination(10f, -180f), 1e-4f)
    }

    @Test fun markedBearingKeepsItsDirection() {
        // Marked at 101° true with 1.2° E, saved as magnetic, then shown again either way.
        val saved = normalizeDegrees(101 - 1.2f)
        assertEquals(101, roundedDegrees(applyDeclination(saved, 1.2f)))
        assertEquals(100, roundedDegrees(saved))
        // Across north the other way: 0° true with 3° W is 3° magnetic.
        assertEquals(3f, normalizeDegrees(0 - -3f), 1e-4f)
    }

    @Test fun formatsEastAndWest() {
        assertEquals("1.2° E", formatDeclination(1.2f))
        assertEquals("1.2° W", formatDeclination(-1.2f))
        assertEquals("3.0° W", formatDeclination(-2.96f))
        assertEquals("0.0°", formatDeclination(0.04f))
        assertEquals("0.0°", formatDeclination(-0.04f))
        assertEquals("12.5° E", formatDeclination(12.46f))
    }

    @Test fun speaksEastAndWest() {
        assertEquals("1.2 degrees east", spokenDeclination(1.2f))
        assertEquals("3 degrees west", spokenDeclination(-3f))
        assertEquals("0 degrees", spokenDeclination(0.01f))
    }

    @Test fun parsesTypedDeclination() {
        assertEquals(1.5f, parseDeclination("1.5", east = true)!!, 1e-6f)
        assertEquals(-1.5f, parseDeclination(" 1,5 ", east = false)!!, 1e-6f)
        assertEquals(0f, parseDeclination("0", east = false)!!, 1e-6f)
        assertEquals(180f, parseDeclination("180", east = true)!!, 1e-6f)
        assertNull(parseDeclination("", east = true))
        assertNull(parseDeclination("180.1", east = true))
        assertNull(parseDeclination("-2", east = true))
        assertNull(parseDeclination("1.2.3", east = true))
        assertNull(parseDeclination(".", east = true))
    }

    @Test fun fieldTextRoundTrips() {
        assertEquals("1.25" to false, declinationFieldText(-1.25f))
        assertEquals("3" to true, declinationFieldText(3f))
        for (d in listOf(-12.3f, 0.5f, 4f, -179.9f)) {
            val (text, east) = declinationFieldText(d)
            assertEquals(d, parseDeclination(text, east)!!, 1e-6f)
        }
    }

    @Test fun refreshesAtMostOnceADay() {
        assertTrue(declinationNeedsRefresh(savedDay = null, today = 20_000))
        assertFalse(declinationNeedsRefresh(savedDay = 20_000, today = 20_000))
        assertTrue(declinationNeedsRefresh(savedDay = 19_999, today = 20_000))
        assertTrue("clock moved back", declinationNeedsRefresh(savedDay = 20_001, today = 20_000))
    }

    @Test fun locationWinsWhileAllowedOtherwiseManual() {
        assertEquals(1.2f to DeclinationSource.Location, chooseDeclination(true, 1.2f, -4f))
        assertEquals(-4f to DeclinationSource.Manual, chooseDeclination(false, 1.2f, -4f))
        assertEquals(-4f to DeclinationSource.Manual, chooseDeclination(true, null, -4f))
        assertNull(chooseDeclination(false, 1.2f, null))
        assertNull(chooseDeclination(true, null, null))
    }
}
