package com.toolbox.app.tools.stopwatch

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.tools.timer.formatClock
import com.toolbox.app.ui.common.KeepScreenOn
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref

@Composable
fun StopwatchScreen(onBack: () -> Unit) {
    var keepScreenOn by rememberPref("stopwatch_screen_on", false)
    ToolScaffold(
        title = "Stopwatch",
        onBack = onBack,
        actions = {
            IconToggleButton(checked = keepScreenOn, onCheckedChange = { keepScreenOn = it }) {
                Icon(
                    if (keepScreenOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                    contentDescription = "Keep screen on while running",
                )
            }
        },
    ) { padding ->
        // Top-aligned rather than centred: the lap list grows downward beneath the controls.
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { StopwatchPane(keepScreenOn) }
    }
}

/** Lap times, newest first, saved as a LongArray so they survive the screen being recreated. */
private val LapsSaver = Saver<SnapshotStateList<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toList().toMutableStateList() },
)

private fun format(ms: Long) = formatClock(ms) + ".%02d".format((ms / 10) % 100)

private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"

/** A time as TalkBack says it, such as "1 minute, 5.23 seconds", rather than reading out the colons. */
internal fun spokenTime(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    val s = (ms / 1000) % 60
    return listOfNotNull(
        plural(h, "hour").takeIf { h > 0 },
        plural(m, "minute").takeIf { h > 0 || m > 0 },
        "%d.%02d seconds".format(s, (ms / 10) % 100),
    ).joinToString(", ")
}

/** A lap as TalkBack reads it: its own time first, as that's what a lap is timed for, then the total. */
internal fun spokenLap(number: Int, split: Long, total: Long) = "Lap $number, ${spokenTime(split)}, total ${spokenTime(total)}"

@Composable
private fun StopwatchPane(keepScreenOn: Boolean) {
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()
    var running by rememberSaveable { mutableStateOf(false) }
    var accumulated by rememberSaveable { mutableLongStateOf(0L) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val laps = rememberSaveable(saver = LapsSaver) { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        while (running) { withFrameNanos { }; now = SystemClock.elapsedRealtime() }
    }
    val elapsed = accumulated + if (running) now - startedAt else 0L
    KeepScreenOn(keepScreenOn && running)

    // Not read out as it runs, which would talk over everything; Stop and Lap say the time instead.
    val big = MaterialTheme.typography.displayMedium
    Text(
        format(elapsed),
        style = big,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = big.fontSize),
    )
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledTonalButton(
            onClick = {
                if (running) {
                    say("Lap ${laps.size + 1}, ${spokenTime(elapsed - (laps.firstOrNull() ?: 0L))}")
                    laps.add(0, elapsed)
                } else {
                    accumulated = 0
                    laps.clear()
                }
                haptics.click()
            },
            enabled = running || elapsed > 0,
        ) { Text(if (running) "Lap" else "Reset") }
        Button(onClick = {
            val t = SystemClock.elapsedRealtime()
            if (running) {
                accumulated += t - startedAt
                say(spokenTime(accumulated))
            } else {
                startedAt = t
            }
            now = t
            running = !running
            haptics.click()
        }) { Text(if (running) "Stop" else "Start") }
    }
    Spacer(Modifier.height(24.dp))
    LazyColumn(Modifier.fillMaxWidth()) {
        itemsIndexed(laps) { i, lapTotal ->
            val prev = laps.getOrNull(i + 1) ?: 0L
            val number = laps.size - i
            // Wraps rather than overlapping at large font sizes; read as one line.
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).clearAndSetSemantics {
                    contentDescription = spokenLap(number, lapTotal - prev, lapTotal)
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Lap $number", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+" + format(lapTotal - prev), fontFamily = FontFamily.Monospace)
                Text(format(lapTotal), fontFamily = FontFamily.Monospace)
            }
            HorizontalDivider()
        }
    }
}
