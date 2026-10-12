package com.toolbox.app

import com.toolbox.app.ui.theme.GoogleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The website's tool list (`docs/index.html`) shows every tool as the app does: category, order, title, colour and icon. */
class WebsiteTest {
    private val html = File(System.getProperty("websiteFile") ?: "../docs/index.html").readText()

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
            val listed = tile.findAll(section).map { it.groupValues[1] }.toList()
            assertEquals(category.title, Tool.sorted.filter { it.category == category }.map { it.route }, listed)
        }
    }

    @Test fun tilesMatchTheApp() {
        val tiles = tile.findAll(html).associateBy { it.groupValues[1] }
        assertEquals(Tool.entries.size, html.split("data-tool=").size - 1) // none left unmatched by the pattern
        for (tool in Tool.entries) {
            val t = tiles[tool.route] ?: error("${tool.title} isn't on the website")
            assertEquals(tool.title, colourName(tool), t.groupValues[2])
            assertEquals(tool.title, tool.route, t.groupValues[3])
            assertEquals(tool.title.replace("&", "&amp;"), t.groupValues[4])
        }
    }

    @Test fun theToolCountIsRight() {
        val n = Tool.entries.size
        assertTrue("""<div class="stat"><b>$n</b>""" in html)
        assertTrue("""content="Toolbox: $n everyday tools""" in html)
    }
}
