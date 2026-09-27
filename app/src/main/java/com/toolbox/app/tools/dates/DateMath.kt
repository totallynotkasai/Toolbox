package com.toolbox.app.tools.dates

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * The gap between two dates. All the fields are magnitudes; [negative] says the end is before the start.
 * [workingDays] counts Monday–Friday (no bank holidays).
 */
data class DateDiff(
    val days: Long,
    val weeks: Long,
    val weekDays: Long,
    val years: Int,
    val months: Int,
    val monthDays: Int,
    val workingDays: Long,
    val negative: Boolean,
)

/**
 * Difference from [start] to [end]. By default the end date is excluded, so Monday → next Monday is 7 days
 * and 5 working days. With [includeEnd], the end day counts too (a Mon–Fri holiday is 5 days).
 */
fun dateDiff(start: LocalDate, end: LocalDate, includeEnd: Boolean = false): DateDiff {
    val negative = end < start
    val from = if (negative) end else start
    val to = (if (negative) start else end).let { if (includeEnd) it.plusDays(1) else it }
    val days = ChronoUnit.DAYS.between(from, to)
    val period = Period.between(from, to)
    return DateDiff(
        days = days,
        weeks = days / 7,
        weekDays = days % 7,
        years = period.years,
        months = period.months,
        monthDays = period.days,
        workingDays = workingDaysBetween(from, to),
        negative = negative,
    )
}

/** Monday–Friday days in [from, to) (end exclusive). Zero if [to] is not after [from]. */
fun workingDaysBetween(from: LocalDate, to: LocalDate): Long {
    val days = ChronoUnit.DAYS.between(from, to)
    if (days <= 0) return 0
    // Whole weeks contribute 5 each; walk the leftover (at most 6) days.
    var count = days / 7 * 5
    var d = from.plusDays(days / 7 * 7)
    while (d < to) {
        if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) count++
        d = d.plusDays(1)
    }
    return count
}

enum class DateStep(val label: String, val unit: ChronoUnit) {
    Days("Days", ChronoUnit.DAYS),
    Weeks("Weeks", ChronoUnit.WEEKS),
    Months("Months", ChronoUnit.MONTHS),
    Years("Years", ChronoUnit.YEARS),
}

/**
 * [date] ± [amount] [step]s. Months and years clamp to the end of a shorter month, as calendars do:
 * 31 Jan + 1 month = 28 Feb (29 in a leap year). [clamped] reports when that happened so the UI can say so.
 */
data class DateShift(val result: LocalDate, val clamped: Boolean)

fun shiftDate(date: LocalDate, amount: Long, step: DateStep): DateShift {
    val result = date.plus(amount, step.unit)
    val clamped = (step == DateStep.Months || step == DateStep.Years) && result.dayOfMonth != date.dayOfMonth
    return DateShift(result, clamped)
}

/** Everything the Age tab shows. [nextBirthday] is today when [isBirthday]. */
data class AgeInfo(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long,
    val nextBirthday: LocalDate,
    val daysToBirthday: Long,
    val turning: Int,
    val isBirthday: Boolean,
)

/**
 * The date someone born on [birth] turns [age]. For a 29 February birthday in a common year that is
 * 1 March, which is the UK legal rule and also what [Period.between] implies (it never reports a whole
 * year on 28 February), so the age and the countdown always agree.
 */
fun birthdayAt(birth: LocalDate, age: Int): LocalDate {
    val d = birth.plusYears(age.toLong())
    return if (d.dayOfMonth != birth.dayOfMonth) d.plusDays(1) else d
}

/** Age on [today], or null if [birth] is in the future. */
fun ageOn(birth: LocalDate, today: LocalDate): AgeInfo? {
    if (birth > today) return null
    val p = Period.between(birth, today)
    val isBirthday = p.years > 0 && birthdayAt(birth, p.years) == today
    val turning = if (isBirthday) p.years else p.years + 1
    val next = birthdayAt(birth, turning)
    return AgeInfo(
        years = p.years,
        months = p.months,
        days = p.days,
        totalDays = ChronoUnit.DAYS.between(birth, today),
        nextBirthday = next,
        daysToBirthday = ChronoUnit.DAYS.between(today, next),
        turning = turning,
        isBirthday = isBirthday,
    )
}

/** "1 year, 2 months, 3 days"; zero parts are dropped, and an all-zero span is "0 days". */
fun describeYmd(years: Int, months: Int, days: Int): String {
    val parts = buildList {
        if (years != 0) add(plural(years.toLong(), "year"))
        if (months != 0) add(plural(months.toLong(), "month"))
        if (days != 0 || isEmpty()) add(plural(days.toLong(), "day"))
    }
    return parts.joinToString(", ")
}

fun plural(n: Long, word: String): String = "%,d %s".format(n, if (n == 1L) word else word + "s")
