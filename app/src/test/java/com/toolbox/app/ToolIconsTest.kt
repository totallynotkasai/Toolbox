package com.toolbox.app

import com.toolbox.app.ui.common.Glyph
import com.toolbox.app.ui.common.GlyphBuilder
import com.toolbox.app.ui.common.ToolIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The website shows the same icons as the app, from SVGs in `docs/icons/` written from [ToolIcons].
 * This fails when they differ; `./gradlew testDebugUnitTest -PupdateIcons` rewrites them instead.
 */
class ToolIconsTest {
    private val glyphs: List<Glyph> = ToolIcons::class.java.declaredFields
        .filter { it.type == Glyph::class.java }
        .map { it.isAccessible = true; it.get(ToolIcons) as Glyph }

    private val dir = File(System.getProperty("iconsDir") ?: "../docs/icons")
    private val update = System.getProperty("updateIcons") == "true"

    private fun fileName(glyph: Glyph) = glyph.name.replace(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[a-z])(?=[0-9])"), "-").lowercase() + ".svg"

    @Test fun everyIconIsFound() {
        assertEquals(16, glyphs.size)
        assertEquals("merge-2048.svg", fileName(glyphs.first { it.name == "Merge2048" }))
        assertEquals("eight-ball.svg", fileName(glyphs.first { it.name == "EightBall" }))
    }

    @Test fun linesComeInTwoWeights() {
        val weights = glyphs.flatMap { g -> g.layers.mapNotNull { it.stroke } }.toSet()
        assertEquals(setOf(GlyphBuilder.LINE, GlyphBuilder.DETAIL), weights)
    }

    @Test fun everyPathParses() {
        for (g in glyphs) assertTrue(g.name, g.vector.root.size == g.layers.size)
    }

    @Test fun websiteIconsMatchTheApp() {
        if (update) dir.mkdirs()
        val expected = glyphs.associate { fileName(it) to it.toSvg() }
        if (update) {
            dir.listFiles { f -> f.extension == "svg" }?.filter { it.name !in expected }?.forEach { it.delete() }
            expected.forEach { (name, svg) -> File(dir, name).writeText(svg) }
        }
        val onDisk = dir.listFiles { f -> f.extension == "svg" }.orEmpty().map { it.name }.toSet()
        assertEquals("Run ./gradlew testDebugUnitTest -PupdateIcons", expected.keys, onDisk)
        expected.forEach { (name, svg) ->
            // Git may check the files out with Windows line endings.
            val file = File(dir, name).readText().replace("\r\n", "\n")
            assertEquals("$name is out of date. Run ./gradlew testDebugUnitTest -PupdateIcons", svg, file)
        }
    }
}
