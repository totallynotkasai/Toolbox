package com.toolbox.app.tools.dice

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.toolbox.app.ui.common.TOSS_LIGHT
import com.toolbox.app.ui.common.TossColors
import com.toolbox.app.ui.common.Vec3
import com.toolbox.app.ui.common.drawTossShadow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Maps the dice table onto a canvas: looking down at [elevationDeg] above the horizontal, orthographic,
 * with the table centre at ([cx], [cy]) and [scale] px per die edge.
 */
class DiceView(elevationDeg: Float, val cx: Float, val cy: Float, val scale: Float) {
    private val a = Math.toRadians(elevationDeg.toDouble())
    val sinA = sin(a).toFloat()
    val cosA = cos(a).toFloat()

    /** Unit vector from the table towards the camera. */
    val toViewer = Vec3(0f, sinA, cosA)

    fun point(p: Vec3) = Offset(cx + p.x * scale, cy + (p.z * sinA - p.y * cosA) * scale)
    fun direction(d: Vec3) = Offset(d.x * scale, (d.z * sinA - d.y * cosA) * scale)
}

/** Pip centres in face design units (a face spans −100…100 on each axis). */
private val PIPS: Map<Int, List<Offset>> = run {
    val a = -48f; val m = 0f; val b = 48f
    mapOf(
        1 to listOf(Offset(m, m)),
        2 to listOf(Offset(a, a), Offset(b, b)),
        3 to listOf(Offset(a, a), Offset(m, m), Offset(b, b)),
        4 to listOf(Offset(a, a), Offset(b, a), Offset(a, b), Offset(b, b)),
        5 to listOf(Offset(a, a), Offset(b, a), Offset(m, m), Offset(a, b), Offset(b, b)),
        6 to listOf(Offset(a, a), Offset(b, a), Offset(a, m), Offset(b, m), Offset(a, b), Offset(b, b)),
    )
}

/** Soft shadows first (so no die's shadow falls on another die), then the dice from the back forwards. */
fun DrawScope.drawDice(dice: List<DieLook>, view: DiceView) {
    for (d in dice) {
        val lift = ((d.position.y - d.shape.inradius) / 2f).coerceIn(0f, 1f)
        val s = view.scale * d.shape.radius / 0.866f // sized as for a d6
        val c = view.point(Vec3(d.position.x, 0f, d.position.z)) + Offset(s * (0.08f + 0.4f * lift), s * (0.06f + 0.2f * lift))
        drawTossShadow(c, rx = s * (0.85f - 0.2f * lift), ry = s * (0.85f - 0.2f * lift) * view.sinA, height = lift)
    }
    for (d in dice.sortedBy { it.position dot view.toViewer }) drawDie(d, view)
}

/**
 * One die in the coin's colours: each face that points at the camera is drawn flat (rim, lighter inset face, ink
 * number or pips) and mapped onto its projection, shaded by the same light as the coin. The solids are convex, so
 * the visible faces never overlap and need no sorting.
 */
private fun DrawScope.drawDie(d: DieLook, view: DiceView) {
    val r = d.pose()
    val shape = d.shape
    for (face in shape.faces) {
        val n = r * face.normal
        if ((n dot view.toViewer) <= 0.001f) continue
        val centre = view.point(d.position + r * face.centre)
        val pu = view.direction(r * face.across) * (face.unit / 100f)
        val pv = view.direction(r * face.down) * (face.unit / 100f)
        val m = Matrix(
            floatArrayOf(
                pu.x, pu.y, 0f, 0f,
                pv.x, pv.y, 0f, 0f,
                0f, 0f, 1f, 0f,
                centre.x, centre.y, 0f, 1f,
            ),
        )
        val brightness = 0.62f + 0.38f * max(0f, n dot TOSS_LIGHT)
        withTransform({ transform(m) }) {
            if (shape.sides == 6) drawPipFace(face.value, brightness) else drawNumberFace(shape, face, brightness)
        }
    }
}

private fun lit(c: Color, brightness: Float) = lerp(TossColors.Shade, c, brightness)

private fun DrawScope.drawPipFace(value: Int, brightness: Float) {
    drawRect(lit(TossColors.Rim, brightness), Offset(-100f, -100f), Size(200f, 200f))
    drawRoundRect(lit(TossColors.Face, brightness), Offset(-86f, -86f), Size(172f, 172f), CornerRadius(30f))
    val ink = lit(TossColors.Ink, brightness)
    PIPS.getValue(value).forEach { drawCircle(ink, 15f, it) }
}

private val facePath = Path()
private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val textBounds = Rect()

/** Distance from the face's centre to its nearest edge, in design units: how much room the number has. */
private val roomCache = HashMap<DieShape.Face, Float>()

private fun room(face: DieShape.Face): Float = roomCache.getOrPut(face) {
    val o = face.outline
    o.indices.minOf { i ->
        val a = o[i]; val b = o[(i + 1) % o.size]
        abs(a.x * b.y - a.y * b.x) / hypot(b.x - a.x, b.y - a.y)
    }
}

/** A polygonal face: rim, a slightly rounded inset face, and its number (or a d4's three corner numbers). */
private fun DrawScope.drawNumberFace(shape: DieShape, face: DieShape.Face, brightness: Float) {
    val o = face.outline
    facePath.reset()
    o.forEachIndexed { i, p -> if (i == 0) facePath.moveTo(p.x, p.y) else facePath.lineTo(p.x, p.y) }
    facePath.close()
    drawPath(facePath, lit(TossColors.Rim, brightness))

    val inset = 1f - 16f / room(face)
    facePath.reset()
    o.forEachIndexed { i, p -> if (i == 0) facePath.moveTo(p.x * inset, p.y * inset) else facePath.lineTo(p.x * inset, p.y * inset) }
    facePath.close()
    val faceColour = lit(TossColors.Face, brightness)
    drawPath(facePath, faceColour)
    drawPath(facePath, faceColour, style = Stroke(width = 10f, join = StrokeJoin.Round))

    val ink = lit(TossColors.Ink, brightness).toArgb()
    if (shape.numberedCorners) {
        // Each corner's number sits near it, pointing at it, so the one at the top reads upright from any side.
        face.cornerIndices.forEachIndexed { i, c ->
            val p = o[i] * 0.52f
            val deg = Math.toDegrees(atan2(p.x, -p.y).toDouble()).toFloat()
            rotate(deg, pivot = p) { drawNumber(shape.cornerValue(c), shape.sides, p, 40f, ink) }
        }
    } else {
        drawNumber(face.value, shape.sides, Offset.Zero, min(room(face) * 1.15f, 75f), ink)
    }
}

private fun DrawScope.drawNumber(value: Int, sides: Int, at: Offset, size: Float, ink: Int) {
    val text = value.toString()
    textPaint.textSize = size
    textPaint.color = ink
    textPaint.getTextBounds(text, 0, text.length, textBounds)
    val baseline = at.y - textBounds.exactCenterY()
    drawContext.canvas.nativeCanvas.drawText(text, at.x, baseline, textPaint)
    if ((value == 6 || value == 9) && sides >= 8) {
        // Underlined, so a 6 isn't read as a 9 upside down.
        drawRect(Color(ink), Offset(at.x - size * 0.24f, baseline + size * 0.1f), Size(size * 0.48f, size * 0.08f))
    }
}
