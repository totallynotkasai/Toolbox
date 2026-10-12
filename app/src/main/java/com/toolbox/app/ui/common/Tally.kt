package com.toolbox.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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

    /** One line such as "Games: You 3 · CPU 2 · Drawn 1". Draws are left out of games that can't have any. */
    fun summary(noun: String, aName: String, bName: String, withDraws: Boolean = true): String =
        "$noun: $aName $a · $bName $b" + if (withDraws) " · Drawn $draws" else ""

    companion object {
        fun decode(s: String): Tally {
            val p = s.split(',').mapNotNull { it.toIntOrNull() }
            return if (p.size == 3) Tally(p[0], p[1], p[2]) else Tally()
        }
    }
}

/**
 * A persisted [Tally]. [value] and [record] always read what is saved now, so a result recorded from a long-running
 * effect can't undo a "Reset scores" made in the meantime.
 */
@Stable
class TallyState internal constructor(private val stored: MutableState<String>) {
    val value: Tally get() = Tally.decode(stored.value)
    fun record(winner: Int) { stored.value = value.record(winner).encode() }
    fun reset() { stored.value = Tally().encode() }
}

/** `val tally = rememberTally("dots_tally_Easy")`, then `tally.value.a` and `tally.record(1)`. */
@Composable
fun rememberTally(key: String): TallyState {
    val stored = rememberPref(key, "")
    return remember(stored) { TallyState(stored) }
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

/** One entry of a [ScoreRow]. */
data class Score(val label: String, val value: Int, val color: Color? = null)

/** The scoreboard above a board: evenly spaced [ScoreColumn]s, read by TalkBack as one line ("You 3, Draws 1, CPU 2"). */
@Composable
fun ScoreRow(scores: List<Score>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = scores.joinToString(", ") { "${it.label} ${it.value}" } },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (s in scores) ScoreColumn(s.label, s.value, s.color)
    }
}
