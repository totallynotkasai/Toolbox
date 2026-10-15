package com.toolbox.app

import com.toolbox.app.games.blockdrop.BlockDropGame
import com.toolbox.app.games.blockdrop.BlockDropGame.Event
import com.toolbox.app.games.blockdrop.COLS
import com.toolbox.app.games.blockdrop.Kind
import com.toolbox.app.games.blockdrop.Piece
import com.toolbox.app.games.blockdrop.PieceBag
import com.toolbox.app.games.blockdrop.ROWS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlockDropTest {
    private fun started(seed: Int = 1) = BlockDropGame(Random(seed)).apply { start() }

    /** Fills [rows] all the way across except column [gap]. */
    private fun BlockDropGame.fillRows(rows: IntRange, gap: Int) {
        for (y in rows) for (x in 0 until COLS) if (x != gap) setCell(x, y, Kind.Z)
    }

    private fun BlockDropGame.isEmpty() = (0 until ROWS).all { y -> (0 until COLS).all { x -> cell(x, y) == null } }

    @Test fun theBagDealsEachPieceOncePerSeven() {
        val bag = PieceBag(Random(5))
        repeat(10) { assertEquals(Kind.entries.toSet(), List(7) { bag.draw() }.toSet()) }
        // A game's first two pieces come from one bag, so they differ.
        repeat(20) { seed -> started(seed).let { assertTrue(it.piece!!.kind != it.next) } }
    }

    @Test fun piecesTurnAboutTheirBoxAndStartAtTheTop() {
        // The T's first turn points it right, as in the usual rotation system.
        assertEquals(setOf(1 to 0, 1 to 1, 2 to 1, 1 to 2), Kind.T.turns[1].toSet())
        assertEquals(Kind.O.turns[0].toSet(), Kind.O.turns[3].toSet())
        for (kind in Kind.entries) {
            val cells = Piece.spawn(kind).cells
            assertEquals(kind.name, 4, cells.toSet().size)
            assertTrue(kind.name, cells.all { (x, y) -> y in 0..1 && x in 3..6 })
        }
    }

    @Test fun turnsKickOffTheWallsAndTheFloor() {
        val game = started()
        // A standing I against the left wall moves two columns right to lie down.
        game.place(Piece(Kind.I, 1, -2, 5))
        assertTrue(game.rotate())
        assertEquals((0..3).map { it to 7 }.toSet(), game.piece!!.cells.toSet())
        // Against the right wall, one column left.
        game.place(Piece(Kind.I, 1, 7, 5))
        assertTrue(game.rotate())
        assertEquals((6..9).map { it to 7 }.toSet(), game.piece!!.cells.toSet())
        // A T standing on the left wall moves out one.
        game.place(Piece(Kind.T, 1, -1, 5))
        assertTrue(game.rotate())
        assertEquals(0, game.piece!!.x)
        // A T lying on the floor is lifted a row to turn, and an I two rows to stand up.
        game.place(Piece(Kind.T, 0, 3, ROWS - 2))
        assertTrue(game.rotate())
        assertEquals(ROWS - 3, game.piece!!.y)
        assertTrue(game.piece!!.cells.maxOf { it.second } == ROWS - 1)
        game.place(Piece(Kind.I, 0, 3, ROWS - 2))
        assertTrue(game.rotate())
        assertEquals((ROWS - 4 until ROWS).map { 5 to it }.toSet(), game.piece!!.cells.toSet())
        // The O doesn't turn.
        game.place(Piece.spawn(Kind.O))
        assertFalse(game.rotate())
    }

    @Test fun aTurnWithNoRoomIsRefused() {
        val game = started()
        // A standing I down a one-wide shaft can't lie down anywhere.
        game.fillRows(8 until ROWS, gap = 4)
        val standing = Piece(Kind.I, 1, 2, 12)
        game.place(standing)
        assertFalse(game.rotate())
        assertEquals(standing, game.piece)
        assertFalse(game.move(1))
        assertFalse(game.move(-1))
    }

    @Test fun movesStopAtTheWalls() {
        val game = started()
        game.place(Piece(Kind.O, 0, 0, 5))
        assertFalse(game.move(-1))
        assertTrue(game.move(1))
        repeat(7) { assertTrue(game.move(1)) }
        assertFalse(game.move(1))
        assertEquals(COLS - 2, game.piece!!.x)
    }

    @Test fun dropsScoreAPointARow() {
        val game = started()
        game.place(Piece.spawn(Kind.O))
        repeat(3) { assertTrue(game.softDrop()) }
        assertEquals(3, game.score)
        // The ghost shows where it lands, and a hard drop takes it there and locks it.
        assertEquals(ROWS - 2, game.ghost()!!.y)
        val events = game.hardDrop()
        assertEquals(Event.Locked(0, false), events.first())
        assertEquals(3 + (ROWS - 2 - 3), game.score)
        assertEquals(Kind.O, game.cell(4, ROWS - 1))
        // The next piece is already falling.
        assertEquals(0, game.piece!!.cells.minOf { it.second })
    }

    @Test fun fullRowsClearAndScoreByTheLevel() {
        val game = started()
        // A Tetris: four rows at once, at level 1, after a 16-row hard drop.
        game.fillRows(ROWS - 4 until ROWS, gap = 9)
        game.setCell(0, ROWS - 5, Kind.T)
        game.place(Piece(Kind.I, 1, 7, 0))
        assertEquals(listOf(Event.Locked(4, false)), game.hardDrop())
        assertEquals(16 + 800, game.score)
        assertEquals((ROWS - 4 until ROWS).toList(), game.clearing)
        assertNull(game.piece)
        // The rows flash, then go, and what was above them falls.
        game.step(BlockDropGame.CLEAR_SECONDS / 2)
        assertEquals(4, game.clearing.size)
        game.step(BlockDropGame.CLEAR_SECONDS / 2 + 0.01f)
        assertTrue(game.clearing.isEmpty())
        assertEquals(Kind.T, game.cell(0, ROWS - 1))
        assertEquals(null, game.cell(0, ROWS - 5))
        assertTrue(game.piece != null)
        game.setCell(0, ROWS - 1, null)
        assertTrue(game.isEmpty())

        // Two more Tetrises reach 12 lines: level 2.
        var levelUp = false
        repeat(2) {
            game.fillRows(ROWS - 4 until ROWS, gap = 9)
            game.place(Piece(Kind.I, 1, 7, 0))
            levelUp = (game.hardDrop().first() as Event.Locked).levelUp
            game.step(1f)
        }
        assertTrue(levelUp)
        assertEquals(12, game.lines)
        assertEquals(2, game.level)
        // A single at level 2 is worth 200; 2 and 3 rows at once would be 300 and 500 a level.
        val before = game.score
        game.fillRows(ROWS - 1 until ROWS, gap = 0)
        game.place(Piece(Kind.I, 1, -2, ROWS - 4))
        assertEquals(listOf(Event.Locked(1, false)), game.hardDrop())
        assertEquals(before + 200, game.score)
        assertEquals(listOf(0, 100, 300, 500, 800), BlockDropGame.LINE_SCORES.toList())
    }

    @Test fun gravitySpeedsUpWithTheLevel() {
        assertEquals(1f, BlockDropGame.gravity(1), 0.0001f)
        assertEquals(0.793f, BlockDropGame.gravity(2), 0.001f)
        for (level in 2..BlockDropGame.TOP_SPEED_LEVEL) assertTrue(BlockDropGame.gravity(level) < BlockDropGame.gravity(level - 1))
        assertEquals(BlockDropGame.gravity(BlockDropGame.TOP_SPEED_LEVEL), BlockDropGame.gravity(30), 0f)
        // At level 1 a piece falls a row a second.
        val game = started()
        val y = game.piece!!.y
        game.step(0.6f)
        assertEquals(y, game.piece!!.y)
        game.step(0.5f)
        assertEquals(y + 1, game.piece!!.y)
    }

    @Test fun aPieceOnTheGroundLocksAfterTheLockDelay() {
        val game = started()
        game.place(Piece(Kind.T, 0, 3, ROWS - 2))
        assertTrue(game.step(0.4f).isEmpty())
        // A move restarts the delay.
        assertTrue(game.move(1))
        assertTrue(game.step(0.4f).isEmpty())
        assertEquals(listOf(Event.Locked(0, false)), game.step(0.15f).take(1))
        assertEquals(Kind.T, game.cell(5, ROWS - 1))
    }

    @Test fun movesCanOnlyHoldOffTheLockSoOften() {
        val game = started()
        game.place(Piece(Kind.T, 0, 3, ROWS - 2))
        // Each move on the ground buys another half second, fifteen times over: far longer than one delay.
        repeat(BlockDropGame.MAX_RESETS) { i ->
            assertTrue(game.step(0.4f).isEmpty())
            assertTrue(game.move(if (i % 2 == 0) 1 else -1))
        }
        // After that, the piece locks the moment it's on the ground.
        assertEquals(Event.Locked(0, false), game.step(0.01f).first())
    }

    @Test fun reachingANewLowestRowGivesTheMovesBack() {
        val game = started()
        // A T on a one-row ledge on the left uses up its moves, then slides off and falls a row.
        for (x in 0..3) game.setCell(x, ROWS - 1, Kind.Z)
        game.place(Piece(Kind.T, 0, 1, ROWS - 3))
        repeat(BlockDropGame.MAX_RESETS - 1) { i -> assertTrue(game.move(if (i % 2 == 0) -1 else 1)) }
        repeat(3) { assertTrue(game.move(1)) }
        game.step(1.1f) // falls clear of the ledge
        assertEquals(ROWS - 2, game.piece!!.y)
        assertTrue(game.step(0.4f).isEmpty())
        assertTrue(game.move(1))
        assertTrue(game.step(0.4f).isEmpty())
    }

    @Test fun theGameEndsWhenTheNextPieceHasNoRoom() {
        val game = started()
        for (x in 3..6) for (y in 0..1) game.setCell(x, y, Kind.S)
        game.place(Piece(Kind.O, 0, 0, ROWS - 2))
        assertEquals(listOf(Event.Locked(0, false), Event.Over), game.hardDrop())
        assertEquals(BlockDropGame.State.Over, game.state)
        assertNull(game.piece)
        assertTrue(game.step(1f).isEmpty())
        assertFalse(game.move(1))

        // So does a piece locking above the top of the well.
        val high = started()
        for (x in 0 until COLS) if (x != 4) high.setCell(x, 0, Kind.S)
        high.place(Piece(Kind.T, 2, 3, -2))
        assertEquals(listOf(Event.Locked(0, false), Event.Over), high.step(0.6f))
    }

    @Test fun savedStateComesBackPaused() {
        val game = started(seed = 3)
        game.move(1)
        game.softDrop()
        game.hardDrop()
        game.rotate()
        game.softDrop()
        val saved = game.save()
        val restored = BlockDropGame.restore(saved, Random(99))
        assertEquals(BlockDropGame.State.Paused, restored.state)
        assertEquals(saved.drop(1), restored.save().drop(1))
        assertEquals(game.piece, restored.piece)
        assertEquals(game.next, restored.next)
        assertEquals(game.score, restored.score)
        // Once resumed, it deals the same pieces from the bag.
        restored.resume()
        repeat(4) {
            game.hardDrop()
            restored.hardDrop()
            assertEquals(game.piece?.kind, restored.piece?.kind)
        }

        // Rows part-way through clearing are kept, and finish clearing after Resume.
        val clearing = started()
        clearing.fillRows(ROWS - 1 until ROWS, gap = 0)
        clearing.place(Piece(Kind.I, 1, -2, 0))
        clearing.hardDrop()
        val back = BlockDropGame.restore(clearing.save())
        assertEquals(listOf(ROWS - 1), back.clearing)
        back.resume()
        back.step(1f)
        assertTrue(back.clearing.isEmpty())
        assertEquals(Kind.I, back.cell(0, ROWS - 1))
    }
}
