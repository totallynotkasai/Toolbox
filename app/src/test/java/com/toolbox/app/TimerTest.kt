package com.toolbox.app

import com.toolbox.app.tools.timer.TimerPhase
import com.toolbox.app.tools.timer.TimerState
import com.toolbox.app.tools.timer.describeDuration
import com.toolbox.app.tools.timer.formatClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Timer's end-time and adjustment rules. Times are elapsed-realtime milliseconds. */
class TimerTest {
    private val fiveMin = TimerState().set(5 * 60_000L)

    @Test fun runningTimerIsItsEndTime() {
        val running = fiveMin.start(now = 1_000)
        assertEquals(301_000, running.endAt)
        assertEquals(240_000, running.remaining(now = 61_000))
        assertFalse(running.isDue(now = 300_999))
        assertTrue(running.isDue(now = 301_000))
        assertEquals(0, running.remaining(now = 400_000))
    }

    @Test fun timeAwayFromTheAppIsCounted() {
        // Stored, the process killed, and read back 2 minutes later: nothing is added on.
        val stored = fiveMin.start(now = 1_000).encode()
        val back = TimerState.decode(stored)
        assertEquals(TimerPhase.Running, back.phase)
        assertEquals(180_000, back.remaining(now = 121_000))
    }

    @Test fun pausingKeepsTheTimeLeft() {
        val paused = fiveMin.start(now = 0).pause(now = 100_000)
        assertEquals(TimerPhase.Paused, paused.phase)
        assertEquals(200_000, paused.remaining(now = 999_999))
        assertEquals(1_200_000, paused.start(now = 1_000_000).endAt)
    }

    @Test fun pausingAtZeroRings() {
        assertEquals(TimerPhase.Ringing, fiveMin.start(now = 0).pause(now = 300_000).phase)
    }

    @Test fun adjustingWhilePausedChangesTheTimeLeftOnly() {
        val paused = fiveMin.start(now = 0).pause(now = 100_000) // 3:20 left.
        val more = paused.adjust(60_000, now = 0)
        assertEquals(TimerPhase.Paused, more.phase)
        assertEquals(260_000, more.remainingMs)
        assertEquals(5 * 60_000L, more.durationMs) // Reset still goes back to 5 minutes.
        assertEquals(5 * 60_000L, more.reset().remainingMs)

        assertEquals(190_000, paused.adjust(-10_000, now = 0).remainingMs)
    }

    @Test fun takingTimeOffStopsAtTenSeconds() {
        val paused = fiveMin.start(now = 0).pause(now = 270_000) // 30 s left.
        assertEquals(10_000, paused.adjust(-60_000, now = 0).remainingMs)
        // Already under 10 s, taking time off does nothing rather than adding some.
        val nearlyDone = fiveMin.start(now = 0).pause(now = 295_000)
        assertEquals(5_000, nearlyDone.adjust(-10_000, now = 0).remainingMs)
    }

    @Test fun timeLeftIsCappedAtADay() {
        val day = 24 * 3_600_000L
        assertEquals(day, TimerState().set(day).adjust(60_000, now = 0).durationMs)
        val paused = TimerState().set(day).start(now = 0).pause(now = 1_000)
        assertEquals(day, paused.adjust(60_000, now = 0).remainingMs)
    }

    @Test fun addingAMinuteWhileRunningMovesTheEnd() {
        val running = fiveMin.start(now = 0).adjust(60_000, now = 100_000)
        assertEquals(TimerPhase.Running, running.phase)
        assertEquals(360_000, running.endAt)
        assertEquals(1f, running.progress(now = 0), 0f) // The ring grows with the time added.
    }

    @Test fun adjustingAStoppedTimerChangesTheSetTime() {
        val longer = fiveMin.adjust(10_000, now = 0)
        assertEquals(TimerPhase.Idle, longer.phase)
        assertEquals(310_000, longer.durationMs)
        assertEquals(310_000, longer.remainingMs)
        assertEquals(10_000, TimerState().set(15_000).adjust(-60_000, now = 0).durationMs)
    }

    @Test fun plusOneMinuteAfterRingingRunsAMinute() {
        val ringing = fiveMin.start(now = 0).ring()
        assertEquals(0, ringing.remaining(now = 0))
        val again = ringing.adjust(60_000, now = 400_000)
        assertEquals(TimerPhase.Running, again.phase)
        assertEquals(460_000, again.endAt)
        assertEquals(1f, again.progress(now = 400_000), 0f)
        assertEquals(5 * 60_000L, again.durationMs)
        assertEquals(ringing, ringing.adjust(-10_000, now = 400_000))
    }

    @Test fun startingAfterRingingRunsTheSetTimeAgain() {
        val again = fiveMin.start(now = 0).ring().start(now = 1_000_000)
        assertEquals(TimerPhase.Running, again.phase)
        assertEquals(1_300_000, again.endAt)
    }

    @Test fun aRebootLosesARunningTimerButNotAPausedOne() {
        val running = fiveMin.copy(boot = 7).start(now = 0)
        assertEquals(running, running.afterBoot(7))
        val lost = running.afterBoot(8)
        assertEquals(TimerPhase.Idle, lost.phase)
        assertEquals(5 * 60_000L, lost.remainingMs)
        assertEquals(8, lost.boot)

        val paused = running.pause(now = 60_000)
        assertEquals(paused.copy(boot = 8), paused.afterBoot(8))
    }

    @Test fun unreadableStateIsAFreshTimer() {
        assertEquals(TimerState(), TimerState.decode(null))
        assertEquals(TimerState(), TimerState.decode("Running|1|2"))
        assertEquals(TimerState(), TimerState.decode("Sleeping|1|2|3|4|5"))
        assertEquals(TimerState(), TimerState.decode("Running|x|2|3|4|5"))
        val any = fiveMin.start(now = 123).copy(boot = 4)
        assertEquals(any, TimerState.decode(any.encode()))
    }

    @Test fun durationsReadNaturally() {
        assertEquals("5 min", describeDuration(5 * 60_000L))
        assertEquals("1 h 30 min", describeDuration(90 * 60_000L))
        assertEquals("2 min 10 s", describeDuration(130_000))
        assertEquals("45 s", describeDuration(45_000))
        assertEquals("04:05", formatClock(245_000))
        assertEquals("1:02:03", formatClock(3_723_000))
    }
}
