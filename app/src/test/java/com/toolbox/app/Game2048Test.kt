package com.toolbox.app

import com.toolbox.app.games.twentyfortyeight.Move
import com.toolbox.app.games.twentyfortyeight.move
import com.toolbox.app.games.twentyfortyeight.slide
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
