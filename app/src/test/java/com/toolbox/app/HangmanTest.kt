package com.toolbox.app

import com.toolbox.app.games.hangman.HangmanState
import com.toolbox.app.games.hangman.LetterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class HangmanTest {
    private fun HangmanState.guessAll(letters: String) = letters.fold(this) { s, c -> s.guess(c) }

    @Test fun onlyPlayableSavedWordsAreBroughtBack() {
        assertTrue(HangmanState.isWord("snowman"))
        assertFalse(HangmanState.isWord(""))
        assertFalse(HangmanState.isWord("Snow"))
        assertFalse(HangmanState.isWord("snow man"))
    }

    @Test fun hitsRevealAndMissesCount() {
        val s = HangmanState("castle").guessAll("atz")
        assertEquals("_ a _ t _ _", s.masked())
        assertEquals(1, s.wrong)
        assertEquals(7, s.wrongLeft)
        assertEquals(LetterStatus.Hit, s.status('A'))
        assertEquals(LetterStatus.Miss, s.status('z'))
        assertEquals(LetterStatus.Unused, s.status('q'))
        assertEquals(listOf('z'), s.misses)
    }

    @Test fun repeatedLetterAppearsEverywhere() {
        val s = HangmanState("bubble").guess('b')
        assertEquals("b _ b b _ _", s.masked())
        assertEquals(0, s.wrong)
    }

    @Test fun winning() {
        val s = HangmanState("apple").guessAll("aple")
        assertTrue(s.won); assertTrue(s.over); assertFalse(s.lost)
    }

    @Test fun losingAfterEightMisses() {
        var s = HangmanState("apple").guessAll("bcdfghi")
        assertFalse(s.over)
        s = s.guess('j')
        assertTrue(s.lost); assertEquals(8, s.wrong); assertEquals(0, s.wrongLeft)
    }

    @Test fun ignoredGuesses() {
        val s = HangmanState("apple").guess('a')
        assertSame(s, s.guess('a'))
        assertSame(s, s.guess('A'))
        assertSame(s, s.guess('1'))
        assertSame(s, s.guess('é'))
        val lost = HangmanState("apple").guessAll("bcdfghij")
        assertSame(lost, lost.guess('p'))
        val won = HangmanState("apple").guessAll("aple")
        assertSame(won, won.guess('z'))
    }

    @Test fun customWordsAreCleanedAndLengthChecked() {
        assertEquals("snowman", HangmanState.customWord("Snow Man!"))
        assertEquals("abc", HangmanState.customWord("a1b2c3"))
        assertEquals(null, HangmanState.customWord("ab"))
        assertEquals("abcdefghijkl", HangmanState.filterCustomInput("abcdefghijklmnop"))
        assertEquals(null, HangmanState.customWord("é!"))
    }

    @Test fun uppercaseGuessesWork() {
        assertEquals(LetterStatus.Hit, HangmanState("apple").guess('P').status('p'))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadWords() {
        HangmanState("Apple")
    }

    @Test fun parseWordsFilters() {
        val words = HangmanState.parseWords("Apple\n  banana \ncat\nice-cream\nabcdefghijk\nbanana\n\ncastle")
        assertEquals(listOf("apple", "banana", "castle"), words)
    }

    @Test fun pickAvoidsPrevious() {
        val words = listOf("apple", "banana")
        repeat(50) { assertEquals("banana", HangmanState.pick(words, previous = "apple", random = Random(it))) }
        assertEquals("apple", HangmanState.pick(listOf("apple"), previous = "apple"))
    }

    @Test fun bundledWordListIsClean() {
        // Unit tests run with the module directory as the working directory.
        val file = File("src/main/res/raw/words.txt")
        val lines = file.readLines().filter { it.isNotBlank() }
        val words = HangmanState.parseWords(file.readText())
        assertEquals("every line should be a unique 5–10 letter lowercase word", lines, words)
        assertTrue("expected a decent-sized list, got ${words.size}", words.size >= 550)
        assertNotEquals(0, words.count { it.length == 10 })
    }
}
