package com.toolbox.app

import com.toolbox.app.ui.common.GameMode
import com.toolbox.app.ui.common.Tally
import com.toolbox.app.ui.common.decodeEnum
import org.junit.Assert.assertEquals
import org.junit.Test

class GameChromeTest {
    private fun mode(stored: String) = decodeEnum(stored, GameMode.entries, GameMode.Solo, GameMode::key)

    @Test fun savedModesKeepTheirOldNames() {
        // Solo was saved as "Easy" when there were difficulty levels, and tallies are keyed by it.
        assertEquals(GameMode.Solo, mode("Easy"))
        assertEquals("Easy", GameMode.Solo.key)
        assertEquals(GameMode.TwoPlayer, mode("TwoPlayer"))
    }

    @Test fun missingOrUnknownModesReadAsTheDefault() {
        assertEquals(GameMode.Solo, mode(""))
        assertEquals(GameMode.Solo, mode("Hard"))
        assertEquals(GameMode.TwoPlayer, decodeEnum("Medium", GameMode.entries, GameMode.TwoPlayer, GameMode::key))
    }

    @Test fun talliesRecordAndRoundTrip() {
        val t = Tally().record(1).record(1).record(2).record(0)
        assertEquals(Tally(2, 1, 1), t)
        assertEquals(t, Tally.decode(t.encode()))
        assertEquals(Tally(), Tally.decode("garbage"))
    }

    @Test fun tallySummaries() {
        val t = Tally(3, 2, 1)
        assertEquals("Games: You 3 · CPU 2 · Drawn 1", t.summary("Games", "You", "CPU"))
        assertEquals("Matches: Blue 3 · Red 2", t.summary("Matches", "Blue", "Red", withDraws = false))
    }
}
