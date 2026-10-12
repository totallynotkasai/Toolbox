package com.toolbox.app

import com.toolbox.app.games.snake.COLS
import com.toolbox.app.games.snake.Cell
import com.toolbox.app.games.snake.Dir
import com.toolbox.app.games.snake.ROWS
import com.toolbox.app.games.snake.SnakeGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnakeTest {
    private val playing = SnakeGame(state = SnakeGame.State.Playing)

    @Test fun turningBackOnYourselfIsIgnored() {
        assertEquals(playing, playing.turn(Dir.Left))
        assertEquals(playing, playing.turn(Dir.Right))
        // Two quick turns can't add up to a reversal within one tick either.
        assertEquals(listOf(Dir.Up), playing.turn(Dir.Up).turn(Dir.Down).queued)
        assertEquals(listOf(Dir.Up, Dir.Left), playing.turn(Dir.Up).turn(Dir.Left).queued)
    }

    @Test fun queuedTurnsAreTakenOnePerTick() {
        val (a, _) = playing.turn(Dir.Up).turn(Dir.Left).step(Random(1))
        assertEquals(Dir.Up, a.dir)
        assertEquals(Cell(4, ROWS / 2 - 1), a.head)
        val (b, _) = a.step(Random(1))
        assertEquals(Dir.Left, b.dir)
        assertTrue(b.queued.isEmpty())
    }

    @Test fun edgesWrapAround() {
        val game = playing.copy(body = listOf(Cell(COLS - 1, 0), Cell(COLS - 2, 0), Cell(COLS - 3, 0)))
        assertEquals(Cell(0, 0), game.step(Random(1)).first.head)
        val up = game.copy(dir = Dir.Up, body = listOf(Cell(3, 0), Cell(3, 1), Cell(3, 2)))
        assertEquals(Cell(3, ROWS - 1), up.step(Random(1)).first.head)
    }

    @Test fun bitingYourselfEndsTheGame() {
        val game = playing.copy(body = listOf(Cell(5, 5), Cell(5, 6), Cell(6, 6), Cell(6, 5), Cell(6, 4)), dir = Dir.Right)
        val (after, step) = game.step(Random(1))
        assertEquals(SnakeGame.Step.Died, step)
        assertEquals(SnakeGame.State.Over, after.state)
    }

    @Test fun movingIntoTheCellTheTailIsLeavingIsFine() {
        val game = playing.copy(body = listOf(Cell(5, 5), Cell(5, 6), Cell(6, 6), Cell(6, 5)), dir = Dir.Right)
        val (after, step) = game.step(Random(1))
        assertEquals(SnakeGame.Step.Moved, step)
        assertEquals(Cell(6, 5), after.head)
        assertEquals(4, after.body.size)
    }

    @Test fun eatingGrowsAndScoresAndPutsFoodOnAFreeCell() {
        val game = playing.copy(food = Cell(5, ROWS / 2))
        val (after, step) = game.step(Random(3))
        assertEquals(SnakeGame.Step.Ate, step)
        assertEquals(4, after.body.size)
        assertEquals(1, after.score)
        assertFalse(after.food in after.body)
    }

    @Test fun fillingTheBoardIsAWin() {
        val food = Cell(0, 0)
        val head = Cell(COLS - 1, 0)
        val rest = (0 until COLS).flatMap { x -> (0 until ROWS).map { y -> Cell(x, y) } } - setOf(food, head)
        val game = playing.copy(body = listOf(head) + rest, food = food, dir = Dir.Right)
        val (after, step) = game.step(Random(1))
        assertEquals(SnakeGame.Step.Won, step)
        assertEquals(SnakeGame.State.Won, after.state)
        assertEquals(COLS * ROWS, after.body.size)
        assertTrue(after.finished)
    }

    @Test fun itSpeedsUpAsItGrows() {
        assertEquals(0.15f, playing.tickSeconds, 1e-6f)
        assertEquals(0.12f, playing.copy(score = 10).tickSeconds, 1e-6f)
        assertEquals(0.07f, playing.copy(score = 100).tickSeconds, 1e-6f)
    }
}
