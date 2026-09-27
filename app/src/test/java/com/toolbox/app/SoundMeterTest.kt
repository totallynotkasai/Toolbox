package com.toolbox.app

import com.toolbox.app.tools.soundmeter.FLOOR_DBFS
import com.toolbox.app.tools.soundmeter.LevelStats
import com.toolbox.app.tools.soundmeter.PeakHold
import com.toolbox.app.tools.soundmeter.ReferenceLevel
import com.toolbox.app.tools.soundmeter.Zone
import com.toolbox.app.tools.soundmeter.referenceFor
import com.toolbox.app.tools.soundmeter.rmsDbfs
import com.toolbox.app.tools.soundmeter.zoneFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SoundMeterTest {
    @Test fun fullScaleSineIsMinus3dBfs() {
        val s = ShortArray(44_100) { (32767 * sin(2 * PI * 1000 * it / 44_100)).toInt().toShort() }
        assertEquals(-3.01f, rmsDbfs(s), 0.05f)
    }

    @Test fun halvingAmplitudeLoses6dB() {
        val loud = ShortArray(4410) { (16000 * sin(2 * PI * 440 * it / 44_100)).toInt().toShort() }
        val quiet = ShortArray(4410) { (8000 * sin(2 * PI * 440 * it / 44_100)).toInt().toShort() }
        assertEquals(6.02f, rmsDbfs(loud) - rmsDbfs(quiet), 0.05f)
    }

    @Test fun silenceAndDcOffsetReadAsTheFloor() {
        assertEquals(FLOOR_DBFS, rmsDbfs(ShortArray(100)))
        assertEquals(FLOOR_DBFS, rmsDbfs(ShortArray(100) { 500 }))
        assertEquals(FLOOR_DBFS, rmsDbfs(ShortArray(0)))
    }

    @Test fun leqIsAnEnergyAverage() {
        val stats = LevelStats()
        stats.add(60f); stats.add(80f)
        // Mean power of 1e6 and 1e8 is 5.05e7 → 77.03 dB, far above the plain mean of 70.
        assertEquals(77.03f, stats.leq, 0.01f)
        assertEquals(60f, stats.min)
        assertEquals(80f, stats.max)
        stats.reset()
        assertTrue(stats.leq.isNaN())
        stats.add(-40f)
        assertEquals(-40f, stats.leq, 1e-3f)
    }

    @Test fun peakHoldsThenFalls() {
        val p = PeakHold(holdMs = 1000, fallDbPerSec = 20f)
        assertEquals(-20f, p.update(-20f, 0))
        assertEquals(-20f, p.update(-50f, 500))   // held
        assertEquals(-20f, p.update(-50f, 1000))  // still held
        assertEquals(-30f, p.update(-50f, 1500), 1e-3f) // 0.5 s of falling at 20 dB/s
        assertEquals(-40f, p.update(-50f, 2000), 1e-3f)
        assertEquals(-50f, p.update(-50f, 5000))  // never below the live level
        assertEquals(-10f, p.update(-10f, 5100))  // jumps to a new peak
    }

    @Test fun referencesAndZones() {
        assertNull(referenceFor(25f))
        assertEquals(ReferenceLevel.Whisper, referenceFor(45f))
        assertEquals(ReferenceLevel.Conversation, referenceFor(60f))
        assertEquals(ReferenceLevel.Pain, referenceFor(125f))
        assertEquals(Zone.Quiet, zoneFor(40f))
        assertEquals(Zone.Loud, zoneFor(84.9f))
        assertEquals(Zone.Harmful, zoneFor(85f))
    }
}
