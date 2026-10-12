package com.toolbox.app.tools.stopwatch

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.KeepScreenOn
import com.toolbox.app.ui.common.NotificationsOffCard
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberNotificationAccess
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberStored

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
    val context = LocalContext.current
    val stopwatch = remember(context) { StopwatchController(context) }
    // Changed by the notification's buttons too, so it's read back from storage rather than kept here.
    val state by rememberStored(stopwatch.prefs) { stopwatch.state }
    // Refreshing re-posts the notification once it's allowed, and catches a reboot.
    val notifications = rememberNotificationAccess(
        "stopwatch_notifications_asked", { stopwatch.canNotify }, { stopwatch.refresh() },
    )
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()

    var now by remember { mutableLongStateOf(StopwatchController.now()) }
    LaunchedEffect(state.running, state.startedAt) {
        now = StopwatchController.now()
        while (state.running) { withFrameNanos { }; now = StopwatchController.now() }
    }
    val elapsed = state.elapsed(now)
    KeepScreenOn(keepScreenOn && state.running)

    if (!notifications.canNotify && (notifications.asked || !state.isClear)) {
        NotificationsOffCard(
            "Notifications are off, so the stopwatch and its Pause and Lap buttons can't show outside the app. " +
                "It still keeps counting.",
            onAllow = notifications::allow,
        )
        Spacer(Modifier.height(24.dp))
    }

    // Not read out as it runs, which would talk over everything; Pause and Lap say the time instead.
    val big = MaterialTheme.typography.displayMedium
    Text(
        formatStopwatch(elapsed),
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
                if (state.running) {
                    stopwatch.lap()
                    stopwatch.state.lapRows().firstOrNull()?.let { say("Lap ${it.number}, ${spokenTime(it.split)}") }
                } else {
                    stopwatch.reset()
                }
                haptics.click()
            },
            enabled = !state.isClear,
        ) { Text(if (state.running) "Lap" else "Reset") }
        Button(onClick = {
            if (state.running) {
                stopwatch.pause()
                say(spokenTime(stopwatch.state.bankedMs))
            } else {
                stopwatch.start()
                notifications.askOnce()
            }
            haptics.click()
        }) {
            Text(
                when {
                    state.running -> "Pause"
                    state.bankedMs > 0 -> "Resume"
                    else -> "Start"
                },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    val rows = remember(state.laps, state.dropped, state.droppedAt) { state.lapRows() }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(rows, key = { it.number }) { lap ->
            // Wraps rather than overlapping at large font sizes; read as one line.
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).clearAndSetSemantics {
                    contentDescription = spokenLap(lap.number, lap.split, lap.total)
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Lap ${lap.number}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+" + formatStopwatch(lap.split), fontFamily = FontFamily.Monospace)
                Text(formatStopwatch(lap.total), fontFamily = FontFamily.Monospace)
            }
            HorizontalDivider()
        }
    }
}
