package com.toolbox.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Wins for each side of a two-sided game, plus draws, stored in [Prefs] as "a,b,draws". */
data class Tally(val a: Int = 0, val b: Int = 0, val draws: Int = 0) {
    fun encode() = "$a,$b,$draws"

    /** Adds a result: 1 is a win for side a, 2 for side b, 0 a draw. */
    fun record(winner: Int) = when (winner) {
        1 -> copy(a = a + 1)
        2 -> copy(b = b + 1)
        else -> copy(draws = draws + 1)
    }

    companion object {
        fun decode(s: String): Tally {
            val p = s.split(',').mapNotNull { it.toIntOrNull() }
            return if (p.size == 3) Tally(p[0], p[1], p[2]) else Tally()
        }
    }
}

/** A persisted [Tally]: `val (tally, setTally) = rememberTally("dots_tally_easy")`. */
@Composable
fun rememberTally(key: String): Pair<Tally, (Tally) -> Unit> {
    var stored by rememberPref(key, "")
    return Tally.decode(stored) to { t: Tally -> stored = t.encode() }
}

/** One column of a scoreboard: a big number over a label, with an optional colour dot. */
@Composable
fun ScoreColumn(label: String, value: Int, color: Color?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (color != null) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
