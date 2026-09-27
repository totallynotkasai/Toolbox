package com.toolbox.app.games.simon

import kotlin.random.Random

/** Pads in reading order: green (top left), red (top right), yellow (bottom left), blue (bottom right). */
const val PAD_COUNT = 4

/** The original Simon's tones, indexed like the pads: green E4, red A4, yellow C♯4, blue E3. */
val PAD_TONES_HZ = floatArrayOf(329.63f, 440.00f, 277.18f, 164.81f)

val PAD_NAMES = listOf("Green", "Red", "Yellow", "Blue")

enum class PressResult { Correct, RoundComplete, Wrong }

/**
 * A Simon sequence plus how far through repeating it the player is. Immutable.
 * [round] is the length of the sequence being played; [completed] is how many rounds were fully repeated.
 */
data class SimonGame(val sequence: List<Int>, val inputIndex: Int = 0) {
    init {
        require(sequence.all { it in 0 until PAD_COUNT })
    }

    val round: Int get() = sequence.size
    val completed: Int get() = (round - 1).coerceAtLeast(0)
    val expected: Int? get() = sequence.getOrNull(inputIndex)

    fun press(pad: Int): Pair<SimonGame, PressResult> = when {
        pad != expected -> this to PressResult.Wrong
        inputIndex + 1 == sequence.size -> copy(inputIndex = sequence.size) to PressResult.RoundComplete
        else -> copy(inputIndex = inputIndex + 1) to PressResult.Correct
    }

    /** The next round: the same sequence plus one random pad, with input back at the start. */
    fun extended(random: Random = Random): SimonGame = SimonGame(sequence + random.nextInt(PAD_COUNT))

    /** Compact form for rememberSaveable, e.g. "0213:2". */
    fun encode(): String = sequence.joinToString("") + ":" + inputIndex

    companion object {
        fun start(random: Random = Random) = SimonGame(listOf(random.nextInt(PAD_COUNT)))

        fun decode(s: String): SimonGame? = runCatching {
            val (seq, index) = s.split(":")
            SimonGame(seq.map { it.digitToInt() }, index.toInt())
        }.getOrNull()

        /** How long each pad sounds during playback. Speeds up at rounds 5, 9 and 13, like the original. */
        fun toneMs(round: Int): Int = when {
            round < 5 -> 420
            round < 9 -> 340
            round < 13 -> 270
            else -> 210
        }

        /** Silence between pads during playback. */
        fun gapMs(round: Int): Int = toneMs(round) * 2 / 5

        fun speedsUpAt(round: Int): Boolean = round == 5 || round == 9 || round == 13
    }
}
