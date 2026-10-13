package com.toolbox.app.tools.teams

import kotlin.random.Random

/**
 * Shuffles [names] and deals them round-robin into [teams] teams, so team sizes differ by at most one.
 * [teams] is clamped to 1..names.size (no empty teams); an empty list gives no teams.
 */
fun splitTeams(names: List<String>, teams: Int, random: Random = Random.Default): List<List<String>> {
    if (names.isEmpty()) return emptyList()
    val count = teams.coerceIn(1, names.size)
    val buckets = List(count) { mutableListOf<String>() }
    names.shuffled(random).forEachIndexed { i, name -> buckets[i % count] += name }
    return buckets
}

/** How many teams are needed so no team has more than [perTeam] people. */
fun teamsForSize(people: Int, perTeam: Int): Int =
    if (people <= 0) 0 else (people + perTeam.coerceAtLeast(1) - 1) / perTeam.coerceAtLeast(1)

/** Splits typed or pasted text into names: commas, semicolons and new lines separate entries. */
fun parseNames(text: String): List<String> =
    text.split(',', ';', '\n').map { it.trim().take(MAX_NAME_LENGTH) }.filter { it.isNotEmpty() }

/** Plain-text version of the teams for the share sheet. */
fun teamsAsText(teams: List<List<String>>): String =
    teams.withIndex().joinToString("\n\n") { (i, t) -> "Team ${i + 1}\n" + t.joinToString("\n") { "• $it" } }

/**
 * The last split, saved with the [names] it was made from, so it can come back on reopening. Names can't contain these
 * control characters (the name field replaces them); an empty split saves as "".
 */
fun encodeSplit(names: List<String>, teams: List<List<String>>): String =
    if (teams.isEmpty()) "" else names.joinToString(UNIT) + GROUP + teams.joinToString(RECORD) { it.joinToString(UNIT) }

/**
 * The split saved in [text], if it was made from exactly [names] (in the same order, as the list only changes by adding
 * and removing) and still holds each of them once. Otherwise it's stale and there's none.
 */
fun decodeSplit(text: String, names: List<String>): List<List<String>> {
    val saved = text.split(GROUP)
    if (saved.size != 2 || names.size < 2 || saved[0] != names.joinToString(UNIT)) return emptyList()
    val teams = saved[1].split(RECORD).map { it.split(UNIT) }
    return if (teams.flatten().sorted() == names.sorted()) teams else emptyList()
}

/** Characters a name can never contain, so they can split the saved names and teams. */
const val GROUP = "\u001D"
const val RECORD = "\u001E"
const val UNIT = "\u001F"

/** [text] with the separators above turned into spaces, for the name field. */
fun withoutSeparators(text: String): String = text.replace(GROUP, " ").replace(RECORD, " ").replace(UNIT, " ")

const val MAX_NAME_LENGTH = 30
const val MAX_PEOPLE = 60
