package com.toolbox.app

import com.toolbox.app.ui.common.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {
    /** Feeds one sample every 20 ms (SENSOR_DELAY_GAME) and returns every strength reported. */
    private fun ShakeDetector.feed(vararg magnitudes: Float, startMs: Long = 0): List<Float> =
        magnitudes.toList().mapIndexedNotNull { i, m -> onSample(m, startMs + i * 20L) }

    @Test fun ignoresGentleMovement() {
        assertEquals(emptyList<Float>(), ShakeDetector().feed(1f, 4f, 8f, 11.9f, 6f, 2f))
    }

    @Test fun reportsPeakOfOneShake() {
        // Crosses 12, peaks at 25, then drops back: one shake, reported once it's over.
        assertEquals(listOf(25f), ShakeDetector().feed(3f, 14f, 25f, 18f, 5f, 2f))
    }

    @Test fun reportsWhileStillAboveThresholdAfterPeakWindow() {
        // A long hard shake shouldn't wait for the phone to stop moving.
        val fired = ShakeDetector(peakWindowMs = 120).feed(15f, 20f, 30f, 28f, 26f, 24f, 22f, 20f, 18f)
        assertEquals(listOf(30f), fired)
    }

    @Test fun cooldownSuppressesRapidRepeats() {
        val d = ShakeDetector(cooldownMs = 800)
        assertEquals(listOf(20f), d.feed(20f, 5f))
        assertNull(d.onSample(20f, 300))       // too soon
        assertNull(d.onSample(5f, 320))
        assertNull(d.onSample(20f, 900))       // cooldown over: starts a new shake...
        assertEquals(20f, d.onSample(5f, 920)) // ...reported when it ends
    }
}

class SearchTest {
    private val fields = listOf("Sound meter", "Measure", "decibel", "noise")

    @Test fun blankMatchesEverything() {
        assertTrue(matchesSearch("", fields))
        assertTrue(matchesSearch("   ", fields))
    }

    @Test fun matchesTitleCategoryAndKeywordsIgnoringCase() {
        assertTrue(matchesSearch("sound", fields))
        assertTrue(matchesSearch("MEASURE", fields))
        assertTrue(matchesSearch(" decibel ", fields))
    }

    @Test fun rejectsUnrelated() {
        assertFalse(matchesSearch("north", fields))
    }
}
