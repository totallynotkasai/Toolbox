package com.toolbox.app.tools.steps

/** How many past days of totals to keep. The chart shows 7; a little extra costs nothing. */
const val HISTORY_DAYS = 31

/**
 * Everything needed to turn the hardware step counter (steps since boot, reset to 0 on reboot) into
 * "steps since midnight". Days are `LocalDate.toEpochDay()` values; times are epoch millis.
 *
 * @property day the day [dayStartRaw] and [carried] belong to
 * @property dayStartRaw counter value the day's count is measured from
 * @property lastRaw last counter value seen, used to spot reboots and to close off a day
 * @property carried steps banked earlier today, before a reboot reset the counter
 * @property lifetime every step ever observed; only goes up, so sessions survive midnight and reboots
 * @property history finished days' totals by epoch day
 * @property started false until the first reading ever
 * @property lastAt when [lastRaw] was read; 0 if unknown (saved by an older version)
 * @property boot the phone's boot count at [lastRaw]; -1 if unknown
 * @property countingSince when [day]'s count started, if it didn't run on from the day before: the first reading
 *   ever, or the first after a gap; 0 otherwise
 * @property afterGap true if [day]'s count started after a gap of a whole day or more with no readings
 * @property gapSince the last reading before that gap; 0 if unknown
 */
data class StepState(
    val day: Long = 0,
    val dayStartRaw: Long = 0,
    val lastRaw: Long = 0,
    val carried: Long = 0,
    val lifetime: Long = 0,
    val history: Map<Long, Long> = emptyMap(),
    val started: Boolean = false,
    val lastAt: Long = 0,
    val boot: Int = -1,
    val countingSince: Long = 0,
    val afterGap: Boolean = false,
    val gapSince: Long = 0,
) {
    /**
     * Total for any day: the live count for the state's current [day], history otherwise, null if unknown.
     * Just after midnight, before the first reading of the new day, yesterday still shows its live count.
     */
    fun totalFor(day: Long): Long? = if (started && day == this.day) carried + (lastRaw - dayStartRaw) else history[day]
}

/**
 * Applies one counter reading [raw] taken on [today] at [now], with the phone's boot count [boot] (-1 if unknown).
 *
 * - **First reading ever:** counting starts now; earlier steps can't be attributed to a day.
 * - **Reboot** (`raw < lastRaw`, or a new boot count): the counter restarted from 0, so bank today's steps so far
 *   in `carried` and measure from 0. Steps between the last reading and the reboot are lost. The boot count catches
 *   a reboot after which more steps were taken than the last reading showed, which the counter alone can't.
 * - **New day:** yesterday's total is written to history as of the last reading, and the new day is measured from
 *   that last reading. Steps between that reading and midnight (at most an hour, given the hourly job) count
 *   towards the new day rather than being lost.
 * - **A whole day or more missed** (the phone held the hourly job back): the steps since the last reading can't be
 *   placed on a day, so they aren't credited to today. Today counts from this reading and notes the gap. The
 *   missed days get no history entry.
 * - **Clock moved backwards:** treated as the same day, so a time zone change can't rewrite history.
 */
fun applyReading(s: StepState, raw: Long, today: Long, now: Long = 0, boot: Int = -1): StepState {
    if (!s.started) {
        return StepState(day = today, dayStartRaw = raw, lastRaw = raw, history = s.history, lifetime = s.lifetime,
            started = true, lastAt = now, boot = boot, countingSince = now)
    }

    val rebooted = raw < s.lastRaw || (boot >= 0 && s.boot >= 0 && boot != s.boot)
    val gained = if (rebooted) raw else raw - s.lastRaw
    val lifetime = s.lifetime + gained

    if (today <= s.day) {
        return if (rebooted) {
            s.copy(carried = s.carried + (s.lastRaw - s.dayStartRaw), dayStartRaw = 0, lastRaw = raw, lifetime = lifetime,
                lastAt = now, boot = boot)
        } else {
            s.copy(lastRaw = raw, lifetime = lifetime, lastAt = now, boot = boot)
        }
    }

    // New day: close off the old one as of its last reading.
    val closed = s.carried + (s.lastRaw - s.dayStartRaw)
    val history = (s.history + (s.day to closed)).filterKeys { it > today - HISTORY_DAYS }
    val gap = today - s.day > 1
    return StepState(
        day = today,
        dayStartRaw = when {
            gap -> raw
            rebooted -> 0
            else -> s.lastRaw
        },
        lastRaw = raw,
        carried = 0,
        lifetime = lifetime,
        history = history,
        started = true,
        lastAt = now,
        boot = boot,
        countingSince = if (gap) now else 0,
        afterGap = gap,
        gapSince = if (gap) s.lastAt else 0,
    )
}

/**
 * True if [after] has to be saved straight away, rather than when the screen next saves its running count: anything
 * beyond the count moving on (a new day, a reboot, the first reading) is saved at once.
 */
fun mustSave(before: StepState, after: StepState): Boolean =
    before.copy(lastRaw = after.lastRaw, lifetime = after.lifetime, lastAt = after.lastAt, boot = after.boot) != after

/** Compact text form for SharedPreferences: "day:steps,day:steps". Bad entries are skipped. */
fun encodeHistory(history: Map<Long, Long>): String = history.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

fun decodeHistory(text: String): Map<Long, Long> = text.split(',').mapNotNull { entry ->
    val parts = entry.split(':')
    val day = parts.getOrNull(0)?.toLongOrNull()
    val steps = parts.getOrNull(1)?.toLongOrNull()
    if (parts.size == 2 && day != null && steps != null && steps >= 0) day to steps else null
}.toMap()

/** Distance in metres for [steps] of [strideCm]. */
fun distanceMetres(steps: Long, strideCm: Int): Double = steps * strideCm / 100.0

/**
 * Rough active energy of walking [metres] for someone weighing [weightKg]: about 0.5 kcal per kg per km
 * (the net cost of walking at an easy pace, over and above resting).
 */
fun walkingKcal(metres: Double, weightKg: Int): Double = 0.5 * weightKg * metres / 1000.0
