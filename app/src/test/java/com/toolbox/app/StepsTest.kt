package com.toolbox.app

import com.toolbox.app.tools.steps.HISTORY_DAYS
import com.toolbox.app.tools.steps.StepState
import com.toolbox.app.tools.steps.applyReading
import com.toolbox.app.tools.steps.decodeHistory
import com.toolbox.app.tools.steps.distanceMetres
import com.toolbox.app.tools.steps.encodeHistory
import com.toolbox.app.tools.steps.mustSave
import com.toolbox.app.tools.steps.walkingKcal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsTest {
    private val day = 20_000L
    private val hour = 3_600_000L

    /** Feeds (raw, day) readings from a fresh state. */
    private fun run(vararg readings: Pair<Long, Long>): StepState =
        readings.fold(StepState()) { s, (raw, d) -> applyReading(s, raw, d) }

    /** The count shown for day [d]: 0 if nothing is known about it. */
    private fun StepState.steps(d: Long): Long = totalFor(d) ?: 0

    @Test fun firstReadingStartsAtZero() {
        val s = run(5_000L to day)
        assertTrue(s.started)
        assertEquals(0, s.steps(day))
        assertEquals(0, s.lifetime)
    }

    @Test fun countsWithinADay() {
        val s = run(5_000L to day, 5_200L to day, 6_000L to day)
        assertEquals(1_000, s.steps(day))
        assertEquals(1_000, s.lifetime)
    }

    @Test fun dayChangeWritesHistoryAndStartsFromLastReading() {
        // 23:30 reading, then the midnight job at 00:01 sees 40 more steps taken around midnight.
        val s = run(1_000L to day, 4_000L to day, 4_040L to day + 1)
        assertEquals(3_000L, s.history[day])
        assertEquals(40, s.steps(day + 1))
        assertEquals(day + 1, s.day)
        assertEquals(3_040, s.lifetime)
    }

    @Test fun rebootMidDayCarriesStepsForward() {
        // 2,000 steps, reboot (counter back to 0), then 300 more.
        val s = run(10_000L to day, 12_000L to day, 50L to day, 300L to day)
        assertEquals(2_300, s.steps(day))
        assertEquals(2_000, s.carried)
        assertEquals(2_300, s.lifetime)
    }

    @Test fun twoRebootsInOneDay() {
        val s = run(0L to day, 100L to day, 20L to day, 70L to day, 5L to day, 30L to day)
        assertEquals(100 + 70 + 30L, s.steps(day))
    }

    @Test fun rebootAndDayChangeTogether() {
        // Last reading 22:00 yesterday; phone rebooted overnight; first reading today shows 150 since boot.
        val s = run(8_000L to day, 9_500L to day, 150L to day + 1)
        assertEquals(1_500L, s.history[day])
        assertEquals(150, s.steps(day + 1))
        assertEquals(0, s.carried)
    }

    @Test fun carriedStepsFromBeforeARebootLandInHistory() {
        val s = run(0L to day, 1_000L to day, 10L to day, 510L to day, 600L to day + 1)
        assertEquals(1_510L, s.history[day])
        assertEquals(90, s.steps(day + 1))
    }

    @Test fun missedDaysAreNotCreditedToToday() {
        // Last reading Monday 20:00, next one Thursday 09:00: the 2,000 steps in between can't be put on a day.
        val monday = day * 24 * hour
        var s = applyReading(StepState(), 0, day, monday + 8 * hour)
        s = applyReading(s, 700, day, monday + 20 * hour)
        val thursday9 = monday + 3 * 24 * hour + 9 * hour
        s = applyReading(s, 2_700, day + 3, thursday9)
        assertEquals(700L, s.history[day])
        assertNull(s.history[day + 1])
        assertNull(s.history[day + 2])
        assertEquals(0, s.steps(day + 3))
        assertTrue(s.afterGap)
        assertEquals(thursday9, s.countingSince)
        assertEquals(monday + 20 * hour, s.gapSince)
        assertEquals(2_700, s.lifetime) // sessions still count them: they span the gap
        // Later readings that day count from 09:00, and the gap note stays for the day.
        s = applyReading(s, 3_000, day + 3, thursday9 + hour)
        assertEquals(300, s.steps(day + 3))
        assertTrue(s.afterGap)
        // The next day counts from midnight again.
        s = applyReading(s, 3_100, day + 4, thursday9 + 16 * hour)
        assertEquals(300L, s.history[day + 3])
        assertEquals(100, s.steps(day + 4))
        assertFalse(s.afterGap)
        assertEquals(0, s.countingSince)
    }

    @Test fun aOneNightGapStillCountsTowardsToday() {
        // Last reading 20:00 yesterday, next one 09:00 today: overnight steps are mostly this morning's.
        val s = run(0L to day, 700L to day, 2_700L to day + 1)
        assertEquals(2_000, s.steps(day + 1))
        assertFalse(s.afterGap)
    }

    @Test fun gapAfterAReboot() {
        // A reboot during the gap: the counter's 150 steps since boot belong to the gap too.
        val s = run(0L to day, 9_000L to day, 150L to day + 2, 400L to day + 2)
        assertEquals(9_000L, s.history[day])
        assertEquals(250, s.steps(day + 2))
        assertTrue(s.afterGap)
        assertEquals(0L, s.gapSince) // the last reading's time wasn't known
    }

    @Test fun firstReadingNotesWhenCountingBegan() {
        val s = applyReading(StepState(), 5_000, day, 1_234L, boot = 7)
        assertEquals(1_234L, s.countingSince)
        assertFalse(s.afterGap)
        assertEquals(7, s.boot)
    }

    @Test fun aNewBootCountIsAReboot() {
        // 200 steps, then a reboot after which 500 were walked: more than the last reading, so only the boot count
        // shows the counter restarted. Before, 200 of the 500 were credited.
        var s = applyReading(StepState(), 100, day, boot = 4)
        s = applyReading(s, 300, day, boot = 4)
        s = applyReading(s, 500, day, boot = 5)
        assertEquals(700, s.steps(day))
        assertEquals(200, s.carried)
        assertEquals(700, s.lifetime)
        // An unknown boot count (an older version's state, or a phone without one) falls back to the counter.
        s = applyReading(s, 600, day, boot = -1)
        assertEquals(800, s.steps(day))
        s = applyReading(s, 700, day, boot = 6)
        assertEquals(900, s.steps(day))
    }

    @Test fun onlyTheRunningCountWaitsToBeSaved() {
        val s = run(0L to day, 100L to day)
        assertFalse(mustSave(s, applyReading(s, 150, day, 99L, boot = 3)))
        assertTrue(mustSave(StepState(), s))                // first reading
        assertTrue(mustSave(s, applyReading(s, 20, day)))   // reboot
        assertTrue(mustSave(s, applyReading(s, 150, day + 1))) // new day
    }

    @Test fun clockGoingBackwardsIsTheSameDay() {
        val s = run(0L to day, 500L to day, 800L to day - 1)
        assertEquals(day, s.day)
        assertEquals(800, s.steps(day))
        assertTrue(s.history.isEmpty())
    }

    @Test fun staleStateShowsZeroForToday() {
        val s = run(0L to day, 500L to day)
        assertEquals(0, s.steps(day + 1))
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
