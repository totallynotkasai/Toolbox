package com.toolbox.app

import com.toolbox.app.games.airhockey.AirHockey
import com.toolbox.app.games.battleships.Board
import com.toolbox.app.games.battleships.Fleet
import com.toolbox.app.games.battleships.GRID
import com.toolbox.app.games.battleships.Ship
import com.toolbox.app.games.battleships.ShotResult
import com.toolbox.app.games.battleships.aiShot
import com.toolbox.app.games.battleships.cellOf
import com.toolbox.app.games.dotsboxes.DotsBoxes
import com.toolbox.app.games.dotsboxes.dotsMove
import com.toolbox.app.games.stack.StackGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StackGameTest {
    /** Slides until the moving block's left edge is within [tolerance] of [x]. */
    private fun StackGame.slideTo(x: Float, tolerance: Float = 0.004f) {
        repeat(100_000) {
            if (kotlin.math.abs(moving.x - x) <= tolerance) return
            step(0.001f)
        }
        error("never reached $x")
    }

    @Test fun perfectDropKeepsWidthAndStreakGrowsIt() {
        val g = StackGame()
        g.start()
        repeat(3) { g.slideTo(g.top.x) ; assertTrue(g.drop() is StackGame.Drop.Perfect) }
        assertEquals(3, g.score)
        assertEquals(StackGame.BASE_WIDTH, g.top.width, 1e-4f) // already at full width, so nothing to grow
    }

    @Test fun overhangIsCutOff() {
        val g = StackGame()
        g.start()
        val base = g.top
        g.slideTo(base.x + 0.1f)
        val d = g.drop()
        assertTrue(d is StackGame.Drop.Cut)
        assertEquals(base.width - 0.1f, g.top.width, 0.01f)
        assertEquals(base.right, g.top.right, 0.01f)
        assertEquals(0.1f, (d as StackGame.Drop.Cut).offcut.width, 0.01f)
    }

    @Test fun missingEndsTheGameAndCutBlocksCanRegrow() {
        val g = StackGame()
        g.start()
        g.slideTo(g.top.x + 0.2f)
        g.drop()
        val narrow = g.top.width
        repeat(3) { g.slideTo(g.top.x, 0.002f); g.drop() }
        assertTrue(g.top.width > narrow)
        g.slideTo(g.top.right + 0.03f, 0.015f)
        assertTrue(g.drop() is StackGame.Drop.Miss)
        assertEquals(StackGame.State.Over, g.state)
        assertNull(g.drop())
    }

    @Test fun onANarrowTowerAPerfectStillHasToOverlap() {
        // Playing, no streak, moving right; the moving block, then the tower: the base and a sliver 0.008 wide.
        val saved = floatArrayOf(StackGame.State.Playing.ordinal.toFloat(), 0f, 1f, 0.51f, 0.008f, 0.2f, 0.6f, 0.5f, 0.008f)
        // 0.01 off is within the usual allowance, but it misses the sliver completely.
        val miss = StackGame().apply { restore(saved) }
        assertTrue(miss.drop() is StackGame.Drop.Miss)
        // Within a quarter of the width it's still a perfect.
        val perfect = StackGame().apply { restore(saved.copyOf().also { it[3] = 0.5015f }) }
        assertTrue(perfect.drop() is StackGame.Drop.Perfect)
    }

    @Test fun resetGoesBackToTheBase() {
        val g = StackGame()
        g.start()
        g.slideTo(g.top.x)
        g.drop()
        g.reset()
        assertEquals(StackGame.State.Ready, g.state)
        assertEquals(0, g.score)
        assertNull(g.drop())
    }
}

class DotsBoxesTest {
    @Test fun closingABoxScoresAndGoesAgain() {
        var g = DotsBoxes(2, 2)
        // Three sides of the top-left box, then player 2 draws the fourth.
        g = g.play(g.horizontal(0, 0)) // p1
        g = g.play(g.vertical(0, 0)) // p2
        g = g.play(g.vertical(0, 1)) // p1
        assertEquals(2, g.toMove)
        assertEquals(1, g.closes(g.horizontal(1, 0)))
        g = g.play(g.horizontal(1, 0)) // p2 closes
        assertEquals(1, g.score(2))
        assertEquals(2, g.toMove) // goes again
        assertEquals(2, g.boxOwner(0, 0))
    }

