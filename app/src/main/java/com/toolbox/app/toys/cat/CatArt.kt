package com.toolbox.app.toys.cat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the cat is, in its own units: those of the approved "chibi loaf" sketch, with the head on the left facing
 * us, the body running right to the rump, and y growing downwards. The box is [VIEW_W] units across (a cat-width,
 * the unit [CatMood] measures strokes in) and [VIEW_H] down, and the cat sits in the middle of it.
 * Plain geometry, so the touch areas can be tested.
 */
internal object CatShape {
    const val VIEW_W = 230f
    const val VIEW_H = 184f

    /** The middle of the cat as drawn, whisker tips to rump and ear tips to tail, which goes in the middle of the box. */
    val MIDDLE = Offset(105.5f, 122f)

    /** The face: an oval this wide and tall either side of its centre. */
    val HEAD = Offset(78f, 104f)
    const val HEAD_RX = 58f
    const val HEAD_RY = 48f

    /** Where the head meets the body, which it tilts about. */
    val NECK = Offset(80f, 146f)

    /** The loaf, tail and paws together, as one oval with a fingertip's room around them. */
    private val BODY = Offset(124f, 150f)
    private const val BODY_RX = 92f
    private const val BODY_RY = 58f

    /** Pixels per unit in a box [width] × [height] px. */
    fun scale(width: Float, height: Float): Float = min(width / VIEW_W, height / VIEW_H)

    /** Where the units' origin falls in a box [width] × [height] px. */
    fun origin(width: Float, height: Float): Offset = Offset(width / 2f, height / 2f) - MIDDLE * scale(width, height)

    /** A point [p] in px in a box [width] × [height], in cat units. */
    fun toCat(p: Offset, width: Float, height: Float): Offset = (p - origin(width, height)) / scale(width, height)

    /** A point [p] in cat units, in px in a box [width] × [height]. */
    fun toBox(p: Offset, width: Float, height: Float): Offset = origin(width, height) + p * scale(width, height)

    /** Whether [p] (cat units) is on the head, ears included, with a little room for a fingertip. */
    fun onHead(p: Offset): Boolean = inOval(p, HEAD + Offset(0f, -8f), HEAD_RX + 8f, HEAD_RY + 14f)

    /** Whether [p] (cat units) is on the cat anywhere, where a stroke counts. */
    fun onCat(p: Offset): Boolean = onHead(p) || inOval(p, BODY, BODY_RX, BODY_RY)

    private fun inOval(p: Offset, c: Offset, rx: Float, ry: Float): Boolean {
        val x = (p.x - c.x) / rx
        val y = (p.y - c.y) / ry
        return x * x + y * y <= 1f
    }
}

// A warm ginger tabby with a soft dark-brown outline.
private val Fur = Color(0xFFF4A85E)
private val Stripe = Color(0xFFE08A45)
private val Outline = Color(0xFF8A4B1C)
private val Cream = Color(0xFFFFF1DE)
private val Pink = Color(0xFFF7B5B0)
private val NosePink = Color(0xFFE8857F)
private val EyeDark = Color(0xFF3A2A22)

private const val LINE = 2f // outline width, in cat units

private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val m = 1f - t
    return p0 * (m * m * m) + p1 * (3f * m * m * t) + p2 * (3f * m * t * t) + p3 * (t * t * t)
}

