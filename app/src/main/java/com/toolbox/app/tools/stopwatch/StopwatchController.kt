package com.toolbox.app.tools.stopwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit

/**
 * The one stopwatch: its stored [StopwatchState] and its notification. The screen and [StopwatchReceiver] (the
 * notification's buttons) both drive it, so it keeps counting after leaving the screen or the app, after Android stops
 * the app's process, and after a reboot.
 *
 * Every change is written straight to storage, then the notification is brought in line with it.
 */
class StopwatchController(context: Context) {
    private val app = context.applicationContext
    internal val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val notification = StopwatchNotification(app)

    /** The stored stopwatch, already checked against the current boot. */
    val state: StopwatchState
        get() = StopwatchState.decode(prefs.getString(KEY, null)).afterBoot(bootCount(), now(), wall())

    /** Whether its notification can show outside the app. */
    val canNotify: Boolean get() = notification.allowed()

    fun start() = update(show = true) { it.start(now(), wall()) }
    fun pause() = update { it.pause(now()) }
    fun lap() = update { it.lap(now()) }
    fun reset() = update(show = true) { it.reset() }

    /** Re-posts the notification, say after notifications were allowed or the phone restarted. */
    fun refresh() = update { it }

    /** The notification was swiped away: it stays away until the stopwatch is next started or reset. */
    fun hide() {
        prefs.edit(commit = true) { putBoolean(KEY_HIDDEN, true) }
        notification.cancel()
    }

    /** @param show brings back a notification that was swiped away. */
    private fun update(show: Boolean = false, change: (StopwatchState) -> StopwatchState) {
        val stored = prefs.getString(KEY, null)
        val next = change(state).copy(boot = bootCount())
        val encoded = next.encode()
        // commit, not apply: a notification button's broadcast can be the last thing the process does.
        if (encoded != stored || show) {
            prefs.edit(commit = true) {
                putString(KEY, encoded)
                if (show) remove(KEY_HIDDEN)
            }
        }
        if (prefs.getBoolean(KEY_HIDDEN, false)) notification.cancel() else notification.show(next, now(), wall())
    }

    private fun bootCount() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, 0)

    companion object {
        internal const val PREFS = "stopwatch"
        private const val KEY = "state"
        private const val KEY_HIDDEN = "notification_hidden"

        internal const val ACTION_PAUSE = "com.toolbox.app.stopwatch.PAUSE"
        internal const val ACTION_RESUME = "com.toolbox.app.stopwatch.RESUME"
        internal const val ACTION_LAP = "com.toolbox.app.stopwatch.LAP"
        internal const val ACTION_RESET = "com.toolbox.app.stopwatch.RESET"
        internal const val ACTION_HIDE = "com.toolbox.app.stopwatch.HIDE"

        fun now() = SystemClock.elapsedRealtime()
        fun wall() = System.currentTimeMillis()
    }
}

/** The buttons on the stopwatch's notification, and it being swiped away. */
class StopwatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stopwatch = StopwatchController(context)
        when (intent.action) {
            StopwatchController.ACTION_PAUSE -> stopwatch.pause()
            StopwatchController.ACTION_RESUME -> stopwatch.start()
            StopwatchController.ACTION_LAP -> stopwatch.lap()
            StopwatchController.ACTION_RESET -> stopwatch.reset()
            StopwatchController.ACTION_HIDE -> stopwatch.hide()
        }
    }
}

/** Posts a running or paused stopwatch's notification again after the phone restarts. */
class StopwatchBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) StopwatchController(context).refresh()
    }
}
