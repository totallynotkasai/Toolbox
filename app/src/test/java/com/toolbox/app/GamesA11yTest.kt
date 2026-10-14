package com.toolbox.app

import com.toolbox.app.games.airhockey.airHockeyGoalText
import com.toolbox.app.games.airhockey.airHockeyResultText
import com.toolbox.app.games.blockdrop.BlockDropGame
import com.toolbox.app.games.blockdrop.Kind
import com.toolbox.app.games.blockdrop.Piece
import com.toolbox.app.games.blockdrop.blockDropState
import com.toolbox.app.games.hangman.HangmanState
import com.toolbox.app.games.hangman.snowmanGuessText
import com.toolbox.app.games.hangman.spokenWord
import com.toolbox.app.games.memory.memoryFlipText
import com.toolbox.app.games.snake.Dir
import com.toolbox.app.games.snake.SnakeGame
import com.toolbox.app.games.snake.snakeBoardDescription
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class GamesA11yTest {
    @Test fun memorySaysTheSymbolThenWhetherThePairMatches() {
        assertEquals("Heart", memoryFlipText(null, "heart"))
        assertEquals("Music note, match", memoryFlipText("music note", "music note"))
        assertEquals("Heart and star, no match", memoryFlipText("heart", "star"))
    }

    @Test fun snowmanSaysTheGuessTheWordAndWhatsLeft() {
        assertEquals("C, blank, T", spokenWord(HangmanState("cat", setOf('c', 't'))))
        assertEquals(
            "C is in the word. C, blank, blank. 8 guesses left",
            snowmanGuessText(HangmanState("cat", setOf('c')), 'c'),
        )
        assertEquals("Q is not in the word. 7 guesses left", snowmanGuessText(HangmanState("cat", setOf('q')), 'q'))
        assertEquals("Z is not in the word. 1 guess left", snowmanGuessText(HangmanState("cat", "bdefghz".toSet()), 'z'))
    }

    @Test fun snowmanLeavesTheResultToTheCardOnceTheRoundIsOver() {
        assertEquals("T is in the word", snowmanGuessText(HangmanState("cat", setOf('c', 'a', 't')), 't'))
        assertEquals("Z is not in the word", snowmanGuessText(HangmanState("cat", "bdefghjz".toSet()), 'z'))
    }

    @Test fun snakeBoardSaysTheScoreAndHeading() {
        assertEquals("Snake, score 0, heading right", snakeBoardDescription(SnakeGame()))
        assertEquals("Snake, score 4, heading up", snakeBoardDescription(SnakeGame(dir = Dir.Up, score = 4)))
    }

    @Test fun blockDropSaysThePieceItsColumnsAndTheScore() {
        val game = BlockDropGame(Random(1))
        assertEquals("Not started", blockDropState(game))
        game.start()
        game.place(Piece(Kind.T, 0, 3, 5))
        assertEquals("T piece in columns 4 to 6. Score 0", blockDropState(game))
        game.place(Piece(Kind.I, 1, 7, 5))
        game.softDrop()
        assertEquals("I piece in column 10. Score 1", blockDropState(game))
        game.pause()
        assertEquals("Paused. Score 1", blockDropState(game))
    }

    @Test fun airHockeyGoalsAndResultsNameTheSideAndScore() {
        assertEquals("Blue scores, 3 to 2", airHockeyGoalText(0, 3, 2, cpu = false))
        assertEquals("Red scores, 3 to 3", airHockeyGoalText(1, 3, 3, cpu = false))
        assertEquals("The computer scores, 1 to 4", airHockeyGoalText(1, 1, 4, cpu = true))
        assertEquals("You win the match, 7 to 5", airHockeyResultText(0, 7, 5, cpu = true))
        assertEquals("Red wins the match, 5 to 7", airHockeyResultText(1, 5, 7, cpu = false))
    }
}
