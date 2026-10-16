package com.toolbox.app

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.toolbox.app.ui.common.Glyph
import com.toolbox.app.ui.common.GlyphBuilder
import com.toolbox.app.ui.common.ToolIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The website shows the same icons as the app, from SVGs in `docs/icons/` named after each tool's route: custom icons
 * from their [Glyph], Material ones from their [ImageVector]. This fails when they differ;
 * `./gradlew testDebugUnitTest -PupdateIcons` rewrites them instead.
 */
class ToolIconsTest {
    private val glyphs: List<Glyph> = ToolIcons::class.java.declaredFields
        .filter { it.type == Glyph::class.java }
        .map { it.isAccessible = true; it.get(ToolIcons) as Glyph }

    private val dir = File(System.getProperty("iconsDir") ?: "../docs/icons")
    private val update = System.getProperty("updateIcons") == "true"

    private fun svgFor(tool: Tool): String =
        glyphs.firstOrNull { it.vector === tool.icon }?.toSvg() ?: tool.icon.toSvg()

    @Test fun everyIconIsFound() {
        assertEquals(20, glyphs.size)
        // Every custom icon is on a tile, so the website has all of them.
        for (g in glyphs) assertTrue(g.name, Tool.entries.any { it.icon === g.vector })
    }

    @Test fun linesComeInTwoWeights() {
        val weights = glyphs.flatMap { g -> g.layers.mapNotNull { it.stroke } }.toSet()
        assertEquals(setOf(GlyphBuilder.LINE, GlyphBuilder.DETAIL), weights)
    }

    @Test fun everyPathParses() {
        for (g in glyphs) assertTrue(g.name, g.vector.root.size == g.layers.size)
    }

    @Test fun materialIconsBecomeSvg() {
        val svg = Tool.Counter.icon.toSvg()
        assertTrue(svg, svg.contains("Filled.PlusOne") && svg.contains("<path d=\"M"))
        assertTrue(svg, svg.trimEnd().endsWith("</svg>"))
    }

    @Test fun websiteIconsMatchTheApp() {
        if (update) dir.mkdirs()
        val expected = Tool.entries.associate { "${it.route}.svg" to svgFor(it) }
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

/** A Material icon as a standalone SVG drawn in `currentColor`, like [Glyph.toSvg]. */
private fun ImageVector.toSvg(): String = buildString {
    appendLine("<!-- $name: Material Icons (Apache License 2.0), written by ToolIconsTest from Tools.kt. -->")
    appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${num(viewportWidth)} ${num(viewportHeight)}" width="24" height="24" fill="currentColor">""")
    appendNodes(root, "  ")
    appendLine("</svg>")
}

private fun StringBuilder.appendNodes(group: VectorGroup, indent: String) {
    for (node: VectorNode in group) when (node) {
        is VectorGroup -> {
            val transform = buildList {
                if (node.translationX != 0f || node.translationY != 0f) add("translate(${num(node.translationX)} ${num(node.translationY)})")
                if (node.rotation != 0f) add("rotate(${num(node.rotation)} ${num(node.pivotX)} ${num(node.pivotY)})")
                if (node.scaleX != 1f || node.scaleY != 1f) {
                    add("translate(${num(node.pivotX)} ${num(node.pivotY)}) scale(${num(node.scaleX)} ${num(node.scaleY)}) translate(${num(-node.pivotX)} ${num(-node.pivotY)})")
                }
            }.joinToString(" ")
            appendLine(if (transform.isEmpty()) "$indent<g>" else """$indent<g transform="$transform">""")
            appendNodes(node, "$indent  ")
            appendLine("$indent</g>")
        }
        is VectorPath -> {
            val attrs = buildString {
                if (node.fill == null) append(""" fill="none"""")
                if (node.fillAlpha != 1f) append(""" fill-opacity="${num(node.fillAlpha)}"""")
                if (node.pathFillType == PathFillType.EvenOdd) append(""" fill-rule="evenodd"""")
                if (node.stroke is SolidColor && node.strokeLineWidth > 0f) {
                    append(""" stroke="currentColor" stroke-width="${num(node.strokeLineWidth)}"""")
                    if (node.strokeLineCap == StrokeCap.Round) append(""" stroke-linecap="round"""")
                    if (node.strokeLineJoin == StrokeJoin.Round) append(""" stroke-linejoin="round"""")
                }
            }
            appendLine("""$indent<path d="${node.pathData.toPathData()}"$attrs/>""")
        }
    }
}

private fun List<PathNode>.toPathData(): String = joinToString("") { n ->
    fun f(vararg v: Float) = v.joinToString(",") { num(it) }
    fun b(v: Boolean) = if (v) "1" else "0"
    when (n) {
        is PathNode.Close -> "Z"
        is PathNode.MoveTo -> "M" + f(n.x, n.y)
        is PathNode.RelativeMoveTo -> "m" + f(n.dx, n.dy)
        is PathNode.LineTo -> "L" + f(n.x, n.y)
        is PathNode.RelativeLineTo -> "l" + f(n.dx, n.dy)
        is PathNode.HorizontalTo -> "H" + f(n.x)
        is PathNode.RelativeHorizontalTo -> "h" + f(n.dx)
        is PathNode.VerticalTo -> "V" + f(n.y)
        is PathNode.RelativeVerticalTo -> "v" + f(n.dy)
        is PathNode.CurveTo -> "C" + f(n.x1, n.y1, n.x2, n.y2, n.x3, n.y3)
        is PathNode.RelativeCurveTo -> "c" + f(n.dx1, n.dy1, n.dx2, n.dy2, n.dx3, n.dy3)
        is PathNode.ReflectiveCurveTo -> "S" + f(n.x1, n.y1, n.x2, n.y2)
        is PathNode.RelativeReflectiveCurveTo -> "s" + f(n.dx1, n.dy1, n.dx2, n.dy2)
        is PathNode.QuadTo -> "Q" + f(n.x1, n.y1, n.x2, n.y2)
        is PathNode.RelativeQuadTo -> "q" + f(n.dx1, n.dy1, n.dx2, n.dy2)
        is PathNode.ReflectiveQuadTo -> "T" + f(n.x, n.y)
        is PathNode.RelativeReflectiveQuadTo -> "t" + f(n.dx, n.dy)
        is PathNode.ArcTo -> "A" + f(n.horizontalEllipseRadius, n.verticalEllipseRadius, n.theta) +
            ",${b(n.isMoreThanHalf)},${b(n.isPositiveArc)}," + f(n.arcStartX, n.arcStartY)
        is PathNode.RelativeArcTo -> "a" + f(n.horizontalEllipseRadius, n.verticalEllipseRadius, n.theta) +
            ",${b(n.isMoreThanHalf)},${b(n.isPositiveArc)}," + f(n.arcStartDx, n.arcStartDy)
    }
}

private fun num(v: Float): String = String.format(Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
