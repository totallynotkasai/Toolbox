package com.toolbox.app.tools.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * The one timer: its stored [TimerState], the exact alarm that ends it, its notification and the [TimerAlarmService]
 * that rings. The screen and [TimerReceiver] (the alarm and the notification buttons) both drive it, so the timer
 * carries on after leaving the screen or the app, and after Android stops the app's process.
 *
 * Every change is written straight to storage, then the alarm, notification and ringing are brought in line with it.
 */
class TimerController(context: Context) {
    private val app = context.applicationContext
    internal val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val notifications = TimerNotifications(app)

    /** The stored timer, already checked against the current boot. */
    val state: TimerState get() = TimerState.decode(prefs.getString(KEY, null)).afterBoot(bootCount())

    /** Whether the countdown and the alarm's Stop button can show outside the app. */
    val canNotify: Boolean get() = notifications.allowed()

    fun set(ms: Long) = update { it.set(ms) }
    fun start() = update { it.start(now()) }
    fun pause() = update { it.pause(now()) }
    fun reset() = update { it.reset() }
    fun adjust(deltaMs: Long) = update { it.adjust(deltaMs, now()) }

    /** Called by the alarm, and by the screen if it sees the time run out first (say the alarm was held back). */
    fun ringIfDue() = update { if (it.isDue(now())) it.ring() else it }

    /** Re-posts the notification and restarts the ringing if Android stopped it, say after notifications were allowed. */
    fun refresh() = update { it }

    private fun update(change: (TimerState) -> TimerState) {
        val stored = prefs.getString(KEY, null)
        val next = change(state).copy(boot = bootCount())
        val encoded = next.encode()
        // commit, not apply: a notification button's broadcast can be the last thing the process does.
        if (encoded != stored) prefs.edit(commit = true) { putString(KEY, encoded) }
        if (next.phase == TimerPhase.Running) schedule(next.endAt) else cancelAlarm()
        notifications.show(next, now())
        if (next.phase == TimerPhase.Ringing) ring() else app.stopService(Intent(app, TimerAlarmService::class.java))
    }

    private fun ring() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, TimerAlarmService::class.java))
        } catch (_: IllegalStateException) {
            // Not allowed from the background, which only happens if the alarm wasn't exact. The screen restarts it.
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        app, 0, Intent(app, TimerReceiver::class.java).setAction(ACTION_RING),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * An alarm-clock alarm rather than a plain exact one: some phones (HyperOS) freeze apps in the background and hold
     * plain alarms back until the app is opened, but always let alarm clocks through. Android may show it as the next
     * alarm while the timer runs.
     */
    private fun schedule(endAt: Long) {
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        // USE_EXACT_ALARM is granted at install from API 33. On 31-32 SCHEDULE_EXACT_ALARM can be taken away, and the
        // alarm then falls back to an inexact one; the screen still rings on time if it's open.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        val inexact = { alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAt, alarmIntent()) }
        try {
            if (exact) {
                val wallClockEnd = System.currentTimeMillis() + (endAt - now())
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(wallClockEnd, openTimerIntent(app)), alarmIntent())
            } else inexact()
        } catch (_: SecurityException) {
            inexact()
        }
    }

    private fun cancelAlarm() {
        app.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent())
    }

    private fun bootCount() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, 0)

    companion object {
        internal const val PREFS = "timer"
        private const val KEY = "state"

        internal const val ACTION_RING = "com.toolbox.app.timer.RING"
        internal const val ACTION_ADD_MINUTE = "com.toolbox.app.timer.ADD_MINUTE"
        internal const val ACTION_RESUME = "com.toolbox.app.timer.RESUME"
        internal const val ACTION_STOP = "com.toolbox.app.timer.STOP"

        fun now() = SystemClock.elapsedRealtime()
    }
}

/** The timer's alarm going off, and the buttons on its notification. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = TimerController(context)
        when (intent.action) {
            TimerController.ACTION_RING -> timer.ringIfDue()
            TimerController.ACTION_ADD_MINUTE -> timer.adjust(60_000L)
            TimerController.ACTION_RESUME -> timer.start()
            TimerController.ACTION_STOP -> timer.reset()
        }
    }
}
