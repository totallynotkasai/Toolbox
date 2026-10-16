package com.toolbox.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Hand-drawn home-grid icons for tools that no Material icon fits. All are single-colour on a 24×24 viewport
 * (the home grid tints them), written as SVG path data, and kept within the 2-unit keyline like Material's.
 *
 * Solid parts are filled shapes, as in Material Filled. Lines come in two weights only, [GlyphBuilder.line] (2.0)
 * and [GlyphBuilder.detail] (1.5), so the icons sit together and beside Material's. Holes are cut with the
 * non-zero rule: a hole circle runs the opposite way round to the shape it sits in.
 *
 * The same paths are exported to `docs/icons/` for the website by `ToolIconsTest`.
 */
object ToolIcons {
    /** Black ball with a white window showing an "8". */
    val EightBall = glyph("EightBall") {
        fill(
            "M2,12a10,10 0 1,1 20,0a10,10 0 1,1 -20,0Z" +
                "M6.6,12a5.4,5.4 0 1,0 10.8,0a5.4,5.4 0 1,0 -10.8,0Z" +
                "M10,9.65a2,2 0 1,1 4,0a2,2 0 1,1 -4,0ZM11.15,9.65a0.85,0.85 0 1,0 1.7,0a0.85,0.85 0 1,0 -1.7,0Z" +
                "M9.7,13.9a2.3,2.3 0 1,1 4.6,0a2.3,2.3 0 1,1 -4.6,0ZM10.95,13.9a1.05,1.05 0 1,0 2.1,0a1.05,1.05 0 1,0 -2.1,0Z",
        )
    }

    /** A wheel in four quarters, two filled and two outlined. */
    val Spinner = glyph("Spinner") {
        fill("M12,12V3A9,9 0 0,0 3,12ZM12,12V21A9,9 0 0,0 21,12Z")
        line("M3,12a9,9 0 1,1 18,0a9,9 0 1,1 -18,0ZM12,3V21M3,12H21")
    }

    /** A coin mid-flip: the crown from its heads face on a tilted face, and the edge showing below. */
    val Coin = glyph("Coin") {
        turned(-14f, 12f, 12f, dy = 1f) {
            fill("M3.5,9.6a8.5,5 0 1,1 17,0a8.5,5 0 1,1 -17,0ZM8,11.6L16,11.6L16.4,8.2L14,9.6L12,7.36L10,9.6L7.6,8.2Z")
            fill("M3.5,10.9V12.5A8.5,5 0 0,0 20.5,12.5V10.9A8.5,5 0 0,1 3.5,10.9Z")
        }
    }

    /** Two dice tumbling, showing three and five. */
    val Dice = glyph("Dice") {
        turned(-14f, 7.6f, 15.6f) {
            fill(
                "M5.1,11.1H10.1A2,2 0 0,1 12.1,13.1V18.1A2,2 0 0,1 10.1,20.1H5.1A2,2 0 0,1 3.1,18.1V13.1A2,2 0 0,1 5.1,11.1Z" +
                    pips(7.6f, 15.6f, "\\.\\"),
            )
        }
        turned(12f, 16.2f, 8.2f) {
            fill(
                "M13.7,3.7H18.7A2,2 0 0,1 20.7,5.7V10.7A2,2 0 0,1 18.7,12.7H13.7A2,2 0 0,1 11.7,10.7V5.7A2,2 0 0,1 13.7,3.7Z" +
                    pips(16.2f, 8.2f, ":.:"),
            )
        }
    }

    /** Classic tri-spinner: three lobes joined by concave sides, a bearing in each lobe and the hub. */
    val FidgetSpinner = glyph("FidgetSpinner") {
        fill(
            "M7.93,7.17A4.1,4.1 0 1,1 16.07,7.17A5.05,5.05 0 0,0 19.09,12.39A4.1,4.1 0 1,1 15.01,19.44" +
                "A5.05,5.05 0 0,0 8.99,19.44A4.1,4.1 0 1,1 4.91,12.39A5.05,5.05 0 0,0 7.93,7.17Z" +
                "M9.7,13a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM10.8,13a1.2,1.2 0 1,1 2.4,0a1.2,1.2 0 1,1 -2.4,0Z" +
                "M9.7,6.7a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM10.85,6.7a1.15,1.15 0 1,1 2.3,0a1.15,1.15 0 1,1 -2.3,0Z" +
                "M15.16,16.15a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM16.31,16.15a1.15,1.15 0 1,1 2.3,0a1.15,1.15 0 1,1 -2.3,0Z" +
                "M4.24,16.15a2.3,2.3 0 1,0 4.6,0a2.3,2.3 0 1,0 -4.6,0ZM5.39,16.15a1.15,1.15 0 1,1 2.3,0a1.15,1.15 0 1,1 -2.3,0Z",
        )
    }

