package com.toolbox.app.games.solitaire

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A card's height over its width, as a real playing card. */
const val CARD_ASPECT = 1.4f

/**
 * Where the piles go on a table [width] × [height] pixels, with [gap] pixels between them. The top row holds the
 * stock, the waste (fanned into the next slot with Draw 3) and the four foundations; the seven columns are below.
 * Cards are as wide as seven fit across, unless that would leave too little room down the columns.
 */
internal class TableLayout(val width: Float, val height: Float, val gap: Float) {
    val cardW: Float
    val cardH: Float
    private val left: Float

    init {
        val byWidth = (width - gap * 8) / 7
        val byHeight = (height - gap * 3) / (CARD_ASPECT * 4.6f)
        cardW = min(byWidth, byHeight).coerceAtLeast(1f)
        cardH = cardW * CARD_ASPECT
        left = (width - cardW * 7 - gap * 6) / 2
    }

    val top: Float get() = gap
    val tableauTop: Float get() = top + cardH + gap * 2

    /** How far each card fanned out on the waste sits right of the one under it. */
    val fan: Float get() = cardW * 0.3f

    val cardSize: Size get() = Size(cardW, cardH)

    fun slotX(i: Int) = left + i * (cardW + gap)

    /** The top-left corner of [pile]'s first card. */
    fun origin(pile: Pile): Offset = when {
        pile == Pile.Stock -> Offset(slotX(0), top)
        pile == Pile.Waste -> Offset(slotX(1), top)
        pile.isFoundation -> Offset(slotX(3 + pile.number), top)
        else -> Offset(slotX(pile.number), tableauTop)
    }

    /**
     * How far down a column each face-down and each face-up card sits below the one before. Face-up cards leave their
     * corner showing; a long column squeezes both so its last card still fits on the table.
     */
    fun steps(faceDown: Int, faceUp: Int): Pair<Float, Float> {
        var down = cardH * 0.12f
        var up = cardH * 0.3f
        val room = max(0f, height - tableauTop - cardH - gap)
        val need = down * faceDown + up * max(0, faceUp - 1)
        if (need > room) {
            val k = room / need
            down *= k
            up *= k
        }
        return down to up
    }

    /** Where a card dropped onto [pile] counts as over it: a foundation's card, or a column's whole strip of table. */
    fun dropZone(pile: Pile): Rect = if (pile.isColumn) {
        Rect(slotX(pile.number) - gap / 2, tableauTop - gap, slotX(pile.number) + cardW + gap / 2, height)
    } else {
        Rect(origin(pile), cardSize)
    }

    /** Every card where it belongs now, in the order they're drawn: stock, waste, foundations, then the columns. */
    fun place(game: Klondike): List<Placed> = buildList {
        game.cards(Pile.Stock).forEachIndexed { i, c -> add(Placed(c, Pile.Stock, i, origin(Pile.Stock), faceUp = false)) }
        val waste = game.cards(Pile.Waste)
        val fanned = if (game.draw == 1) 1 else min(3, waste.size)
        waste.forEachIndexed { i, c ->
            val along = max(0, i - (waste.size - fanned))
            add(Placed(c, Pile.Waste, i, origin(Pile.Waste) + Offset(fan * along, 0f), faceUp = true))
        }
        for (f in Pile.foundations) game.cards(f).forEachIndexed { i, c -> add(Placed(c, f, i, origin(f), faceUp = true)) }
        for (column in Pile.columns) {
            val cards = game.cards(column)
            val hidden = game.faceDown(column.number)
            val (down, up) = steps(hidden, cards.size - hidden)
            var y = tableauTop
            cards.forEachIndexed { i, c ->
                add(Placed(c, column, i, Offset(slotX(column.number), y), faceUp = i >= hidden))
                y += if (i < hidden) down else up
            }
        }
    }
}

/** A card where it belongs: [index] in [pile], with its top-left corner at [pos]. */
internal data class Placed(val card: Card, val pile: Pile, val index: Int, val pos: Offset, val faceUp: Boolean)

/** A place on the table: the card at [index] in [pile], or the pile's empty place when [index] is −1. */
internal data class Spot(val pile: Pile, val index: Int)

/** What a touch at [point] lands on: the card drawn on top there, or the stock's place when it's empty. Null for bare table. */
internal fun TableLayout.hit(placed: List<Placed>, point: Offset): Spot? {
    placed.lastOrNull { Rect(it.pos, cardSize).contains(point) }?.let { return Spot(it.pile, it.index) }
    return if (Rect(origin(Pile.Stock), cardSize).contains(point)) Spot(Pile.Stock, -1) else null
}

/**
 * Where cards dragged from [index] in [from], with their first card now covering [rect], are dropped: whichever place
 * they can go that they cover most. Null if they cover none, and they go back.
 */