/** Direction of travel along the same curve at [t]; not normalised. */
private fun cubicSlope(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val m = 1f - t
    return (p1 - p0) * (3f * m * m) + (p2 - p1) * (6f * m * t) + (p3 - p2) * (3f * t * t)
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

// The back of the loaf, from behind the head over to the top of the rump: the ruffle grows out of it.
private val Back = arrayOf(Offset(90f, 110f), Offset(130f, 100f), Offset(186f, 106f), Offset(196f, 142f))

/**
 * The chibi loaf: a big round head sat into the front of a low body, two cream front paws, and the tail curled
 * along the ground in front of the haunch. [breathe] 0..1 raises it a little; [closed] 0..1 lowers the lids, ending
 * in content "‿" eyes; [earsFlat] and [ruffle] 0..1 are for a cross cat; the tail tip swings by [tailSwing] (±1) and
 * lifts by [tailLift] (0..1), both up to twice as far when cross; [headTilt] is in degrees; [blush] 0..1 deepens the
 * cheeks.
 */
internal fun DrawScope.drawCat(
    breathe: Float,
    closed: Float,
    earsFlat: Float,
    ruffle: Float,
    tailSwing: Float,
    tailLift: Float,
    headTilt: Float,
    blush: Float,
    shadow: Color,
) {
    val s = CatShape.scale(size.width, size.height)
    val o = CatShape.origin(size.width, size.height)
    withTransform({
        translate(o.x, o.y)
        scale(s, s, pivot = Offset.Zero)
    }) {
        drawOval(shadow, topLeft = Offset(26f, 176f), size = Size(184f, 16f))

        // The whole cat rises a little with each breath.
        scale(scaleX = 1f + 0.006f * breathe, scaleY = 1f + 0.02f * breathe, pivot = Offset(120f, 182f)) {
            if (ruffle > 0.01f) drawRuffle(ruffle)
            drawTailRoot(tailSwing, tailLift)
            drawBody()
            drawTail(tailSwing, tailLift)
            drawPaw(Offset(66f, 180f))
            drawPaw(Offset(93f, 182f))
            rotate(headTilt, pivot = CatShape.NECK) {
                translate(CatShape.HEAD.x, CatShape.HEAD.y) { drawHead(closed, earsFlat, blush) }
            }
        }
    }
}

private fun DrawScope.drawBody() {
    val body = Path().apply {
        moveTo(62f, 180f)
        cubicTo(38f, 180f, 40f, 122f, Back[0].x, Back[0].y)
        cubicTo(Back[1].x, Back[1].y, Back[2].x, Back[2].y, Back[3].x, Back[3].y)
        cubicTo(203f, 167f, 196f, 180f, 181f, 180f)
        close()
    }
    drawPath(body, Fur)
    clipPath(body) {
        // Tabby stripes down the back; the head hides where they'd start behind it.
        for ((x0, y0, drop) in listOf(Triple(132f, 100f, 26f), Triple(152f, 102f, 26f), Triple(171f, 109f, 24f))) {
            val stripe = Path().apply {
                moveTo(x0, y0)
                quadraticTo(x0 + 4f, y0 + drop / 2f, x0 + 1f, y0 + drop)
            }
            drawPath(stripe, Stripe, style = Stroke(6f, cap = StrokeCap.Round))
        }
    }
    drawPath(body, Outline, style = Stroke(LINE, join = StrokeJoin.Round))
}

/** Fur standing on end along the back, drawn behind the body so only the tufts show. */
private fun DrawScope.drawRuffle(amount: Float) {
    val tufts = 22
    val spikes = Path().apply {
        moveTo(150f, 150f) // inside the body, which hides the sides back to here
        for (i in 0..tufts) {
            val t = 0.3f + 0.7f * i / tufts // from just behind the head to the top of the rump
            val p = cubic(Back[0], Back[1], Back[2], Back[3], t)
            val d = cubicSlope(Back[0], Back[1], Back[2], Back[3], t)
            val out = Offset(d.y, -d.x) / d.getDistance()
            // Odd points are the tips; vary their length so it looks tufty rather than sawn.
            val reach = if (i % 2 == 1) amount * (8f + 4f * ((i * 37) % 5) / 4f) else -1.5f
            val q = p + out * reach
            lineTo(q.x, q.y)
        }
        close()
    }
    // Only the outer half of this outline shows, so it's twice the width to match the body's.
    drawPath(spikes, Outline, style = Stroke(2f * LINE, join = StrokeJoin.Round))
    drawPath(spikes, Fur)
}

private fun DrawScope.drawPaw(center: Offset) {
    val size = Size(26f, 15f)
    val topLeft = center - Offset(size.width / 2f, size.height / 2f)
    drawOval(Cream, topLeft, size)
    drawOval(Outline, topLeft, size, style = Stroke(LINE * 0.85f))
}

private const val TAIL_W = 10f
private const val TAIL_OUT = 0.3f // how far along the tail it comes out from behind the rump

/** The tail's curve from the root, behind the rump, to the tip. */
private fun tailCurve(swing: Float, lift: Float) = arrayOf(
    Offset(186f, 166f),
    Offset(206f, 182f),
    Offset(176f + 3f * swing, 194f - 3f * lift),
    Offset(122f + 8f * swing, 190f - 10f * lift),
)

/** The tail as it goes behind the body: drawn first, so the rump hides its root. */
private fun DrawScope.drawTailRoot(swing: Float, lift: Float) {
    val (p0, p1, p2, p3) = tailCurve(swing, lift)
    val tail = Path().apply {
        moveTo(p0.x, p0.y)
        cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
    }
    drawPath(tail, Outline, style = Stroke(TAIL_W + 2f * LINE, cap = StrokeCap.Round))
    drawPath(tail, Fur, style = Stroke(TAIL_W, cap = StrokeCap.Round))
}

/**
 * The rest of the tail, from where it comes out from behind the rump, drawn again over the body: it lies along the
 * ground in front of the haunch, and a tip swished up keeps its outline. It starts square, exactly over the first
 * pass, so there's no seam, and ends round.
 */
private fun DrawScope.drawTail(swing: Float, lift: Float) {
    val (p0, p1, p2, p3) = tailCurve(swing, lift)
    // The part of the curve after TAIL_OUT (de Casteljau): it starts at f, with controls e and c.
    val t = TAIL_OUT
    val a = lerp(p0, p1, t)
    val b = lerp(p1, p2, t)
    val c = lerp(p2, p3, t)
    val e = lerp(b, c, t)
    val f = lerp(lerp(a, b, t), e, t)
    val tail = Path().apply {
        moveTo(f.x, f.y)
        cubicTo(e.x, e.y, c.x, c.y, p3.x, p3.y)
    }
    drawPath(tail, Outline, style = Stroke(TAIL_W + 2f * LINE))
    drawCircle(Outline, TAIL_W / 2f + LINE, p3)
    drawPath(tail, Fur, style = Stroke(TAIL_W))
    drawCircle(Fur, TAIL_W / 2f, p3)
}

// The left ear and its pink inside as closed pairs of curves (start, two controls, tip, two controls, back to the
// head), in head units from the middle of the face. The right ear is the mirror image.
private val Ear = arrayOf(
    Offset(-48f, -17f), Offset(-54f, -45f), Offset(-48f, -61f), Offset(-38f, -59f),
    Offset(-28f, -57f), Offset(-16f, -43f), Offset(-12f, -35f),
)
private val InnerEar = arrayOf(
    Offset(-42f, -24f), Offset(-46f, -42f), Offset(-42f, -51f), Offset(-37f, -50f),
    Offset(-31f, -48f), Offset(-24f, -40f), Offset(-21f, -35f),
)

// Flattened, an ear swings out sideways about a point low in its base. Its last control and the corner it ends at
// go to these instead, so the base stays tucked under the head as it turns.
private const val EAR_FLAT_TURN = -70f
private val EarPivot = Offset(-28f, -18f)
private val EarTuck = arrayOf(Offset(-34f, -37.8f), Offset(-14f, -36f))

private fun earPath(shape: Array<Offset>, flatness: Float, mirror: Boolean, tuck: Boolean) = Path().apply {
    val a = Math.toRadians((EAR_FLAT_TURN * flatness).toDouble())
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    val p = Array(shape.size) { i ->
        val d = shape[i] - EarPivot
        val q = if (tuck && i >= 5) lerp(shape[i], EarTuck[i - 5], flatness) else EarPivot + Offset(d.x * c - d.y * s, d.x * s + d.y * c)
        if (mirror) Offset(-q.x, q.y) else q
    }
    moveTo(p[0].x, p[0].y)
    cubicTo(p[1].x, p[1].y, p[2].x, p[2].y, p[3].x, p[3].y)
    cubicTo(p[4].x, p[4].y, p[5].x, p[5].y, p[6].x, p[6].y)
    close()
}

/** The head, drawn about the middle of the face. */
private fun DrawScope.drawHead(closed: Float, earsFlat: Float, blush: Float) {
    // Ears first, so the head overlaps their bases. Flattened ears swing out sideways.
    for (mirror in listOf(false, true)) {
        val ear = earPath(Ear, earsFlat, mirror, tuck = true)
        drawPath(ear, Fur)
        drawPath(ear, Outline, style = Stroke(LINE, join = StrokeJoin.Round))
        drawPath(earPath(InnerEar, earsFlat, mirror, tuck = false), Pink)
    }

    val face = Size(2f * CatShape.HEAD_RX, 2f * CatShape.HEAD_RY)
    val faceTl = Offset(-CatShape.HEAD_RX, -CatShape.HEAD_RY)
    drawOval(Fur, faceTl, face)
    drawOval(Outline, faceTl, face, style = Stroke(LINE))

    // Tabby marks on the forehead.
    for ((top, bottom) in listOf(Offset(-6f, -44f) to Offset(-5f, -35f), Offset(0f, -46f) to Offset(0f, -36f), Offset(6f, -44f) to Offset(5f, -35f))) {
        drawLine(Stripe, top, bottom, strokeWidth = 4f, cap = StrokeCap.Round)
    }

    // Blush, deepening and spreading as it relaxes.
    val cheek = Size(18f, 11f) * (1f + 0.15f * blush)
    for (x in floatArrayOf(-36f, 36f)) {
        drawOval(Pink.copy(alpha = 0.5f + 0.45f * blush), Offset(x, 18f) - Offset(cheek.width / 2f, cheek.height / 2f), cheek)
    }

    drawEye(Offset(-20f, 4f), closed)
    drawEye(Offset(20f, 4f), closed)

    // Muzzle, nose and an "ω" mouth.
    drawOval(Cream, Offset(-14f, 16f), Size(16f, 12f))
    drawOval(Cream, Offset(-2f, 16f), Size(16f, 12f))
    val nose = Path().apply {
        moveTo(-4f, 15f)
        lineTo(4f, 15f)
        lineTo(0f, 19.5f)
        close()
    }
    drawPath(nose, NosePink)
    drawPath(nose, NosePink, style = Stroke(1.5f, join = StrokeJoin.Round))
    val mouth = Path().apply {
        moveTo(0f, 19.5f)
        relativeQuadraticTo(-3f, 5f, -7f, 2f)
        moveTo(0f, 19.5f)
        relativeQuadraticTo(3f, 5f, 7f, 2f)
    }
    drawPath(mouth, Outline, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Short whiskers, mirrored about the nose.
    val whisker = Outline.copy(alpha = 0.6f)
    for (side in floatArrayOf(-1f, 1f)) {
        drawLine(whisker, Offset(46f * side, 15f), Offset(68f * side, 11f), strokeWidth = 1.3f, cap = StrokeCap.Round)
        drawLine(whisker, Offset(46f * side, 21f), Offset(68f * side, 23f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    }
}

private const val EYE_R = 11.5f

/** A big dark eye with two highlights; [closed] 0..1 lowers a lid, ending in a content "‿". */
private fun DrawScope.drawEye(c: Offset, closed: Float) {
    if (closed < 0.97f) {
        // Only what's below the lid is drawn, rather than covering the rest with fur, which leaves a faint rim.
        val lid = c.y - EYE_R + 2f * EYE_R * closed
        clipRect(c.x - EYE_R - 1f, lid, c.x + EYE_R + 1f, c.y + EYE_R + 1f) {
            drawCircle(EyeDark, EYE_R, c)
            drawCircle(Color.White, 4.2f, c + Offset(-4f, -5f))
            drawCircle(Color.White, 2f, c + Offset(5f, 5f))
        }
        if (closed > 0f) {
            val dy = lid - c.y
            val half = sqrt(max(0f, EYE_R * EYE_R - dy * dy))
            drawLine(EyeDark, Offset(c.x - half, lid), Offset(c.x + half, lid), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
    }
    val shut = ((closed - 0.75f) / 0.25f).coerceIn(0f, 1f)
    if (shut > 0f) {
        val arc = Path().apply {
            moveTo(c.x - 10f, c.y + 3f)
            quadraticTo(c.x, c.y + 13f, c.x + 10f, c.y + 3f)
        }
        drawPath(arc, EyeDark, alpha = shut, style = Stroke(3f, cap = StrokeCap.Round))
    }
}
