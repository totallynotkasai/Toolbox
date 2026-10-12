package com.toolbox.app.tools.dice

/** One roll for the Dice's history: the die type and the faces, in the order of the dice. */
data class DiceRoll(val sides: Int, val faces: List<Int>) {
    val total get() = faces.sum()

    /** The fields it's saved as: the sides, then each face. */
    fun fields(): List<String> = listOf("$sides") + faces.map { "$it" }

    /** "3d6: 4 + 2 + 5 = 11", or "1d20: 17" for one die. */
    val text: String get() = "${faces.size}d$sides: " + if (faces.size == 1) "${faces[0]}" else "${faces.joinToString(" + ")} = $total"

    /** What the strip shows: the total, which for one die is its face. */
    val short: String get() = "$total"

    /** "3 6-sided dice: 4, 2, 5, total 11", or "1 20-sided die: 17". */
    val spoken: String get() =
        if (faces.size == 1) "1 $sides-sided die: ${faces[0]}"
        else "${faces.size} $sides-sided dice: ${faces.joinToString(", ")}, total $total"

    companion object {
        /** Reads [fields] back, or null if they aren't a die type and faces that fit it. */
        fun from(fields: List<String>): DiceRoll? {
            val numbers = fields.map { it.toIntOrNull() ?: return null }
            val sides = numbers.firstOrNull() ?: return null
            val faces = numbers.drop(1)
            if (sides < 2 || faces.isEmpty() || faces.any { it !in 1..sides }) return null
            return DiceRoll(sides, faces)
        }
    }
}
