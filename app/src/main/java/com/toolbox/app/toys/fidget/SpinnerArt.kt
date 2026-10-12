package com.toolbox.app.toys.fidget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.toolbox.app.ui.common.polar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The shape of a classic tri-spinner, in units of the canvas's half-size, centred on the origin. Three round lobes
 * [ARM] out from the hub, joined by concave curves (circles [FILLET_R] across, centred [FILLET_AT] out between the
 * lobes) that pinch the body to [WAIST] at its narrowest. Angles are clockwise from 12 o'clock, lobe 0 at the top.
 */
internal object SpinnerShape {
    const val ARM = 0.58f
    const val LOBE = 0.35f
    const val WAIST = 0.3f

    /** How far out the concave curves' circles sit, chosen so each just touches its two lobes. */
    val FILLET_AT: Float
    val FILLET_R: Float

    init {
        // The fillet circle touches a lobe: |Q - P| = LOBE + FILLET_R, with Q 60° round from P and FILLET_R = Q - WAIST.
        val k = LOBE - WAIST
        FILLET_AT = (ARM * ARM - k * k) / (ARM + 2f * k)
        FILLET_R = FILLET_AT - WAIST
    }

    fun lobe(i: Int): Offset = polar(Offset.Zero, ARM, 120f * i)
    fun fillet(i: Int): Offset = polar(Offset.Zero, FILLET_AT, 120f * i + 60f)

    /** Weights in the lobes: a steel ring from [INSERT_IN] to [INSERT_OUT] lobe radii, dark inside. */
    const val INSERT_OUT = 0.64f
    const val INSERT_IN = 0.42f

    /** The middle bearing, in half-sizes: the hole in the body, the balls' track and the cap you hold. */
    const val HUB = 0.25f
    const val BALL_TRACK = 0.18f
    const val CAP = 0.14f

    /** Whether [p] is on the body (lobes included). */
    fun contains(p: Offset): Boolean {
        for (i in 0 until 3) if ((p - lobe(i)).getDistance() <= LOBE) return true
        for (i in 0 until 3) if ((p - fillet(i)).getDistance() < FILLET_R) return false
        // Between the lobes the body is the polygon of lobe centres and the points where curves meet the lobes;
        // the fillet circles (already ruled out above) take the bites out of it.
        return insidePolygon(p, outline)
    }

    /** Whether [p] is on one of the lobes' steel weights. */
    fun onInsert(p: Offset): Boolean = (0 until 3).any {
        val d = (p - lobe(it)).getDistance()
        d <= LOBE * INSERT_OUT && d >= LOBE * INSERT_IN
    }

    private val outline: List<Offset> = buildList {
        for (i in 0 until 3) {
            val p = lobe(i)
            val q = fillet(i)
            val next = lobe((i + 1) % 3)
            add(p)
            add(p + (q - p) * (LOBE / (q - p).getDistance()))
            add(next + (q - next) * (LOBE / (q - next).getDistance()))
        }
    }

