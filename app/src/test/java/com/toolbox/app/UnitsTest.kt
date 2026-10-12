package com.toolbox.app

import com.toolbox.app.tools.units.UnitCategory
import com.toolbox.app.tools.units.convert
import com.toolbox.app.tools.units.decimalMark
import com.toolbox.app.tools.units.formatNumber
import com.toolbox.app.tools.units.parseNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    private fun unit(category: UnitCategory, id: String) = category.unit(id)!!
    private fun conv(category: UnitCategory, value: Double, from: String, to: String) =
        convert(value, unit(category, from), unit(category, to))

    @Test fun freezingPoint() {
        assertEquals(32.0, conv(UnitCategory.Temperature, 0.0, "c", "f"), 1e-9)
        assertEquals(0.0, conv(UnitCategory.Temperature, 32.0, "f", "c"), 1e-9)
        assertEquals(273.15, conv(UnitCategory.Temperature, 0.0, "c", "k"), 1e-9)
        assertEquals(-40.0, conv(UnitCategory.Temperature, -40.0, "c", "f"), 1e-9)
        assertEquals(212.0, conv(UnitCategory.Temperature, 373.15, "k", "f"), 1e-9)
    }

    @Test fun stoneAndGallon() {
        assertEquals(6.35029, conv(UnitCategory.Mass, 1.0, "st", "kg"), 1e-5)
        assertEquals(14.0, conv(UnitCategory.Mass, 1.0, "st", "lb"), 1e-12)
        assertEquals(4.54609, conv(UnitCategory.Volume, 1.0, "gal_uk", "l"), 1e-12)
        assertEquals(8.0, conv(UnitCategory.Volume, 1.0, "gal_uk", "pt_uk"), 1e-12)
        assertEquals(0.568261, conv(UnitCategory.Volume, 1.0, "pt_uk", "l"), 1e-6)
        assertEquals(0.473176, conv(UnitCategory.Volume, 1.0, "pt_us", "l"), 1e-6)
    }

    @Test fun commonFacts() {
        assertEquals(2.54, conv(UnitCategory.Length, 1.0, "in", "cm"), 1e-12)
        assertEquals(1.609344, conv(UnitCategory.Length, 1.0, "mi", "km"), 1e-12)
        assertEquals(43_560.0, conv(UnitCategory.Area, 1.0, "acre", "ft2"), 1e-6)
        assertEquals(1.609344, conv(UnitCategory.Speed, 1.0, "mph", "kph"), 1e-12)
        assertEquals(1024.0, conv(UnitCategory.Data, 1.0, "MiB", "KiB"), 1e-12)
        assertEquals(14.6959, conv(UnitCategory.Pressure, 1.0, "atm", "psi"), 1e-4)
        assertEquals(1013.25, conv(UnitCategory.Pressure, 1.0, "atm", "hpa"), 1e-9)
    }

    @Test fun fuelEconomyIsInverse() {
        // 50 mpg (UK) ≈ 5.65 L/100 km, and 10 L/100 km ≈ 28.25 mpg (UK) ≈ 23.52 mpg (US).
        assertEquals(5.6496, conv(UnitCategory.Fuel, 50.0, "mpg_uk", "l100km"), 1e-4)
        assertEquals(28.2481, conv(UnitCategory.Fuel, 10.0, "l100km", "mpg_uk"), 1e-4)
        assertEquals(23.5215, conv(UnitCategory.Fuel, 10.0, "l100km", "mpg_us"), 1e-4)
        // A UK gallon is bigger, so the same car does more miles per UK gallon than per US gallon.
        assertEquals(1.20095, conv(UnitCategory.Fuel, 1.0, "mpg_us", "mpg_uk"), 1e-5)
        assertTrue(conv(UnitCategory.Fuel, 0.0, "mpg_uk", "l100km").isInfinite())
    }

    @Test fun everyUnitRoundTrips() {
        // Cooking pairs only some units and snaps gas marks, so CookingTest covers it.
        for (category in UnitCategory.entries - UnitCategory.Cooking) {
            for (a in category.units) for (b in category.units) {
                for (v in listOf(0.37, 1.0, 12.5, 1234.5)) {
                    val back = convert(convert(v, a, b), b, a)
                    assertEquals("${category.name}: $v ${a.id} → ${b.id} → ${a.id}", v, back, v * 1e-9)
                }
            }
        }
    }

    @Test fun idsAreUniqueAndDefaultsExist() {
        for (category in UnitCategory.entries) {
            assertEquals(category.name, category.units.size, category.units.map { it.id }.toSet().size)
            assertNotNull(category.unit(category.defaultFrom))
            assertNotNull(category.unit(category.defaultTo))
        }
    }

    @Test fun parsing() {
        assertEquals(1000.0, parseNumber("1,000")!!, 0.0)
        assertEquals(-3.5, parseNumber(" -3.5 ")!!, 0.0)
        assertEquals(0.5, parseNumber(".5")!!, 0.0)
        assertEquals(1500.0, parseNumber("1.5e3")!!, 0.0)
        assertNull(parseNumber(""))
        assertNull(parseNumber("-"))
        assertNull(parseNumber("."))
        assertNull(parseNumber("1.2.3"))
        assertNull(parseNumber("abc"))
    }

    @Test fun formatting() {
        assertEquals("0.3", formatNumber(0.1 + 0.2))
        assertEquals("32", formatNumber(32.0))
        assertEquals("6.35029318", formatNumber(6.35029318))
        assertEquals("0.3937007874", formatNumber(1 / 2.54))
        assertEquals("-40", formatNumber(-40.0))
        assertEquals("0", formatNumber(-0.0))
        assertEquals("1e15", formatNumber(1e15))
        assertEquals("1.5e-9", formatNumber(1.5e-9))
        assertEquals("123456789012345", formatNumber(123456789012345.0, 15))
        assertEquals("—", formatNumber(Double.POSITIVE_INFINITY))
        assertEquals("—", formatNumber(Double.NaN))
    }
}

