package com.toolbox.app.tools.random

/** One number for the Random Number's history, with the range it was picked from. */
data class Draw(val value: Long, val min: Long, val max: Long) {
    fun fields(): List<String> = listOf("$value", "$min", "$max")

    /** "42 (1–100)". With a negative end the dash would read as a minus, so that's "-3 (-10 to 10)". */
    val text: String get() = "$value ($min${if (min < 0 || max < 0) " to " else "–"}$max)"

    /** "42, from 1 to 100". */
    val spoken: String get() = "$value, from $min to $max"

    companion object {
        /** Reads [fields] back, or null if they aren't a number inside its range. */
        fun from(fields: List<String>): Draw? {
            if (fields.size != 3) return null
            val (value, min, max) = fields.map { it.toLongOrNull() ?: return null }
            if (min > max || value !in min..max) return null
            return Draw(value, min, max)
        }
    }
}
