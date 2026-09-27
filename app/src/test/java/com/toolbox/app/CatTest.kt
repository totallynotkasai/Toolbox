package com.toolbox.app

import com.toolbox.app.toys.cat.CatMood
import com.toolbox.app.toys.cat.purrEnvelope
import com.toolbox.app.toys.cat.purrVibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatTest {
    private fun strokeRight(mood: CatMood, strokes: Int) {
        repeat(strokes) {
            mood.startStroke()
            repeat(20) { mood.stroke(0.025f) } // half a cat-width, head to tail
        }
    }

    @Test fun strokingHeadToTailBuildsThePurr() {
        val mood = CatMood()
        strokeRight(mood, 1)
        val one = mood.purr
        assertTrue(one > 0.1f)
        strokeRight(mood, 10)
        assertTrue(mood.purr > one)
        assertTrue(mood.purr <= 1f)
    }

    @Test fun strokingTheWrongWayGrumbles() {
        val mood = CatMood()
        strokeRight(mood, 6)
        val before = mood.purr
        mood.startStroke()
        var grumbled = false
        repeat(10) { if (mood.stroke(-0.025f)) grumbled = true }
        assertTrue(grumbled)
        assertEquals(1f, mood.grumble, 0.001f)
        assertTrue(mood.purr < before * 0.5f)
    }

    @Test fun aSmallWiggleBackIsForgiven() {
        val mood = CatMood()
        mood.startStroke()
        repeat(10) { mood.stroke(0.025f) }
        assertFalse(mood.stroke(-0.05f))
        assertEquals(0f, mood.grumble, 0f)
    }

    @Test fun grumblesOncePerStroke() {
        val mood = CatMood()
        mood.startStroke()
        var count = 0
        repeat(40) { if (mood.stroke(-0.025f)) count++ }
        assertEquals(1, count)
    }

    @Test fun purrFadesSlowlyOnItsOwn() {
        val mood = CatMood()
        strokeRight(mood, 20)
        val full = mood.purr
        repeat(60) { mood.update(1f / 60f) }
        assertTrue(mood.purr > full * 0.9f) // still purring a second later
        repeat(60 * 60) { mood.update(1f / 60f) }
        assertEquals(0f, mood.purr, 0f)
    }

    @Test fun breathingEnvelopeDipsAtTheTurnAndSwellsMidBreath() {
        for (i in 0..100) {
            val e = purrEnvelope(i / 100f)
            assertTrue(e in 0.25f..1f)
        }
        assertEquals(0.25f, purrEnvelope(0f), 0.01f)
        assertEquals(0.25f, purrEnvelope(0.5f), 0.01f)
        assertTrue(purrEnvelope(0.75f) > purrEnvelope(0.25f)) // out-breath is fuller
    }

    @Test fun noPurrNoVibration() {
        assertEquals(0f, purrVibration(0f, 1f), 0f)
        assertTrue(purrVibration(1f, 1f) > purrVibration(0.5f, 1f))
        assertTrue(purrVibration(1f, 1f) <= 1f)
    }
}