    /** A lotus of three petals: the Calm category, and Meditate's tile. */
    val Lotus = glyph("Lotus") {
        fill(
            "M12,3.95C16.02,7.98 16.02,14.88 12,18.9C7.98,14.88 7.98,7.98 12,3.95Z" +
                "M2.57,9.93C2.34,15.22 5.56,19.59 10.16,20.28C8.09,17.29 6.71,12.46 2.57,9.93Z" +
                "M21.43,9.93C21.66,15.22 18.44,19.59 13.84,20.28C15.91,17.29 17.29,12.46 21.43,9.93Z",
        )
    }

    /**
     * A crescent moon over two waves: night, and sound. The moon is what's left of a circle of radius 5 at (12, 7)
     * outside one of radius 4.3 at (14.6, 5.2); the arcs meet where the two circles cross.
     */
    val Soundscapes = glyph("Soundscapes") {
        fill("M11.72,2.01A5,5 0 1,0 16.57,9.02A4.3,4.3 0 0,1 11.72,2.01Z")
        line("M3,15q2.25,-2 4.5,0t4.5,0t4.5,0t4.5,0M3,19.5q2.25,-2 4.5,0t4.5,0t4.5,0t4.5,0")
    }

    /** Two "2"s tipping into each other, with impact sparks above. */
    val Merge2048 = glyph("Merge2048") {
        val two = "M0.2,3A2.8,2.8 0 1,1 5.29,4.61L0.2,9H6.2"
        turned(14f, 3.1f, 4.5f, dx = 3.3f, dy = 8.6f) { line(two) }
        turned(-14f, 3.1f, 4.5f, dx = 14.1f, dy = 8.6f) { line(two) }
        detail("M12,7.1V4.3M9.7,7.5L8.5,5.7M14.3,7.5L15.5,5.7")
    }

    /** Two rows of bricks with the middle one knocked out, a ball and a paddle. */
    val BlockBreaker = glyph("BlockBreaker") {
        fill(
            "M2.5,3h5.5v2.8h-5.5ZM9.25,3h5.5v2.8h-5.5ZM16,3h5.5v2.8h-5.5ZM2.5,7h5.5v2.8h-5.5ZM16,7h5.5v2.8h-5.5Z" +
                "M10.5,13.4a1.5,1.5 0 1,1 3,0a1.5,1.5 0 1,1 -3,0ZM7,19h10v2h-10Z",
        )
    }

    /**
     * A T piece a row above the notch it fits, over a stack of two pieces that interlock. The pieces sit on a grid of
     * 5-unit cells, each inset by 0.75, so a gap of 1.5 shows where they meet.
     */
    val BlockDrop = glyph("BlockDrop") {
        fill(
            "M2.75,2.75H16.25V6.25H11.25V11.25H7.75V6.25H2.75Z" +
                "M17.75,7.75H21.25V21.25H17.75V16.25H12.75V12.75H17.75Z" +
                "M2.75,12.75H6.25V17.75H16.25V21.25H2.75Z",
        )
    }

    /**
     * A card held up to a head: a card with a word on it, just above a head and shoulders. Gaps of 1.5 keep the three
     * apart, and the word is a bar cut out of the card.
     */
    val Charades = glyph("Charades") {
        fill(
            "M6,2.5H18A1.5,1.5 0 0,1 19.5,4V7.6A1.5,1.5 0 0,1 18,9.1H6A1.5,1.5 0 0,1 4.5,7.6V4A1.5,1.5 0 0,1 6,2.5Z" +
                "M8,5.05V6.55H16V5.05Z" +
                circle(12f, 13.9f, 3.3f) +
                "M4.5,22V21.2C4.5,19.6 8,18.7 12,18.7S19.5,19.6 19.5,21.2V22Z",
        )
    }

