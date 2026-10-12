package com.toolbox.app.tools.coin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.Stat
import com.toolbox.app.ui.common.StatRow
import com.toolbox.app.ui.common.rememberPref

/** [CoinStats] persisted under [key], so the counts carry on across visits. */
@Composable
fun rememberCoinStats(key: String): MutableState<CoinStats> {
    val pref = rememberPref(key, "")
    return remember(pref) {
        object : MutableState<CoinStats> {
            override var value: CoinStats
                get() = CoinStats.decode(pref.value)
                set(v) { pref.value = v.encode() }
            override fun component1() = value
            override fun component2(): (CoinStats) -> Unit = { value = it }
        }
    }
}

/** Heads/tails counts, the current streak and a strip of the last ten results (newest on the right). */
@Composable
fun CoinStatsPanel(stats: CoinStats, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StatRow(gap = 40.dp) {
            val shares = stats.percentages()
            Stat(shareLabel("Heads", shares?.first), "${stats.heads}")
            Stat(shareLabel("Tails", shares?.second), "${stats.tails}")
        }
        Text(
            stats.streakText() ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        val history = stats.history
        Row(
            Modifier
                .padding(top = 8.dp)
                .heightIn(min = 26.dp) // grows for the placeholder text at large font sizes
                .clearAndSetSemantics {
                    contentDescription = if (history.isEmpty()) "No results yet"
                    else "Last ${history.length}, oldest first: " + history.map { if (it == 'H') "heads" else "tails" }.joinToString()
                },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (history.isEmpty()) {
                Text("History appears here", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            history.forEachIndexed { i, c -> HistoryChip(c == 'H', newest = i == history.lastIndex) }
        }
    }
}

private fun shareLabel(label: String, percent: Int?) = if (percent != null) "$label · $percent%" else label

/** Shared by every chip: the paths are built once. */
private val chipArt by lazy { CoinArt() }

/** A tiny coin face, crown for heads and feather for tails, so the strip reads at a glance. */
@Composable
private fun HistoryChip(heads: Boolean, newest: Boolean) {
    val size = if (newest) 24.dp else 20.dp
    Canvas(Modifier.size(size)) { chipArt.drawFlat(this, center, this.size.minDimension / 2, heads) }
}
