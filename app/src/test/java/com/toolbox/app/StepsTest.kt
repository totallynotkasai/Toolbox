package com.toolbox.app

import com.toolbox.app.tools.steps.HISTORY_DAYS
import com.toolbox.app.tools.steps.StepState
import com.toolbox.app.tools.steps.applyReading
import com.toolbox.app.tools.steps.decodeHistory
import com.toolbox.app.tools.steps.distanceMetres
import com.toolbox.app.tools.steps.encodeHistory
import com.toolbox.app.tools.steps.walkingKcal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsTest {
    private val day = 20_000L

    /** Feeds (raw, day) readings from a fresh state. */
    private fun run(vararg readings: Pair<Long, Long>): StepState =
        readings.fold(StepState()) { s, (raw, d) -> applyReading(s, raw, d) }

    @Test fun firstReadingStartsAtZero() {
        val s = run(5_000L to day)
        assertTrue(s.started)
        assertEquals(0, s.stepsOn(day))
        assertEquals(0, s.lifetime)
    }

    @Test fun countsWithinADay() {
        val s = run(5_000L to day, 5_200L to day, 6_000L to day)
        assertEquals(1_000, s.stepsOn(day))
        assertEquals(1_000, s.lifetime)
    }

    @Test fun dayChangeWritesHistoryAndStartsFromLastReading() {
        // 23:30 reading, then the midnight job at 00:01 sees 40 more steps taken around midnight.
        val s = run(1_000L to day, 4_000L to day, 4_040L to day + 1)
        assertEquals(3_000L, s.history[day])
        assertEquals(40, s.stepsOn(day + 1))
        assertEquals(day + 1, s.day)
        assertEquals(3_040, s.lifetime)
    }

    @Test fun rebootMidDayCarriesStepsForward() {
        // 2,000 steps, reboot (counter back to 0), then 300 more.
        val s = run(10_000L to day, 12_000L to day, 50L to day, 300L to day)
        assertEquals(2_300, s.stepsOn(day))
        assertEquals(2_000, s.carried)
        assertEquals(2_300, s.lifetime)
    }

    @Test fun twoRebootsInOneDay() {
        val s = run(0L to day, 100L to day, 20L to day, 70L to day, 5L to day, 30L to day)
        assertEquals(100 + 70 + 30L, s.stepsOn(day))
    }

    @Test fun rebootAndDayChangeTogether() {
        // Last reading 22:00 yesterday; phone rebooted overnight; first reading today shows 150 since boot.
        val s = run(8_000L to day, 9_500L to day, 150L to day + 1)
        assertEquals(1_500L, s.history[day])
        assertEquals(150, s.stepsOn(day + 1))
        assertEquals(0, s.carried)
    }

    @Test fun carriedStepsFromBeforeARebootLandInHistory() {
        val s = run(0L to day, 1_000L to day, 10L to day, 510L to day, 600L to day + 1)
        assertEquals(1_510L, s.history[day])
        assertEquals(90, s.stepsOn(day + 1))
    }

    @Test fun missedMidnightJobSkipsWholeDays() {
        // No readings for two whole days: the gap goes to the day of the next reading, the missed days have no entry.
        val s = run(0L to day, 700L to day, 2_700L to day + 3)
        assertEquals(700L, s.history[day])
        assertNull(s.history[day + 1])
        assertNull(s.history[day + 2])
        assertEquals(2_000, s.stepsOn(day + 3))
    }

    @Test fun clockGoingBackwardsIsTheSameDay() {
        val s = run(0L to day, 500L to day, 800L to day - 1)
        assertEquals(day, s.day)
        assertEquals(800, s.stepsOn(day))
        assertTrue(s.history.isEmpty())
    }

    @Test fun staleStateShowsZeroForToday() {
        val s = run(0L to day, 500L to day)
        assertEquals(0, s.stepsOn(day + 1))
        assertEquals(500L, s.totalFor(day)) // yesterday still reads live until the next reading
        assertNull(s.totalFor(day + 1))
    }

    @Test fun historyIsPruned() {
        var s = run(0L to day)
        for (d in 1..40) s = applyReading(s, d * 100L, day + d)
        assertEquals(HISTORY_DAYS - 1, s.history.size)
        assertTrue(s.history.keys.all { it > day + 40 - HISTORY_DAYS })
    }

    @Test fun historyEncodingRoundTrips() {
        val h = mapOf(20_001L to 1_234L, 20_000L to 0L, 19_990L to 99_999L)
        assertEquals(h, decodeHistory(encodeHistory(h)))
        assertEquals(emptyMap<Long, Long>(), decodeHistory(""))
        assertEquals(mapOf(5L to 6L), decodeHistory("5:6,bad,7:,8:-1,:9"))
    }

    @Test fun distanceAndCalories() {
        assertEquals(7_000.0, distanceMetres(10_000, 70), 1e-9)
        assertEquals(245.0, walkingKcal(7_000.0, 70), 1e-9)
    }
}
