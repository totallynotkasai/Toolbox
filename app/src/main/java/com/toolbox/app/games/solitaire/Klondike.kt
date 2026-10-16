package com.toolbox.app.games.solitaire

import com.toolbox.app.games.solitaire.Step.Kind
import kotlin.random.Random

/** The four suits, in the order [Card.id] counts through them. */
enum class Suit(val red: Boolean) {
    Clubs(false), Diamonds(true), Hearts(true), Spades(false);

    val title: String get() = name.lowercase()
}

/** One of the 52 cards: [id] / 13 is its suit and [id] % 13 its rank, from the ace (1) to the king (13). */
@JvmInline
value class Card(val id: Int) {
    val suit: Suit get() = Suit.entries[id / 13]
    val rank: Int get() = id % 13 + 1
    val red: Boolean get() = suit.red

    /** "7 of hearts", as TalkBack reads it. */
    val name: String get() = "${RANK_NAMES[rank]} of ${suit.title}"

    /** "A", "7" or "K", for the card's corner. */
    val label: String get() = RANK_LABELS[rank]

    /** The card as one letter in a saved game: a to z, then A to Z. */
    val letter: Char get() = if (id < 26) 'a' + id else 'A' + (id - 26)

    companion object {
        fun of(rank: Int, suit: Suit) = Card(suit.ordinal * 13 + rank - 1)

        fun fromLetter(c: Char) = Card(
            when (c) {
                in 'a'..'z' -> c - 'a'
                in 'A'..'Z' -> c - 'A' + 26
                else -> throw IllegalArgumentException("Not a card: $c")
            },
        )

        private val RANK_NAMES = listOf("", "Ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King")
        private val RANK_LABELS = listOf("", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
    }
}

/** Where cards lie: the stock, the waste, four foundations and seven columns, numbered 0 to 12 in that order. */
@JvmInline
value class Pile(val index: Int) {
    val isFoundation: Boolean get() = index in 2..5
    val isColumn: Boolean get() = index in 6..12

    /** Which foundation or column this is, counting from 0. */
    val number: Int get() = if (isColumn) index - 6 else index - 2

    companion object {
        val Stock = Pile(0)
        val Waste = Pile(1)
        fun foundation(i: Int) = Pile(2 + i)
        fun column(i: Int) = Pile(6 + i)
        val foundations = List(4) { foundation(it) }
        val columns = List(7) { column(it) }
        val all = List(13) { Pile(it) }
    }
}

/**
 * One thing done to a game, with what [Klondike.undo] needs to take it back: its [kind], where [count] cards went
 * [from] and [to], and whether the column they left [turned] its new top card face up.
 */
data class Step(val kind: Kind, val from: Pile, val to: Pile, val count: Int, val turned: Boolean = false) {
    enum class Kind { Draw, Recycle, Move }

    /** The step in one int, for a saved game. */
    fun pack(): Int = kind.ordinal or (from.index shl 2) or (to.index shl 6) or (count shl 10) or ((if (turned) 1 else 0) shl 16)

    companion object {
        fun unpack(v: Int) = Step(Kind.entries[v and 3], Pile((v shr 2) and 15), Pile((v shr 6) and 15), (v shr 10) and 63, ((v shr 16) and 1) == 1)
    }
}

/**
 * A game of Klondike patience with one deck. Seven columns build down in alternating colours, four foundations build
 * up by suit from the ace, and the stock is turned onto the waste [draw] cards at a time (1 or 3), as often as you
 * like. A column's top card turns face up by itself when the cards on it are moved away.
 *
 * Pure and mutable, like Block Drop's game: the screen calls the moves as the player makes them and redraws when
 * [revision] changes. Every move is kept, so [undo] can go back to the deal.
 */
class Klondike private constructor(
    /** How many cards are turned from the stock at a time: 1 or 3. */
    val draw: Int,
    private val piles: Array<MutableList<Card>>,
    private val faceDown: IntArray,
) {
    private val history = ArrayList<Step>()

    /** Time played so far, kept by the screen. */
    var elapsedMs = 0L

    /** Whether this deal has been counted as a game played, which happens on its first move. */
    var counted = false

    /** Goes up with every change the screen should show. */
    var revision = 0; private set

    val moves: Int get() = history.size
    val canUndo: Boolean get() = history.isNotEmpty()
    val won: Boolean get() = Pile.foundations.all { piles[it.index].size == 13 }

    /** Nothing is left to find: the stock and waste are empty and every card is face up, so the rest plays itself. */
    val canAutoFinish: Boolean get() = !won && piles[0].isEmpty() && piles[1].isEmpty() && faceDown.all { it == 0 }

    fun cards(pile: Pile): List<Card> = piles[pile.index]
    fun top(pile: Pile): Card? = piles[pile.index].lastOrNull()

    /** How many cards at the bottom of [column] are still face down. */
    fun faceDown(column: Int): Int = faceDown[column]

    fun isFaceUp(pile: Pile, index: Int): Boolean = when {
        pile == Pile.Stock -> false
        pile.isColumn -> index >= faceDown[pile.number]
        else -> true
    }

    /** Turns the next [draw] cards from the stock onto the waste, or the waste back over once the stock is empty. */
    fun turnStock(): Step? {
        val stock = piles[0]
        val waste = piles[1]
        return when {
            stock.isNotEmpty() -> perform(Kind.Draw, Pile.Stock, Pile.Waste, minOf(draw, stock.size))
            waste.isNotEmpty() -> perform(Kind.Recycle, Pile.Waste, Pile.Stock, waste.size)
            else -> null
        }
    }

    /**
     * Whether the card at [index] in [from], and everything on it, can be picked up: a face-up card in a column and
     * the cards on it, or the top card of the waste or a foundation.
     */
    fun canLift(from: Pile, index: Int): Boolean {
        val size = piles[from.index].size
        return when {
            index !in 0 until size -> false
            from.isColumn -> index >= faceDown[from.number]
            from == Pile.Waste || from.isFoundation -> index == size - 1
            else -> false
        }
    }

    fun canMove(from: Pile, index: Int, to: Pile): Boolean {
        if (from == to || !canLift(from, index)) return false
        val source = piles[from.index]
        val card = source[index]
        val target = piles[to.index]
        return when {
            to.isFoundation -> index == source.lastIndex && !from.isFoundation && fitsFoundation(card, target)
            to.isColumn -> fitsColumn(card, target)
            else -> false
        }
    }

    /** Moves the card at [index] in [from], and everything on it, onto [to]; null if it can't go there. */
    fun move(from: Pile, index: Int, to: Pile): Step? =
        if (canMove(from, index, to)) perform(Kind.Move, from, to, piles[from.index].size - index) else null

    /**
     * Everywhere the cards from [index] up in [from] can go, foundations first and then columns from the left. A king
     * alone in its column going to an empty one is left out, since it changes nothing.
     */
    fun destinations(from: Pile, index: Int): List<Pile> =
        (Pile.foundations + Pile.columns).filter { to ->
            canMove(from, index, to) && !(from.isColumn && index == 0 && to.isColumn && piles[to.index].isEmpty())
        }

    /**
     * Where a tap sends the cards from [index] up in [from]: onto a foundation if they can go there, otherwise onto the
     * next column to the right that takes them, wrapping round, so tapping again moves them on. A column with cards in
     * it comes before an empty one.
     */
    fun bestDestination(from: Pile, index: Int): Pile? {
        val options = destinations(from, index)
        options.firstOrNull { it.isFoundation }?.let { return it }
        val start = if (from.isColumn) from.number + 1 else 0
        val order = List(7) { Pile.column((start + it) % 7) }
        return order.firstOrNull { it in options && piles[it.index].isNotEmpty() } ?: order.firstOrNull { it in options }
    }

    /** Takes back the last step; null if there's nothing to undo. */
    fun undo(): Step? {
        val step = history.removeLastOrNull() ?: return null
        val source = piles[step.from.index]
        val target = piles[step.to.index]
        when (step.kind) {
            Kind.Draw -> repeat(step.count) { source.add(target.removeAt(target.lastIndex)) }
            Kind.Recycle -> {
                source.addAll(target.asReversed())
                target.clear()
            }
            Kind.Move -> {
                if (step.turned) faceDown[step.from.number]++
                val run = target.subList(target.size - step.count, target.size)
                source.addAll(run)
                run.clear()
            }
        }
        revision++
        return step
    }

    /**
     * While [canAutoFinish], puts the lowest card left onto its foundation. Every column is then one run in order, so
     * that card is always on top of one.
     */
    fun autoStep(): Step? {
        if (!canAutoFinish) return null
        val from = Pile.columns.filter { piles[it.index].isNotEmpty() }.minByOrNull { piles[it.index].last().rank } ?: return null
        val index = piles[from.index].lastIndex
        val to = Pile.foundations.firstOrNull { canMove(from, index, it) } ?: return null
        return move(from, index, to)
    }

    /**
     * Whether nothing useful can be done: no run of moves (turning the stock, shuffling cards between columns, taking
     * them back off the foundations) ever puts another card on a foundation, turns a card over, or gets a card out of
     * the stock and waste. It looks at no more than [limit] positions of the columns and foundations and says no if it
     * runs out, so it never calls a game stuck when it isn't sure.
     */
    fun isStuck(limit: Int = 20_000): Boolean {
        if (won || canAutoFinish) return false
        // Turning the stock never changes the columns, so rather than searching through every place in the stock,
        // the search only shuffles the columns and foundations, and asks each position whether it takes any card
        // that can ever come to the top of the waste.
        val fromStock = wasteTops()
        val home = onFoundations()
        val hidden = faceDown.sum()
        val start = copy()
        val seen = hashSetOf(start.key())
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val position = queue.removeFirst()
            if (fromStock.any { position.takes(it) }) return false
            for (next in position.nextPositions()) {
                if (next.onFoundations() > home || next.faceDown.sum() < hidden) return false
                if (seen.add(next.key())) {
                    if (seen.size > limit) return false
                    queue.addLast(next)
                }
            }
        }
        return true
    }

    /**
     * Every card that turning the stock can ever bring to the top of the waste. With Draw 1 that's all of them. With
     * Draw 3 it's every third card, so this plays out the rest of this time through the stock and one whole time more,
     * after which the same cards come round again.
     */
    private fun wasteTops(): Set<Card> {
        if (draw == 1) return (piles[0] + piles[1]).toSet()
        val tops = piles[1].takeLast(1).toMutableSet()
        var stock = piles[0].toMutableList()
        var waste = piles[1].toMutableList()
        repeat(2) {
            while (stock.isNotEmpty()) {
                repeat(minOf(draw, stock.size)) { waste.add(stock.removeAt(stock.lastIndex)) }
                tops += waste.last()
            }
            stock = waste.asReversed().toMutableList()
            waste = mutableListOf()
        }
        return tops
    }

    /** Whether [card] could go onto a foundation or a column here. */
    private fun takes(card: Card) =
        Pile.foundations.any { fitsFoundation(card, piles[it.index]) } || Pile.columns.any { fitsColumn(card, piles[it.index]) }

    /**
     * The game as text for saving: a version, the draw, the time, whether it's been counted, the 13 piles as letters,
     * the face-down counts, and every step taken, so undo still works after a restart.
     */
    fun save(): String = buildString {
        append("1;$draw;$elapsedMs;${if (counted) 1 else 0};")
        piles.joinTo(this, ",") { pile -> pile.joinToString("") { it.letter.toString() } }
        append(';')
        faceDown.joinTo(this, ",")
        append(';')
        history.joinTo(this, ".") { it.pack().toString(36) }
    }

    /** A copy of the cards where they lie, without the steps, time or count. */
    fun copy(): Klondike = Klondike(draw, Array(13) { piles[it].toMutableList() }, faceDown.copyOf())

    private fun fitsFoundation(card: Card, target: List<Card>): Boolean {
        val top = target.lastOrNull() ?: return card.rank == 1
        return card.suit == top.suit && card.rank == top.rank + 1
    }

    // A column's top card is always face up, since it turns over as soon as it's uncovered.
    private fun fitsColumn(card: Card, target: List<Card>): Boolean {
        val top = target.lastOrNull() ?: return card.rank == 13
        return card.red != top.red && card.rank == top.rank - 1
    }

    private fun perform(kind: Kind, from: Pile, to: Pile, count: Int): Step {
        val source = piles[from.index]
        val target = piles[to.index]
        var turned = false
        when (kind) {
            Kind.Draw -> repeat(count) { target.add(source.removeAt(source.lastIndex)) }
            Kind.Recycle -> {
                target.addAll(source.asReversed())
                source.clear()
            }
            Kind.Move -> {
                val run = source.subList(source.size - count, source.size)
                target.addAll(run)
                run.clear()
                if (from.isColumn && source.isNotEmpty() && faceDown[from.number] >= source.size) {
                    faceDown[from.number] = source.size - 1
                    turned = true
                }
            }
        }
        revision++
        return Step(kind, from, to, count, turned).also { history += it }
    }

    private fun onFoundations() = Pile.foundations.sumOf { piles[it.index].size }

    /** Every position one move of a column's or a foundation's cards away. */
    private fun nextPositions(): List<Klondike> = buildList {
        for (from in Pile.foundations + Pile.columns) {
            val size = piles[from.index].size
            val starts = if (from.isColumn) faceDown[from.number] until size else maxOf(0, size - 1) until size
            for (i in starts) for (to in destinations(from, i)) add(copy().apply { move(from, i, to) })
        }
    }

    /** The foundations and columns, for telling positions apart in [isStuck] (the stock and waste don't change there). */
    private fun key(): String = buildString {
        for (pile in piles.drop(2)) {
            for (card in pile) append(card.letter)
            append('|')
        }
        for (d in faceDown) append('0' + d)
    }

    companion object {
        /** A new game: a shuffled deck in seven columns of 1 to 7 cards with only the top ones face up, and 24 in the stock. */
        fun deal(draw: Int, random: Random = Random): Klondike {
            val deck = List(52) { Card(it) }.shuffled(random).toMutableList()
            val piles = Array(13) { mutableListOf<Card>() }
            for (c in 0 until 7) repeat(c + 1) { piles[6 + c].add(deck.removeAt(deck.lastIndex)) }
            piles[0].addAll(deck)
            return Klondike(draw, piles, IntArray(7) { it })
        }

        /** For tests: a game with these piles (stock, waste, foundations, columns) and face-down counts. */
        internal fun arrange(draw: Int, piles: List<List<Card>>, faceDown: IntArray = IntArray(7)): Klondike {
            require(piles.size == 13 && faceDown.size == 7)
            return Klondike(draw, Array(13) { piles[it].toMutableList() }, faceDown.copyOf())
        }

        /** A game from [save], or null if the text isn't one (a missing or damaged save starts a new deal). */
        fun restore(text: String): Klondike? = runCatching {
            val parts = text.split(';')
            require(parts.size == 7 && parts[0] == "1")
            val draw = parts[1].toInt()
            require(draw == 1 || draw == 3)
            val piles = parts[4].split(',').map { s -> s.map { Card.fromLetter(it) }.toMutableList() }
            require(piles.size == 13)
            require(piles.flatten().map { it.id }.sorted() == List(52) { it })
            val faceDown = parts[5].split(',').map { it.toInt() }.toIntArray()
            require(faceDown.size == 7)
            for (c in 0 until 7) require(faceDown[c] in 0..maxOf(0, piles[6 + c].size - 1))
            Klondike(draw, piles.toTypedArray(), faceDown).apply {
                elapsedMs = parts[2].toLong()
                counted = parts[3] == "1"
                if (parts[6].isNotEmpty()) parts[6].split('.').mapTo(history) { Step.unpack(it.toInt(36)) }
            }
        }.getOrNull()
    }
}
