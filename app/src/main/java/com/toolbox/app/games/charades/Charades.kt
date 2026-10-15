package com.toolbox.app.games.charades

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt
import kotlin.random.Random

/** A deck of cards. [id] names its saved state, so it never changes, even when the deck is renamed. */
data class Deck(val id: String, val name: String, val cards: List<String>, val builtIn: Boolean = false)

/** A card from a round, in the order it came up: [got] it, or passed. */
data class Played(val card: String, val got: Boolean)

/** Which way the phone was tipped: [Down] (screen to the floor) is got it, [Up] is pass. */
enum class Tilt { Down, Up }

/**
 * Turns gravity readings into tilts, for a phone held sideways on a forehead with the screen facing out. Only the
 * screen's facing matters: [elevation] is how far it faces up (positive) or down (negative) from straight out. That
 * reads the same whichever way round the phone is held sideways, so neither landscape needs telling apart.
 *
 * A tilt counts once it passes [TRIGGER_DEGREES], and the next one only once the phone has come back within
 * [LEVEL_DEGREES] of upright. It starts disarmed, so a phone that's still tipped when a round starts doesn't count.
 */
class TiltDetector {
    var armed = false
        private set

    /** One reading in the phone's own axes, as `TYPE_GRAVITY` gives them; a tilt if it just made one. */
    fun update(x: Float, y: Float, z: Float): Tilt? {
        val e = elevation(x, y, z) ?: return null
        if (!armed) {
            if (abs(e) <= LEVEL_DEGREES) armed = true
            return null
        }
        val tilt = when {
            e <= -TRIGGER_DEGREES -> Tilt.Down
            e >= TRIGGER_DEGREES -> Tilt.Up
            else -> return null
        }
        armed = false
        return tilt
    }

    companion object {
        const val TRIGGER_DEGREES = 45f
        const val LEVEL_DEGREES = 25f

        /** Weaker than this (m/s²), the phone is being swung about and the reading says nothing about its facing. */
        private const val MIN_GRAVITY = 4f

        /** tan 30°: held upright, gravity runs within 30° of the screen's up and down. */
        private const val UPRIGHT_SLOPE = 0.577f

        /** Degrees the screen faces above (positive) or below straight out, or null for a reading too weak to trust. */
        fun elevation(x: Float, y: Float, z: Float): Float? {
            val g = sqrt(x * x + y * y + z * z)
            if (g < MIN_GRAVITY) return null
            return Math.toDegrees(asin((z / g).coerceIn(-1f, 1f).toDouble())).toFloat()
        }

        /**
         * Whether the phone is held upright as on a forehead, ready to count in: screen facing out, and the right way
         * up for the screen as shown. [x] and [y] are in the screen's axes (see `toDisplayAxes`), so a phone still
         * held in portrait under a landscape screen isn't counted.
         */
        fun heldUpright(x: Float, y: Float, z: Float): Boolean {
            val e = elevation(x, y, z) ?: return false
            return abs(e) <= LEVEL_DEGREES && abs(x) <= abs(y) * UPRIGHT_SLOPE
        }
    }
}

/** Longest card or deck name kept; anything longer is cut. */
const val MAX_CARD_LENGTH = 40
const val MAX_NAME_LENGTH = 30
const val MAX_CARDS = 300
const val MAX_DECKS = 30

/** [text] on one line: control characters made spaces, the ends trimmed and runs of spaces made one. */
fun cleanLine(text: String): String =
    text.map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("").trim().replace(MULTI_SPACE, " ")

private val MULTI_SPACE = Regex(" {2,}")

/**
 * Cards from text with one per line, as typed into a deck or kept in a built-in one. Each is cleaned (see
 * [cleanLine]) and cut to [MAX_CARD_LENGTH]. Blank lines are skipped, and a card that's already in, whatever its
 * capitals, is left out. At most [MAX_CARDS].
 */
fun parseCards(text: String): List<String> {
    val seen = mutableSetOf<String>()
    return text.lineSequence()
        .map { cleanLine(it).take(MAX_CARD_LENGTH).trimEnd() }
        .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
        .take(MAX_CARDS)
        .toList()
}

/** How many of [text]'s cards [parseCards] leaves out for repeating one above them. */
fun countRepeats(text: String): Int {
    val lines = text.lineSequence().map { cleanLine(it).take(MAX_CARD_LENGTH).trimEnd().lowercase() }.filter { it.isNotEmpty() }.toList()
    return lines.size - lines.toSet().size
}

/**
 * The next card from [cards] that hasn't been [played] since the deck last ran out, and the played set with it added.
 * Once every card has been played the deck starts again, keeping clear of the cards already [shown] this round where
 * it can. Cards in [played] that are no longer in the deck, after an edit, are forgotten. Null for an empty deck.
 */
fun dealCard(
    cards: List<String>,
    played: Set<String>,
    shown: Collection<String> = emptyList(),
    random: Random = Random,
): Pair<String, Set<String>>? {
    if (cards.isEmpty()) return null
    val deck = cards.toSet()
    val still = played.filterTo(mutableSetOf()) { it in deck }
    val fresh = cards.filter { it !in still }
    if (fresh.isNotEmpty()) {
        val card = fresh.random(random)
        return card to (still + card)
    }
    // A small deck can run out more than once in a round. Then it keeps clear of the latest half of the cards shown,
    // so a card never comes straight back.
    val seen = shown.toSet()
    val recent = shown.toList().takeLast(maxOf(1, cards.size / 2)).toSet()
    val card = cards.filter { it !in seen }.ifEmpty { cards.filter { it !in recent } }.ifEmpty { cards }.random(random)
    return card to setOf(card)
}