class DecimalMarkTest {
    @Test fun ukReadsALoneCommaAsAPointUnlessItGroupsThousands() {
        assertEquals(1.5, parseNumber("1,5")!!, 0.0)
        assertEquals(12.25, parseNumber("12,25")!!, 0.0)
        assertEquals(1000.0, parseNumber("1,000")!!, 0.0)
        assertEquals(1234567.0, parseNumber("1,234,567")!!, 0.0)
        assertEquals(1000.5, parseNumber("1,000.5")!!, 0.0)
        assertEquals(0.5, parseNumber(",5")!!, 0.0)
    }

    @Test fun commaLocalesReadTheCommaAsThePoint() {
        assertEquals(1.5, parseNumber("1,5", ',')!!, 0.0)
        assertEquals(-3.25, parseNumber("-3,25", ',')!!, 0.0)
        assertEquals(1000.5, parseNumber("1.000,5", ',')!!, 0.0)
        assertEquals(1000.5, parseNumber("1 000,5", ',')!!, 0.0)
        assertEquals(1000.5, parseNumber("1 000,5", ',')!!, 0.0)
        assertEquals(1000.0, parseNumber("1.000", ',')!!, 0.0)
        assertEquals(1.5, parseNumber("1.5", ',')!!, 0.0) // a keyboard with only "." still works
        assertEquals(1500.0, parseNumber("1,5e3", ',')!!, 0.0)
        assertNull(parseNumber("1,2,3", ','))
        assertNull(parseNumber(",", ','))
        assertNull(parseNumber("-,", ','))
    }

    @Test fun formattingUsesTheMarkAndReadsBack() {
        assertEquals("0,3937007874", formatNumber(1 / 2.54, decimal = ','))
        assertEquals("1,5e-9", formatNumber(1.5e-9, decimal = ','))
        for (v in listOf(0.3937007874, 1234.5, -40.25, 1.5e-9)) {
            assertEquals(v, parseNumber(formatNumber(v, decimal = ','), ',')!!, 0.0)
            assertEquals(v, parseNumber(formatNumber(v))!!, 0.0)
        }
    }

    @Test fun localeMarks() {
        assertEquals('.', decimalMark(java.util.Locale.UK))
        assertEquals(',', decimalMark(java.util.Locale.GERMANY))
        assertEquals(',', decimalMark(java.util.Locale.FRANCE))
    }
}
