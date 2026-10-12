package com.toolbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesTest {
    @Test fun toggleAddsAndRemoves() {
        val one = Favourites.toggle("", "coin")
        assertEquals(setOf("coin"), Favourites.parse(one))
        val two = Favourites.toggle(one, "snake")
        assertEquals(setOf("coin", "snake"), Favourites.parse(two))
        assertEquals(setOf("snake"), Favourites.parse(Favourites.toggle(two, "coin")))
    }

    @Test fun parseIgnoresBlanksAndRepeats() {
        assertEquals(setOf("a", "b"), Favourites.parse(" a,,b,a, "))
        assertEquals(emptySet<String>(), Favourites.parse(""))
    }

    @Test fun toggleTidiesWhatWasStored() {
        // A repeat or stray space saved by an older version is cleaned up by the next star.
        assertEquals(setOf("coin", "dice"), Favourites.parse(Favourites.toggle(" coin,coin, ", "dice")))
        assertEquals("", Favourites.toggle("coin", "coin"))
    }

    @Test fun anOldRouteIsKeptButShowsNothing() {
        // A route that no longer names a tool stays saved, and the home grid only shows real tools.
        val stored = Favourites.toggle("gone", "timer")
        assertEquals(setOf("gone", "timer"), Favourites.parse(stored))
        assertEquals(listOf(Tool.Timer), Tool.sorted.filter { it.route in Favourites.parse(stored) })
        assertNull(Tool.byRoute("gone"))
        assertNull(Tool.byRoute(null))
    }

    @Test fun theMergedCoinAndDiceToolsTakeOverTheOldFavourites() {
        val renames = Migrations.MERGED_ROUTES
        assertEquals("coin,snake,dice", Favourites.rename("cointoss,snake,dicetoss", renames))
        assertEquals("coin,dice", Favourites.rename("coin,cointoss,dice,dicetoss", renames)) // just one of each
        assertEquals("timer", Favourites.rename("timer", renames))
        assertEquals("", Favourites.rename("", renames))
        // The old routes are gone and the new ones are real tools.
        assertTrue(renames.keys.none { Tool.byRoute(it) != null })
        assertTrue(renames.values.all { Tool.byRoute(it) != null })
    }

    @Test fun everyToolHasAUniqueRouteAndIsSortedByTitle() {
        assertEquals(Tool.entries.size, Tool.entries.map { it.route }.toSet().size)
        assertEquals(Tool.entries.size, Tool.entries.map { it.title.lowercase() }.toSet().size)
        val titles = Tool.sorted.map { it.title.lowercase() }
        assertEquals(titles.sorted(), titles)
    }

    @Test fun routesAreSafeToStoreAndLinkTo() {
        // Routes are saved comma-separated, used as navigation routes and named the website's icon files.
        for (tool in Tool.entries) {
            assertTrue(tool.route, tool.route.matches(Regex("[a-z0-9]+")))
            assertEquals(tool, Tool.byRoute(tool.route))
        }
    }

    @Test fun everyCategoryHasTools() {
        for (category in Category.entries) assertTrue(category.title, Tool.entries.any { it.category == category })
    }
}
