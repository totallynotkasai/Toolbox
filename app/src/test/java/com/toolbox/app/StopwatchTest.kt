package com.toolbox.app

import com.toolbox.app.tools.stopwatch.LapRow
import com.toolbox.app.tools.stopwatch.StopwatchState
import com.toolbox.app.tools.stopwatch.formatStopwatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Stopwatch's stored state. `now` is elapsed-realtime milliseconds and `wall` the wall clock, which move together
 * unless the phone restarts.
 */
class StopwatchTest {
    private val fresh = StopwatchState(boot = 7)

    @Test fun countsFromItsStart() {
        val running = fresh.start(now = 1_000, wall = 50_000)
        assertEquals(0, running.elapsed(now = 1_000))
        assertEquals(65_230, running.elapsed(now = 66_230))
        assertEquals(running, running.start(now = 9_000, wall = 58_000)) // Already running: nothing changes.
    }

    @Test fun pausingBanksTheTimeAndResumingAddsToIt() {
        val paused = fresh.start(now = 0, wall = 0).pause(now = 10_000)
        assertFalse(paused.running)
        assertEquals(10_000, paused.elapsed(now = 999_999)) // Paused time doesn't move.
        val resumed = paused.start(now = 100_000, wall = 100_000)
        assertEquals(15_000, resumed.elapsed(now = 105_000))
        assertEquals(17_000, resumed.pause(now = 107_000).pause(now = 200_000).elapsed(now = 300_000))
    }

    @Test fun timeAwayFromTheAppIsCounted() {
        // Stored, the process killed, and read back 2 minutes later on the same boot: nothing is added on.
        val stored = fresh.start(now = 1_000, wall = 1_000).encode()
        val back = StopwatchState.decode(stored).afterBoot(currentBoot = 7, now = 121_000, wall = 121_000)
        assertTrue(back.running)
        assertEquals(120_000, back.elapsed(now = 121_000))
    }

    @Test fun afterARebootTheWallClockCarriesOn() {
        // Started at wall 1,000,000 with 5 s banked; the phone restarts and the elapsed clock starts again from 0.
        val before = StopwatchState(running = true, bankedMs = 5_000, startedAt = 900_000, startedWall = 1_000_000, boot = 7)
        val after = before.afterBoot(currentBoot = 8, now = 20_000, wall = 1_600_000)
        assertEquals(8, after.boot)
        assertEquals(605_000, after.elapsed(now = 20_000)) // 5 s + 10 min by the wall clock, phone off included.
        assertEquals(606_000, after.elapsed(now = 21_000)) // Then on the new boot's clock.
        assertEquals(after, after.afterBoot(currentBoot = 8, now = 30_000, wall = 1_610_000))
    }

    @Test fun aRebootKeepsAPausedTime() {
        val paused = StopwatchState(bankedMs = 42_000, startedAt = 900_000, startedWall = 1_000_000, boot = 7)
        val after = paused.afterBoot(currentBoot = 8, now = 10, wall = 9_000_000)
        assertEquals(42_000, after.elapsed(now = 10))
        assertEquals(8, after.boot)
    }

    @Test fun aWallClockSetBackwardsAcrossARebootAddsNothing() {
        val before = StopwatchState(running = true, bankedMs = 5_000, startedAt = 900_000, startedWall = 1_000_000, boot = 7)
        assertEquals(5_000, before.afterBoot(currentBoot = 8, now = 0, wall = 400_000).elapsed(now = 0))
    }

    @Test fun lapsAreNumberedWithTheirOwnTimes() {
        val s = fresh.start(now = 0, wall = 0).lap(now = 30_000).lap(now = 75_000).lap(now = 80_000)
        assertEquals(
            listOf(LapRow(3, 5_000, 80_000), LapRow(2, 45_000, 75_000), LapRow(1, 30_000, 30_000)),
            s.lapRows(),
        )
    }

    @Test fun lapsOnlyWhileRunning() {
        val paused = fresh.start(now = 0, wall = 0).pause(now = 10_000)
        assertEquals(paused, paused.lap(now = 20_000))
    }

    @Test fun lapsAreCappedDroppingTheOldest() {
        var s = fresh.start(now = 0, wall = 0)
        repeat(StopwatchState.MAX_LAPS + 2) { s = s.lap(now = (it + 1) * 1_000L) }
        assertEquals(StopwatchState.MAX_LAPS, s.laps.size)
        assertEquals(2, s.dropped)
        val rows = s.lapRows()
        assertEquals(LapRow(1001, 1_000, 1_001_000), rows.first())
        // The oldest kept lap is lap 3, and still has its own time rather than its total.
        assertEquals(LapRow(3, 1_000, 3_000), rows.last())
        assertEquals(s, StopwatchState.decode(s.encode()))
    }

    @Test fun storedStateRoundTrips() {
        val s = fresh.start(now = 1_000, wall = 2_000).lap(now = 4_000).pause(now = 6_000).start(now = 9_000, wall = 10_000)
        assertEquals(s, StopwatchState.decode(s.encode()))
        assertEquals(fresh, StopwatchState.decode(fresh.encode()))
    }

    @Test fun unreadableStateIsACleanStopwatch() {
        listOf(null, "", "garbage", "1|2|3", "1|x|0|0|0|0|0|", "1|0|0|0|0|0|0|5,,6").forEach {
            assertEquals(it.toString(), StopwatchState(), StopwatchState.decode(it))
        }
    }

    @Test fun resetClearsAllButTheBoot() {
        val s = fresh.start(now = 0, wall = 0).lap(now = 1_000).pause(now = 2_000)
        assertFalse(s.isClear)
        assertTrue(s.reset().isClear)
        assertEquals(7, s.reset().boot)
        assertFalse(fresh.start(now = 0, wall = 0).isClear) // Running at 0 still has something to show.
    }

    @Test fun theNotificationCountsFromAVirtualStart() {
        // 10 s, paused for a minute, then 5 s more: the chronometer counts from 15 s before the wall clock now.
        val s = fresh.start(now = 0, wall = 1_000_000).pause(now = 10_000).start(now = 70_000, wall = 1_070_000)
        assertEquals(1_075_000 - 15_000, s.virtualStart(now = 75_000, wall = 1_075_000))
        assertEquals(0, fresh.start(now = 500, wall = 2_000).virtualStart(now = 500, wall = 2_000) - 2_000)
    }

    @Test fun timesShowHundredths() {
        assertEquals("00:00.00", formatStopwatch(0))
        assertEquals("01:05.23", formatStopwatch(65_239))
        assertEquals("1:02:03.45", formatStopwatch(3_723_450))
    }
}
