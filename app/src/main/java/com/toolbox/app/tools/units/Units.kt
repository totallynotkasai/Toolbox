package com.toolbox.app.tools.units

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * One unit of measure. Most units are a plain factor to their category's base unit ([linear]);
 * temperature (an offset) and L/100 km (an inverse) supply their own conversions.
 * [id] is stable and is what gets saved in Prefs, so don't rename ids.
 * [group] sorts the Cooking category into volumes, weights and oven temperatures; other categories leave it null.
 */
class UnitDef(
    val id: String,
    val name: String,
    val symbol: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double,
    val group: UnitGroup? = null,
) {
    override fun toString() = id
}

private fun linear(id: String, name: String, symbol: String, factor: Double) =
    UnitDef(id, name, symbol, { it * factor }, { it / factor })

// Exact definitions (international yard and pound, 1959; imperial gallon, 1985).
internal const val INCH = 0.0254
internal const val FOOT = 0.3048
internal const val YARD = 0.9144
internal const val MILE = 1609.344
internal const val NAUTICAL_MILE = 1852.0
internal const val POUND = 0.45359237
internal const val UK_GALLON = 4.54609
internal const val US_GALLON = 3.785411784

/** Temperature's base unit is Celsius. */
const val ABSOLUTE_ZERO_C = -273.15

enum class UnitCategory(val title: String, val units: List<UnitDef>, val defaultFrom: String, val defaultTo: String) {
    Length(
        "Length",
        listOf(
            linear("mm", "Millimetres", "mm", 0.001),
            linear("cm", "Centimetres", "cm", 0.01),
            linear("m", "Metres", "m", 1.0),
            linear("km", "Kilometres", "km", 1000.0),
            linear("in", "Inches", "in", INCH),
            linear("ft", "Feet", "ft", FOOT),
            linear("yd", "Yards", "yd", YARD),
            linear("mi", "Miles", "mi", MILE),
            linear("nmi", "Nautical miles", "nmi", NAUTICAL_MILE),
        ),
        "cm", "in",
    ),
    Mass(
        "Mass",
        listOf(
            linear("mg", "Milligrams", "mg", 1e-6),
            linear("g", "Grams", "g", 0.001),
            linear("kg", "Kilograms", "kg", 1.0),
            linear("t", "Tonnes", "t", 1000.0),
            linear("oz", "Ounces", "oz", POUND / 16),
            linear("lb", "Pounds", "lb", POUND),
            linear("st", "Stone", "st", POUND * 14),
            linear("ton_uk", "Long tons (UK)", "ton (UK)", POUND * 2240),
            linear("ton_us", "Short tons (US)", "ton (US)", POUND * 2000),
        ),
        "kg", "st",
    ),
    Temperature(
        "Temperature",
        listOf(
            UnitDef("c", "Celsius", "°C", { it }, { it }),
            UnitDef("f", "Fahrenheit", "°F", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }),
            UnitDef("k", "Kelvin", "K", { it + ABSOLUTE_ZERO_C }, { it - ABSOLUTE_ZERO_C }),
        ),
        "c", "f",
    ),
    Volume(
        "Volume",
        listOf(
            linear("ml", "Millilitres", "mL", 0.001),
            linear("l", "Litres", "L", 1.0),
            linear("m3", "Cubic metres", "m³", 1000.0),
            linear("tsp", "Teaspoons", "tsp", 0.005),
            linear("tbsp", "Tablespoons", "tbsp", 0.015),
            linear("floz_uk", "Fluid ounces (UK)", "fl oz (UK)", UK_GALLON / 160),
            linear("floz_us", "Fluid ounces (US)", "fl oz (US)", US_GALLON / 128),
            linear("cup_uk", "Cups (metric)", "cup (metric)", 0.25),
            linear("cup_us", "Cups (US)", "cup (US)", US_GALLON / 16),
            linear("pt_uk", "Pints (UK)", "pt (UK)", UK_GALLON / 8),
            linear("pt_us", "Pints (US)", "pt (US)", US_GALLON / 8),
            linear("gal_uk", "Gallons (UK)", "gal (UK)", UK_GALLON),
            linear("gal_us", "Gallons (US)", "gal (US)", US_GALLON),
            linear("ft3", "Cubic feet", "ft³", FOOT * FOOT * FOOT * 1000),
        ),
        "l", "pt_uk",
    ),
    Cooking("Cooking", COOKING_UNITS, "cup_us", "g"),
    Area(
        "Area",
        listOf(
            linear("cm2", "Square centimetres", "cm²", 1e-4),
            linear("m2", "Square metres", "m²", 1.0),
            linear("ha", "Hectares", "ha", 1e4),
            linear("km2", "Square kilometres", "km²", 1e6),
            linear("in2", "Square inches", "in²", INCH * INCH),
            linear("ft2", "Square feet", "ft²", FOOT * FOOT),
            linear("yd2", "Square yards", "yd²", YARD * YARD),
            linear("acre", "Acres", "ac", YARD * YARD * 4840),
            linear("mi2", "Square miles", "mi²", MILE * MILE),
        ),
        "m2", "ft2",
    ),
    Speed(
        "Speed",
        listOf(
            linear("mps", "Metres per second", "m/s", 1.0),
            linear("kph", "Kilometres per hour", "km/h", 1 / 3.6),
            linear("mph", "Miles per hour", "mph", MILE / 3600),
            linear("kn", "Knots", "kn", NAUTICAL_MILE / 3600),
            linear("fps", "Feet per second", "ft/s", FOOT),
        ),
        "mph", "kph",
    ),
    Time(
        "Time",
        listOf(
            linear("ms", "Milliseconds", "ms", 0.001),
            linear("s", "Seconds", "s", 1.0),
            linear("min", "Minutes", "min", 60.0),
            linear("h", "Hours", "h", 3600.0),
            linear("d", "Days", "d", 86_400.0),
            linear("wk", "Weeks", "wk", 604_800.0),
            linear("yr", "Years (365.25 d)", "yr", 31_557_600.0),
        ),
        "h", "min",
    ),
    Data(
        "Data",
        listOf(
            linear("bit", "Bits", "bit", 0.125),
            linear("B", "Bytes", "B", 1.0),
            linear("kB", "Kilobytes", "kB", 1e3),
            linear("MB", "Megabytes", "MB", 1e6),
            linear("GB", "Gigabytes", "GB", 1e9),
            linear("TB", "Terabytes", "TB", 1e12),
            linear("KiB", "Kibibytes", "KiB", 1024.0),
            linear("MiB", "Mebibytes", "MiB", 1024.0 * 1024),
            linear("GiB", "Gibibytes", "GiB", 1024.0 * 1024 * 1024),
            linear("TiB", "Tebibytes", "TiB", 1024.0 * 1024 * 1024 * 1024),
        ),
        "GB", "GiB",
    ),
    Pressure(
        "Pressure",
        listOf(
            linear("pa", "Pascals", "Pa", 1.0),
            linear("hpa", "Hectopascals / millibars", "hPa", 100.0),
            linear("kpa", "Kilopascals", "kPa", 1000.0),
            linear("bar", "Bar", "bar", 1e5),
            linear("atm", "Atmospheres", "atm", 101_325.0),
            linear("psi", "Pounds per sq inch", "psi", POUND * 9.80665 / (INCH * INCH)),
            linear("mmhg", "Millimetres of mercury", "mmHg", 133.322387415),
            linear("inhg", "Inches of mercury", "inHg", 3386.389),
        ),
        "psi", "bar",
    ),
    Fuel(
        "Fuel economy",
        listOf(
            // Base unit is km/L, so the "per distance" units are factors and L/100 km is the inverse.
            linear("mpg_uk", "Miles per gallon (UK)", "mpg (UK)", MILE / 1000 / UK_GALLON),
            linear("mpg_us", "Miles per gallon (US)", "mpg (US)", MILE / 1000 / US_GALLON),
            linear("kmpl", "Kilometres per litre", "km/L", 1.0),
            UnitDef("l100km", "Litres per 100 km", "L/100 km", { 100 / it }, { 100 / it }),
        ),
        "mpg_uk", "l100km",
    );

    fun unit(id: String?): UnitDef? = units.firstOrNull { it.id == id }
}

