package com.toolbox.app.tools.coin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A camera looking down at the table from [elevationDeg] above the horizontal (90 = straight down).
 * Orthographic: height and perspective are faked by the caller (screen offset and scale).
 */
class CoinCamera(elevationDeg: Float) {
    private val a = Math.toRadians(elevationDeg.toDouble())
    private val up = Vec3(0f, cos(a).toFloat(), -sin(a).toFloat())

    /** Unit vector from the table towards the camera. */
    val toViewer = Vec3(0f, sin(a).toFloat(), cos(a).toFloat())

    /** Where a world direction points on screen (canvas coordinates, y down). */
    fun project(v: Vec3) = Offset(v.x, -(v dot up))
}

/** Light from the upper left, above the table. */
internal val LIGHT = Vec3(-0.45f, 0.8f, 0.45f).normalized()

/** Flat periwinkle palette, shared with the dice so the chance tools match. */
object CoinColors {
    val Rim = Color(0xFF7D7FE3)
    val Face = Color(0xFF9A9CF4)
    val Dots = Color(0xFFE4E4FF)
    val Ink = Color(0xFF3C3A9E)
    val Shade = Color(0xFF2A2870)
    val EdgeHi = Color(0xFFA9ABFA)
    val EdgeLo = Color(0xFF4E50B8)
}

private val RIM = CoinColors.Rim
private val FACE = CoinColors.Face
private val DOTS = CoinColors.Dots
private val INK = CoinColors.Ink
private val SHADE = CoinColors.Shade
private val EDGE_HI = CoinColors.EdgeHi
private val EDGE_LO = CoinColors.EdgeLo

/** Proportions, as a fraction of the coin's radius. */
const val COIN_THICKNESS = 0.14f

/**
 * Flat artwork for both faces, drawn in "design units" (a coin of radius 100 centred on the origin, y down):
 * a three-pointed crown for heads and a feather for tails.
 * Build once per screen with `remember { CoinArt() }`: it caches the paths.
 */
class CoinArt {
    private val crown = Path().apply {
        moveTo(-40f, 34f); lineTo(-44f, -24f); lineTo(-20f, 0f); lineTo(0f, -38f)
        lineTo(20f, 0f); lineTo(44f, -24f); lineTo(40f, 34f); close()
    }
    private val vane = Path().apply {
        moveTo(0f, -62f)
        cubicTo(30f, -42f, 34f, 6f, 6f, 40f)
        lineTo(-6f, 40f)
        cubicTo(-34f, 6f, -30f, -42f, 0f, -62f)
        close()
    }
    private val scratch = Path()

    /**
     * Draws the coin centred at [center] with radius [radius], oriented by [pose] and seen through [camera].
     * Shows whichever face points at the camera, shaded slightly by a fixed light as it turns,
     * and the reeded edge wherever it faces the viewer, which gives it visible thickness side-on.
     */
    fun draw(scope: DrawScope, center: Offset, radius: Float, pose: Rot, camera: CoinCamera) = with(scope) {
        val u = pose.column(0); val v = pose.column(1); val w = pose.column(2)
        val headsUp = (w dot camera.toViewer) >= 0f
        // The visible face's frame. Tails is mirrored in v so it reads upright after a flip about x.
        val fv = if (headsUp) v else -v
        val n = if (headsUp) w else -w
        val pu = camera.project(u) * radius
        val pv = camera.project(fv) * radius
        val half = camera.project(n) * (radius * COIN_THICKNESS / 2)

        // Edge: a band of strips between the back and front rims, only where it faces the viewer.
        val strips = 120
        for (i in 0 until strips) {
            val s0 = (2 * PI * i / strips).toFloat()
            val s1 = (2 * PI * (i + 1) / strips).toFloat()
            val sm = (s0 + s1) / 2
            val radial = u * cos(sm) + fv * sin(sm)
            if ((radial dot camera.toViewer) <= 0f) continue
            val p0 = center + pu * cos(s0) + pv * sin(s0)
            val p1 = center + pu * cos(s1) + pv * sin(s1)
            val lit = 0.25f + 0.75f * max(0f, radial dot LIGHT)
            val reed = if (i % 2 == 0) 1f else 0.85f
            scratch.reset()
            scratch.moveTo((p0 - half).x, (p0 - half).y)
            scratch.lineTo((p1 - half).x, (p1 - half).y)
            scratch.lineTo((p1 + half).x, (p1 + half).y)
            scratch.lineTo((p0 + half).x, (p0 + half).y)
            scratch.close()
            drawPath(scratch, lerp(EDGE_LO, EDGE_HI, lit * reed))
        }

        // Face, mapped from design units onto the tilted ellipse.
        val c = center + half
        val k = 1f / 100f
        val m = Matrix(
            floatArrayOf(
                pu.x * k, pu.y * k, 0f, 0f,
                -pv.x * k, -pv.y * k, 0f, 0f,
                0f, 0f, 1f, 0f,
                c.x, c.y, 0f, 1f,
            ),
        )
        val brightness = 0.7f + 0.3f * max(0f, n dot LIGHT)
        withTransform({ transform(m) }) { drawFace(headsUp, brightness) }
    }

    /** One face, flat and upright, filling a circle of [radius] at [center]. For the history strip. */
    fun drawFlat(scope: DrawScope, center: Offset, radius: Float, heads: Boolean) = with(scope) {
        withTransform({
            translate(center.x, center.y)
            scale(radius / 100f, radius / 100f, Offset.Zero)
        }) { drawFace(heads, 1f) }
    }

    private fun DrawScope.drawFace(heads: Boolean, brightness: Float) {
        fun lit(c: Color) = lerp(SHADE, c, brightness)
        val face = lit(FACE)
        val ink = lit(INK)

        drawCircle(lit(RIM), 100f, Offset.Zero)
        drawCircle(face, 90f, Offset.Zero)
        val dots = lit(DOTS)
        for (i in 0 until 60) {
            val a = (2 * PI * i / 60).toFloat()
            drawCircle(dots, 1.5f, Offset(cos(a), sin(a)) * 81f)
        }

        if (heads) {
            drawPath(crown, ink)
            drawPath(crown, ink, style = Stroke(8f, join = StrokeJoin.Round))
        } else {
            rotate(35f, Offset.Zero) {
                drawPath(vane, ink)
                // Splits in the vane and the shaft are cut through in the face colour, then the quill runs on below.
                drawLine(face, Offset(31f, -20f), Offset(12f, -8f), 3.5f, StrokeCap.Round)
                drawLine(face, Offset(-31f, 4f), Offset(-12f, 14f), 3.5f, StrokeCap.Round)
                drawLine(face, Offset(0f, -46f), Offset(0f, 38f), 3f, StrokeCap.Round)
                drawLine(ink, Offset(0f, 38f), Offset(0f, 64f), 5f, StrokeCap.Round)
            }
        }
    }
}

/**
 * A soft shadow on the table under a coin: an ellipse [rx] by [ry] that fades and blurs as [height] (0 on the table,
 * 1 at the top of a big throw) rises, and is sharpest on landing.
 */
fun DrawScope.drawCoinShadow(center: Offset, rx: Float, ry: Float, height: Float) {
    val h = height.coerceIn(0f, 1f)
    val alpha = 0.34f * (1f - 0.7f * h)
    val core = 0.72f - 0.6f * h
    withTransform({ scale(1f, ry / rx, center) }) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = alpha),
                core to Color.Black.copy(alpha = alpha * 0.75f),
                1f to Color.Transparent,
                center = center,
                radius = rx,
            ),
            rx,
            center,
        )
    }
}