    @Test fun lineCountsAndRebuild() {
        val g = DotsBoxes(3, 4)
        assertEquals(4 * 4 + 3 * 5, g.lineCount)
        val played = listOf(0, 5, 20, 3).fold(g) { acc, m -> acc.play(m) }
        assertEquals(played.moves, DotsBoxes.fromMoves(3, 4, played.moves).moves)
        assertTrue(played.play(0) === played) // already drawn
    }

    @Test fun gamesAlwaysFinishWithEveryBoxOwned() {
        val rng = Random(4)
        repeat(20) {
            var g = DotsBoxes(4, 4)
            while (!g.isOver) g = g.play(dotsMove(g, rng))
            assertEquals(16, g.score(1) + g.score(2))
        }
    }

    @Test fun computerTakesABoxOnOffer() {
        var g = DotsBoxes(2, 2)
        g = g.play(g.horizontal(0, 0)).play(g.vertical(0, 0)).play(g.vertical(0, 1))
        assertEquals(g.horizontal(1, 0), dotsMove(g))
    }
}

class AirHockeyTest {
    @Test fun aShotIntoTheTopGoalScoresForTheBottomPlayer() {
        val g = AirHockey(height = 1.7f, seed = 1)
        g.reset()
        // Put the bottom mallet right behind the puck and flick it straight up, with the top mallet out of the way.
        g.setTarget(1, 0.08f, 0.1f)
        g.setTarget(0, g.puckX, g.puckY + 0.12f)
        repeat(200) { g.step(1 / 60f) }
        val goals = ArrayList<AirHockey.Event.Goal>()
        g.setTarget(0, g.puckX, 0.9f)
        repeat(240) { goals += g.step(1 / 60f).filterIsInstance<AirHockey.Event.Goal>() }
        assertEquals(listOf(AirHockey.Event.Goal(0)), goals.take(1))
        assertEquals(1, g.scores[0])
    }

    @Test fun puckStaysOnTheTableAndMalletsInTheirHalves() {
        val g = AirHockey(height = 1.6f, seed = 2)
        g.reset()
        val rng = Random(3)
        repeat(3000) {
            g.setTarget(0, rng.nextFloat(), rng.nextFloat() * 1.6f)
            g.setTarget(1, rng.nextFloat(), rng.nextFloat() * 1.6f)
            g.step(1 / 60f)
            assertTrue(g.puckX in 0f..1f)
            assertTrue(g.puckY in -0.2f..1.8f)
            assertTrue(g.mallet(0).y >= 0.8f && g.mallet(1).y <= 0.8f)
            assertTrue(g.puckSpeed <= AirHockey.MAX_SPEED + 1e-3f)
        }
    }

    @Test fun computerScoresAgainstAnIdleOpponent() {
        val g = AirHockey(height = 1.7f, seed = 5)
        g.reset(serveTo = 1)
        g.malletSpeed[1] = 2.6f
        repeat(60 * 60) {
            g.aiTarget().let { t -> g.setTarget(1, t.x, t.y) }
            g.setTarget(0, 0.1f, 1.65f) // parked in a corner, not defending
            g.step(1 / 60f)
            if (g.scores[1] > 0) return
        }
        error("the computer never scored in a minute: ${g.scores.toList()}")
    }

    @Test fun computerClearsADeadPuckAnywhereInItsHalf() {
        for (xi in 0..8) for (yi in 0..7) {
            val x = 0.046f + xi * (0.908f / 8); val y = 0.046f + yi * (0.78f / 7)
            val g = AirHockey(height = 1.7f, seed = 1)
            g.reset(serveTo = 1)
            g.malletSpeed[1] = 1.3f; g.aiReaction = 0.18f
            g.placePuck(x, y)
            val cleared = (0 until 60 * 10).any {
                g.aiTarget().let { t -> g.setTarget(1, t.x, t.y) }
                g.setTarget(0, 0.5f, 1.6f)
                g.step(1 / 60f)
                g.puckY > g.height / 2 + AirHockey.PUCK_R || g.scores.sum() > 0
            }
            assertTrue("puck left at $x, $y stayed in the computer's half", cleared)
        }
    }

