package com.toolbox.app.tools.steps

/** How many past days of totals to keep. The chart shows 7; a little extra costs nothing. */
const val HISTORY_DAYS = 31

/**
 * Everything needed to turn the hardware step counter (steps since boot, reset to 0 on reboot) into
 * "steps since midnight". Days are `LocalDate.toEpochDay()` values.
 *
 * @property day the day [dayStartRaw] and [carried] belong to
 * @property dayStartRaw counter value the day's count is measured from
 * @property lastRaw last counter value seen, used to spot reboots and to close off a day
 * @property carried steps banked earlier today, before a reboot reset the counter
 * @property lifetime every step ever observed; only goes up, so sessions survive midnight and reboots
 * @property history finished days' totals by epoch day
 * @property started false until the first reading ever
 */
data class StepState(
    val day: Long = 0,
    val dayStartRaw: Long = 0,
    val lastRaw: Long = 0,
    val carried: Long = 0,
    val lifetime: Long = 0,
    val history: Map<Long, Long> = emptyMap(),
    val started: Boolean = false,
) {
    /** Steps so far on [today]; 0 if the state hasn't caught up with today yet. */
    fun stepsOn(today: Long): Long = if (started && day == today) carried + (lastRaw - dayStartRaw) else 0

    /**
     * Total for any day: the live count for the state's current [day], history otherwise, null if unknown.
     * Just after midnight, before the first reading of the new day, yesterday still shows its live count.
     */
    fun totalFor(day: Long): Long? = if (started && day == this.day) carried + (lastRaw - dayStartRaw) else history[day]
}

/**
 * Applies one counter reading [raw] taken on [today].
 *
 * - **First reading ever:** counting starts now; earlier steps can't be attributed to a day.
 * - **Reboot** (`raw < lastRaw`): the counter restarted from 0, so bank today's steps so far in `carried` and
 *   measure from 0. Steps between the last reading and the reboot are lost.
 * - **New day:** yesterday's total is written to history as of the last reading, and the new day is measured from
 *   that last reading. Any steps between that reading and midnight (at most an hour, given the hourly job) count
 *   towards the new day rather than being lost. If whole days were missed they get no history entry.
 * - **Clock moved backwards:** treated as the same day, so a time zone change can't rewrite history.
 */
fun applyReading(s: StepState, raw: Long, today: Long): StepState {
    if (!s.started) return StepState(day = today, dayStartRaw = raw, lastRaw = raw, history = s.history, lifetime = s.lifetime, started = true)

    val rebooted = raw < s.lastRaw
    val gained = if (rebooted) raw else raw - s.lastRaw
    val lifetime = s.lifetime + gained

    if (today <= s.day) {
        return if (rebooted) {
            s.copy(carried = s.carried + (s.lastRaw - s.dayStartRaw), dayStartRaw = 0, lastRaw = raw, lifetime = lifetime)
        } else {
            s.copy(lastRaw = raw, lifetime = lifetime)
        }
    }

    // New day: close off the old one as of its last reading.
    val closed = s.carried + (s.lastRaw - s.dayStartRaw)
    val history = (s.history + (s.day to closed)).filterKeys { it > today - HISTORY_DAYS }
    return StepState(
        day = today,
        dayStartRaw = if (rebooted) 0 else s.lastRaw,
        lastRaw = raw,
        carried = 0,
        lifetime = lifetime,
        history = history,
        started = true,
    )
}

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