    /** The Simon pad: four quarters with one lit. */
    val Simon = glyph("Simon") {
        fill("M11.15,2.04A10,10 0 0,0 2.04,11.15L11.15,11.15Z")
        line(
            "M13.85,3.19A9,9 0 0,1 20.81,10.15L13.85,10.15Z" +
                "M10.15,20.81A9,9 0 0,1 3.19,13.85L10.15,13.85Z" +
                "M13.85,20.81A9,9 0 0,0 20.81,13.85L13.85,13.85Z",
        )
    }

    /**
     * An S-shaped snake with an eye and a forked tongue. The body is a filled band 3 wide, built from straight runs
     * and arcs (each turn's outer and inner edges are arcs about the same centre), with round ends.
     */
    val Snake = glyph("Snake") {
        fill(
            "M4.5,20.5L13.5,20.5A5,5 0 0,0 13.5,10.5L9.5,10.5A2,2 0 0,1 9.5,6.5L14.5,6.5A1.5,1.5 0 0,0 14.5,3.5" +
                "L9.5,3.5A5,5 0 0,0 9.5,13.5L13.5,13.5A2,2 0 0,1 13.5,17.5L4.5,17.5A1.5,1.5 0 0,0 4.5,20.5Z",
        )
        fill(circle(16.5f, 5f, 2.6f) + circle(17.2f, 4.2f, 0.75f, hole = true))
        detail("M19.1,5H20.5L21.6,3.9M20.5,5L21.6,6.1")
    }

    /** The upright board on its legs, with discs in the four slots of a rising diagonal. */
    val ConnectFour = glyph("ConnectFour") {
        val columns = listOf(5f, 9.67f, 14.33f, 19f)
        val rows = listOf(4.6f, 8.4f, 12.2f, 16f)
        fill(
            "M4.5,2.2H19.5A2,2 0 0,1 21.5,4.2V16.4A2,2 0 0,1 19.5,18.4H4.5A2,2 0 0,1 2.5,16.4V4.2A2,2 0 0,1 4.5,2.2Z" +
                columns.joinToString("") { x -> rows.joinToString("") { y -> circle(x, y, 1.55f, hole = true) } } +
                "M3.5,18L5,22H7L7.5,18ZM20.5,18L19,22H17L16.5,18Z",
        )
        fill(columns.indices.joinToString("") { i -> circle(columns[i], rows[3 - i], 1f) })
    }

    /** A 3×3 grid with an X and an O in opposite corners. */
    val TicTacToe = glyph("TicTacToe") {
        line("M9,3.5V20.5M15,3.5V20.5M3.5,9H20.5M3.5,15H20.5")
        detail("M3.7,3.7L6.7,6.7M6.7,3.7L3.7,6.7" + circle(18.8f, 18.8f, 1.6f))
    }

    /** Rows of bubbles, one of them popped. */
    val BubbleWrap = glyph("BubbleWrap") {
        fill(
            "M2.6,5.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0ZM3.7,4.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                "M9.1,5.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                "M10.2,4.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                "M15.6,5.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                "M16.7,4.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                "M5.85,12a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0ZM6.95,11a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                "M12.35,12a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                "M13.45,11a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                "M2.6,18.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                "M3.7,17.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z" +
                "M15.6,18.5a2.9,2.9 0 1,1 5.8,0a2.9,2.9 0 1,1 -5.8,0Z" +
                "M16.7,17.5a0.8,0.8 0 1,0 1.6,0a0.8,0.8 0 1,0 -1.6,0Z",
        )
        detail("M10.2,16.8L11.3,18.1L10.3,19.4M13.8,16.8L12.7,18.1L13.7,19.4")
    }

    /** A knob with a pointer slot, and five notch marks round the top. It sits low to balance the marks. */
    val Dial = glyph("Dial") {
        fill(circle(12f, 14f, 7f) + "M11.05,9.5V12.5a0.95,0.95 0 0,0 1.9,0V9.5a0.95,0.95 0 0,0 -1.9,0Z")
        detail("M12,5V3.5M16.5,6.21L17.25,4.91M7.5,6.21L6.75,4.91M19.79,9.5L21.09,8.75M4.21,9.5L2.91,8.75")
    }