    @Test fun aPuckDeadOnTheBoardsIsServedAgain() {
        val g = AirHockey(height = 1.7f, seed = 1)
        g.reset()
        g.setTarget(0, 0.1f, 1.2f)
        g.placePuck(0.955f, 1.655f) // the bottom player's corner
        repeat(60 * 4) { g.step(1 / 60f) }
        assertEquals(1.7f * 0.68f, g.puckY, 1e-4f)
        assertTrue(g.puckX in 0.4f..0.6f)
    }
}

class BattleshipsTest {
    @Test fun randomFleetsAreValidAndComplete() {
        repeat(50) {
            val f = Fleet.random(Random(it))
            assertTrue(Fleet.isValid(f))
            assertEquals(Fleet.LENGTHS, f.map { s -> s.length })
        }
        assertFalse(Fleet.isValid(listOf(Ship(5, 7, 0, true))))
        assertFalse(Fleet.isValid(listOf(Ship(3, 0, 0, true), Ship(3, 1, 0, false))))
    }

    /** Whether any two ships sit side by side or corner to corner. */
    private fun touching(ships: List<Ship>): Boolean = ships.indices.any { i ->
        (i + 1 until ships.size).any { j ->
            ships[i].cells.any { a -> ships[j].cells.any { b -> maxOf(kotlin.math.abs(a % GRID - b % GRID), kotlin.math.abs(a / GRID - b / GRID)) <= 1 } }
        }
    }

    @Test fun spacedFleetsNeverTouchButTheComputersCan() {
        repeat(100) { assertFalse(touching(Fleet.random(Random(it)))) }
        val loose = List(100) { Fleet.random(Random(it), spaced = false) }
        loose.forEach { assertTrue(Fleet.isValid(it)); assertEquals(Fleet.LENGTHS, it.map { s -> s.length }) }
        // Five ships placed freely on a 10 × 10 grid touch more often than not.
        assertTrue(loose.count(::touching) > 30)
    }

    @Test fun firingHitsMissesAndSinks() {
        var b = Board(listOf(Ship(2, 0, 0, true), Ship(3, 5, 5, false)))
        val (b1, r1) = b.fire(cellOf(9, 9))!!
        assertEquals(ShotResult.Miss, r1)
        val (b2, r2) = b1.fire(cellOf(0, 0))!!
        assertEquals(ShotResult.Hit, r2)
        val (b3, r3) = b2.fire(cellOf(1, 0))!!
        assertEquals(ShotResult.Sunk, r3)
        assertNull(b3.fire(cellOf(1, 0)))
        assertFalse(b3.allSunk)
        b = b3
        for (y in 5..7) b = b.fire(cellOf(5, y))!!.first
        assertTrue(b.allSunk)
    }

    @Test fun encodeRoundTrips() {
        val b = Board(Fleet.random(Random(9)), listOf(3, 44, 99))
        assertEquals(b, Board.decode(b.encode()))
        assertNull(Board.decode("junk"))
        val empty = Board(Fleet.random(Random(1)))
        assertEquals(empty, Board.decode(empty.encode()))
    }

    private fun shotsToWin(seed: Int): Int {
        val rng = Random(seed)
        var b = Board(Fleet.random(rng))
        var shots = 0
        while (!b.allSunk) {
            val cell = aiShot(b, rng)
            b = checkNotNull(b.fire(cell)) { "repeated shot at $cell" }.first
            shots++
            check(shots <= GRID * GRID)
        }
        return shots
    }

    @Test fun computerNeverRepeatsAndFollowsUpHits() {
        val average = (0 until 25).map { shotsToWin(it) }.average()
        // Pure random fire needs about 95 shots; chasing hits should do clearly better.
        assertTrue("took $average shots on average", average < 85)
    }
}
