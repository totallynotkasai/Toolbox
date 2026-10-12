package com.toolbox.app

import com.toolbox.app.games.twentyfortyeight.Move
import com.toolbox.app.games.twentyfortyeight.canMove
import com.toolbox.app.games.twentyfortyeight.move
import com.toolbox.app.games.twentyfortyeight.newBoard
import com.toolbox.app.games.twentyfortyeight.play
import com.toolbox.app.games.twentyfortyeight.slide
import com.toolbox.app.games.twentyfortyeight.spawn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class Game2048Test {
    @Test fun mergesEachPairOnce() {
        assertEquals(listOf(4, 4, 0, 0) to 8, slide(listOf(2, 2, 2, 2)))
        assertEquals(listOf(4, 2, 0, 0) to 4, slide(listOf(2, 2, 2, 0)))
        assertEquals(listOf(8, 0, 0, 0) to 0, slide(listOf(0, 0, 0, 8)))
        assertEquals(listOf(4, 8, 0, 0) to 12, slide(listOf(2, 2, 4, 4)))
    }

    @Test fun movesInAllDirections() {
        val b = listOf(
            2, 0, 0, 2,
            0, 0, 0, 0,
            0, 0, 0, 0,
            2, 0, 0, 0,
        )
        assertEquals(4, move(b, Move.Left).first[0])
        assertEquals(4, move(b, Move.Right).first[3])
        assertEquals(4, move(b, Move.Up).first[0])
        assertEquals(4, move(b, Move.Down).first[12])
    }

    @Test fun aMoveThatChangesNothingAddsNoTile() {
        val b = listOf(
            2, 4, 0, 0,
            8, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )
        assertNull(play(b, Move.Left, Random(1)))
        assertNull(play(b, Move.Up, Random(1)))
        val (after, gained) = play(b, Move.Right, Random(1))!!
        assertEquals(0, gained)
        // The three tiles slid over, and one new tile was added.
        assertEquals(4, after.count { it != 0 })
    }

    @Test fun newTilesOnlyLandOnEmptySquaresAndAreTwosOrFours() {
        repeat(50) { seed ->
            val b = newBoard(Random(seed))
            assertEquals(2, b.count { it != 0 })
            assertTrue(b.all { it in setOf(0, 2, 4) })
            val full = List(16) { 8 }
            assertEquals(full, spawn(full, Random(seed)))
        }
    }

    @Test fun theGameIsOverOnlyWhenNothingCanMove() {
        val stuck = listOf(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2,
        )
        assertFalse(canMove(stuck))
        // A full board with one pair can still move.
        assertTrue(canMove(stuck.toMutableList().also { it[1] = 2 }))
        // So can a board with a gap.
        assertTrue(canMove(stuck.toMutableList().also { it[15] = 0 }))
    }
}
