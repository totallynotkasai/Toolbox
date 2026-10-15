package com.toolbox.app

import android.view.Surface
import com.toolbox.app.games.charades.BuiltInDeck
import com.toolbox.app.games.charades.CharadesRound
import com.toolbox.app.games.charades.ClockEvent
import com.toolbox.app.games.charades.CustomDecks
import com.toolbox.app.games.charades.Deck
import com.toolbox.app.games.charades.MAX_CARDS
import com.toolbox.app.games.charades.MAX_CARD_LENGTH
import com.toolbox.app.games.charades.Played
import com.toolbox.app.games.charades.Tilt
import com.toolbox.app.games.charades.TiltDetector
import com.toolbox.app.games.charades.cardsNote
import com.toolbox.app.games.charades.cleanLine
import com.toolbox.app.games.charades.countRepeats
import com.toolbox.app.games.charades.dealCard
import com.toolbox.app.games.charades.parseCards
import com.toolbox.app.games.charades.secondsLeft
import com.toolbox.app.ui.common.toDisplayAxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class CharadesTest {
    private val g = 9.81f

    /**
     * A gravity reading for a phone held sideways with its screen facing [degrees] above straight out (negative is
     * towards the floor). The screen's up is the phone's +x when it's turned one way ([Surface.ROTATION_90]) and -x
     * the other ([Surface.ROTATION_270]).
     */
    private fun reading(degrees: Float, rotation: Int = Surface.ROTATION_90): FloatArray {
        val a = Math.toRadians(degrees.toDouble())
        val side = if (rotation == Surface.ROTATION_90) 1f else -1f
        return floatArrayOf(side * g * cos(a).toFloat(), 0f, g * sin(a).toFloat())
    }

    private fun TiltDetector.feed(degrees: Float, rotation: Int = Surface.ROTATION_90): Tilt? =
        reading(degrees, rotation).let { update(it[0], it[1], it[2]) }

    private fun heldUpright(r: FloatArray, rotation: Int): Boolean =
        toDisplayAxes(r[0], r[1], rotation).let { (x, y) -> TiltDetector.heldUpright(x, y, r[2]) }

    @Test fun aTiltCountsOncePastTheThreshold() {
        val tilt = TiltDetector()
        assertNull(tilt.feed(0f))
        assertTrue(tilt.armed)
        assertNull(tilt.feed(-(TiltDetector.TRIGGER_DEGREES - 5)))
        assertNull(tilt.feed(TiltDetector.TRIGGER_DEGREES - 5))
        assertEquals(Tilt.Down, tilt.feed(-(TiltDetector.TRIGGER_DEGREES + 5)))
    }

    @Test fun theNextTiltWaitsForThePhoneToComeBackLevel() {
        val tilt = TiltDetector()
        tilt.feed(0f)
        assertEquals(Tilt.Down, tilt.feed(-60f))
        assertNull(tilt.feed(-70f)) // still tipped
        assertNull(tilt.feed(-30f)) // on the way back, but not level yet
        assertNull(tilt.feed(60f)) // straight over to the other way doesn't count either
        assertFalse(tilt.armed)
        assertNull(tilt.feed(TiltDetector.LEVEL_DEGREES - 5))
        assertTrue(tilt.armed)
        assertEquals(Tilt.Up, tilt.feed(60f))
    }

    @Test fun aPhoneAlreadyTippedWhenTheRoundStartsDoesntCount() {
        val tilt = TiltDetector()
        assertNull(tilt.feed(-80f))
        assertFalse(tilt.armed)
        tilt.feed(5f)
        assertEquals(Tilt.Down, tilt.feed(-80f))
    }

    @Test fun bothWaysRoundReadTheSame() {
        for (rotation in listOf(Surface.ROTATION_90, Surface.ROTATION_270)) {
            val tilt = TiltDetector()
            tilt.feed(0f, rotation)
            assertEquals(Tilt.Down, tilt.feed(-55f, rotation))
            tilt.feed(0f, rotation)
            assertEquals(Tilt.Up, tilt.feed(55f, rotation))
            // Held upright on a forehead, either way round, it's ready to count in; tipped or still in portrait, not.
            assertTrue(heldUpright(reading(10f, rotation), rotation))
            assertFalse(heldUpright(reading(40f, rotation), rotation))
            assertFalse(heldUpright(floatArrayOf(0f, g, 0f), rotation))
        }
    }

    @Test fun aWeakReadingIsIgnored() {
        assertNull(TiltDetector.elevation(1f, 0f, -1f))
        val tilt = TiltDetector()
        assertNull(tilt.update(0.5f, 0f, 0f))
        assertFalse(tilt.armed)
        assertFalse(TiltDetector.heldUpright(0f, 1f, 0f))
    }

    @Test fun cardsAreReadOnePerLine() {
        val text = "  Elephant \n\n\tGiraffe\r\nelephant\nPolar   bear\n   \nGIRAFFE\nA\u001Fb\n" + "x".repeat(50)
        assertEquals(listOf("Elephant", "Giraffe", "Polar bear", "A b", "x".repeat(MAX_CARD_LENGTH)), parseCards(text))
        assertEquals(2, countRepeats(text))
        assertEquals(emptyList<String>(), parseCards("\n \n"))
        assertEquals(MAX_CARDS, parseCards((1..MAX_CARDS + 50).joinToString("\n") { "Card $it" }).size)
    }

    @Test fun theEditorSaysWhatSavingWillDo() {
        assertEquals("No cards yet", cardsNote(""))
        assertEquals("1 card", cardsNote("Cat\n\n"))
        assertEquals("2 cards · 1 repeat left out", cardsNote("Cat\nDog\ncat"))
        assertEquals("1 card · cards cut to $MAX_CARD_LENGTH characters", cardsNote("y".repeat(MAX_CARD_LENGTH + 1)))
        assertEquals("1 second left", secondsLeft(1))
        assertEquals("42 seconds left", secondsLeft(42))
    }

    @Test fun aDeckDealsEveryCardOnceBeforeAnyComesRound() {
        val cards = (1..20).map { "Card $it" }
        val random = Random(7)
        var played = emptySet<String>()
        val dealt = mutableListOf<String>()
        repeat(cards.size) {
            val (card, now) = dealCard(cards, played, dealt, random)!!
            dealt += card
            played = now
        }
        assertEquals(cards.toSet(), dealt.toSet())
        // Run out, the deck starts again, keeping clear of the cards already shown this round.
        repeat(50) { seed ->
            val (card, now) = dealCard(cards, played, dealt.drop(1), Random(seed))!!
            assertEquals(dealt.first(), card)
            assertEquals(setOf(card), now)
        }
        // Every card has been shown this round, so the latest half of them are kept back and none comes straight back.
        val four = listOf("A", "B", "C", "D")
        repeat(50) { seed ->
            val (card, _) = dealCard(four, four.toSet(), listOf("B", "A", "C", "D"), Random(seed))!!
            assertTrue(card, card in setOf("A", "B"))
        }
        // A deck of one can only repeat.
        assertEquals("Solo" to setOf("Solo"), dealCard(listOf("Solo"), setOf("Solo"), listOf("Solo")))
        assertNull(dealCard(emptyList(), emptySet()))
    }

    @Test fun playedCardsSurviveBetweenVisitsAndForgetEditedOnes() {
        val played = setOf("Cat", "Polar bear", "Dog, sitting")
        assertEquals(played, CustomDecks.decodePlayed(CustomDecks.encodePlayed(played)))
        assertEquals(emptySet<String>(), CustomDecks.decodePlayed(""))
        // "Zebra" was taken out of the deck, so it's dropped; "A" is played, so "B" is next.
        repeat(10) { seed -> assertEquals("B" to setOf("A", "B"), dealCard(listOf("A", "B"), setOf("A", "Zebra"), random = Random(seed))) }
    }

    @Test fun customDecksRoundTrip() {
        val decks = listOf(
            Deck("custom1", "Party, words", listOf("Cat", "Dog, with a comma", "Mum's birthday")),
            Deck("custom3", "Empty", emptyList()),
        )
        assertEquals(decks, CustomDecks.decode(CustomDecks.encode(decks)))
        assertEquals(emptyList<Deck>(), CustomDecks.decode(""))
        assertEquals("custom4", CustomDecks.newId(decks))
        assertEquals("custom1", CustomDecks.newId(emptyList()))
        // Names and cards lose control characters before saving, so they can't break the separators.
        assertEquals("a b", cleanLine("a\u001Eb"))
    }

    @Test fun deckNamesDontClash() {
        assertEquals("My deck", CustomDecks.uniqueName("  ", emptyList()))
        assertEquals("Animals 2", CustomDecks.uniqueName("Animals", listOf("Animals")))
        assertEquals("animals 3", CustomDecks.uniqueName("animals", listOf("Animals", "Animals 2")))
        assertEquals("Holiday", CustomDecks.uniqueName(" Holiday ", listOf("Animals")))
    }

    @Test fun theLastFiveSecondsTickThenTimeIsUp() {
        var round = CharadesRound("animals", 30, current = "Cat")
        val events = mutableListOf<ClockEvent>()
        repeat(30 * 20 + 5) {
            val (next, happened) = round.advance(50)
            round = next
            events += happened
        }
        assertEquals((5 downTo 1).map { ClockEvent.Tick(it) } + ClockEvent.TimeUp, events)
        // The card showing at the end counts as passed.
        assertEquals(CharadesRound("animals", 30, 0, listOf(Played("Cat", false)), null), round)
        assertTrue(round.timeUp)
        // Ended early, the time left is kept, so the results can say it wasn't time that ended it.
        val early = CharadesRound("animals", 30, 20_000, current = "Dog").end()
        assertEquals(CharadesRound("animals", 30, 20_000, listOf(Played("Dog", false)), null), early)
        assertFalse(early.timeUp)
        // A long jump still ticks each second it passes.
        val (_, jump) = CharadesRound("animals", 30, remainingMs = 12_000).advance(10_000)
        assertEquals((5 downTo 2).map { ClockEvent.Tick(it) }, jump)
    }

    @Test fun answersAreKeptInOrder() {
        val round = CharadesRound("animals", 60, current = "Cat").answer(true, "Dog").answer(false, "Fish")
        assertEquals(listOf(Played("Cat", true), Played("Dog", false)), round.played)
        assertEquals("Fish", round.current)
        assertEquals(1, round.score)
        assertEquals(1, round.passed)
        assertEquals(listOf("Cat", "Dog", "Fish"), round.shown)
        assertTrue(round.started)
        assertFalse(CharadesRound("animals", 60).started)
    }

    @Test fun aRoundSurvivesSavedState() {
        val round = CharadesRound("custom2", 90, 12_345, listOf(Played("-Minus", true), Played("+Plus", false)), "Fish")
        assertEquals(round, CharadesRound.restore(round.save()))
        val fresh = CharadesRound("animals", 30)
        assertEquals(fresh, CharadesRound.restore(fresh.save()))
        assertNull(CharadesRound.restore(listOf("animals", "x", "0", "")))
    }

    @Test fun builtInDecksAreFullAndTidy() {
        assertEquals(BuiltInDeck.entries.size, BuiltInDeck.entries.map { it.id }.toSet().size)
        assertEquals(BuiltInDeck.entries.size, BuiltInDeck.entries.map { it.title }.toSet().size)
        for (deck in BuiltInDeck.entries) {
            val lines = File("src/main/res/raw/charades_${deck.id}.txt").readLines().filter { it.isNotBlank() }
            val cards = parseCards(lines.joinToString("\n"))
            // Nothing repeated or cut short, so the file is exactly the deck.
            assertEquals(deck.title, lines, cards)
            assertTrue("${deck.title} has ${cards.size} cards", cards.size in 60..80)
        }
    }
}
