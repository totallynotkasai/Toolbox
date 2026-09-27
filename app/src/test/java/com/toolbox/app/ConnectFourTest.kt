package com.toolbox.app

import com.toolbox.app.games.connectfour.COLS
import com.toolbox.app.games.connectfour.ConnectFour
import com.toolbox.app.games.connectfour.aiMove
import com.toolbox.app.games.connectfour.bestMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ConnectFourTest {
    private fun cell(col: Int, row: Int) = row * COLS + col

    @Test fun horizontalWin() {
        // Red 0,1,2,3 on the bottom row; yellow stacks on top.
        val b = ConnectFour.fromMoves(listOf(0, 0, 1, 1, 2, 2, 3))
        assertEquals(listOf(cell(0, 0), cell(1, 0), cell(2, 0), cell(3, 0)), b.lineThrough(3, 0))
        assertNotNull(b.winLine())
    }

    @Test fun verticalWin() {
        val b = ConnectFour.fromMoves(listOf(4, 5, 4, 5, 4, 5, 4))
        assertEquals(listOf(cell(4, 0), cell(4, 1), cell(4, 2), cell(4, 3)), b.lineThrough(4, 3))
    }

    @Test fun risingDiagonalWin() {
        // Red at (0,0) (1,1) (2,2) (3,3).
        val b = ConnectFour.fromMoves(listOf(0, 1, 1, 2, 2, 3, 2, 3, 3, 6, 3))
        assertEquals(1, b[3, 3])
        assertEquals(listOf(cell(0, 0), cell(1, 1), cell(2, 2), cell(3, 3)), b.lineThrough(3, 3))
    }

    @Test fun fallingDiagonalWin() {
        // Mirror of the rising case: red at (6,0) (5,1) (4,2) (3,3).
        val b = ConnectFour.fromMoves(listOf(6, 5, 5, 4, 4, 3, 4, 3, 3, 0, 3))
        assertNotNull(b.lineThrough(3, 3))
        assertEquals(4, b.lineThrough(3, 3)!!.size)
    }

    @Test fun noWinForThreeOrMixedLines() {
        val b = ConnectFour.fromMoves(listOf(0, 0, 1, 1, 2, 6))
        assertNull(b.winLine())
        assertFalse(b.isWinningMove(4))
        assertTrue(b.isWinningMove(3))
    }

    @Test fun takesAnImmediateWin() {
        // Red has three in column 2 and it's red's turn: both levels play it.
        val b = ConnectFour.fromMoves(listOf(2, 5, 2, 5, 2, 6))
        assertEquals(2, bestMove(b, 8))
        assertEquals(2, bestMove(b, 2))
    }

    @Test fun blocksAnImmediateLoss() {
        // Yellow to move; red threatens column 3 on the bottom row (0,1,2 filled).
        val b = ConnectFour.fromMoves(listOf(0, 6, 1, 6, 2))
        assertEquals(2, b.toMove)
        assertEquals(3, bestMove(b, 8))
        assertEquals(3, bestMove(b, 2))
    }

    @Test fun fullBoardIsADraw() {
        // Pairs of columns filled alternately give RRRYYY / YYYRRR stacks; the last column alternates.
        // No four ever line up, so the board fills with no winner.
        val b = ConnectFour()
        for (pair in listOf(0 to 1, 2 to 3, 4 to 5)) {
            repeat(3) { b.play(pair.first); b.play(pair.second) }
            repeat(3) { b.play(pair.second); b.play(pair.first) }
        }
        repeat(6) { b.play(6) }
        assertTrue(b.isFull)
        assertNull(b.winLine())
        assertFalse((0 until COLS).any { b.canPlay(it) })
    }

    @Test fun computerBeatsRandomPlayMostOfTheTime() {
        val rnd = Random(9)
        var wins = 0
        repeat(20) { game ->
            val b = ConnectFour()
            val aiIsRed = game % 2 == 0
            while (!b.isFull) {
                val aiTurn = (b.toMove == 1) == aiIsRed
                val col = if (aiTurn) aiMove(b, rnd) else (0 until COLS).filter { b.canPlay(it) }.random(rnd)
                val row = b.play(col)
                if (b.lineThrough(col, row) != null) { if (aiTurn) wins++; break }
            }
        }
        assertTrue("won $wins of 20", wins >= 14)
    }

    @Test fun deepSearchIsQuickEnough() {
        val start = System.nanoTime()
        bestMove(ConnectFour.fromMoves(listOf(3, 3, 2)), 8)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1500)
    }
}