    /**
     * The infinity cube part-folded: the top four cubes lifting away from the bottom four about the back edge, seen from
     * the front corner and a little above, with a gap of 1.4 between faces. The faces were worked out from a model of
     * the eight cubes (the top half turned 32°, the view 42° from level), and the parts the top half hides cut away.
     */
    val InfinityCube = glyph("InfinityCube") {
        fill(
            "M18.28,15.83L18.28,13.7L15.66,15.46L15.66,18L18.03,16.41ZM14.26,18.94L14.26,16.39L11.63,18.15L11.63,20.69Z" +
                "M13.34,13.16L14.96,14.24L16.63,13.12ZM6.91,10.54L4.15,12.39L5.45,13.26L8.43,13.22L9.68,12.39Z" +
                "M10.93,13.23L8.17,15.08L10.93,16.93L13.7,15.08ZM3.59,16.24L6.21,18L6.21,15.46L3.59,13.7Z" +
                "M7.61,18.94L10.23,20.69L10.23,18.15L7.61,16.39ZM18.51,11.7L20.04,8.1L18.16,8.12L16.63,11.72Z" +
                "M15.1,11.74L16.63,8.14L14.75,8.16L13.22,11.76ZM12.86,2.7L11.92,2.71L13.89,4.03L14.82,4.02Z" +
                "M16.88,5.39L15.94,5.41L17.91,6.72L18.85,6.71ZM9.45,2.74L8.51,2.76L10.47,4.07L11.41,4.06Z" +
                "M13.47,5.44L12.53,5.45L14.5,6.76L15.44,6.75ZM4.98,6.84L7.82,8.73L9.4,5.03L6.56,3.14Z" +
                "M9.01,9.53L11.84,11.42L13.42,7.73L10.58,5.83Z",
        )
    }

    /**
     * A tilted tube with the grains settled at its low end and two still falling. The pile's top is drawn level
     * with the ground, not square to the tube: in the tube's frame (turned 35°) that's a slope of tan 35°.
     */
    val RainStick = glyph("RainStick") {
        turned(35f, 12f, 12f) {
            line("M8.8,5a3.2,3.2 0 0,1 6.4,0V19a3.2,3.2 0 0,1 -6.4,0Z")
            fill("M10.4,17.12L13.6,14.88V19A1.6,1.6 0 0,1 10.4,19Z" + circle(11.4f, 12.4f, 0.65f) + circle(12.6f, 9.6f, 0.65f))
        }
    }

    /** A zip: closed teeth above the slider and open below, splayed clear of the pull tab. */
    val Zip = glyph("Zip") {
        val open = listOf(16.3f to 3.3f, 18f to 4.9f, 19.7f to 6.5f, 21.4f to 8.1f)
        fill(
            "M9,1.8h3v1.25h-3ZM12,3.55h3v1.25h-3ZM9,5.3h3v1.25h-3ZM12,7.05h3v1.25h-3ZM9,8.8h3v1.25h-3Z" +
                "M7.8,10.6H16.2L14.6,15H9.4Z" +
                open.joinToString("") { (y, out) -> circle(12 - out, y, 0.95f) + circle(12 + out, y, 0.95f) },
        )
        fill("M10.8,15.7a1.2,1.2 0 0,1 2.4,0V20.3a1.2,1.2 0 0,1 -2.4,0Z")
    }
}

/** One icon as layers of path data: builds the app's [vector] and the website's SVG from the same paths. */
class Glyph(val name: String, val layers: List<Layer>) {
    /** A filled path, or a stroked one [stroke] wide, optionally [turn]ed. */
    class Layer(val pathData: String, val stroke: Float?, val turn: Turn?)

    /** Rotates a layer [degrees] clockwise about ([pivotX], [pivotY]), then moves it by ([dx], [dy]). */
    class Turn(val degrees: Float, val pivotX: Float, val pivotY: Float, val dx: Float, val dy: Float)