/** Converts [value] from one unit to another in the same category. May return ±Infinity (e.g. 0 mpg → L/100 km). */
fun convert(value: Double, from: UnitDef, to: UnitDef): Double =
    if (from === to) value else to.fromBase(from.toBase(value))

/** The [locale]'s decimal mark when it is "." or ","; other marks fall back to ".", which every keyboard has. */
fun decimalMark(locale: Locale): Char =
    DecimalFormatSymbols.getInstance(locale).decimalSeparator.takeIf { it == ',' } ?: '.'

/**
 * Lenient number parsing for a text field, where [decimal] is the locale's decimal mark ("." or ","). Spaces are
 * ignored, and so are thousands separators: "1,000" is 1000 and "1.000,5" is 1000.5 wherever you are.
 *
 * The other mark is read as a decimal point too when it can't be a thousands separator, so "1,5" is 1.5 in the UK
 * and "1.5" is 1.5 in Germany, because some keyboards only offer one of the two. It counts as a thousands separator
 * when it is followed by exactly three digits or appears more than once. A lone "-" or decimal mark is null.
 */
fun parseNumber(text: String, decimal: Char = '.'): Double? {
    val s = text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }
    val other = if (decimal == ',') '.' else ','
    val point = when {
        // With both marks the last one is the decimal point, as in "1,000.5" and "1.000,5".
        decimal in s && other in s -> if (s.lastIndexOf(decimal) > s.lastIndexOf(other)) decimal else other
        decimal in s -> decimal
        other in s && s.count { it == other } == 1 && s.substringAfter(other).takeWhile(Char::isDigit).length != 3 -> other
        else -> null
    }
    if (point != null && s.count { it == point } > 1) return null
    val plain = buildString {
        for (c in s) when (c) {
            point -> append('.')
            '.', ',' -> {} // thousands separators
            else -> append(c)
        }
    }
    if (plain.isEmpty() || plain == "-" || plain == "." || plain == "-.") return null
    return plain.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/**
 * Human-friendly output: up to [significant] significant digits, no trailing zeros, no floating-point
 * noise (0.1 + 0.2 shows as 0.3), and scientific notation only for very large or tiny magnitudes.
 * Non-finite results come back as "—" so the UI never shows "Infinity". [decimal] is the locale's decimal mark, so
 * the text can be edited and read back by [parseNumber].
 */
fun formatNumber(value: Double, significant: Int = 10, decimal: Char = '.'): String {
    if (!value.isFinite()) return "—"
    if (value == 0.0) return "0"
    val rounded = BigDecimal(value).round(MathContext(significant)).stripTrailingZeros()
    val magnitude = abs(rounded.toDouble())
    val text = if (magnitude >= 1e15 || magnitude < 1e-6) {
        // Scientific, e.g. "1.5e-9"; BigDecimal's own form is "1.5E-9".
        rounded.toString().replace("E+", "e").replace("E", "e")
    } else {
        rounded.toPlainString()
    }
    return text.replace('.', decimal)
}
