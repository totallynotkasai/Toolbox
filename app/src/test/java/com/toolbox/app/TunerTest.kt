package com.toolbox.app

import com.toolbox.app.tools.tuner.PitchDetector
import com.toolbox.app.tools.tuner.centsFrom
import com.toolbox.app.tools.tuner.nearestNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PitchDetectorTest {
    private fun tone(freq: Double, harmonics: Boolean = false) = FloatArray(4096) { i ->
        val t = i / 44_100.0
        var v = sin(2 * PI * freq * t)
        // A plucked string has strong overtones; YIN must still report the fundamental.
        if (harmonics) v += 0.6 * sin(2 * PI * 2 * freq * t) + 0.4 * sin(2 * PI * 3 * freq * t)
        (0.3 * v).toFloat()
    }

    @Test fun detectsA4() {
        val f = PitchDetector(44_100, 4096).detect(tone(440.0))!!
        assertEquals(440f, f, 1f)
        assertEquals("A4", nearestNote(f).toString())
    }

    @Test fun detectsLowEStringWithOvertones() {
        val f = PitchDetector(44_100, 4096).detect(tone(82.41, harmonics = true))!!
        assertEquals(82.41f, f, 0.5f)
        assertEquals("E2", nearestNote(f).toString())
    }

    @Test fun reportsCentsOffset() {
        // 10 cents sharp of A4.
        val sharp = 440.0 * Math.pow(2.0, 10 / 1200.0)
        val f = PitchDetector(44_100, 4096).detect(tone(sharp))!!
        assert(abs(centsFrom(f, nearestNote(f)) - 10f) < 2f)
    }

    @Test fun detectsQuietNoisySingleString() {
        // A lone A string (110 Hz) at roughly -50 dBFS with room noise, the case the old gate missed.
        val rnd = java.util.Random(1)
        val buf = FloatArray(4096) { i ->
            val t = i / 44_100.0
            val v = sin(2 * PI * 110 * t) + 0.5 * sin(2 * PI * 220 * t) + 0.3 * sin(2 * PI * 330 * t)
            (0.004 * v + 0.0015 * rnd.nextGaussian()).toFloat()
        }
        val f = PitchDetector(44_100, 4096).detect(buf)!!
        assertEquals("A2", nearestNote(f).toString())
        assertEquals(110f, f, 1f)
    }

    @Test fun detectsEveryGuitarStringQuietAndNoisy() {
        val rnd = java.util.Random(7)
        mapOf("E2" to 82.41, "A2" to 110.0, "D3" to 146.83, "G3" to 196.0, "B3" to 246.94, "E4" to 329.63).forEach { (name, hz) ->
            repeat(5) { trial ->
                val phase = trial * 0.7
                val buf = FloatArray(4096) { i ->
                    val t = i / 44_100.0
                    val v = sin(2 * PI * hz * t + phase) + 0.6 * sin(2 * PI * 2 * hz * t) + 0.3 * sin(2 * PI * 3 * hz * t)
                    (0.004 * v + 0.0015 * rnd.nextGaussian()).toFloat()
                }
                val f = PitchDetector(44_100, 4096).detect(buf)
                assertEquals("$name trial $trial read ${f}Hz", name, f?.let { nearestNote(it).toString() })
            }
        }
    }

    @Test fun silenceIsNull() {
        assertNull(PitchDetector(44_100, 4096).detect(FloatArray(4096)))
    }
}
