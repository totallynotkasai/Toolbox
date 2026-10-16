package com.toolbox.app

import com.toolbox.app.ui.theme.GoogleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The website's tool list (`docs/index.html`) shows every tool as the app does: category, order, title, colour and icon. */
class WebsiteTest {
    private val html = File(System.getProperty("websiteFile") ?: "../docs/index.html").readText()

    /**
     * Tools in the app that the live website leaves out until the release that ships them, so it never offers
     * something the APK doesn't have. A new tool goes in here, and the release that ships it adds its tile and
     * takes it out. Empty at 1.3.0; 1.4.0 ships Solitaire.
     */
    private val unreleased = setOf(Tool.Solitaire)
    private val listed = Tool.entries - unreleased

    private val tile = Regex(
        """<div class="tile" data-tool="([^"]+)"><span class="ic c-([a-z]+)" style="--i:url\(icons/([^)]+)\.svg\)"></span><div><b>([^<]+)</b>""",
    )

    private fun colourName(tool: Tool) = when (tool.accent) {
        GoogleColors.Blue -> "blue"
        GoogleColors.Red -> "red"
        GoogleColors.Yellow -> "yellow"
        GoogleColors.Green -> "green"
        else -> error("${tool.title} has no website colour")
    }

    @Test fun everyToolIsListedInTheAppsOrder() {
        // Each category's section holds its tools, alphabetically by title as on the home grid.
        val sections = html.split("""<div class="cat" data-c="""").drop(1)
        assertEquals(Category.entries.map { it.name }, sections.map { it.substringBefore('"') })
        for ((category, section) in Category.entries.zip(sections)) {
            val onSite = tile.findAll(section).map { it.groupValues[1] }.toList()
            assertEquals(category.title, Tool.sorted.filter { it.category == category && it in listed }.map { it.route }, onSite)
        }
    }

    @Test fun tilesMatchTheApp() {
        val tiles = tile.findAll(html).associateBy { it.groupValues[1] }
        assertEquals(listed.size, html.split("data-tool=").size - 1) // none left unmatched by the pattern
        for (tool in listed) {
            val t = tiles[tool.route] ?: error("${tool.title} isn't on the website")
            assertEquals(tool.title, colourName(tool), t.groupValues[2])
            assertEquals(tool.title, tool.route, t.groupValues[3])
            assertEquals(tool.title.replace("&", "&amp;"), t.groupValues[4])
        }
    }

    @Test fun theToolCountIsRight() {
        val n = listed.size
        assertTrue("""<div class="stat"><b>$n</b>""" in html)
        assertTrue("""content="Toolbox: $n everyday tools""" in html)
    }
}
