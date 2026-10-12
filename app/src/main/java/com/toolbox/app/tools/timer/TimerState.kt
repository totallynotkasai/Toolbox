package com.toolbox.app.tools.timer

/** What the timer is doing. */
enum class TimerPhase { Idle, Running, Paused, Ringing }

/**
 * The whole timer, as stored. Plain logic, so the end-time and adjustment rules have unit tests.
 *
 * Times are `SystemClock.elapsedRealtime()` milliseconds. That clock keeps counting while the phone sleeps and while
 * the app's process is gone, so a running timer is just its end time and nothing is added when the app comes back.
 * It starts again from zero on a reboot, which is why [boot] is stored too: see [afterBoot].
 *
 * @property durationMs the time that was set, which Reset goes back to.
 * @property totalMs the full length of the current run, for the progress ring. Adjusting a run changes it too.
 * @property remainingMs time left while [TimerPhase.Idle] (always [durationMs]) or [TimerPhase.Paused].
 * @property endAt when a [TimerPhase.Running] timer ends.
 * @property boot `Settings.Global.BOOT_COUNT` when this was stored.
 */
data class TimerState(
    val phase: TimerPhase = TimerPhase.Idle,
    val durationMs: Long = DEFAULT_MS,
    val totalMs: Long = DEFAULT_MS,
    val remainingMs: Long = DEFAULT_MS,
    val endAt: Long = 0L,
    val boot: Int = 0,
) {
    fun remaining(now: Long): Long = when (phase) {
        TimerPhase.Running -> (endAt - now).coerceAtLeast(0)
        TimerPhase.Ringing -> 0
        else -> remainingMs
    }

    /** How much of the ring is left, from 1 down to 0. */
    fun progress(now: Long): Float = if (totalMs <= 0) 0f else (remaining(now).toFloat() / totalMs).coerceIn(0f, 1f)

    /** Running and out of time: it should be ringing. */
    fun isDue(now: Long) = phase == TimerPhase.Running && now >= endAt

    /** A fresh timer of [ms], stopped. */
    fun set(ms: Long): TimerState {
        val d = ms.coerceIn(MIN_MS, MAX_MS)
        return TimerState(TimerPhase.Idle, d, d, d, boot = boot)
    }

    fun reset() = set(durationMs)

    /** Starts or resumes; after ringing it runs the same time again. */
    fun start(now: Long): TimerState = when (phase) {
        TimerPhase.Idle, TimerPhase.Paused -> copy(phase = TimerPhase.Running, endAt = now + remainingMs)
        TimerPhase.Ringing -> reset().start(now)
        TimerPhase.Running -> this
    }

    fun pause(now: Long): TimerState = when {
        isDue(now) -> ring()
        phase == TimerPhase.Running -> copy(phase = TimerPhase.Paused, remainingMs = remaining(now))
        else -> this
    }

    fun ring() = copy(phase = TimerPhase.Ringing, remainingMs = 0)

    /**
     * Adds [deltaMs] (negative to take time off). Stopped, it changes the set time. Paused or running, it changes the
     * time left and leaves the set time alone. Ringing, adding time runs the timer again for just that long ("+1 min").
     *
     * Taking time off never goes below 10 seconds, or below what's left if that's already less.
     */
    fun adjust(deltaMs: Long, now: Long): TimerState = when (phase) {
        TimerPhase.Idle -> set(durationMs + deltaMs)
        TimerPhase.Paused -> {
            val left = clampLeft(remainingMs, deltaMs)
            copy(remainingMs = left, totalMs = (totalMs + left - remainingMs).coerceAtLeast(left))
        }
        TimerPhase.Running -> {
            val before = remaining(now)
            val left = clampLeft(before, deltaMs)
            copy(endAt = now + left, totalMs = (totalMs + left - before).coerceAtLeast(left))
        }
        TimerPhase.Ringing ->
            if (deltaMs <= 0) this
            else copy(phase = TimerPhase.Running, totalMs = deltaMs, remainingMs = 0, endAt = now + deltaMs)
    }

    /**
     * Checks a stored state against the current boot. A reboot cancels the alarm and resets the clock the end time was
     * measured on, so a running or ringing timer is lost and goes back to its set time. A paused one keeps its time left.
     */
    fun afterBoot(currentBoot: Int): TimerState = when {
        boot == currentBoot -> this
        phase == TimerPhase.Running || phase == TimerPhase.Ringing -> reset().copy(boot = currentBoot)
        else -> copy(boot = currentBoot)
    }

    fun encode(): String = listOf(phase.name, durationMs, totalMs, remainingMs, endAt, boot).joinToString("|")

    companion object {
        const val DEFAULT_MS = 5 * 60_000L
        const val MIN_MS = 10_000L
        const val MAX_MS = 24 * 3_600_000L

        private fun clampLeft(left: Long, deltaMs: Long) = (left + deltaMs).coerceIn(minOf(left, MIN_MS), MAX_MS)

        /** Reads [encode]'s output; anything unreadable gives a fresh 5-minute timer. */
        fun decode(text: String?): TimerState {
            val parts = text?.split('|') ?: return TimerState()
            if (parts.size != 6) return TimerState()
            return try {
                TimerState(
                    TimerPhase.valueOf(parts[0]),
                    parts[1].toLong(), parts[2].toLong(), parts[3].toLong(), parts[4].toLong(), parts[5].toInt(),
                )
            } catch (_: IllegalArgumentException) {
                TimerState() // NumberFormatException is one too.
            }
        }
    }
}
