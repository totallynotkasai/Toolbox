package com.toolbox.app.tools.steps

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Persists [StepState] in its own SharedPreferences file, which is left out of backups and device transfers: its
 * counter values only mean something on the phone, and since the boot, that recorded them. Shared by the screen
 * and [StepSnapshotJob]. One copy of the state is kept for the whole process and every change goes through it under
 * a lock, so the two can never undo each other's readings.
 */
class StepStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("steps", Context.MODE_PRIVATE)

    fun load(): StepState = synchronized(LOCK) { cached ?: read().also { cached = it; saved = it } }

    /**
     * Applies a counter reading taken now. With [throttle], as for the screen's stream of readings (about two a
     * second while walking), the running count is saved at most once every [SAVE_EVERY_MS]; call [flush] when the
     * screen stops. A new day or a reboot is always saved at once. Losing an unsaved count costs nothing: the next
     * reading measures from the last saved one.
     */
    fun record(raw: Long, throttle: Boolean = false, today: Long = LocalDate.now().toEpochDay()): StepState = synchronized(LOCK) {
        val now = System.currentTimeMillis()
        val before = load()
        val after = applyReading(before, raw, today, now, bootCount())
        cached = after
        if (!throttle || mustSave(before, after) || now - savedAt >= SAVE_EVERY_MS) write(after)
        after
    }

    /** Saves a count held back by a throttled [record]. */
    fun flush() = synchronized(LOCK) { cached?.let { if (it != saved) write(it) } }

    private fun read() = StepState(
        day = prefs.getLong(DAY, 0),
        dayStartRaw = prefs.getLong(DAY_START_RAW, 0),
        lastRaw = prefs.getLong(LAST_RAW, 0),
        carried = prefs.getLong(CARRIED, 0),
        lifetime = prefs.getLong(LIFETIME, 0),
        history = decodeHistory(prefs.getString(HISTORY, "") ?: ""),
        started = prefs.getBoolean(STARTED, false),
        lastAt = prefs.getLong(LAST_AT, 0),
        boot = prefs.getInt(BOOT, -1),
        countingSince = prefs.getLong(COUNTING_SINCE, 0),
        afterGap = prefs.getBoolean(AFTER_GAP, false),
        gapSince = prefs.getLong(GAP_SINCE, 0),
    )

    private fun write(s: StepState) {
        val old = saved
        prefs.edit {
            putLong(DAY, s.day)
            putLong(DAY_START_RAW, s.dayStartRaw)
            putLong(LAST_RAW, s.lastRaw)
            putLong(CARRIED, s.carried)
            putLong(LIFETIME, s.lifetime)
            if (s.history != old?.history) putString(HISTORY, encodeHistory(s.history))
            putBoolean(STARTED, s.started)
            putLong(LAST_AT, s.lastAt)
            putInt(BOOT, s.boot)
            putLong(COUNTING_SINCE, s.countingSince)
            putBoolean(AFTER_GAP, s.afterGap)
            putLong(GAP_SINCE, s.gapSince)
        }
        saved = s
        savedAt = System.currentTimeMillis()
    }

    /** How many times the phone has booted, which tells a reboot apart from a counter that simply went up. */
    private fun bootCount(): Int = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private companion object {
        val LOCK = Any()
        /** The state as of the latest reading, and as last written; null until first loaded. */
        var cached: StepState? = null
        var saved: StepState? = null
        var savedAt = 0L
        const val SAVE_EVERY_MS = 60_000L

        const val DAY = "day"
        const val DAY_START_RAW = "day_start_raw"
        const val LAST_RAW = "last_raw"
        const val CARRIED = "carried"
        const val LIFETIME = "lifetime"
        const val HISTORY = "history"
        const val STARTED = "started"
        const val LAST_AT = "last_at"
        const val BOOT = "boot"
        const val COUNTING_SINCE = "counting_since"
        const val AFTER_GAP = "after_gap"
        const val GAP_SINCE = "gap_since"
    }
}

/**
 * Schedules [StepSnapshotJob]: an hourly job that keeps `lastRaw` fresh (so a reboot loses at most an hour of steps),
 * and a one-off job just after midnight that closes off the day. The midnight job alternates between two ids, each
 * run scheduling the other, because re-scheduling the id of a running job would cancel it.
 * All jobs are persisted, so they come back after a reboot.
 */
object StepScheduler {
    const val JOB_PERIODIC = 41_001
    const val JOB_MIDNIGHT_A = 41_002
    const val JOB_MIDNIGHT_B = 41_003

    private const val HOUR_MS = 60 * 60 * 1000L
    private const val AFTER_MIDNIGHT_MS = 60 * 1000L // A minute past, so the date has definitely changed.
    private const val MIDNIGHT_WINDOW_MS = 30 * 60 * 1000L

    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Schedules whichever jobs are missing. Cheap to call every time the screen opens. */
    fun ensureScheduled(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_PERIODIC) == null) {
            scheduler.schedule(
                JobInfo.Builder(JOB_PERIODIC, component(context))
                    .setPeriodic(HOUR_MS, 15 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }
        if (scheduler.getPendingJob(JOB_MIDNIGHT_A) == null && scheduler.getPendingJob(JOB_MIDNIGHT_B) == null) {
            scheduleMidnight(context, JOB_MIDNIGHT_A)
        }
    }

    /** Schedules the midnight job that follows the one with id [runningId]. */
    fun scheduleNextMidnight(context: Context, runningId: Int) =
        scheduleMidnight(context, if (runningId == JOB_MIDNIGHT_A) JOB_MIDNIGHT_B else JOB_MIDNIGHT_A)

    private fun scheduleMidnight(context: Context, id: Int) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val now = ZonedDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val delay = Duration.between(now, midnight).toMillis() + AFTER_MIDNIGHT_MS
        scheduler.schedule(
            JobInfo.Builder(id, component(context))
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + MIDNIGHT_WINDOW_MS)
                .setPersisted(true)
                .build(),
        )
    }

    private fun component(context: Context) = ComponentName(context, StepSnapshotJob::class.java)
}
