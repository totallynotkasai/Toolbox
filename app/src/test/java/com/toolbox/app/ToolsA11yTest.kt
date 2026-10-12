package com.toolbox.app

import com.toolbox.app.tools.stopwatch.spokenLap
import com.toolbox.app.tools.stopwatch.spokenTime
import com.toolbox.app.tools.timer.spokenDuration
import com.toolbox.app.tools.tuner.Note
import com.toolbox.app.tools.tuner.spokenNote
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolsA11yTest {
    @Test fun stopwatchTimeIsSpokenInUnits() {
        assertEquals("0.00 seconds", spokenTime(0))
        assertEquals("5.23 seconds", spokenTime(5_234))
        assertEquals("1 minute, 5.20 seconds", spokenTime(65_200))
        // Minutes are kept once there are hours, so "2 hours, 0 minutes" doesn't read as seconds after the hour.
        assertEquals("2 hours, 0 minutes, 3.00 seconds", spokenTime(7_203_000))
    }

    @Test fun lapGivesItsOwnTimeThenTheTotal() {
        assertEquals("Lap 2, 30.00 seconds, total 1 minute, 10.00 seconds", spokenLap(2, 30_000, 70_000))
    }

    @Test fun timerDurationRoundsUpLikeTheClock() {
        assertEquals("1 minute", spokenDuration(60_000))
        assertEquals("5 minutes", spokenDuration(300_000))
        assertEquals("1 minute 30 seconds", spokenDuration(89_001))
        assertEquals("1 hour 1 second", spokenDuration(3_601_000))
        assertEquals("0 seconds", spokenDuration(0))
    }

    @Test fun sharpsAreSpokenAsWords() {
        assertEquals("A 4", spokenNote(Note(69)))
        assertEquals("F sharp 2", spokenNote(Note(42)))
    }
}