internal fun TableLayout.dropTarget(game: Klondike, from: Pile, index: Int, rect: Rect): Pile? =
    game.destinations(from, index)
        .map { it to rect.intersectArea(dropZone(it)) }
        .filter { it.second > 0f }
        .maxByOrNull { it.second }?.first

private fun Rect.intersectArea(other: Rect): Float {
    val i = intersect(other)
    return if (i.width > 0f && i.height > 0f) i.width * i.height else 0f
}

/**
 * Where each card is drawn, easing towards where it belongs, so a move, an undo or a deal slides rather than jumps.
 * A card that has changed pile flies over the others until it lands. Cards also turn over as they go, shake when they
 * can't go anywhere, and the foundations hop when the game is won.
 */
internal class CardMotion {
    private val pos = arrayOfNulls<Offset>(52)
    private val face = FloatArray(52) // 0 shows the back, 1 the face
    private val lastPile = IntArray(52) { -1 }
    private val flying = BooleanArray(52)
    private val wait = FloatArray(52) // seconds before a card dealt starts moving
    private val shaking = FloatArray(52) // seconds of shake left

    /** Seconds since the game was won, or below 0 while it isn't. */
    private var party = -1f

    fun position(card: Card): Offset? = pos[card.id]
    fun face(card: Card): Float = face[card.id]
    fun isFlying(card: Card): Boolean = flying[card.id]

    /** Sideways nudge of a shaking card, as a fraction of the card's width. */
    fun shake(card: Card): Float {
        val left = shaking[card.id]
        if (left <= 0f) return 0f
        return sin((SHAKE_S - left) * 55f) * 0.07f * (left / SHAKE_S)
    }

    /** How high foundation [f]'s top card hops on a win, as a fraction of a card's height. */
    fun hop(f: Int): Float {
        if (party < 0f) return 0f
        val t = ((party - f * 0.12f) / 0.45f).coerceIn(0f, 1f)
        return sin(t * PI.toFloat()) * 0.3f
    }

    /** Starts every card at [from] face down, to be dealt out one by one in [order]. */
    fun deal(order: List<Card>, from: Offset) {
        for (i in 0 until 52) {
            pos[i] = from
            face[i] = 0f
            lastPile[i] = -1
            flying[i] = false
            wait[i] = 0f
            shaking[i] = 0f
        }
        order.forEachIndexed { i, c ->
            flying[c.id] = true
            wait[c.id] = 0.05f + i * DEAL_STAGGER_S
        }
        party = -1f
    }

    fun shake(cards: List<Card>) {
        for (c in cards) shaking[c.id] = SHAKE_S
    }

    fun celebrate() { party = 0f }

    /** Forgets the win, for an undo or a new deal. */
    fun calm() { party = -1f }

    /**
     * Moves every card [dt] seconds on towards [placed]; cards in [held] are put straight where the finger has them.
     * True while anything is still moving.
     */
    fun step(placed: List<Placed>, dt: Float, held: Map<Card, Offset>): Boolean {
        var moving = false
        val ease = 1f - exp(-dt * EASE_PER_S)
        for (p in placed) {
            val id = p.card.id
            if (lastPile[id] != p.pile.index) {
                if (lastPile[id] >= 0) flying[id] = true
                lastPile[id] = p.pile.index
            }
            val faceTarget = if (p.faceUp) 1f else 0f
            val now = pos[id]
            val drag = held[p.card]
            when {
                now == null -> {
                    pos[id] = p.pos
                    face[id] = faceTarget
                }
                drag != null -> {
                    pos[id] = drag
                    flying[id] = true
                }
                wait[id] > 0f -> {
                    wait[id] -= dt
                    moving = true
                }
                else -> {
                    val d = p.pos - now
                    if (d.getDistanceSquared() < 0.25f) {
                        pos[id] = p.pos
                        flying[id] = false
                    } else {
                        pos[id] = now + d * ease
                        moving = true
                    }
                }
            }
            if (face[id] != faceTarget && wait[id] <= 0f) {
                val turn = dt / FLIP_S
                face[id] = if (faceTarget > face[id]) min(faceTarget, face[id] + turn) else max(faceTarget, face[id] - turn)
                moving = true
            }
            if (shaking[id] > 0f) {
                shaking[id] = max(0f, shaking[id] - dt)
                moving = true
            }
        }
        if (party >= 0f && party < PARTY_S) {
            party += dt
            moving = true
        }
        return moving
    }

    private companion object {
        const val EASE_PER_S = 16f
        const val FLIP_S = 0.18f
        const val SHAKE_S = 0.35f
        const val DEAL_STAGGER_S = 0.035f
        const val PARTY_S = 1.4f
    }
}
