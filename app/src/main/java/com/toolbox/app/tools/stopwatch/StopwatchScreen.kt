package com.toolbox.app.tools.stopwatch

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics

@Composable
fun TimerScreen(onBack: () -> Unit) {
    ToolScaffold(title = "Timer", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { TimerPane() }
    }
}

@Composable
fun StopwatchScreen(onBack: () -> Unit) {
    ToolScaffold(title = "Stopwatch", onBack = onBack) { padding ->
        // Top-aligned rather than centred: the lap list grows downward beneath the controls.
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { StopwatchPane() }
    }
}

/** Lap times, newest first, saved as a LongArray so they survive the screen being recreated. */
private val LapsSaver = Saver<SnapshotStateList<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toList().toMutableStateList() },
)

private fun format(ms: Long, hundredths: Boolean): String {
    val total = ms / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (hundredths) base + ".%02d".format((ms / 10) % 100) else base
}

@Composable
private fun TimerPane() {
    val haptics = rememberHaptics()
    var durationMs by rememberSaveable { mutableLongStateOf(5 * 60_000L) }
    var remainingMs by rememberSaveable { mutableLongStateOf(durationMs) }
    var running by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_ALARM, 90) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val endAt = SystemClock.elapsedRealtime() + remainingMs
        while (running) {
            withFrameNanos { }
            remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            if (remainingMs == 0L) {
                running = false
                finished = true
                haptics.heavy()
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
            }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        val arc = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        val fraction = if (durationMs == 0L) 0f else remainingMs.toFloat() / durationMs
        Canvas(Modifier.size(280.dp)) {
            val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(arc, -90f, 360f * fraction, false, style = stroke)
        }
        Text(
            if (finished) "Time's up" else format(remainingMs + 999, hundredths = false),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 3, 5, 10, 15).forEach { min ->
            SuggestionChip(
                onClick = { durationMs = min * 60_000L; remainingMs = durationMs; running = false; finished = false },
                label = { Text("${min}m") },
                enabled = !running,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-60_000L to "−1 min", -10_000L to "−10 s", 10_000L to "+10 s", 60_000L to "+1 min").forEach { (d, label) ->
            SuggestionChip(
                onClick = {
                    durationMs = (durationMs + d).coerceIn(10_000L, 24 * 3_600_000L)
                    remainingMs = durationMs; finished = false
                },
                label = { Text(label) },
                enabled = !running,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledTonalButton(onClick = { running = false; finished = false; remainingMs = durationMs; tone.stopTone() }) { Text("Reset") }
        Button(onClick = {
            if (finished) { finished = false; remainingMs = durationMs; tone.stopTone() }
            running = !running
        }) { Text(if (running) "Pause" else "Start") }
    }
}

@Composable
private fun StopwatchPane() {
    val haptics = rememberHaptics()
    var running by rememberSaveable { mutableStateOf(false) }
    var accumulated by rememberSaveable { mutableLongStateOf(0L) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val laps = rememberSaveable(saver = LapsSaver) { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        while (running) { withFrameNanos { }; now = SystemClock.elapsedRealtime() }
    }
    val elapsed = accumulated + if (running) now - startedAt else 0L

    Text(format(elapsed, hundredths = true), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace)
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledTonalButton(
            onClick = {
                if (running) laps.add(0, elapsed) else { accumulated = 0; laps.clear() }
                haptics.click()
            },
            enabled = running || elapsed > 0,
        ) { Text(if (running) "Lap" else "Reset") }
        Button(onClick = {
            val t = SystemClock.elapsedRealtime()
            if (running) accumulated += t - startedAt else startedAt = t
            now = t
            running = !running
            haptics.click()
        }) { Text(if (running) "Stop" else "Start") }
    }
    Spacer(Modifier.height(24.dp))
    LazyColumn(Modifier.fillMaxWidth()) {
        itemsIndexed(laps) { i, lapTotal ->
            val prev = laps.getOrNull(i + 1) ?: 0L
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Lap ${laps.size - i}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+" + format(lapTotal - prev, true), fontFamily = FontFamily.Monospace)
                Text(format(lapTotal, true), fontFamily = FontFamily.Monospace)
            }
            HorizontalDivider()
        }
    }
}