    val vector: ImageVector by lazy {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (layer in layers) {
                layer.turn?.let { addGroup(rotate = it.degrees, pivotX = it.pivotX, pivotY = it.pivotY, translationX = it.dx, translationY = it.dy) }
                val nodes = addPathNodes(layer.pathData)
                if (layer.stroke == null) {
                    addPath(nodes, fill = ink)
                } else {
                    addPath(nodes, stroke = ink, strokeLineWidth = layer.stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
                }
                if (layer.turn != null) clearGroup()
            }
        }.build()
    }

    /** The icon as a standalone SVG drawn in `currentColor`, so a page can tint it. */
    fun toSvg(): String = buildString {
        appendLine("<!-- $name: generated from ToolIcons.kt by ToolIconsTest. Edit the Kotlin, not this file. -->")
        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="currentColor">""")
        for (layer in layers) {
            val stroke = layer.stroke?.let {
                """ fill="none" stroke="currentColor" stroke-width="${num(it)}" stroke-linecap="round" stroke-linejoin="round""""
            } ?: ""
            val path = """<path d="${layer.pathData}"$stroke/>"""
            val turn = layer.turn
            if (turn == null) {
                appendLine("  $path")
            } else {
                val move = if (turn.dx != 0f || turn.dy != 0f) "translate(${num(turn.dx)} ${num(turn.dy)}) " else ""
                appendLine("""  <g transform="${move}rotate(${num(turn.degrees)} ${num(turn.pivotX)} ${num(turn.pivotY)})">$path</g>""")
            }
        }
        appendLine("</svg>")
    }
}

class GlyphBuilder {
    private val layers = mutableListOf<Glyph.Layer>()
    private var turn: Glyph.Turn? = null

    fun fill(d: String) {
        layers += Glyph.Layer(d, null, turn)
    }

    /** A line at the standard 2.0 weight. */
    fun line(d: String) {
        layers += Glyph.Layer(d, LINE, turn)
    }

    /** A line at the 1.5 detail weight. */
    fun detail(d: String) {
        layers += Glyph.Layer(d, DETAIL, turn)
    }

    /** Layers added in [block] are rotated [degrees] clockwise about ([pivotX], [pivotY]), then moved by ([dx], [dy]). */
    fun turned(degrees: Float, pivotX: Float, pivotY: Float, dx: Float = 0f, dy: Float = 0f, block: GlyphBuilder.() -> Unit) {
        turn = Glyph.Turn(degrees, pivotX, pivotY, dx, dy)
        block()
        turn = null
    }

    fun build(name: String) = Glyph(name, layers.toList())

    companion object {
        const val LINE = 2f
        const val DETAIL = 1.5f
    }
}

private fun glyph(name: String, block: GlyphBuilder.() -> Unit): Glyph = GlyphBuilder().apply(block).build(name)

private val ink = SolidColor(Color.Black)

/** A circle as path data, drawn clockwise, or anticlockwise to cut a [hole] in a shape it sits in. */
private fun circle(cx: Float, cy: Float, r: Float, hole: Boolean = false): String {
    val sweep = if (hole) 0 else 1
    return "M${num(cx - r)},${num(cy)}a${num(r)},${num(r)} 0 1,$sweep ${num(2 * r)},0a${num(r)},${num(r)} 0 1,$sweep ${num(-2 * r)},0Z"
}

/**
 * Pip holes for a 9-unit die centred on ([cx], [cy]), one character per row of its 3×3 pattern: `.` is a pip in
 * the middle, `:` one at each side, and `\` one on the falling diagonal. So "\\.\\" is three and ":.:" is five.
 */
private fun pips(cx: Float, cy: Float, rows: String): String {
    val step = 2.43f
    return rows.withIndex().joinToString("") { (row, c) ->
        val y = cy + (row - 1) * step
        val xs = when (c) {
            '.' -> listOf(cx)
            ':' -> listOf(cx - step, cx + step)
            '\\' -> listOf(cx + (row - 1) * step)
            else -> emptyList()
        }
        xs.joinToString("") { x -> circle(x, y, 0.9f, hole = true) }
    }
}

/** A number for path data or SVG: at most two decimals, no trailing zeros, always with a '.' whatever the locale. */
private fun num(v: Float): String = String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
