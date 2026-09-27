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

const val MAX_NAME_LENGTH = 30
const val MAX_PEOPLE = 60
