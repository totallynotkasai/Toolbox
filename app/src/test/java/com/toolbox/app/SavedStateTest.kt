package com.toolbox.app

import android.view.Surface
import com.toolbox.app.games.airhockey.AirHockey
import com.toolbox.app.games.snake.SnakeGame
import com.toolbox.app.games.snake.State
import com.toolbox.app.games.stack.StackGame
import com.toolbox.app.ui.common.toDisplayAxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Games brought back after the screen is recreated carry on where they were. */
class SavedStateTest {
    @Test fun snakeComesBackPausedWithTheSameBoard() {
        val game = SnakeGame().apply { state = State.Playing; repeat(3) { step() } }
        val back = SnakeGame().apply { restore(game.save()) }
        assertEquals(State.Paused, back.state)
        assertEquals(game.snake, back.snake)
        assertEquals(game.food, back.food)
        assertEquals(game.score, back.score)

        // Resumed, it moves on in the same direction.
        game.step(); back.state = State.Playing; back.step()
        assertEquals(game.snake, back.snake)
    }

    @Test fun finishedSnakeStaysFinished() {
        val game = SnakeGame().apply { state = State.Over }
        assertEquals(State.Over, SnakeGame().apply { restore(game.save()) }.state)
    }

    @Test fun stackCarriesOnWithTheSameTower() {
        val game = StackGame().apply {
            start()
            repeat(4) { step(0.37f); drop() }
            step(0.1f)
        }
        val back = StackGame().apply { restore(game.save()) }
        assertEquals(game.state, back.state)
        assertEquals(game.tower, back.tower)
        assertEquals(game.moving, back.moving)
        assertEquals(game.streak, back.streak)
        assertEquals(game.score, back.score)

        // Same direction of travel, so the next drop lands the same.
        game.step(0.2f); back.step(0.2f)
        assertEquals(game.drop(), back.drop())
        assertEquals(game.tower, back.tower)
    }

    @Test fun airHockeyResumesAtTheSavedScore() {
        val game = AirHockey(seed = 1).apply { reset(); resume(blue = 3, red = 5) }
        assertEquals(3, game.scores[0])
        assertEquals(5, game.scores[1])
        assertNull(game.winner)
        assertEquals(0, AirHockey(seed = 1).apply { resume(blue = AirHockey.WIN_SCORE, red = 2) }.winner)
    }

    @Test fun gravityIsUpTheScreenWhicheverWayItIsTurned() {
        val g = 9.8f
        // An upright reading for each rotation: the device edge that is on top of the screen reads +g.
        val upright = mapOf(
            Surface.ROTATION_0 to (0f to g),
            Surface.ROTATION_90 to (g to 0f),
            Surface.ROTATION_180 to (0f to -g),
            Surface.ROTATION_270 to (-g to 0f),
        )
        for ((rotation, reading) in upright) {
            val (x, y) = toDisplayAxes(reading.first, reading.second, rotation)
            assertEquals("x at rotation $rotation", 0f, x, 1e-6f)
            assertEquals("y at rotation $rotation", g, y, 1e-6f)
        }
    }

    @Test fun raisingTheScreensLeftEdgeReadsAsUpToTheLeft() {
        // Turned to ROTATION_90, the screen's left edge is the device's top edge. Raising it gives the device's y a
        // positive reading, which on screen is "up" toward the left: negative x, as it would be held upright.
        val (x, _) = toDisplayAxes(0f, 2f, Surface.ROTATION_90)
        assertEquals(-2f, x, 1e-6f)
    }
}
