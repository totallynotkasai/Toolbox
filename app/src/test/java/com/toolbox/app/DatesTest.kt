package com.toolbox.app

import com.toolbox.app.tools.dates.DateStep
import com.toolbox.app.tools.dates.ageOn
import com.toolbox.app.tools.dates.birthdayAt
import com.toolbox.app.tools.dates.dateDiff
import com.toolbox.app.tools.dates.describeYmd
import com.toolbox.app.tools.dates.shiftDate
import com.toolbox.app.tools.dates.workingDaysBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DatesTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun betweenBasics() {
        val diff = dateDiff(d("2026-01-01"), d("2026-03-15"))
        assertEquals(73, diff.days)
        assertEquals(10, diff.weeks)
        assertEquals(3, diff.weekDays)
        assertEquals(0, diff.years); assertEquals(2, diff.months); assertEquals(14, diff.monthDays)
        assertFalse(diff.negative)
    }

    @Test fun betweenAcrossLeapDay() {
        assertEquals(366, dateDiff(d("2024-01-01"), d("2025-01-01")).days)
        assertEquals(365, dateDiff(d("2025-01-01"), d("2026-01-01")).days)
        assertEquals(29, dateDiff(d("2024-02-01"), d("2024-03-01")).days)
        assertEquals(1, dateDiff(d("2024-02-28"), d("2024-02-29")).days)
    }

    @Test fun endBeforeStartIsNegativeWithSameMagnitude() {
        val forward = dateDiff(d("2026-01-01"), d("2026-03-15"))
        val backward = dateDiff(d("2026-03-15"), d("2026-01-01"))
        assertTrue(backward.negative)
        assertEquals(forward.copy(negative = true), backward)
    }

    @Test fun includeEndAddsOneDay() {
        // Mon 21 Sep to Fri 25 Sep 2026: 4 days apart, a 5-day working week when both ends count.
        val excl = dateDiff(d("2026-09-21"), d("2026-09-25"))
        val incl = dateDiff(d("2026-09-21"), d("2026-09-25"), includeEnd = true)
        assertEquals(4, excl.days); assertEquals(4, excl.workingDays)
        assertEquals(5, incl.days); assertEquals(5, incl.workingDays)
        assertEquals(1, dateDiff(d("2026-09-21"), d("2026-09-21"), includeEnd = true).days)
    }

    @Test fun workingDays() {
        val mon = d("2026-09-21")
        assertEquals(5, workingDaysBetween(mon, mon.plusDays(7)))
        assertEquals(0, workingDaysBetween(d("2026-09-26"), d("2026-09-28"))) // Sat → Mon: the weekend only
        assertEquals(1, workingDaysBetween(d("2026-09-25"), d("2026-09-28"))) // Fri → Mon
        assertEquals(0, workingDaysBetween(mon, mon))
        assertEquals(0, workingDaysBetween(mon.plusDays(3), mon))
        // Brute-force check over a range of spans and start weekdays.
        for (start in 0L until 7L) for (len in 0L until 40L) {
            val a = mon.plusDays(start)
            val b = a.plusDays(len)
            val expected = (0 until len).count { a.plusDays(it).dayOfWeek.value <= 5 }.toLong()
            assertEquals("$a → $b", expected, workingDaysBetween(a, b))
        }
    }

    @Test fun addMonthClampsToMonthEnd() {
        val s = shiftDate(d("2026-01-31"), 1, DateStep.Months)
        assertEquals(d("2026-02-28"), s.result)
        assertTrue(s.clamped)
        assertEquals(d("2024-02-29"), shiftDate(d("2024-01-31"), 1, DateStep.Months).result)
        assertEquals(d("2025-02-28"), shiftDate(d("2024-02-29"), 1, DateStep.Years).result)
        assertFalse(shiftDate(d("2026-01-15"), 1, DateStep.Months).clamped)
    }

    @Test fun addAndSubtract() {
        assertEquals(d("2026-10-26"), shiftDate(d("2026-09-26"), 30, DateStep.Days).result)
        assertEquals(d("2026-09-12"), shiftDate(d("2026-09-26"), -2, DateStep.Weeks).result)
        assertEquals(d("2025-12-31"), shiftDate(d("2026-01-01"), -1, DateStep.Days).result)
        assertEquals(d("2016-09-26"), shiftDate(d("2026-09-26"), -10, DateStep.Years).result)
        assertFalse(shiftDate(d("2026-03-31"), -1, DateStep.Days).clamped)
    }

    @Test fun age() {
        val a = ageOn(d("1990-05-10"), d("2026-09-26"))!!
        assertEquals(36, a.years); assertEquals(4, a.months); assertEquals(16, a.days)
        assertEquals(37, a.turning)
        assertEquals(d("2027-05-10"), a.nextBirthday)
        assertEquals(226, a.daysToBirthday)
        assertFalse(a.isBirthday)
    }

    @Test fun birthdayToday() {
        val a = ageOn(d("1990-09-26"), d("2026-09-26"))!!
        assertTrue(a.isBirthday)
        assertEquals(36, a.years); assertEquals(36, a.turning); assertEquals(0, a.daysToBirthday)
    }

    @Test fun bornToday() {
        val a = ageOn(d("2026-09-26"), d("2026-09-26"))!!
        assertEquals(0, a.years); assertFalse(a.isBirthday); assertEquals(1, a.turning)
        assertEquals(365, a.daysToBirthday)
    }

    @Test fun futureBirthIsNull() {
        assertNull(ageOn(d("2030-01-01"), d("2026-09-26")))
    }

    @Test fun leapDayBirthdays() {
        val leapling = d("2000-02-29")
        assertEquals(d("2001-03-01"), birthdayAt(leapling, 1))
        assertEquals(d("2004-02-29"), birthdayAt(leapling, 4))
        // On 28 Feb 2001 they're not 1 yet, and the countdown agrees.
        val eve = ageOn(leapling, d("2001-02-28"))!!
        assertEquals(0, eve.years); assertEquals(1, eve.daysToBirthday); assertFalse(eve.isBirthday)
        val day = ageOn(leapling, d("2001-03-01"))!!
        assertEquals(1, day.years); assertTrue(day.isBirthday)
        val leap = ageOn(leapling, d("2004-02-29"))!!
        assertEquals(4, leap.years); assertTrue(leap.isBirthday)
    }

    @Test fun describe() {
        assertEquals("1 year, 2 months, 3 days", describeYmd(1, 2, 3))
        assertEquals("2 months", describeYmd(0, 2, 0))
        assertEquals("0 days", describeYmd(0, 0, 0))
        assertEquals("1 day", describeYmd(0, 0, 1))
    }
}
