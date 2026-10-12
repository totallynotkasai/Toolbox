package com.toolbox.app.tools.stopwatch

import com.toolbox.app.tools.timer.formatClock

/**
 * The whole stopwatch, as stored. Plain logic, so the timing, reboot and lap rules have unit tests.
 *
 * Times are `SystemClock.elapsedRealtime()` milliseconds, which keep counting while the phone sleeps and while the
 * app's process is gone, so a running stopwatch is just when it started. That clock starts again from zero on a
 * reboot, so the wall-clock start and [boot] are stored too: see [afterBoot].
 *
 * @property bankedMs time counted before the last start, from earlier runs.
 * @property startedAt when a running stopwatch last started (elapsed realtime).
 * @property startedWall the same moment by the wall clock, for after a reboot.
 * @property boot `Settings.Global.BOOT_COUNT` when this was stored.
 * @property laps lap totals, newest first, at most [MAX_LAPS].
 * @property dropped how many of the oldest laps were dropped to stay under [MAX_LAPS].
 * @property droppedAt the total of the newest dropped lap, so the oldest kept lap still has its own time.
 */
data class StopwatchState(
    val running: Boolean = false,
    val bankedMs: Long = 0L,
    val startedAt: Long = 0L,
    val startedWall: Long = 0L,
    val boot: Int = 0,
    val laps: List<Long> = emptyList(),
    val dropped: Int = 0,
    val droppedAt: Long = 0L,
) {
    fun elapsed(now: Long): Long = bankedMs + if (running) (now - startedAt).coerceAtLeast(0) else 0L

    /** Nothing on it: stopped at zero with no laps, so there's nothing to reset or show. */
    val isClear: Boolean get() = !running && bankedMs == 0L && laps.isEmpty()

    /** Starts or resumes. */
    fun start(now: Long, wall: Long): StopwatchState = if (running) this else copy(running = true, startedAt = now, startedWall = wall)

    fun pause(now: Long): StopwatchState = if (!running) this else copy(running = false, bankedMs = elapsed(now))

    /** Marks a lap at the current total; only while running. Past [MAX_LAPS] the oldest is dropped. */
    fun lap(now: Long): StopwatchState {
        if (!running) return this
        val added = listOf(elapsed(now)) + laps
        if (added.size <= MAX_LAPS) return copy(laps = added)
        return copy(laps = added.take(MAX_LAPS), dropped = dropped + 1, droppedAt = added[MAX_LAPS])
    }

    fun reset() = StopwatchState(boot = boot)

    /** Every lap, newest first, with its number and its own time. */
    fun lapRows(): List<LapRow> = laps.mapIndexed { i, total ->
        val previous = laps.getOrNull(i + 1) ?: droppedAt
        LapRow(number = dropped + laps.size - i, split = total - previous, total = total)
    }

    /**
     * The wall-clock moment the stopwatch would have started had it never paused, which a notification's chronometer
     * counts up from.
     */
    fun virtualStart(now: Long, wall: Long): Long = wall - elapsed(now)

    /**
     * Checks a stored state against the current boot. A reboot resets the clock [startedAt] was measured on, so a
     * running stopwatch is worked out again from the wall clock, then carries on from now on the new clock. A paused one
     * only keeps its banked time, which doesn't depend on either clock.
     */
    fun afterBoot(currentBoot: Int, now: Long, wall: Long): StopwatchState = when {
        boot == currentBoot -> this
        running -> copy(
            bankedMs = bankedMs + (wall - startedWall).coerceAtLeast(0),
            startedAt = now,
            startedWall = wall,
            boot = currentBoot,
        )
        else -> copy(boot = currentBoot)
    }

    fun encode(): String = listOf(
        if (running) 1 else 0, bankedMs, startedAt, startedWall, boot, dropped, droppedAt, laps.joinToString(","),
    ).joinToString("|")

    companion object {
        const val MAX_LAPS = 999

        /** Reads [encode]'s output; anything unreadable gives a cleared stopwatch. */
        fun decode(text: String?): StopwatchState {
            val parts = text?.split('|') ?: return StopwatchState()
            if (parts.size != 8) return StopwatchState()
            return try {
                StopwatchState(
                    running = parts[0] == "1",
                    bankedMs = parts[1].toLong(),
                    startedAt = parts[2].toLong(),
                    startedWall = parts[3].toLong(),
                    boot = parts[4].toInt(),
                    dropped = parts[5].toInt(),
                    droppedAt = parts[6].toLong(),
                    laps = if (parts[7].isEmpty()) emptyList() else parts[7].split(',').map { it.toLong() },
                )
            } catch (_: NumberFormatException) {
                StopwatchState()
            }
        }
    }
}

/** One lap as listed: its number, its own time and the total when it was marked. */
data class LapRow(val number: Int, val split: Long, val total: Long)

/** "01:05.23" or "1:02:03.45": a stopwatch time to the hundredth. */
internal fun formatStopwatch(ms: Long) = formatClock(ms) + ".%02d".format((ms / 10) % 100)