    private fun insidePolygon(p: Offset, poly: List<Offset>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** Ring radii for the blur disc, from the hub out to the lobe tips, and how much of each ring is body or steel. */
    const val RINGS = 40
    val ringRadius = FloatArray(RINGS) { HUB + (ARM + LOBE - HUB) * (it + 0.5f) / RINGS }
    val bodyCover = FloatArray(RINGS)
    val steelCover = FloatArray(RINGS)

    init {
        // A fast spinner smears into rings, each as solid as the share of its circle the body covers.
        val samples = 720
        for (k in 0 until RINGS) {
            var body = 0
            var steel = 0
            for (s in 0 until samples) {
                val p = polar(Offset.Zero, ringRadius[k], 360f * s / samples)
                if (onInsert(p)) steel++ else if (contains(p)) body++
            }
            bodyCover[k] = body / samples.toFloat()
            steelCover[k] = steel / samples.toFloat()
        }
    }

    /** The outline as a path, scaled by [scale] px per unit and centred on [c]. */
    fun path(c: Offset, scale: Float): Path = Path().apply {
        fun at(p: Offset) = c + p * scale
        fun angle(v: Offset) = Math.toDegrees(atan2(v.y, v.x).toDouble()).toFloat() // screen degrees, from 3 o'clock
        for (i in 0 until 3) {
            val p = lobe(i)
            val prev = fillet((i + 2) % 3)
            val next = fillet(i)
            // Round the outside of the lobe, from where the previous curve meets it to where the next one does...
            val from = angle(prev - p)
            val to = angle(next - p)
            arcTo(Rect(at(p), LOBE * scale), from, ((to - from) % 360f + 360f) % 360f, forceMoveTo = i == 0)
            // ...then in round the concave curve to the next lobe.
            val back = angle(p - next)
            val on = angle(lobe((i + 1) % 3) - next)
            arcTo(Rect(at(next), FILLET_R * scale), back, -(((back - on) % 360f + 360f) % 360f), forceMoveTo = false)
        }
        close()
    }
}

private val SteelLight = Color(0xFFF1F3F4)
private val SteelMid = Color(0xFFBDC1C6)
private val SteelDark = Color(0xFF5F6368)
private val Hole = Color(0xFF202124)
private val LightFrom = Offset(-1f, -1f) // top left

/**
 * The spinner, glossy plastic in [color]: a soft drop shadow, the body (shaded by a light that stays put while it
 * turns), steel weights in the lobes and a bearing in the middle. [angle] turns the body, the balls go round at
 * about half its speed and the cap you hold stays still. [blur] 0..1 fades it into the smeared disc a fast spin
 * looks like, which also hides the strobing a 120°-symmetric shape gets on a 60-120 Hz screen.
 */
internal fun DrawScope.drawFidgetSpinner(outline: Path, angle: Float, blur: Float, color: Color) {
    val c = center
    val unit = size.minDimension / 2f
    val shadowAt = Offset(unit * 0.03f, unit * 0.05f)

    // Shadow: the body when sharp, a soft disc as it blurs.
    translate(shadowAt.x, shadowAt.y) {
        if (blur < 1f) rotate(angle, c) { drawPath(outline, Color.Black.copy(alpha = 0.22f * (1f - blur))) }
        if (blur > 0f) drawSmear(blur, unit) { body, steel -> Color.Black.copy(alpha = 0.22f * (body + steel)) }
    }

    if (blur > 0f) drawBlurDisc(blur, color, unit)
    if (blur < 1f) {
        val alpha = 1f - blur
        // Drawn turned, so the light has to be turned back the other way to stay put.
        val a = Math.toRadians(-angle.toDouble())
        val light = Offset(
            (LightFrom.x * cos(a) - LightFrom.y * sin(a)).toFloat(),
            (LightFrom.x * sin(a) + LightFrom.y * cos(a)).toFloat(),
        )
        withAlpha(alpha) { rotate(angle, c) { drawBody(outline, color, unit, light) } }
    }
    drawBearing(angle, blur, color, unit)
}

/** Draws [block] as one layer at [alpha], so overlapping parts don't show through each other while it fades. */
private inline fun DrawScope.withAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) { block(); return }
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha }) }
    block()
    drawIntoCanvas { it.restore() }
}

