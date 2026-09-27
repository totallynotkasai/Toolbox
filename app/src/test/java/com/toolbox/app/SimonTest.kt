package com.toolbox.app

import com.toolbox.app.games.simon.PAD_COUNT
import com.toolbox.app.games.simon.PressResult
import com.toolbox.app.games.simon.SimonGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimonTest {
    @Test fun repeatingTheSequence() {
        var g = SimonGame(listOf(2, 0, 3))
        var r = g.press(2); g = r.first
        assertEquals(PressResult.Correct, r.second)
        r = g.press(0); g = r.first
        assertEquals(PressResult.Correct, r.second)
        r = g.press(3)
        assertEquals(PressResult.RoundComplete, r.second)
    }

    @Test fun wrongPressLeavesStateAlone() {
        val g = SimonGame(listOf(1, 1), inputIndex = 1)
        val (after, result) = g.press(2)
        assertEquals(PressResult.Wrong, result)
        assertEquals(g, after)
        assertEquals(1, g.completed)
    }

    @Test fun extendAddsOnePadAndResetsInput() {
        val g = SimonGame(listOf(0, 1), inputIndex = 2).extended(Random(7))
        assertEquals(3, g.round)
        assertEquals(listOf(0, 1), g.sequence.take(2))
        assertEquals(0, g.inputIndex)
        assertTrue(g.sequence.last() in 0 until PAD_COUNT)
    }

    @Test fun encodeRoundTrips() {
        val g = SimonGame(listOf(3, 2, 1, 0, 0), inputIndex = 3)
        assertEquals(g, SimonGame.decode(g.encode()))
        assertNull(SimonGame.decode(""))
        assertNull(SimonGame.decode("19:0"))
    }

    @Test fun speedsUpAtRounds5_9_13() {
        val speeds = (1..20).map { SimonGame.toneMs(it) }
        for (round in 2..20) {
            val changed = speeds[round - 1] != speeds[round - 2]
            assertEquals("round $round", round in setOf(5, 9, 13), changed)
            assertEquals(changed, SimonGame.speedsUpAt(round))
        }
        assertTrue(speeds.zipWithNext().all { (a, b) -> b <= a })
    }
}
