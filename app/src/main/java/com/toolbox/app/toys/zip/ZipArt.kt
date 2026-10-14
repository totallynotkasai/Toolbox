package com.toolbox.app.toys.zip

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min

/** Where a tooth on [side] (-1 left, 1 right) sits along the track, in pitches past its row's start: the sides alternate. */
internal fun toothOffset(side: Int): Float = if (side < 0) 0.25f else 0.75f

/**
 * How far right of the centre line the rounded head of a tooth on [side] is, when its tape has pulled [spread] away
 * from the line. Each head sticks out [reach] past its tape, so on a shut zip they cross the line and lock between
 * the other side's heads.
 */
internal fun toothHead(side: Int, spread: Float, reach: Float): Float = side * (spread - reach)

/**
 * Degrees a tooth on [side] is turned to stay square to its tape, which has pulled [above] from the centre line a
 * pixel above the tooth and [below] a pixel below it. Shut teeth lie straight across; open ones fan out with the V.
 */
internal fun toothTurn(side: Int, above: Float, below: Float): Float {
    val slope = side * (below - above) / 2f // dx/dy of this tape's edge
    return Math.toDegrees(-atan(slope).toDouble()).toFloat()
}

private val Lining = Color(0xFF8E2F2A)
private val LiningDeep = Color(0xFF55170F)
private val Panel = Color(0xFF2F4A6D)
private val Tape = Color(0xFF1E2A38)
private val Stitch = Color(0xFF8FA6C4)
internal val BrassLight = Color(0xFFF6DF97)
private val Brass = Color(0xFFE2B85A)
internal val BrassDark = Color(0xFFA67F28)
private val BrassEdge = Color(0xFF7A5812)
internal val SteelLight = Color(0xFFF1F3F4)
private val Steel = Color(0xFFC9CDD2)
internal val SteelMid = Color(0xFFBDC1C6)
internal val SteelDark = Color(0xFF5F6368)

/**
 * A jacket-front zip: two fabric panels, each with a tape and a row of brass teeth. Below the slider the teeth
 * interlock down the centre in a zigzag; above it the halves peel apart into a V (flattening into parallel edges
 * further up), each tooth turning with its tape, and show the red lining. The slider is wide at the top, where the
 * halves go in, and narrow at the bottom, with its pull tab hanging from it.
 */
