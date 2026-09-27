package com.toolbox.app.tools.coin

/**
 * Running heads/tails statistics shared by both coin screens (each stores its own copy).
 * [history] holds the last [HISTORY_MAX] results, oldest first, as 'H'/'T'; [streak] is the length of the current run,
 * which can be longer than the history.
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

    fun encode() = "$heads,$tails,$streak,$history"

    companion object {
        const val HISTORY_MAX = 10

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
