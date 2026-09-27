package com.toolbox.app.tools.steps

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Persists [StepState] in its own SharedPreferences file. Shared by the screen and [StepSnapshotJob]; both run on
 * the main thread, and [record] is synchronised anyway so a read-modify-write can never interleave.
 */
class StepStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("steps", Context.MODE_PRIVATE)

    fun load(): StepState = StepState(
        day = prefs.getLong(DAY, 0),
        dayStartRaw = prefs.getLong(DAY_START_RAW, 0),
        lastRaw = prefs.getLong(LAST_RAW, 0),
        carried = prefs.getLong(CARRIED, 0),
        lifetime = prefs.getLong(LIFETIME, 0),
        history = decodeHistory(prefs.getString(HISTORY, "") ?: ""),
        started = prefs.getBoolean(STARTED, false),
    )

    /** When counting began (first reading ever), in epoch millis; 0 if it hasn't yet. */
    val startedAtMillis: Long get() = prefs.getLong(STARTED_AT, 0)

    /** Applies a counter reading taken now and saves the result. */
    fun record(raw: Long, today: Long = LocalDate.now().toEpochDay()): StepState = synchronized(LOCK) {
        val before = load()
        val after = applyReading(before, raw, today)
        prefs.edit {
            putLong(DAY, after.day)
            putLong(DAY_START_RAW, after.dayStartRaw)
            putLong(LAST_RAW, after.lastRaw)
            putLong(CARRIED, after.carried)
            putLong(LIFETIME, after.lifetime)
            if (after.history != before.history) putString(HISTORY, encodeHistory(after.history))
            putBoolean(STARTED, true)
            if (!before.started) putLong(STARTED_AT, System.currentTimeMillis())
        }
        after
    }

    private companion object {
        val LOCK = Any()
        const val DAY = "day"
        const val DAY_START_RAW = "day_start_raw"
        const val LAST_RAW = "last_raw"
        const val CARRIED = "carried"
        const val LIFETIME = "lifetime"
        const val HISTORY = "history"
        const val STARTED = "started"
        const val STARTED_AT = "started_at"
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