/** The turning part of the spinner, drawn in its own (turned) frame with the light coming from [light]. */
private fun DrawScope.drawBody(outline: Path, color: Color, unit: Float, light: Offset) {
    val c = center
    drawPath(outline, color)
    clipPath(outline) {
        val r = unit * (SpinnerShape.ARM + SpinnerShape.LOBE)
        drawCircle(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.32f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.28f),
                start = c + light * (r * 0.7f), end = c - light * (r * 0.7f),
            ),
            r, c,
        )
        // A bevel round the edge: lit where it faces the light, shaded where it faces away.
        drawPath(
            outline,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                start = c + light * (r * 0.7f), end = c - light * (r * 0.7f),
            ),
            style = Stroke(unit * 0.07f),
        )
    }
    drawPath(outline, lerp(color, Color.Black, 0.35f), style = Stroke(unit * 0.008f))

    // Steel weights in the lobes, and the hole through each.
    for (i in 0 until 3) {
        val p = c + SpinnerShape.lobe(i) * unit
        val outer = SpinnerShape.LOBE * SpinnerShape.INSERT_OUT * unit
        val inner = SpinnerShape.LOBE * SpinnerShape.INSERT_IN * unit
        drawCircle(lerp(color, Color.Black, 0.3f), outer * 1.06f, p)
        drawCircle(Brush.linearGradient(listOf(SteelLight, SteelMid, SteelDark), start = p + light * outer, end = p - light * outer), outer, p)
        drawCircle(Hole, inner, p)
        drawCircle(Brush.linearGradient(listOf(SteelDark, SteelLight), start = p + light * inner, end = p - light * inner), inner, p, style = Stroke(unit * 0.012f))
    }
    // The collar round the middle bearing.
    drawCircle(lerp(color, Color.Black, 0.3f), SpinnerShape.HUB * unit * 1.05f, c)
}

/**
 * A disc whose colour at each radius is [tint] of how much of that circle the body and the steel weights cover,
 * as one radial gradient so the smear has no rings in it.
 */
private inline fun DrawScope.drawSmear(blur: Float, unit: Float, tint: (body: Float, steel: Float) -> Color) {
    val reach = SpinnerShape.ARM + SpinnerShape.LOBE
    val stops = Array(SpinnerShape.RINGS + 1) { k ->
        if (k == SpinnerShape.RINGS) {
            1f to Color.Transparent
        } else {
            val shade = tint(SpinnerShape.bodyCover[k], SpinnerShape.steelCover[k])
            SpinnerShape.ringRadius[k] / reach to shade.copy(alpha = shade.alpha * blur)
        }
    }
    drawCircle(Brush.radialGradient(*stops, center = center, radius = reach * unit), reach * unit, center)
}

private fun DrawScope.drawBlurDisc(blur: Float, color: Color, unit: Float) {
    val c = center
    drawSmear(blur, unit) { body, steel ->
        val cover = body + steel
        if (cover <= 0f) Color.Transparent else lerp(color, SteelMid, steel / cover).copy(alpha = cover)
    }
    // The same fixed light on the blur.
    val r = unit * (SpinnerShape.ARM + SpinnerShape.LOBE)
    drawCircle(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.2f * blur),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.18f * blur),
            start = c + LightFrom * (r * 0.7f), end = c - LightFrom * (r * 0.7f),
        ),
        r * 0.97f, c,
    )
}

/** The middle bearing: an outer race turning with the body, balls going round at half speed, and the still cap. */
private fun DrawScope.drawBearing(angle: Float, blur: Float, color: Color, unit: Float) {
    val c = center
    val hub = SpinnerShape.HUB * unit
    drawCircle(Brush.linearGradient(listOf(SteelLight, SteelDark), start = c - Offset(hub, hub), end = c + Offset(hub, hub)), hub, c)
    val track = SpinnerShape.BALL_TRACK * unit
    drawCircle(Hole, hub * 0.84f, c)
    val ball = unit * 0.03f
    if (blur < 1f) {
        for (k in 0 until 8) {
            val p = polar(c, track, angle * 0.45f + 45f * k)
            drawCircle(
                Brush.radialGradient(listOf(SteelLight, SteelDark), center = p - Offset(ball * 0.35f, ball * 0.35f), radius = ball * 1.3f),
                ball, p, alpha = 1f - blur,
            )
        }
    }
    if (blur > 0f) drawCircle(SteelMid.copy(alpha = 0.55f * blur), track, c, style = Stroke(ball * 2f))
    // The cap, held still by your finger, in the body's colour.
    val cap = SpinnerShape.CAP * unit
    drawCircle(Color.Black.copy(alpha = 0.25f), cap * 1.08f, c + Offset(unit * 0.01f, unit * 0.015f))
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.3f)),
            center = c + LightFrom * (cap * 0.35f), radius = cap * 1.4f,
        ),
        cap, c,
    )
    drawCircle(lerp(color, Color.Black, 0.4f), cap, c, style = Stroke(unit * 0.008f))
}