internal fun DrawScope.drawZip(pos: Float, held: Boolean, teeth: Int) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val top = h * TRACK_TOP
    val len = h * (TRACK_BOTTOM - TRACK_TOP)
    val sliderY = top + pos * len
    val pitch = len / teeth                    // from one tooth to the next on the same side
    val toothLen = min(w * 0.09f, pitch * 2.2f) // across the zip
    val toothW = pitch * 0.56f                  // along it, a little over half a pitch so meshed heads touch
    val reach = toothLen * 0.3f                 // how far each head sticks out past its tape
    val mesh = toothLen - reach                 // half the width of the shut row of teeth
    val tapeW = w * 0.08f
    val maxOpen = w * 0.2f

    // Above the top tooth the tapes carry on parallel to wherever the topmost tooth has got to.
    fun spread(y: Float) = zipSpread(sliderY - max(y, top), maxOpen)

    drawRect(Brush.verticalGradient(listOf(LiningDeep, Lining, LiningDeep)))

    val steps = 72
    fun edge(s: Float, out: Float) = Path().apply {
        for (k in 0..steps) {
            val y = h * k / steps
            val x = cx + s * (spread(y) + out)
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    for (s in floatArrayOf(-1f, 1f)) {
        // A soft shadow on the lining where each half lifts away; the tape covers its other side.
        drawPath(edge(s, 0f), Color.Black.copy(alpha = 0.3f), style = Stroke(8.dp.toPx()))
        val outer = if (s < 0f) 0f else w
        val panel = edge(s, 0f).apply {
            lineTo(outer, h)
            lineTo(outer, 0f)
            close()
        }
        drawPath(panel, Panel)
        val tape = edge(s, 0f).apply {
            for (k in steps downTo 0) {
                val y = h * k / steps
                lineTo(cx + s * (spread(y) + tapeW), y)
            }
            close()
        }
        drawPath(tape, Tape)
        // Stitching along the tape's outer edge.
        drawPath(
            edge(s, tapeW * 0.8f),
            Stitch,
            style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        )
    }

    // Teeth: rounded brass with a cupped head, fixed to their tape and square to it. Drawn top to bottom, alternating
    // sides, so each head lies over the one before.
    val brass = Brush.verticalGradient(listOf(BrassLight, Brass, BrassDark), startY = -toothW / 2f, endY = toothW / 2f)
    val rim = Stroke(max(0.6.dp.toPx(), toothW * 0.1f))
    val toothSize = Size(toothLen, toothW)
    val round = CornerRadius(toothW / 2f)
    for (i in 0 until teeth) {
        for (side in intArrayOf(-1, 1)) {
            val y = top + (i + toothOffset(side)) * pitch
            val inward = -side.toFloat() // which way the head points, in the tooth's own x
            val left = if (side < 0) reach - toothLen else -reach
            val cup = inward * (reach - toothW * 0.62f)
            withTransform({
                translate(cx + side * spread(y), y)
                rotate(toothTurn(side, spread(y - 1f), spread(y + 1f)), pivot = Offset.Zero)
            }) {
                drawRoundRect(brass, Offset(left, -toothW / 2f), toothSize, round)
                drawRoundRect(BrassEdge, Offset(left, -toothW / 2f), toothSize, round, style = rim)
                drawCircle(BrassEdge.copy(alpha = 0.55f), toothW * 0.21f, Offset(cup, -toothW * 0.04f))
                drawCircle(BrassLight, toothW * 0.09f, Offset(cup + inward * toothW * 0.06f, toothW * 0.14f))
            }
        }
    }

    // Everything metal below is sized from the shut row of teeth, in steps of a 13th of its half-width.
    val u = mesh / 13f
    val shade = Offset(2.dp.toPx(), 3.dp.toPx())

    // Bottom stop, where the two tapes are anchored.
    val stopTop = top + len + pitch * 0.1f
    val stopTl = Offset(cx - 14f * u, stopTop)
    val stopSize = Size(28f * u, 14f * u)
    drawRoundRect(Color.Black.copy(alpha = 0.25f), stopTl + shade * 0.5f, stopSize, CornerRadius(3f * u))
    drawRoundRect(
        Brush.verticalGradient(listOf(SteelLight, Steel, SteelMid), startY = stopTop, endY = stopTop + stopSize.height),
        stopTl, stopSize, CornerRadius(3f * u),
    )
    drawRoundRect(SteelDark, stopTl, stopSize, CornerRadius(3f * u), style = Stroke(1.2f * u))

    translate(cx, sliderY) {
        // Slider body: wide at the top where the halves go in, narrow where they leave locked together.
        val body = Path().apply {
            moveTo(-24f * u, -16f * u)
            lineTo(24f * u, -16f * u)
            quadraticTo(27f * u, -16f * u, 26f * u, -12f * u)
            lineTo(14f * u, 22f * u)
            quadraticTo(12f * u, 26f * u, 8f * u, 26f * u)
            lineTo(-8f * u, 26f * u)
            quadraticTo(-12f * u, 26f * u, -14f * u, 22f * u)
            lineTo(-26f * u, -12f * u)
            quadraticTo(-27f * u, -16f * u, -24f * u, -16f * u)
            close()
        }
        translate(shade.x, shade.y) { drawPath(body, Color.Black.copy(alpha = 0.3f)) }
        drawPath(body, Brush.verticalGradient(listOf(SteelLight, Steel, SteelMid), startY = -16f * u, endY = 26f * u))
        drawPath(body, SteelDark, style = Stroke(1.2f * u))
        drawLine(SteelLight, Offset(-18f * u, -11f * u), Offset(-9f * u, 20f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)

        // Pull tab, hanging from a bridge on the slider; it brightens a touch while held.
        val tabTl = Offset(-9f * u, -8f * u)
        val tabSize = Size(18f * u, 58f * u)
        val tabRound = CornerRadius(9f * u)
        drawRoundRect(Color.Black.copy(alpha = 0.3f), tabTl + shade, tabSize, tabRound)
        drawRoundRect(
            Brush.horizontalGradient(
                if (held) listOf(SteelLight, Color.White, SteelLight, Steel) else listOf(Steel, SteelLight, Steel, SteelMid),
                startX = tabTl.x, endX = tabTl.x + tabSize.width,
            ),
            tabTl, tabSize, tabRound,
        )
        drawRoundRect(SteelDark, tabTl, tabSize, tabRound, style = Stroke(1.2f * u))
        // Finger hole.
        drawRoundRect(Tape, Offset(-4f * u, 26f * u), Size(8f * u, 18f * u), CornerRadius(4f * u))
        // The bridge it hangs from.
        drawRoundRect(Color(0xFF9AA0A6), Offset(-6f * u, -12f * u), Size(12f * u, 9f * u), CornerRadius(3f * u))
        drawRoundRect(SteelDark, Offset(-6f * u, -12f * u), Size(12f * u, 9f * u), CornerRadius(3f * u), style = Stroke(u))
    }
}
