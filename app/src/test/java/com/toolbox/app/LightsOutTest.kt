package com.toolbox.app

import com.toolbox.app.games.lightsout.LightsOutBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LightsOutTest {
    private fun board(vararg rows: String) =
        LightsOutBoard(rows.joinToString("").foldIndexed(0) { i, acc, ch -> if (ch == '1') acc or (1 shl i) else acc })

    private fun LightsOutBoard.pressAll(cells: List<Int>) = cells.fold(this) { b, c -> b.press(c) }

    @Test fun pressTogglesCross() {
        val b = LightsOutBoard.SOLVED.press(12)
        assertEquals(board("00000", "00100", "01110", "00100", "00000"), b)
        assertEquals(5, b.lightsOn)
    }

    @Test fun cornerAndEdgePressesStayOnTheBoard() {
        assertEquals(3, LightsOutBoard.SOLVED.press(0).lightsOn)
        assertEquals(3, LightsOutBoard.SOLVED.press(24).lightsOn)
        assertEquals(4, LightsOutBoard.SOLVED.press(2).lightsOn)
        assertEquals(board("11000", "10000", "00000", "00000", "00000"), LightsOutBoard.SOLVED.press(0))
    }

    @Test fun pressingTwiceUndoes() {
        val b = board("10101", "01010", "00100", "01010", "10101")
        assertEquals(b, b.press(7).press(7))
    }

    @Test fun solvedBoardNeedsNoPresses() {
        assertEquals(emptyList<Int>(), LightsOutBoard.SOLVED.solution())
    }

    @Test fun singlePressIsSolvedInOne() {
        assertEquals(listOf(12), LightsOutBoard.SOLVED.press(12).solution())
    }

    @Test fun nullSpacePatternsChangeNothing() {
        // The two known quiet patterns of the 5×5 board: pressing all their cells leaves the board unchanged.
        for (q in listOf(quiet1, quiet2)) {
            val presses = (0 until 25).filter { q.isOn(it) }
            assertTrue(LightsOutBoard.SOLVED.pressAll(presses).isSolved)
        }
    }

    @Test fun unsolvableBoardReturnsNull() {
        // A lone corner light is odd against a quiet pattern, so no presses can clear it.
        assertNull(board("10000", "00000", "00000", "00000", "00000").solution())
    }

    @Test fun randomPuzzlesAreUnsolvedSolvableAndSolutionsWork() {
        val rnd = Random(42)
        repeat(300) {
            val b = LightsOutBoard.random(rnd)
            assertFalse(b.isSolved)
            val sol = requireNotNull(b.solution())
            assertTrue(b.pressAll(sol).isSolved)
            assertTrue(sol.size <= 15)
        }
    }

    @Test fun solutionIsTheShortestOfTheFour() {
        // Every solvable board has exactly 4 solutions: the found one xor any combination of the quiet patterns.
        val q1 = quiet1.bits
        val q2 = quiet2.bits
        val rnd = Random(7)
        repeat(100) {
            val sol = LightsOutBoard.random(rnd).solution()!!.fold(0) { m, c -> m or (1 shl c) }
            listOf(q1, q2, q1 xor q2).forEach { q -> assertTrue(Integer.bitCount(sol) <= Integer.bitCount(sol xor q)) }
        }
    }

    private val quiet1 = board("01110", "10101", "11011", "10101", "01110")
    private val quiet2 = board("10101", "10101", "00000", "10101", "10101")
}
