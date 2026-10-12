package com.toolbox.app.tools.coin

import kotlin.math.roundToInt

/**
 * Running heads/tails statistics for the Coin, shared by its Quick and Throw modes.
 * [history] holds the last [HISTORY_MAX] results, oldest first, as 'H'/'T'. The strip under the counts shows the last
 * [STRIP] of them, and the full list shows them all. [streak] is the length of the current run, which can be longer
 * than the history.
 */
data class CoinStats(val heads: Int = 0, val tails: Int = 0, val streak: Int = 0, val history: String = "") {
    val total get() = heads + tails

    /** The face of the current run, or null before the first result. */
    val streakHeads: Boolean? get() = history.lastOrNull()?.let { it == 'H' }

    fun record(heads: Boolean): CoinStats {
        val c = if (heads) 'H' else 'T'
        return CoinStats(
            heads = this.heads + if (heads) 1 else 0,
            tails = tails + if (heads) 0 else 1,
            streak = if (history.lastOrNull() == c) streak + 1 else 1,
            history = (history + c).takeLast(HISTORY_MAX),
        )
    }

    /** "4 heads in a row", or null for a run of one. */
    fun streakText(): String? {
        val face = streakHeads ?: return null
        return if (streak < 2) null else "$streak ${if (face) "heads" else "tails"} in a row"
    }

    /**
     * Heads and tails as whole percentages that add up to 100 (tails takes what heads leaves, so a half can't be
     * rounded up on both sides), or null before the first result.
     */
    fun percentages(): Pair<Int, Int>? {
        if (total == 0) return null
        val h = (100.0 * heads / total).roundToInt()
        return h to 100 - h
    }

    /** The list emptied and the run ended, keeping the counts: Clear in the full list. */
    fun clearHistory(): CoinStats = CoinStats(heads, tails)

    /**
     * Undoing [clearHistory]: [cleared]'s results go back before any recorded since, cut to [HISTORY_MAX]. A run that
     * carried on the same face since the clear joins up with the run from before it.
     */
    fun undoClear(cleared: CoinStats): CoinStats {
        if (history.isEmpty()) return CoinStats(heads, tails, cleared.streak, cleared.history)
        // The run since the clear started from nothing, so it covers every result since only if it's at least as long.
        val unbroken = streak >= history.length && cleared.history.lastOrNull() == history.last()
        return CoinStats(heads, tails, if (unbroken) cleared.streak + streak else streak, (cleared.history + history).takeLast(HISTORY_MAX))
    }

    /**
     * These counts and [other]'s together, for merging the two old coin tools. Neither recorded when its results
     * came, so the run and the history are taken from whichever was used more.
     */
    fun combine(other: CoinStats): CoinStats {
        val recent = if (other.total > total) other else this
        return CoinStats(heads + other.heads, tails + other.tails, recent.streak, recent.history)
    }

    fun encode() = "$heads,$tails,$streak,$history"

    companion object {
        const val HISTORY_MAX = 20
        const val STRIP = 10

        /** Reads [encode]'s format; anything unreadable gives empty stats rather than a crash. */
        fun decode(s: String): CoinStats {
            val p = s.split(',')
            if (p.size != 4) return CoinStats()
            val h = p[0].toIntOrNull() ?: return CoinStats()
            val t = p[1].toIntOrNull() ?: return CoinStats()
            val streak = p[2].toIntOrNull() ?: return CoinStats()
            val history = p[3].filter { it == 'H' || it == 'T' }.takeLast(HISTORY_MAX)
            if (h < 0 || t < 0 || streak < 0) return CoinStats()
            return CoinStats(h, t, streak, history)
        }
    }
}
