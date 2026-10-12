package com.toolbox.app.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Notifications for a tool that keeps going outside the app, such as the Timer and the Stopwatch.
 *
 * @property canNotify the tool's notification can show.
 * @property asked notifications have been asked for (or offered) on this tool before.
 */
class NotificationAccess internal constructor(
    val canNotify: Boolean,
    val asked: Boolean,
    private val onAllow: () -> Unit,
    private val onAskOnce: () -> Unit,
) {
    /** Shows Android's dialog, or its notification settings once only they can turn notifications on. */
    fun allow() = onAllow()

    /** Asks the first time the tool starts, on Android 13 and later; after that [NotificationsOffCard] offers it. */
    fun askOnce() = onAskOnce()
}

/**
 * @param askedKey the pref remembering that this tool has asked.
 * @param check whether the tool's notification can show, which may also look at its channel.
 * @param onChange called when that may have changed (allowed, or back from the settings), to post the notification.
 */
@Composable
fun rememberNotificationAccess(askedKey: String, check: () -> Boolean, onChange: () -> Unit): NotificationAccess {
    val context = LocalContext.current
    val currentCheck by rememberUpdatedState(check)
    val currentOnChange by rememberUpdatedState(onChange)
    var canNotify by remember { mutableStateOf(check()) }
    var asked by rememberPref(askedKey, false)

    // Only asked for on Android 13+; below that Android reads it as an unknown permission, never granted.
    @SuppressLint("InlinedApi")
    val permission = rememberPermission(Manifest.permission.POST_NOTIFICATIONS) {
        canNotify = currentCheck()
        currentOnChange()
    }
    // Coming back from Android's settings, or after a reboot was spotted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = currentCheck()
        currentOnChange()
    }

    val allow = {
        // Below Android 13, or once allowed but switched off in Android's settings, only the settings can turn them on.
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context) && !permission.blocked
        asked = true
        if (canAsk) permission.request() else context.openNotificationSettings()
    }
    return NotificationAccess(canNotify, asked, allow) {
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) allow()
    }
}

/** Explains what [message] says doesn't work without notifications, with a way to allow them. */
@Composable
fun NotificationsOffCard(message: String, onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.NotificationsOff, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp),
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onAllow, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
            Text("Allow notifications")
        }
    }
}

fun hasNotificationPermission(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.openNotificationSettings() {
    val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    if (!startFirst(app)) openAppSettings()
}
