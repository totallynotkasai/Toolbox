package com.toolbox.app

import androidx.core.content.edit
import com.toolbox.app.tools.coin.COIN_STATS_KEY
import com.toolbox.app.tools.coin.CoinStats
import com.toolbox.app.tools.dice.DICE_COUNT_KEY
import com.toolbox.app.tools.dice.DICE_SIDES_KEY
import com.toolbox.app.ui.common.Prefs

/**
 * Moves saved data along when tools change. Each step only acts while the old data is still there, and removes it in
 * the same write, so running them on every start is cheap and safe. A backup restored from an older version is moved
 * along on its next start too.
 */
object Migrations {
    /** Flip a Coin and Coin Toss became Coin; Roll a Die and Dice Toss became Dice. */
    val MERGED_ROUTES = mapOf("cointoss" to Tool.Coin.route, "dicetoss" to Tool.Dice.route)

    private const val OLD_TOSS_STATS = "cointoss_stats"
    private val OLD_DICE_SETTINGS = mapOf("dicetoss_count" to DICE_COUNT_KEY, "dicetoss_sides" to DICE_SIDES_KEY)

    fun run(prefs: Prefs) {
        mergeCoinAndDice(prefs)
    }

    private fun mergeCoinAndDice(prefs: Prefs) {
        val favourites = prefs.get(Favourites.PREF_KEY, "")
        val renamed = Favourites.rename(favourites, MERGED_ROUTES)
        if (renamed != favourites) prefs.put(Favourites.PREF_KEY, renamed)

        // The two coins' counts become one.
        if (prefs.contains(OLD_TOSS_STATS)) {
            val quick = CoinStats.decode(prefs.get(COIN_STATS_KEY, ""))
            val thrown = CoinStats.decode(prefs.get(OLD_TOSS_STATS, ""))
            prefs.prefs.edit {
                putString(COIN_STATS_KEY, quick.combine(thrown).encode())
                remove(OLD_TOSS_STATS)
            }
        }

        // Dice Toss's die type and count carry on; Roll a Die never saved its own.
        for ((old, new) in OLD_DICE_SETTINGS) {
            if (!prefs.contains(old)) continue
            val value = prefs.get(old, 0)
            prefs.prefs.edit {
                if (!prefs.contains(new)) putInt(new, value)
                remove(old)
            }
        }
    }
}
