package com.toolbox.app

import com.toolbox.app.games.draughts.Draughts
import com.toolbox.app.games.draughts.DrawReason
import com.toolbox.app.games.draughts.Move
import com.toolbox.app.games.draughts.RED
import com.toolbox.app.games.draughts.RED_KING
import com.toolbox.app.games.draughts.RED_MAN
import com.toolbox.app.games.draughts.Result
import com.toolbox.app.games.draughts.YELLOW
import com.toolbox.app.games.draughts.YELLOW_KING
import com.toolbox.app.games.draughts.YELLOW_MAN
import com.toolbox.app.games.draughts.aiMove
import com.toolbox.app.games.draughts.isDark
import com.toolbox.app.games.draughts.leavesAPiece
import com.toolbox.app.games.draughts.owner
import com.toolbox.app.games.draughts.squareName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DraughtsTest {
    /** A square from its name as seen from red's side: "C3" is the third column, third row up. */
    private fun sq(name: String) = (8 - name.substring(1).toInt()) * 8 + (name[0] - 'A')

    private fun move(vararg squares: String) = Move(squares.map(::sq))

    private fun position(toMove: Int = RED, quiet: Int = 0, vararg pieces: Pair<String, Int>) =
        Draughts.arrange(pieces.associate { (s, p) -> sq(s) to p }, toMove, quiet)

    /** A sensible casual player: the biggest capture, a crowning, and nearly always a move that leaves nothing hanging. */
    private fun casual(game: Draughts, random: Random): Move {
        val legal = game.legalMoves()
        if (legal.first().isCapture) {
            val most = legal.maxOf { it.captured.size }
            return legal.filter { it.captured.size == most }.random(random)
        }
        legal.filter { game.crowns(it) }.takeIf { it.isNotEmpty() }?.let { return it.random(random) }
        val g = game.copy()
        val safe = legal.filter { !leavesAPiece(g, it) }
        return if (safe.isNotEmpty() && random.nextFloat() < 0.9f) safe.random(random) else legal.random(random)
    }

    @Test fun theGameStartsWithTwelveMenEachOnTheDarkSquares() {
        val game = Draughts.start()
        assertEquals(12, game.pieces(RED))
        assertEquals(12, game.pieces(YELLOW))
        for (s in 0 until 64) if (game[s] != 0) assertTrue(squareName(s), isDark(s))
        assertEquals(RED_MAN, game[sq("A1")]) // a dark square in red's near left corner
        assertEquals(YELLOW_MAN, game[sq("H8")])
        assertEquals(RED, game.toMove)
        assertEquals(7, game.legalMoves().size) // the seven opening moves
        assertEquals(YELLOW, Draughts.start(YELLOW).toMove)
    }

    @Test fun menMoveAndCaptureForwardsOnly() {
        val red = position(RED, 0, "D4" to RED_MAN)
        assertEquals(setOf(move("D4", "C5"), move("D4", "E5")), red.legalMoves().toSet())
        val yellow = position(YELLOW, 0, "D6" to YELLOW_MAN)
        assertEquals(setOf(move("D6", "C5"), move("D6", "E5")), yellow.legalMoves().toSet())
        // A yellow man behind a red one can't be taken backwards.
        val behind = position(RED, 0, "D4" to RED_MAN, "C3" to YELLOW_MAN)
        assertFalse(behind.legalMoves().any { it.isCapture })
        assertEquals("C3", squareName(sq("C3")))
    }

    @Test fun capturesAreCompulsory() {
        val game = position(RED, 0, "C3" to RED_MAN, "D4" to YELLOW_MAN, "G3" to RED_MAN)
        assertEquals(listOf(move("C3", "E5")), game.legalMoves())
        game.play(move("C3", "E5"))
        assertEquals(0, game[sq("D4")])
        assertEquals(RED_MAN, game[sq("E5")])
    }

    @Test fun aPieceThatHasJumpedMustJumpAgainButMayPickItsWay() {
        val game = position(RED, 0, "C3" to RED_MAN, "D4" to YELLOW_MAN, "F6" to YELLOW_MAN, "D6" to YELLOW_MAN)
        val legal = game.legalMoves().toSet()
        assertEquals(setOf(move("C3", "E5", "G7"), move("C3", "E5", "C7")), legal)
        assertFalse(move("C3", "E5") in legal) // it can't stop halfway
        game.play(move("C3", "E5", "C7"))
        assertEquals(1, game.pieces(YELLOW)) // D4 and D6 taken; F6 left
        assertEquals(YELLOW_MAN, game[sq("F6")])
    }

    @Test fun aManCrownedByAJumpStopsThere() {
        // As a king it could jump on over E7, but crowning ends the move.
        val game = position(RED, 0, "B6" to RED_MAN, "C7" to YELLOW_MAN, "E7" to YELLOW_MAN)
        val crowning = move("B6", "D8")
        assertEquals(listOf(crowning), game.legalMoves())
        assertTrue(game.crowns(crowning))
        game.play(crowning)
        assertEquals(RED_KING, game[sq("D8")])
        assertEquals(YELLOW_MAN, game[sq("E7")])
        // Kings move and capture both ways.
        val king = position(RED, 0, "D4" to RED_KING)
        assertEquals(setOf("C5", "E5", "C3", "E3"), king.legalMoves().map { squareName(it.to) }.toSet())
        val backwards = position(RED, 0, "D4" to RED_KING, "C3" to YELLOW_MAN)
        assertEquals(listOf(move("D4", "B2")), backwards.legalMoves())
    }

    @Test fun aSideWithNoPiecesOrNoMovesHasLost() {
        assertEquals(Result.Win(YELLOW), position(RED, 0, "C7" to YELLOW_MAN).result())
        // Red's only man is blocked: G3 is taken, and the jump's landing square F4 too.
        val blocked = position(RED, 0, "H2" to RED_MAN, "G3" to YELLOW_MAN, "F4" to YELLOW_MAN)
        assertTrue(blocked.legalMoves().isEmpty())
        assertEquals(Result.Win(YELLOW), blocked.result())
        assertNull(Draughts.start().result())
    }

    @Test fun fortyQuietMovesEachOrAThirdRepetitionIsADraw() {
        // One king move short of 40 each without a capture or a man moving.
        val quiet = position(RED, Draughts.QUIET_LIMIT - 1, "A7" to RED_KING, "H2" to YELLOW_KING)
        assertNull(quiet.result())
        quiet.play(move("A7", "B6"))
        assertEquals(Result.Draw(DrawReason.FortyMoves), quiet.result())
        // A man's move starts the count again.
        val reset = position(RED, 50, "A3" to RED_MAN, "H8" to YELLOW_KING)
        reset.play(move("A3", "B4"))
        assertEquals(0, reset.quiet)
        // Two kings going back and forth bring the start round a third time after eight moves.
        val shuffle = position(RED, 0, "A7" to RED_KING, "H2" to YELLOW_KING)
        repeat(2) {
            for (m in listOf(move("A7", "B6"), move("H2", "G3"), move("B6", "A7"), move("G3", "H2"))) {
                assertNull(shuffle.result())
                shuffle.play(m)
            }
        }
        assertEquals(Result.Draw(DrawReason.Repetition), shuffle.result())
        // Taking a move back takes its count back too.
        shuffle.undo()
        assertNull(shuffle.result())
    }

    @Test fun undoAndReplayGiveTheSameGame() {
        val random = Random(4)
        val game = Draughts.start(YELLOW)
        val boards = mutableListOf((0 until 64).map { game[it] })
        while (game.result() == null && game.plies < 150) {
            game.play(game.legalMoves().random(random))
            boards += (0 until 64).map { game[it] }
        }
        val saved = game.moves.joinToString(",") { it.encode() }
        val back = Draughts.replay(YELLOW, saved.split(',').map(Move::decode))!!
        assertEquals(boards.last(), (0 until 64).map { back[it] })
        assertEquals(game.toMove, back.toMove)
        assertEquals(YELLOW, back.first)
        while (game.plies > 0) {
            boards.removeAt(boards.lastIndex)
            game.undo()
            assertEquals(boards.last(), (0 until 64).map { game[it] })
        }
        // A move that isn't legal isn't replayed.
        assertNull(Draughts.replay(RED, listOf(move("A1", "B2"))))
    }

    @Test fun theComputerPlaysLegallyAndBeatsRandomPlay() {
        val random = Random(9)
        var wins = 0
        repeat(20) { g ->
            val game = Draughts.start(if (g % 2 == 0) RED else YELLOW)
            while (game.result() == null) {
                val legal = game.legalMoves()
                val m = if (game.toMove == YELLOW) aiMove(game, random) else legal.random(random)
                assertTrue(m in legal)
                game.play(m)
            }
            if (game.result() == Result.Win(YELLOW)) wins++
        }
        assertTrue("won $wins of 20", wins >= 17)
    }

    @Test fun aCasualPlayerWinsAboutHalfTheTime() {
        val random = Random(7)
        val n = 200
        var human = 0
        repeat(n) { g ->
            val game = Draughts.start(if (g % 2 == 0) RED else YELLOW)
            while (game.result() == null) game.play(if (game.toMove == RED) casual(game, random) else aiMove(game, random))
            if (game.result() == Result.Win(RED)) human++
        }
        assertTrue("the casual player won $human of $n", human in 76..112) // 38% to 56%
    }

    @Test fun theComputerTakesTheBiggestCaptureMostOfTheTime() {
        // One capture takes a piece, the other two.
        val game = position(YELLOW, 0, "C7" to YELLOW_MAN, "B6" to RED_MAN, "G7" to YELLOW_MAN, "F6" to RED_MAN, "F4" to RED_MAN, "H8" to YELLOW_MAN)
        val legal = game.legalMoves()
        assertTrue(legal.any { it.captured.size == 2 } && legal.any { it.captured.size == 1 })
        val big = (0 until 500).count { aiMove(game, Random(it)).captured.size == 2 }
        assertTrue("took two $big of 500", big in 400..500)
        assertTrue(legal.all { owner(game[it.from]) == YELLOW })
    }
}
