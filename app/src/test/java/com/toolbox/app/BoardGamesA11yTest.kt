package com.toolbox.app

import com.toolbox.app.games.battleships.Board
import com.toolbox.app.games.battleships.Ship
import com.toolbox.app.games.battleships.cellName
import com.toolbox.app.games.battleships.cellState
import com.toolbox.app.games.connectfour.ConnectFour
import com.toolbox.app.games.connectfour.columnSpoken
import com.toolbox.app.games.dotsboxes.DotsBoxes
import com.toolbox.app.games.dotsboxes.dotsLineName
import com.toolbox.app.games.dotsboxes.dotsTurnSummary
import com.toolbox.app.games.simon.sequenceSpoken
import com.toolbox.app.games.twentyfortyeight.Move
import com.toolbox.app.games.twentyfortyeight.boardSpoken
import com.toolbox.app.games.twentyfortyeight.move
import com.toolbox.app.games.twentyfortyeight.moveSpoken
import org.junit.Assert.assertEquals
import org.junit.Test

/** What TalkBack reads and says in the board games. */
class BoardGamesA11yTest {
    @Test fun connectFourColumnListsDiscsFromTheBottom() {
        // Red moves first: red, yellow and red go into column 4, then yellow into column 1.
        val b = ConnectFour.fromMoves(listOf(3, 3, 3, 0))
        assertEquals("Column 4: red, yellow, red; 3 free", columnSpoken(b, 3))
        assertEquals("Column 1: yellow; 5 free", columnSpoken(b, 0))
        assertEquals("Column 7: empty", columnSpoken(b, 6))
        assertEquals("full", columnSpoken(ConnectFour.fromMoves(List(6) { 2 }), 2).substringAfterLast("; "))
    }

    @Test fun simonSaysTheRoundInOrder() {
        assertEquals("Green", sequenceSpoken(listOf(0)))
        assertEquals("Blue, red, yellow, green", sequenceSpoken(listOf(3, 1, 2, 0)))
    }

    @Test fun board2048ReadsRowByRow() {
        val board = listOf(2, 0, 4, 0) + List(8) { 0 } + listOf(0, 0, 0, 2048)
        assertEquals(
            "Row 1: 2, empty, 4, empty. Row 2: empty, empty, empty, empty. Row 3: empty, empty, empty, empty. " +
                "Row 4: empty, empty, empty, 2048",
            boardSpoken(board),
        )
    }

    @Test fun move2048SaysTheMergeScoreAndNewTile() {
        val before = listOf(2, 2, 0, 0) + List(12) { 0 }
        val (slid, gained) = move(before, Move.Left)
        val next = slid.toMutableList().also { it[15] = 2 }
        assertEquals("Made 4. Score 4. New 2 in row 4, column 4", moveSpoken(before, slid, next, 4, gained))
    }

    @Test fun move2048WithoutAMergeOnlySaysTheNewTile() {
        val before = listOf(0, 0, 0, 2) + List(12) { 0 }
        val (slid, gained) = move(before, Move.Left)
        val next = slid.toMutableList().also { it[4] = 4 }
        assertEquals("New 4 in row 2, column 1", moveSpoken(before, slid, next, 0, gained))
    }

    @Test fun dotsLinesAreNamedByTheirBox() {
        val g = DotsBoxes.fromMoves(3, 3, emptyList())
        assertEquals("Row 1, column 1, top edge", dotsLineName(g, g.horizontal(0, 0)))
        assertEquals("Row 3, column 2, bottom edge", dotsLineName(g, g.horizontal(3, 1)))
        assertEquals("Row 2, column 1, left edge", dotsLineName(g, g.vertical(1, 0)))
        assertEquals("Row 1, column 3, right edge", dotsLineName(g, g.vertical(0, 3)))
    }

    @Test fun dotsTurnSummarySaysTheLineAndTheScore() {
        val g = DotsBoxes.fromMoves(3, 3, emptyList())
        val sides = listOf(g.horizontal(0, 0), g.horizontal(1, 0), g.vertical(0, 0), g.vertical(0, 1))
        val name = { p: Int -> if (p == 1) "Blue" else "Red" }

        val one = DotsBoxes.fromMoves(3, 3, sides.take(1))
        assertEquals("Blue drew row 1, column 1, top edge", dotsTurnSummary(g, one, "Blue", name))

        // Red draws the fourth side of the first box, closing it.
        val before = DotsBoxes.fromMoves(3, 3, sides.take(3))
        val after = DotsBoxes.fromMoves(3, 3, sides)
        assertEquals("Red drew row 1, column 2, left edge and closed a box. Blue 0, Red 1", dotsTurnSummary(before, after, "Red", name))

        assertEquals("Computer drew 4 lines and closed a box. Blue 0, Red 1", dotsTurnSummary(g, after, "Computer", name))
    }

    @Test fun battleshipSquaresAreNamedLikePaper() {
        assertEquals("A1", cellName(0))
        assertEquals("C2", cellName(12))
        assertEquals("J10", cellName(99))
    }

    @Test fun enemyWatersOnlySayWhatsBeenFound() {
        // A two-square ship across A1 and B1 (it's first in the fleet, so it's the Carrier by name).
        val ships = listOf(Ship(2, 0, 0, true))
        val hit = Board(ships, listOf(0, 5))
        assertEquals("not fired", cellState(hit, 1, showShips = false))
        assertEquals("hit", cellState(hit, 0, showShips = false))
        assertEquals("miss", cellState(hit, 5, showShips = false))
        assertEquals("sunk Carrier", cellState(Board(ships, listOf(0, 1)), 0, showShips = false))
    }

    @Test fun ownFleetNamesShipsAndWater() {
        val board = Board(listOf(Ship(2, 0, 0, true)), listOf(0, 5))
        assertEquals("your Carrier, hit", cellState(board, 0, showShips = true, whose = "your "))
        assertEquals("your Carrier", cellState(board, 1, showShips = true, whose = "your "))
        assertEquals("water, miss", cellState(board, 5, showShips = true))
        assertEquals("water", cellState(board, 6, showShips = true))
    }
}
