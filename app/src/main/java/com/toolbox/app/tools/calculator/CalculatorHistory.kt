package com.toolbox.app.tools.calculator

import java.math.BigDecimal

/**
 * A finished sum: [sum] as it was on the display and its [answer] in full. [base] is the earlier answer in full when
 * the sum carried on from it, so editing the sum again works it out the same way (1÷3 = then ×3 gives 1, not 0.999…).
 */
data class HistoryEntry(val sum: String, val answer: BigDecimal, val base: BigDecimal? = null)

/** The Calculator's last [CAP] finished sums, newest first, saved as one string in Prefs. Pure, for the unit tests. */
object CalculatorHistory {
    const val CAP = 50

    /** [entries] with [entry] in front, unless it repeats the newest one, cut to [CAP]. */
    fun add(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        if (entries.firstOrNull()?.let { it.sum == entry.sum && it.answer == entry.answer } == true) entries
        else (listOf(entry) + entries).take(CAP)

    // A sum is made of keypad symbols and digits, and a BigDecimal's text has no "=" or line break either.
    fun encode(entries: List<HistoryEntry>): String =
        entries.joinToString("\n") { e -> listOfNotNull(e.sum, e.answer.toString(), e.base?.toString()).joinToString("=") }

    /** The entries in [text]; a line that doesn't read is skipped rather than losing the rest. */
    fun decode(text: String): List<HistoryEntry> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('=')
        if (parts.size !in 2..3 || parts[0].isEmpty()) return@mapNotNull null
        try {
            HistoryEntry(parts[0], BigDecimal(parts[1]), parts.getOrNull(2)?.let(::BigDecimal))
        } catch (_: NumberFormatException) {
            null
        }
    }.take(CAP).toList()
}
