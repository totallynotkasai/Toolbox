package com.toolbox.app.tools.timer

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberPref
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
    val state by rememberTimerState(timer)
    var canNotify by remember { mutableStateOf(timer.canNotify) }
    var asked by rememberPref("timer_notifications_asked", false)

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = timer.canNotify
        timer.refresh() // Post the countdown now that it's allowed.
    }
    fun allowNotifications() {
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(context) &&
            (!asked || context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true)
        asked = true
        if (canAsk) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else context.openNotificationSettings()
    }
    fun start() {
        timer.start()
        // Asked once, the first time a timer starts; after that the card below offers it.
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(context)) allowNotifications()
    }

    // Coming back from Android's settings, or after a reboot was spotted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = timer.canNotify
        timer.refresh()
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

    if (!canNotify && (asked || state.phase != TimerPhase.Idle)) {
        NotificationsOffCard(onAllow = ::allowNotifications)
        Spacer(Modifier.height(24.dp))
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        val arc = if (ringing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        val fraction = state.progress(now)
        Canvas(Modifier.size(280.dp)) {
            val stroke = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(arc, -90f, 360f * fraction, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (ringing) {
                Text("Time's up", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    formatClock(state.remaining(now) + 999),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            val caption = when (state.phase) {
                TimerPhase.Running -> "Rings at " + ringTime(context, state.endAt)
                TimerPhase.Paused -> "Paused"
                else -> null
            }
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    // Setting a time is for a stopped or paused timer; paused, the ± chips change the time left.
    val editable = state.phase == TimerPhase.Idle || state.phase == TimerPhase.Paused
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        listOf(1, 3, 5, 10, 15).forEach { min ->
            SuggestionChip(onClick = { timer.set(min * 60_000L) }, label = { Text("${min}m") }, enabled = editable)
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
                onClick = { timer.adjust(d) },
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

/** Explains what doesn't work without notifications, with a way to allow them. */
@Composable
private fun NotificationsOffCard(onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.NotificationsOff, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                "Notifications are off, so the countdown and the alarm's Stop button can't show outside the app. " +
                    "The alarm still rings; open Toolbox to stop it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onAllow, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
            Text("Allow notifications")
        }
    }
}

/** The stored timer, updated whenever the screen, the alarm or a notification button changes it. */
@Composable
private fun rememberTimerState(timer: TimerController): State<TimerState> {
    val state = remember(timer) { mutableStateOf(timer.state) }
    DisposableEffect(timer) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = timer.state }
        timer.prefs.registerOnSharedPreferenceChangeListener(listener)
        state.value = timer.state
        onDispose { timer.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/** The clock time a timer ending at elapsed-realtime [endAt] rings, like "14:32". */
@Composable
private fun ringTime(context: Context, endAt: Long): String = remember(endAt) {
    val wall = System.currentTimeMillis() + (endAt - TimerController.now())
    android.text.format.DateFormat.getTimeFormat(context).format(Date(wall))
}

private fun hasPermission(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.openNotificationSettings() {
    val channel = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    for (intent in listOf(channel, details)) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