/**
 * The player's own decks, saved as one string in Prefs: decks split by a record separator, and each deck's id, name
 * and cards by a unit separator. [cleanLine] takes control characters out of names and cards, so they never clash.
 */
object CustomDecks {
    private const val RECORD = "\u001E"
    private const val UNIT = "\u001F"

    fun encode(decks: List<Deck>): String = decks.joinToString(RECORD) { (listOf(it.id, it.name) + it.cards).joinToString(UNIT) }

    fun decode(text: String): List<Deck> = if (text.isEmpty()) emptyList() else text.split(RECORD).mapNotNull { record ->
        val fields = record.split(UNIT)
        if (fields.size < 2 || fields[0].isEmpty()) null else Deck(fields[0], fields[1], fields.drop(2).filter { it.isNotEmpty() })
    }

    /** An id no deck in [decks] has. A deleted deck's id can come round again, so deleting one forgets its played cards. */
    fun newId(decks: List<Deck>): String = "custom" + ((decks.mapNotNull { it.id.removePrefix("custom").toIntOrNull() }.maxOrNull() ?: 0) + 1)

    /** [wanted], or "My deck" when it's blank, made different from every other deck's name with a number. */
    fun uniqueName(wanted: String, others: List<String>): String {
        val base = cleanLine(wanted).take(MAX_NAME_LENGTH).ifEmpty { "My deck" }
        val taken = others.map { it.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        return (2..MAX_DECKS + 1).map { "${base.take(MAX_NAME_LENGTH - 3).trimEnd()} $it" }.first { it.lowercase() !in taken }
    }

    /** The cards a deck has played since it last ran out, saved one string per deck. */
    fun encodePlayed(played: Set<String>): String = played.joinToString(UNIT)

    fun decodePlayed(text: String): Set<String> = text.split(UNIT).filter { it.isNotEmpty() }.toSet()
}

/** What the clock passed on its way down: one of the last five seconds, or the end of the round. */
sealed interface ClockEvent {
    data class Tick(val secondsLeft: Int) : ClockEvent
    data object TimeUp : ClockEvent
}

/**
 * One round, as a value: the clock running down from [seconds], the cards played so far in order, and the [current]
 * card. Nothing here knows about decks or dealing; the screen deals the next card and hands it to [answer].
 */
data class CharadesRound(
    val deckId: String,
    val seconds: Int,
    val remainingMs: Long = seconds * 1000L,
    val played: List<Played> = emptyList(),
    val current: String? = null,
) {
    val score: Int get() = played.count { it.got }
    val passed: Int get() = played.size - score
    val secondsLeft: Int get() = ((remainingMs + 999) / 1000).toInt()

    /** Whether the clock ran out, rather than the round being ended early. */
    val timeUp: Boolean get() = remainingMs <= 0

    /** Whether any card has come up yet, so leaving would lose something. */
    val started: Boolean get() = current != null || played.isNotEmpty()

    /** Every card that has come up, for dealing clear of them. */
    val shown: List<String> get() = played.map { it.card } + listOfNotNull(current)

    /**
     * The clock moved on [ms]. Each of the last [TICKS] whole seconds it reaches is a tick, and reaching 0 is the end,
     * when the card showing counts as passed (see [end]).
     */
    fun advance(ms: Long): Pair<CharadesRound, List<ClockEvent>> {
        if (remainingMs <= 0 || ms <= 0) return this to emptyList()
        val after = (remainingMs - ms).coerceAtLeast(0)
        val events = (TICKS downTo 1).filter { remainingMs > it * 1000L && after <= it * 1000L }.map { ClockEvent.Tick(it) }
        val next = copy(remainingMs = after)
        return if (after == 0L) next.end() to events + ClockEvent.TimeUp else next to events
    }

    /** The current card was [got] or passed, and [next] comes up. */
    fun answer(got: Boolean, next: String?): CharadesRound {
        val card = current ?: return this
        return copy(played = played + Played(card, got), current = next)
    }

    /**
     * The round is over, by the clock or ended early with time still left: a card still showing counts as passed, so
     * it's on the list at the end.
     */
    fun end(): CharadesRound = copy(played = played + listOfNotNull(current?.let { Played(it, false) }), current = null)

    /** For saved state: the deck, the length, the clock, the card showing ("" for none), then each card played. */
    fun save(): ArrayList<String> = arrayListOf(deckId, "$seconds", "$remainingMs", current ?: "").apply {
        played.forEach { add((if (it.got) "+" else "-") + it.card) }
    }

    companion object {
        const val TICKS = 5
        val LENGTHS = listOf(30, 60, 90)

        fun restore(saved: List<String>): CharadesRound? {
            if (saved.size < 4) return null
            val seconds = saved[1].toIntOrNull() ?: return null
            val remaining = saved[2].toLongOrNull() ?: return null
            val played = saved.drop(4).mapNotNull { s -> s.drop(1).takeIf { it.isNotEmpty() }?.let { Played(it, s[0] == '+') } }
            return CharadesRound(saved[0], seconds, remaining.coerceIn(0, seconds * 1000L), played, saved[3].ifEmpty { null })
        }
    }
}
