package com.toolbox.app.games.solitaire

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import kotlin.math.cos
import kotlin.math.sin

// The table is green baize in both themes, a little deeper in dark. Card faces are paper white, dimmed in dark so
// they don't glare; backs are the app's blue.
private val FELT = Color(0xFF237A4F)
private val FELT_EDGE = Color(0xFF17583A)
private val FELT_DARK = Color(0xFF1B5F3E)
private val FELT_EDGE_DARK = Color(0xFF103E28)
private val FACE = Color(0xFFFFFFFF)
private val FACE_DARK = Color(0xFFE4E4E0)
private val EDGE = Color(0x2E000000)
private val SHADOW = Color(0x40000000)
private val RED = Color(0xFFD93025)
private val BLACK = Color(0xFF202124)
private val BACK = Color(0xFF1A73E8)
private val BACK_INNER = Color(0xFF2B80EE)
private val MARK = Color.White.copy(alpha = 0.32f)

/** The baize, lighter in the middle. */
internal fun DrawScope.drawFelt(dark: Boolean) {
    drawRect(
        Brush.radialGradient(
            listOf(if (dark) FELT_DARK else FELT, if (dark) FELT_EDGE_DARK else FELT_EDGE),
            center = Offset(size.width / 2, size.height * 0.4f),
            radius = maxOf(size.width, size.height) * 0.75f,
        ),
    )
}

/** What an empty place shows: nothing, an "A" for a foundation, or the turn-over arrow for an empty stock. */
internal enum class SlotMark { None, Ace, TurnOver }

/**
 * Draws cards [w] × [h] pixels: a face with a big rank and suit in the top corners (all that shows down a column)
 * and a large suit below, a blue back, or an empty place. The corner letters are laid out once, for this size.
 */
internal class CardPainter(measurer: TextMeasurer, val w: Float, val h: Float, density: Density, private val dark: Boolean) {
    private val line = density.density // 1 dp
    private val radius = CornerRadius(w * 0.09f)
    private val cardSize = Size(w, h)
    private val style = TextStyle(
        fontSize = with(density) { (h * 0.22f).toSp() },
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.04).em,
    )
    private val ranks: List<TextLayoutResult> = (1..13).map { measurer.measure(Card.of(it, Suit.Spades).label, style) }
    private val ace: TextLayoutResult = ranks[0]

    fun DrawScope.face(card: Card, at: Offset) {
        drawRoundRect(SHADOW, at + Offset(0f, h * 0.012f), cardSize, radius)
        drawRoundRect(if (dark) FACE_DARK else FACE, at, cardSize, radius)
        drawRoundRect(EDGE, at, cardSize, radius, style = Stroke(line))
        val ink = if (card.red) RED else BLACK
        drawText(ranks[card.rank - 1], ink, at + Offset(w * 0.07f, h * 0.01f))
        val corner = w * 0.28f
        drawSuit(card.suit, at + Offset(w * 0.94f - corner, h * 0.055f), corner, ink)
        val big = w * 0.56f
        drawSuit(card.suit, at + Offset((w - big) / 2, h * 0.63f - big / 2), big, ink)
    }

    fun DrawScope.back(at: Offset) {
        drawRoundRect(SHADOW, at + Offset(0f, h * 0.012f), cardSize, radius)
        drawRoundRect(BACK, at, cardSize, radius)
        val inset = w * 0.09f
        val inner = Size(w - 2 * inset, h - 2 * inset)
        val innerRadius = CornerRadius(w * 0.05f)
        drawRoundRect(BACK_INNER, at + Offset(inset, inset), inner, innerRadius)
        drawRoundRect(Color.White.copy(alpha = 0.4f), at + Offset(inset, inset), inner, innerRadius, style = Stroke(line * 1.2f))
        val emblem = w * 0.34f
        drawSuit(null, at + Offset((w - emblem) / 2, (h - emblem) / 2), emblem, Color.White.copy(alpha = 0.3f))
    }

    fun DrawScope.slot(at: Offset, mark: SlotMark) {
        drawRoundRect(Color.White.copy(alpha = 0.07f), at, cardSize, radius)
        drawRoundRect(MARK, at, cardSize, radius, style = Stroke(line * 1.5f))
        when (mark) {
            SlotMark.None -> Unit
            SlotMark.Ace -> drawText(ace, MARK, at + Offset((w - ace.size.width) / 2, (h - ace.size.height) / 2))
            SlotMark.TurnOver -> turnOver(at + Offset(w / 2, h / 2), w * 0.24f)
        }
    }

    /** A circular arrow, for turning the waste back over. */
    private fun DrawScope.turnOver(center: Offset, r: Float) {
        val stroke = line * 2.5f
        drawArc(MARK, 30f, 290f, false, center - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round))
        val end = Math.toRadians(320.0)
        val tip = center + Offset((r * cos(end)).toFloat(), (r * sin(end)).toFloat())
        val along = Offset(-sin(end).toFloat(), cos(end).toFloat()) // the way the arrow goes round
        val out = Offset(cos(end).toFloat(), sin(end).toFloat())
        val head = r * 0.55f
        val path = Path().apply {
            moveTo(tip.x + along.x * head * 0.6f, tip.y + along.y * head * 0.6f)
            lineTo(tip.x + out.x * head * 0.6f - along.x * head * 0.3f, tip.y + out.y * head * 0.6f - along.y * head * 0.3f)
            lineTo(tip.x - out.x * head * 0.6f - along.x * head * 0.3f, tip.y - out.y * head * 0.6f - along.y * head * 0.3f)
            close()
        }
        drawPath(path, MARK)
    }
}

