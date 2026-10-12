package com.toolbox.app.tools.timer

import android.app.Notification
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
import java.util.Date

/**
 * The timer's two notifications. While it runs or is paused, a quiet countdown with its buttons. When it ends,
 * "Time's up" with Stop and +1 min, shown by [TimerAlarmService], which also plays the alarm sound. Both channels are
 * silent, so the sound never doubles up on phones that do play notification sounds.
 */
internal class TimerNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Notifications are allowed, and the alarm channel hasn't been turned off. */
    fun allowed(): Boolean {
        createChannels()
        if (!hasNotificationPermission(context)) return false
        if (!manager.areNotificationsEnabled()) return false
        return manager.getNotificationChannel(ALARM_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(state: TimerState, now: Long) {
        if (!allowed()) return
        when (state.phase) {
            TimerPhase.Idle -> { manager.cancel(COUNTDOWN_ID); manager.cancel(ALARM_ID) }
            TimerPhase.Running, TimerPhase.Paused -> { manager.cancel(ALARM_ID); post(COUNTDOWN_ID, countdown(state, now)) }
            TimerPhase.Ringing -> manager.cancel(COUNTDOWN_ID) // TimerAlarmService shows the alarm.
        }
    }

    private fun post(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission taken away between the check and here.
        }
    }

    private fun countdown(state: TimerState, now: Long): Notification {
        val left = state.remaining(now)
        val builder = base(COUNTDOWN_CHANNEL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        return if (state.phase == TimerPhase.Running) {
            val endsAt = System.currentTimeMillis() + left
            builder
                .setContentTitle("Timer")
                .setContentText("Rings at " + android.text.format.DateFormat.getTimeFormat(context).format(Date(endsAt)))
                .setWhen(endsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(0, "+1 min", broadcast(TimerController.ACTION_ADD_MINUTE))
                .addAction(0, "Stop", broadcast(TimerController.ACTION_STOP))
                .build()
        } else {
            builder
                .setContentTitle("Timer paused")
                .setContentText(formatClock(left + 999) + " left")
                .setShowWhen(false)
                .addAction(0, "Resume", broadcast(TimerController.ACTION_RESUME))
                .addAction(0, "Stop", broadcast(TimerController.ACTION_STOP))
                .build()
        }
    }

    /** The ringing timer, for [TimerAlarmService] to show while it plays the alarm. */
    fun alarm(state: TimerState): Notification {
        createChannels()
        return base(ALARM_CHANNEL)
            .setContentTitle("Time's up")
            .setContentText(describeDuration(state.totalMs) + " timer")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setShowWhen(false)
            .setDeleteIntent(broadcast(TimerController.ACTION_STOP)) // Where it can be swiped away, that stops it.
            .addAction(0, "+1 min", broadcast(TimerController.ACTION_ADD_MINUTE))
            .addAction(0, "Stop", broadcast(TimerController.ACTION_STOP))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_timer)
        .setColor(0xFFFBBC04.toInt()) // The Timer tile's yellow.
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(openTimerIntent(context))

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        if (system.getNotificationChannel(COUNTDOWN_CHANNEL) == null) {
            system.createNotificationChannel(
                NotificationChannel(COUNTDOWN_CHANNEL, "Timer countdown", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows a running timer, with buttons to add a minute or stop it."
                    setShowBadge(false)
                },
            )
        }
        if (system.getNotificationChannel(ALARM_CHANNEL) == null) {
            system.createNotificationChannel(
                NotificationChannel(ALARM_CHANNEL, "Timer alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Shows when a timer ends. The Timer plays the alarm sound itself, at alarm volume."
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                },
            )
        }
        // An early build's alarm channel played the sound itself; it was only ever on test phones.
        system.deleteNotificationChannel("timer_alarm")
    }

    companion object {
        private const val COUNTDOWN_CHANNEL = "timer_countdown"
        private const val ALARM_CHANNEL = "timer_ringing"
        private const val COUNTDOWN_ID = 1001
        const val ALARM_ID = 1002
    }
}

/** Opens the Timer screen, from a notification or the alarm icon in the status bar. */
internal fun openTimerIntent(context: Context): PendingIntent = MainActivity.openToolIntent(context, Tool.Timer)

/** "4:05" or "1:02:03", for a time left. */
fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "5 min", "1 h 30 min", "45 s" or "2 min 10 s", for a timer's length. */
fun describeDuration(ms: Long): String {
    val total = (ms + 500) / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    return listOfNotNull(
        "$h h".takeIf { h > 0 },
        "$m min".takeIf { m > 0 },
        "$s s".takeIf { s > 0 || total == 0L },
    ).joinToString(" ")
}
