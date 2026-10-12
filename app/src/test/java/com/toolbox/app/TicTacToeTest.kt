package com.toolbox.app

import com.toolbox.app.games.tictactoe.aiMove
import com.toolbox.app.games.tictactoe.bestMove
import com.toolbox.app.games.tictactoe.firstTurn
import com.toolbox.app.games.tictactoe.winLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class TicTacToeTest {
    @Test fun detectsWin() {
        assertEquals(listOf(0, 4, 8), winLine(listOf(1, 2, 0, 2, 1, 0, 0, 0, 1)))
        assertNull(winLine(listOf(1, 2, 1, 1, 2, 2, 2, 1, 1)))
    }

    @Test fun bestMoveTakesWin() {
        // O (2) can win at index 2.
        assertEquals(2, bestMove(listOf(2, 2, 0, 1, 1, 0, 1, 0, 0)))
    }

    @Test fun bestMoveBlocks() {
        // X (1) threatens index 2; O must block.
        assertEquals(2, bestMove(listOf(1, 1, 0, 0, 2, 0, 0, 0, 0)))
    }

    @Test fun theComputerOnlyStartsOnItsTurnToStart() {
        assertEquals(1, firstTurn(vsComputer = true, cpuFirst = false))
        assertEquals(2, firstTurn(vsComputer = true, cpuFirst = true))
        // X always starts a two-player game.
        assertEquals(1, firstTurn(vsComputer = false, cpuFirst = true))
    }

    @Test fun computerOnlyPlaysFreeSquares() {
        val b = listOf(1, 2, 1, 0, 2, 0, 1, 0, 2)
        repeat(50) { assertEquals(0, b[aiMove(b, Random(it))]) }
    }
}