/** A suit [size] pixels square with its top-left at [at]; null draws a plain diamond, for the card back's emblem. */
internal fun DrawScope.drawSuit(suit: Suit?, at: Offset, size: Float, color: Color) {
    translate(at.x, at.y) {
        scale(size, size, pivot = Offset.Zero) {
            when (suit) {
                Suit.Hearts -> drawPath(HEART, color)
                Suit.Diamonds, null -> drawPath(DIAMOND, color)
                Suit.Spades -> drawPath(SPADE, color)
                Suit.Clubs -> {
                    // Separate shapes, so overlaps can't cancel out into holes.
                    drawCircle(color, 0.215f, Offset(0.5f, 0.27f))
                    drawCircle(color, 0.215f, Offset(0.26f, 0.59f))
                    drawCircle(color, 0.215f, Offset(0.74f, 0.59f))
                    drawCircle(color, 0.13f, Offset(0.5f, 0.52f))
                    drawPath(CLUB_STEM, color)
                }
            }
        }
    }
}

// The suits in a 1 × 1 box.
private val HEART = Path().apply {
    moveTo(0.5f, 0.95f)
    cubicTo(0.2f, 0.72f, 0f, 0.52f, 0f, 0.31f)
    cubicTo(0f, 0.14f, 0.12f, 0.03f, 0.27f, 0.03f)
    cubicTo(0.38f, 0.03f, 0.46f, 0.09f, 0.5f, 0.19f)
    cubicTo(0.54f, 0.09f, 0.62f, 0.03f, 0.73f, 0.03f)
    cubicTo(0.88f, 0.03f, 1f, 0.14f, 1f, 0.31f)
    cubicTo(1f, 0.52f, 0.8f, 0.72f, 0.5f, 0.95f)
    close()
}

private val DIAMOND = Path().apply {
    moveTo(0.5f, 0f)
    cubicTo(0.6f, 0.18f, 0.74f, 0.36f, 0.88f, 0.5f)
    cubicTo(0.74f, 0.64f, 0.6f, 0.82f, 0.5f, 1f)
    cubicTo(0.4f, 0.82f, 0.26f, 0.64f, 0.12f, 0.5f)
    cubicTo(0.26f, 0.36f, 0.4f, 0.18f, 0.5f, 0f)
    close()
}

private val SPADE = Path().apply {
    moveTo(0.5f, 0.02f)
    cubicTo(0.62f, 0.2f, 1f, 0.38f, 1f, 0.6f)
    cubicTo(1f, 0.76f, 0.87f, 0.85f, 0.74f, 0.85f)
    cubicTo(0.64f, 0.85f, 0.57f, 0.8f, 0.53f, 0.73f)
    cubicTo(0.55f, 0.85f, 0.6f, 0.93f, 0.68f, 0.98f)
    lineTo(0.32f, 0.98f)
    cubicTo(0.4f, 0.93f, 0.45f, 0.85f, 0.47f, 0.73f)
    cubicTo(0.43f, 0.8f, 0.36f, 0.85f, 0.26f, 0.85f)
    cubicTo(0.13f, 0.85f, 0f, 0.76f, 0f, 0.6f)
    cubicTo(0f, 0.38f, 0.38f, 0.2f, 0.5f, 0.02f)
    close()
}

private val CLUB_STEM = Path().apply {
    moveTo(0.46f, 0.6f)
    cubicTo(0.45f, 0.82f, 0.4f, 0.92f, 0.32f, 0.98f)
    lineTo(0.68f, 0.98f)
    cubicTo(0.6f, 0.92f, 0.55f, 0.82f, 0.54f, 0.6f)
    close()
}
