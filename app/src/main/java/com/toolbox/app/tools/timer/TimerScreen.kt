package com.toolbox.app.tools.timer

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberNotificationAccess
import com.toolbox.app.ui.common.NotificationsOffCard
import com.toolbox.app.ui.common.rememberStored
import java.util.Date

@Composable
fun TimerScreen(onBack: () -> Unit) {
    ToolScaffold(title = "Timer", onBack = onBack) { padding ->
        ToolBody(padding) { TimerPane() }
    }
}

@Composable
private fun TimerPane() {
    val context = LocalContext.current
    val timer = remember(context) { TimerController(context) }
    val state by rememberStored(timer.prefs) { timer.state }
    // Refreshing re-posts the countdown once it's allowed, and catches a reboot.
    val notifications = rememberNotificationAccess("timer_notifications_asked", { timer.canNotify }, { timer.refresh() })
    fun start() {
        timer.start()
        notifications.askOnce()
    }

    var now by remember { mutableLongStateOf(TimerController.now()) }
    LaunchedEffect(state.phase, state.endAt) {
        now = TimerController.now()
        while (state.phase == TimerPhase.Running) {
            withFrameNanos { }
            now = TimerController.now()
            if (state.isDue(now)) timer.ringIfDue()
        }
    }

    val ringing = state.phase == TimerPhase.Ringing

    if (!notifications.canNotify && (notifications.asked || state.phase != TimerPhase.Idle)) {
        NotificationsOffCard(
            "Notifications are off, so the countdown and the alarm's Stop button can't show outside the app. " +
                "The alarm still rings; open Toolbox to stop it.",
            onAllow = notifications::allow,
        )
        Spacer(Modifier.height(24.dp))
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        val arc = if (ringing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        val fraction = state.progress(now)
        // Drawing only: the time in the middle says how much is left.
        Canvas(Modifier.size(280.dp).clearAndSetSemantics {}) {
            val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(arc, -90f, 360f * fraction, false, style = stroke)
        }
        // Inset from the ring, so the time shrinks to stay inside it at large font sizes.
        Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (ringing) {
                val style = MaterialTheme.typography.displaySmall
                Text(
                    "Time's up",
                    style = style,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            } else {
                val style = MaterialTheme.typography.displayMedium
                Text(
                    formatClock(state.remaining(now) + 999),
                    style = style,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = style.fontSize),
                )
            }
            val caption = when (state.phase) {
                TimerPhase.Running -> "Rings at " + ringTime(context, state.endAt)
                TimerPhase.Paused -> "Paused"
                else -> null
            }
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    // Setting a time is for a stopped or paused timer; paused, the ± chips change the time left. The new time is said
    // aloud, as the clock it changes isn't where TalkBack is.
    val say = rememberAnnouncer()
    fun sayTimeLeft() = say(spokenDuration(timer.state.remainingMs))
    val editable = state.phase == TimerPhase.Idle || state.phase == TimerPhase.Paused
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        listOf(1, 3, 5, 10, 15).forEach { min ->
            SuggestionChip(
                onClick = { timer.set(min * 60_000L); sayTimeLeft() },
                label = { Text("${min}m") },
                enabled = editable,
                // "1m" would be read as a metre.
                modifier = Modifier.semantics { contentDescription = spokenDuration(min * 60_000L) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        // Short labels, like the presets, so the four fit on one row; TalkBack reads the long form.
        listOf(
            Triple(-60_000L, "−1m", "Take off 1 minute"),
            Triple(-10_000L, "−10s", "Take off 10 seconds"),
            Triple(10_000L, "+10s", "Add 10 seconds"),
            Triple(60_000L, "+1m", "Add 1 minute"),
        ).forEach { (d, label, spoken) ->
            SuggestionChip(
                onClick = { timer.adjust(d); sayTimeLeft() },
                label = { Text(label) },
                enabled = editable,
                modifier = Modifier.semantics { contentDescription = spoken },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (ringing) {
            FilledTonalButton(onClick = { timer.adjust(60_000L) }) { Text("+1 min") }
            Button(onClick = { timer.reset() }) { Text("Stop") }
        } else {
            FilledTonalButton(onClick = { timer.reset() }, enabled = state.phase != TimerPhase.Idle) { Text("Reset") }
            Button(onClick = { if (state.phase == TimerPhase.Running) timer.pause() else start() }) {
                Text(
                    when (state.phase) {
                        TimerPhase.Running -> "Pause"
                        TimerPhase.Paused -> "Resume"
                        else -> "Start"
                    },
                )
            }
        }
    }
}

/** The clock time a timer ending at elapsed-realtime [endAt] rings, like "14:32". */
@Composable
private fun ringTime(context: Context, endAt: Long): String = remember(endAt) {
    val wall = System.currentTimeMillis() + (endAt - TimerController.now())
    android.text.format.DateFormat.getTimeFormat(context).format(Date(wall))
}

/** A timer's time as TalkBack says it, such as "1 minute 30 seconds", rounded up to the second like the clock. */
internal fun spokenDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    fun part(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
    return listOfNotNull(
        part(h, "hour").takeIf { h > 0 },
        part(m, "minute").takeIf { m > 0 },
        part(s, "second").takeIf { s > 0 || total == 0L },
    ).joinToString(" ")
}
