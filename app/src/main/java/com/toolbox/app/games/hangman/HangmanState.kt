package com.toolbox.app.games.hangman

import kotlin.random.Random

enum class LetterStatus { Unused, Hit, Miss }

/**
 * One round of Hangman. Immutable: [guess] returns a new state. [word] is lowercase a–z.
 * Guesses after the game is over, repeats and non-letters are ignored (the same state comes back).
 */
data class HangmanState(val word: String, val guessed: Set<Char> = emptySet(), val maxWrong: Int = MAX_WRONG) {
    init {
        require(word.isNotEmpty() && word.all { it in 'a'..'z' }) { "Word must be lowercase letters: '$word'" }
    }

    val wrong: Int get() = guessed.count { it !in word }
    val wrongLeft: Int get() = (maxWrong - wrong).coerceAtLeast(0)
    val won: Boolean get() = word.all { it in guessed }
    val lost: Boolean get() = !won && wrong >= maxWrong
    val over: Boolean get() = won || lost

    /** Letters in guess order are not kept, so misses come back alphabetically. */
    val misses: List<Char> get() = guessed.filter { it !in word }.sorted()

    fun status(c: Char): LetterStatus = when {
        c.lowercaseChar() !in guessed -> LetterStatus.Unused
        c.lowercaseChar() in word -> LetterStatus.Hit
        else -> LetterStatus.Miss
    }

    fun isRevealed(index: Int): Boolean = word[index] in guessed

    fun guess(c: Char): HangmanState {
        val l = c.lowercaseChar()
        if (over || l !in 'a'..'z' || l in guessed) return this
        return copy(guessed = guessed + l)
    }

    /** "c _ _ _ _ t" style, for TalkBack and tests. */
    fun masked(): String = word.map { if (it in guessed) it else '_' }.joinToString(" ")

    companion object {
        const val MAX_WRONG = 8

        /** Words from a one-per-line list: trimmed, lowercased, and only 5–10 plain letters kept. */
        fun parseWords(text: String): List<String> = text.lineSequence()
            .map { it.trim().lowercase() }
            .filter { it.length in 5..10 && it.all { c -> c in 'a'..'z' } }
            .distinct()
            .toList()

        /** Whether [s] can be played: lowercase a–z only, as a saved word must be before it's brought back. */
        fun isWord(s: String): Boolean = s.isNotEmpty() && s.all { it in 'a'..'z' }

        /** Length limits for a word typed in by the other player in two-player mode. */
        val CUSTOM_LENGTH = 3..12

        /** What the word setter's text field keeps as they type: letters only, lowercased, capped at the max length. */
        fun filterCustomInput(text: String): String =
            text.lowercase().filter { it in 'a'..'z' }.take(CUSTOM_LENGTH.last)

        /** The setter's word ready to play, or null if it's too short (or long) to be a fair game. */
        fun customWord(text: String): String? = filterCustomInput(text).takeIf { it.length in CUSTOM_LENGTH }

        /** A random word, avoiding [previous] so a new game never repeats the last word. */
        fun pick(words: List<String>, previous: String? = null, random: Random = Random): String {
            val choices = if (words.size > 1) words.filter { it != previous } else words
            return choices.random(random)
        }
    }
}
