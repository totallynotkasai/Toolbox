package com.toolbox.app

import com.toolbox.app.tools.coin.CoinStats
import com.toolbox.app.tools.dice.DiceRoll
import com.toolbox.app.tools.random.Draw
import com.toolbox.app.tools.spinner.stripLabel
import com.toolbox.app.tools.teams.decodeSplit
import com.toolbox.app.tools.teams.encodeSplit
import com.toolbox.app.tools.teams.splitTeams
import com.toolbox.app.tools.teams.withoutSeparators
import com.toolbox.app.ui.common.ResultHistory
import com.toolbox.app.ui.common.stripDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ResultHistoryTest {
    @Test fun awkwardTextRoundTrips() {
        val entries = listOf(
            listOf("Pizza, then films"),
            listOf("back\\slash", "trailing\\"),
            listOf("two\nlines", ""),
            listOf(",", "\\,", "\\n", "\n\n"),
            listOf("Ünïcødé 🎲", "–"),
            listOf("6", "4", "2", "5"),
        )
        assertEquals(entries, ResultHistory.decode(ResultHistory.encode(entries)))
        assertEquals(emptyList<List<String>>(), ResultHistory.decode(""))
        // Every result is one line, however many line breaks its text has.
        assertEquals(entries.size, ResultHistory.encode(entries).lines().size)
    }

    @Test fun aLoneTrailingBackslashIsDroppedRatherThanBreakingTheRest() {
        assertEquals(listOf(listOf("a", "b")), ResultHistory.decode("a,b\\"))
    }

    @Test fun newestFirstAndCappedAtTwenty() {
        var entries = emptyList<List<String>>()
        for (i in 1..25) entries = ResultHistory.add(entries, listOf("$i"))
        assertEquals(ResultHistory.CAP, entries.size)
        assertEquals(listOf("25"), entries.first())
        assertEquals(listOf("6"), entries.last())
        // A repeat is a real result, and is kept.
        assertEquals(listOf(listOf("25"), listOf("25")), ResultHistory.add(entries, listOf("25")).take(2))
        // More than the cap in the saved text reads as the newest twenty.
        val long = (1..30).joinToString("\n")
        assertEquals(ResultHistory.CAP, ResultHistory.decode(long).size)
    }

    @Test fun undoingAClearPutsTheOldResultsUnderNewOnes() {
        val before = (1..20).map { listOf("$it") }
        val since = listOf(listOf("new"))
        val restored = ResultHistory.restore(since, before)
        assertEquals(ResultHistory.CAP, restored.size)
        assertEquals(listOf("new"), restored.first())
        assertEquals(listOf("19"), restored.last())
        assertEquals(before, ResultHistory.restore(emptyList(), before))
    }

    @Test fun diceEntries() {
        val roll = DiceRoll(6, listOf(4, 2, 5))
        assertEquals("3d6: 4 + 2 + 5 = 11", roll.text)
        assertEquals("11", roll.short)
        assertEquals("3 6-sided dice: 4, 2, 5, total 11", roll.spoken)
        assertEquals(roll, DiceRoll.from(roll.fields()))
        val one = DiceRoll(20, listOf(17))
        assertEquals("1d20: 17", one.text)
        assertEquals("17", one.short)
        assertEquals("1 20-sided die: 17", one.spoken)
        assertEquals(one, DiceRoll.from(ResultHistory.decode(ResultHistory.encode(listOf(one.fields()))).single()))
        assertNull(DiceRoll.from(listOf("6")))
        assertNull(DiceRoll.from(listOf("6", "7")))
        assertNull(DiceRoll.from(listOf("6", "x")))
        assertNull(DiceRoll.from(listOf("Heads")))
    }

    @Test fun randomNumberEntries() {
        val draw = Draw(42, 1, 100)
        assertEquals("42 (1–100)", draw.text)
        assertEquals("42, from 1 to 100", draw.spoken)
        assertEquals(draw, Draw.from(draw.fields()))
        assertEquals("-3 (-10 to 10)", Draw(-3, -10, 10).text)
        assertEquals("5 (5–5)", Draw(5, 5, 5).text)
        assertNull(Draw.from(listOf("101", "1", "100")))
        assertNull(Draw.from(listOf("4", "10", "1")))
        assertNull(Draw.from(listOf("42")))
    }

    @Test fun spinnerEntriesAndTheStrip() {
        assertEquals("Pizza", stripLabel("Pizza"))
        assertEquals("Exactly12chr", stripLabel("Exactly12chr"))
        assertEquals("Pizza with…", stripLabel("Pizza with extra cheese"))
        assertEquals("No results yet", stripDescription(emptyList()))
        assertEquals("Last 3, oldest first: 4, 11, 7", stripDescription(listOf("4", "11", "7")))
    }

    @Test fun clearingTheCoinsListKeepsItsCountsAndEndsTheRun() {
        var s = CoinStats()
        for (h in listOf(true, false, true, true)) s = s.record(h)
        val cleared = s.clearHistory()
        assertEquals(CoinStats(3, 1, 0, ""), cleared)
        assertNull(cleared.streakText())
        // Undo with nothing since gives it all back.
        assertEquals(s, cleared.undoClear(s))
        // Heads since the clear join the run of heads from before it.
        val more = cleared.record(true).record(true)
        assertEquals(CoinStats(5, 1, 4, "HTHHHH"), more.undoClear(s))
        // A tails since breaks it.
        val broken = cleared.record(true).record(false)
        assertEquals(CoinStats(4, 2, 1, "HTHHHT"), broken.undoClear(s))
        // A long run since the clear, longer than the list holds, still joins up.
        var long = cleared
        repeat(25) { long = long.record(true) }
        val joined = long.undoClear(s)
        assertEquals(27, joined.streak)
        assertEquals(CoinStats.HISTORY_MAX, joined.history.length)
    }

    @Test fun savedTeamsComeBackOnlyForTheSameNames() {
        val names = listOf("Sam", "Alex", "Jo", "Kim", "Lee")
        val teams = splitTeams(names, 2, Random(3))
        val saved = encodeSplit(names, teams)
        assertEquals(teams, decodeSplit(saved, names))
        // Adding, removing or renaming anyone drops it.
        assertEquals(emptyList<List<String>>(), decodeSplit(saved, names + "Max"))
        assertEquals(emptyList<List<String>>(), decodeSplit(saved, names - "Jo"))
        assertEquals(emptyList<List<String>>(), decodeSplit(saved, names.map { if (it == "Kim") "Kym" else it }))
        // Nothing saved, or something unreadable, is no split.
        assertEquals("", encodeSplit(names, emptyList()))
        assertEquals(emptyList<List<String>>(), decodeSplit("", names))
        assertEquals(emptyList<List<String>>(), decodeSplit("garbage", names))
        // Names with commas, spaces and accents are fine; the separators can't be typed in.
        val odd = listOf("Anne-Marie, Jr", "Zoë", "  ")
        val oddTeams = listOf(listOf(odd[0], odd[2]), listOf(odd[1]))
        assertEquals(oddTeams, decodeSplit(encodeSplit(odd, oddTeams), odd))
        assertTrue(withoutSeparators("a\u001Db\u001Ec\u001Fd") == "a b c d")
    }
}
