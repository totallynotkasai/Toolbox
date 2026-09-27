package com.toolbox.app

import com.toolbox.app.tools.teams.parseNames
import com.toolbox.app.tools.teams.splitTeams
import com.toolbox.app.tools.teams.teamsAsText
import com.toolbox.app.tools.teams.teamsForSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TeamsTest {
    private val people = (1..11).map { "P$it" }

    @Test fun everyoneIsPlacedExactlyOnce() {
        val teams = splitTeams(people, 3, Random(1))
        assertEquals(3, teams.size)
        assertEquals(people.sorted(), teams.flatten().sorted())
    }

    @Test fun sizesDifferByAtMostOne() {
        repeat(50) { seed ->
            for (n in 2..11) {
                val sizes = splitTeams(people, n, Random(seed)).map { it.size }
                assertTrue("n=$n sizes=$sizes", sizes.max() - sizes.min() <= 1)
            }
        }
    }

    @Test fun actuallyShuffles() {
        val results = (0 until 20).map { splitTeams(people, 2, Random(it)) }.toSet()
        assertTrue(results.size > 1)
    }

    @Test fun clampsTeamCountToPeople() {
        assertEquals(3, splitTeams(listOf("a", "b", "c"), 10).size)
        assertEquals(1, splitTeams(listOf("a", "b", "c"), 0).size)
        assertEquals(emptyList<List<String>>(), splitTeams(emptyList(), 3))
    }

    @Test fun teamsForSizeRoundsUp() {
        assertEquals(3, teamsForSize(7, 3)) // 3, 2, 2
        assertEquals(2, teamsForSize(8, 4))
        assertEquals(0, teamsForSize(0, 4))
        assertEquals(5, teamsForSize(5, 0)) // guards against divide-by-zero
    }

    @Test fun parsesPastedLists() {
        assertEquals(listOf("Sam", "Alex", "Jo", "Kim"), parseNames(" Sam, Alex ;Jo\n\n Kim ,, "))
    }

    @Test fun shareText() {
        assertEquals("Team 1\n• a\n• b\n\nTeam 2\n• c", teamsAsText(listOf(listOf("a", "b"), listOf("c"))))
    }
}
