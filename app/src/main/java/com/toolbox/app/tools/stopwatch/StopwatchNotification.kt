package com.toolbox.app.tools.stopwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.toolbox.app.MainActivity
import com.toolbox.app.R
import com.toolbox.app.Tool
import com.toolbox.app.ui.common.hasNotificationPermission

/**
 * The stopwatch's quiet notification. Running, it shows a chronometer that Android ticks by itself, so nothing in the
 * app has to stay awake; paused, it shows the time it stopped at. Either way its buttons work without opening the app.
 */
internal class StopwatchNotification(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Notifications are allowed, and the stopwatch's channel hasn't been turned off. */
    fun allowed(): Boolean {
        createChannel()
        return hasNotificationPermission(context) && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(state: StopwatchState, now: Long, wall: Long) {
        if (!allowed()) return
        if (state.isClear) { cancel(); return }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setColor(0xFFEA4335.toInt()) // The Stopwatch tile's red.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(MainActivity.openToolIntent(context, Tool.Stopwatch))
            // Android 14 lets even a running one be swiped away; it stays away until the stopwatch is next started.
            .setDeleteIntent(broadcast(StopwatchController.ACTION_HIDE))
        val lapText = state.lapRows().firstOrNull()?.let { "Lap ${it.number}  +" + formatStopwatch(it.split) }
        val notification = if (state.running) {
            builder
                .setContentTitle("Stopwatch")
                .setContentText(lapText ?: "Running")
                .setOngoing(true)
                .setWhen(state.virtualStart(now, wall))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .addAction(0, "Pause", broadcast(StopwatchController.ACTION_PAUSE))
                .addAction(0, "Lap", broadcast(StopwatchController.ACTION_LAP))
                .build()
        } else {
            builder
                .setContentTitle("Stopwatch paused at " + formatStopwatch(state.elapsed(now)))
                .setContentText(lapText)
                .setShowWhen(false)
                .addAction(0, "Resume", broadcast(StopwatchController.ACTION_RESUME))
                .addAction(0, "Reset", broadcast(StopwatchController.ACTION_RESET))
                .build()
        }
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
            // Permission taken away between the check and here.
        }
    }

    fun cancel() = manager.cancel(ID)

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, StopwatchReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        if (system.getNotificationChannel(CHANNEL) != null) return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, "Stopwatch", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows a running or paused stopwatch, with buttons to pause, lap, resume or reset it."
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL = "stopwatch"
        private const val ID = 1003 // The Timer uses 1001 and 1002.
    }
}
