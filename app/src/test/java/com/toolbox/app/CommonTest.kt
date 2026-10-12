package com.toolbox.app

import com.toolbox.app.ui.common.AccelShakeDetector
import com.toolbox.app.ui.common.PermissionRules
import com.toolbox.app.ui.common.PermissionStatus
import com.toolbox.app.ui.common.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

class AccelShakeDetectorTest {
    private val g = 9.80665

    /** Feeds raw readings, one every 20 ms (SENSOR_DELAY_GAME), and returns every strength reported. */
    private fun AccelShakeDetector.feed(readings: List<DoubleArray>): List<Float> = readings.mapIndexedNotNull { i, a ->
        onSample(a[0].toFloat(), a[1].toFloat(), a[2].toFloat(), i * 20L)
    }

    private fun still(ms: Int, x: Double = 0.0, y: Double = 0.0, z: Double = g) = List(ms / 20) { doubleArrayOf(x, y, z) }

    /**
     * Turning a phone lying face up over onto its face in [ms], about its y axis, with the sensor [offset] metres off
     * the turning axis (so it feels the turn's centripetal and tangential pull) and a [lift] in m/s² on the way.
     */
    private fun turnOver(ms: Int, offset: Double, lift: Double): List<DoubleArray> {
        val out = still(400).toMutableList()
        var lastSpeed = 0.0
        for (i in 0 until ms / 20) {
            val u = i * 20.0 / ms
            val angle = PI * u * u * (3 - 2 * u)            // Eases in and out.
            val speed = PI / (ms / 1000.0) * 6 * u * (1 - u) // rad/s
            val turnAccel = (speed - lastSpeed) / 0.02
            lastSpeed = speed
            out += doubleArrayOf(
                g * sin(angle) - speed * speed * offset,
                0.0,
                g * cos(angle) + turnAccel * offset + lift * sin(PI * u) * cos(angle),
            )
        }
        return out + still(600, z = -g)
    }

    /** Shaking side to side at [hz] with a peak of [strength] m/s², held upright (gravity on y) or lying flat (on z). */
    private fun shake(strength: Double, hz: Double, upright: Boolean): List<DoubleArray> {
        val rest = if (upright) still(400, y = g) else still(400)
        val moves = List(35) { i ->
            val a = strength * sin(2 * PI * hz * i * 0.02)
            if (upright) doubleArrayOf(a, g, 0.0) else doubleArrayOf(a, 0.0, g)
        }
        return rest + moves
    }

    @Test fun turningThePhoneOverIsNotAShake() {
        for (ms in listOf(150, 200, 300, 500)) for (offset in listOf(0.02, 0.05)) for (lift in listOf(0.0, 3.0, 6.0)) {
            assertEquals("turned over in $ms ms", emptyList<Float>(), AccelShakeDetector().feed(turnOver(ms, offset, lift)))
        }
    }

    @Test fun puttingThePhoneDownIsNotAShake() {
        val jolt = still(400) + List(3) { doubleArrayOf(0.0, 0.0, g + 15) } + still(400)
        assertEquals(emptyList<Float>(), AccelShakeDetector().feed(jolt))
    }

    @Test fun shakesFireOnceEach() {
        for (strength in listOf(12.0, 15.0, 20.0, 30.0)) for (hz in listOf(2.0, 3.0, 4.0, 5.0)) for (upright in listOf(true, false)) {
            val fired = AccelShakeDetector().feed(shake(strength, hz, upright))
            assertEquals("$strength m/s² at $hz Hz, upright $upright", 1, fired.size)
        }
    }

    @Test fun reportsAboutTheShakesStrength() {
        val weak = AccelShakeDetector().feed(shake(15.0, 4.0, upright = true)).single()
        val hard = AccelShakeDetector().feed(shake(30.0, 4.0, upright = true)).single()
        assertTrue("weak $weak", weak in 10f..20f)
        assertTrue("hard $hard", hard in 22f..40f)
    }

    @Test fun keepsShakingFiresAgainAfterTheCooldown() {
        val long = List(75) { i -> doubleArrayOf(20 * sin(2 * PI * 4 * i * 0.02), 0.0, g) } // One and a half seconds.
        assertEquals(2, AccelShakeDetector(cooldownMs = 800).feed(still(400) + long).size)
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

class PermissionRulesTest {
    /** Plays a series of Android readings (granted, rationale) through the rules, as the screen would. */
    private fun play(vararg readings: Pair<Boolean, Boolean>): List<PermissionStatus> {
        var seen = false
        return readings.map { (granted, rationale) ->
            seen = PermissionRules.rationaleSeen(granted, rationale, seen)
            PermissionRules.status(granted, rationale, seen)
        }
    }

    @Test fun neverAskedCanAsk() {
        assertEquals(PermissionStatus(granted = false, blocked = false), play(false to false).single())
    }

    @Test fun dialogDismissedWithoutAChoiceCanStillAsk() {
        // Comes back denied with no rationale, just like a block, but Android would show the dialog again.
        assertFalse(play(false to false, false to false).last().blocked)
    }

    @Test fun deniedOnceCanAskAgain() {
        assertFalse(play(false to false, false to true).last().blocked)
    }

    @Test fun deniedTwiceIsBlocked() {
        assertTrue(play(false to false, false to true, false to false).last().blocked)
    }

    @Test fun grantedIsNeverBlocked() {
        assertEquals(PermissionStatus(granted = true, blocked = false), play(false to true, true to false).last())
    }

    @Test fun grantingStartsAfresh() {
        // Denied once, then granted, then taken away again in the settings: nothing has been refused since the grant.
        assertFalse(play(false to true, true to false, false to false).last().blocked)
    }
}
