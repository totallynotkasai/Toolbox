package com.toolbox.app.tools.compass

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Magnetic declination is how far magnetic north lies from true north, in degrees: positive when it's east of true
 * north, negative when west. Adding it to a magnetic heading gives the true heading.
 */
fun applyDeclination(magnetic: Float, declination: Float): Float = normalizeDegrees(magnetic + declination)

/** The largest declination that can be typed in. Only right beside the magnetic poles is it more than this. */
const val MAX_DECLINATION = 180f

/** "1.2° E", "3.0° W", or "0.0°" when it rounds to nothing. */
fun formatDeclination(declination: Float): String {
    val tenths = (abs(declination) * 10).roundToInt()
    val text = "%.1f°".format(tenths / 10f)
    return when {
        tenths == 0 -> text
        declination > 0 -> "$text E"
        else -> "$text W"
    }
}

/** Spoken form for TalkBack: "1.2 degrees east", or "0 degrees". */
fun spokenDeclination(declination: Float): String {
    val tenths = (abs(declination) * 10).roundToInt()
    val amount = if (tenths % 10 == 0) "${tenths / 10}" else "%.1f".format(tenths / 10f)
    return when {
        tenths == 0 -> "0 degrees"
        declination > 0 -> "$amount degrees east"
        else -> "$amount degrees west"
    }
}

/**
 * Reads a typed declination: a number of degrees (a comma also works as the decimal point) from 0 to
 * [MAX_DECLINATION], made negative for west. Returns null for anything else, including a typed minus sign, since the
 * east/west choice says which way it goes.
 */
fun parseDeclination(text: String, east: Boolean): Float? {
    val trimmed = text.trim().replace(',', '.')
    if (trimmed.isEmpty() || trimmed.any { it != '.' && !it.isDigit() }) return null
    val value = trimmed.toFloatOrNull() ?: return null
    if (value > MAX_DECLINATION) return null
    return if (east) value else -value
}

/** Turns a declination back into the field's text and east/west choice, e.g. −1.25 → ("1.25", false). */
fun declinationFieldText(declination: Float): Pair<String, Boolean> {
    val text = abs(declination).toBigDecimal().stripTrailingZeros().toPlainString()
    return text to (declination >= 0f)
}

/**
 * The declination from the phone's location is worked out at most once a day: when the screen opens on a day
 * (counted as days since 1970) other than the one it was last worked out on. A day in the future, after the clock
 * has been moved back, counts as another day too.
 */
fun declinationNeedsRefresh(savedDay: Long?, today: Long): Boolean = savedDay != today

/** Where the declination in use comes from. */
enum class DeclinationSource { Location, Manual }

/**
 * Picks the declination to use. The one worked out from the phone's location wins while location is allowed, even
 * if it's from an earlier day (it changes by a fraction of a degree a year). Otherwise the typed-in one, if any.
 * Null means there's no declination, so the heading stays magnetic.
 */
fun chooseDeclination(locationAllowed: Boolean, fromLocation: Float?, manual: Float?): Pair<Float, DeclinationSource>? = when {
    locationAllowed && fromLocation != null -> fromLocation to DeclinationSource.Location
    manual != null -> manual to DeclinationSource.Manual
    else -> null
}
