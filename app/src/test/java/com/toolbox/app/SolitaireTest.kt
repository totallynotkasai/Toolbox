package com.toolbox.app

import com.toolbox.app.games.solitaire.Card
import com.toolbox.app.games.solitaire.Klondike
import com.toolbox.app.games.solitaire.Pile
import com.toolbox.app.games.solitaire.Step
import com.toolbox.app.games.solitaire.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SolitaireTest {
    /** A card from its short name: "AS", "10H", "QD". */
    private fun card(s: String): Card {
        val suit = Suit.entries.first { it.name.first() == s.last() }
        val rank = when (val r = s.dropLast(1)) { "A" -> 1; "J" -> 11; "Q" -> 12; "K" -> 13; else -> r.toInt() }
        return Card.of(rank, suit)
    }

    private fun cards(s: String) = s.split(' ').filter { it.isNotEmpty() }.map(::card)

    /** The ace up to [rank] of [suit], as a foundation holds them. */
    private fun upTo(suit: Char, rank: Int) = (1..rank).joinToString(" ") { r ->
        (when (r) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$r" }) + suit
    }

    /** A game laid out by hand. Each pile lists its cards bottom first; [faceDown] counts from each column's bottom. */
    private fun table(
        stock: String = "",
        waste: String = "",
        foundations: List<String> = List(4) { "" },
        columns: List<String> = List(7) { "" },
        faceDown: IntArray = IntArray(7),
        draw: Int = 1,
    ) = Klondike.arrange(draw, listOf(cards(stock), cards(waste)) + foundations.map(::cards) + columns.map(::cards), faceDown)

    private fun cols(vararg c: String) = c.toList() + List(7 - c.size) { "" }

    /** Where the cards lie, without the steps or time. */
    private fun Klondike.layout() = copy().save()

    /** Every move that can be made now, the stock's turn first. */
    private fun Klondike.legalMoves(): List<Triple<Pile, Int, Pile>> = Pile.all.flatMap { from ->
        cards(from).indices.flatMap { i -> destinations(from, i).map { Triple(from, i, it) } }
    }

    @Test fun theDealHasSevenColumnsAndTheRestInTheStock() {
        val game = Klondike.deal(1, Random(7))
        for (c in 0 until 7) {
            assertEquals(c + 1, game.cards(Pile.column(c)).size)
            assertEquals(c, game.faceDown(c))
            assertTrue(game.isFaceUp(Pile.column(c), c))
        }
        assertEquals(24, game.cards(Pile.Stock).size)
        assertTrue(game.cards(Pile.Waste).isEmpty())
        assertTrue(Pile.foundations.all { game.cards(it).isEmpty() })
        assertEquals(52, Pile.all.flatMap { game.cards(it) }.toSet().size)
        // A seeded shuffle deals the same game again, and another seed doesn't.
        assertEquals(game.save(), Klondike.deal(1, Random(7)).save())
        assertNotEquals(game.save(), Klondike.deal(1, Random(8)).save())
    }

    @Test fun columnsBuildDownInAlternatingColours() {
        val game = table(columns = cols("8S", "7H", "7C", "6H", "", "KH", "QS JD"))
        assertTrue(game.canMove(Pile.column(1), 0, Pile.column(0))) // red 7 on a black 8
        assertFalse(game.canMove(Pile.column(2), 0, Pile.column(0))) // black on black
        assertFalse(game.canMove(Pile.column(3), 0, Pile.column(0))) // a 6 on an 8
        assertTrue(game.canMove(Pile.column(5), 0, Pile.column(4))) // a king into an empty column
        assertFalse(game.canMove(Pile.column(1), 0, Pile.column(4))) // but nothing else
        // A run moves as one.
        val step = game.move(Pile.column(6), 0, Pile.column(5))!!
        assertEquals(2, step.count)
        assertEquals(cards("KH QS JD"), game.cards(Pile.column(5)))
        assertTrue(game.cards(Pile.column(6)).isEmpty())
    }

    @Test fun foundationsBuildUpBySuitFromTheAce() {
        val game = table(waste = "2D", foundations = listOf("AH", "", "", ""), columns = cols("2H", "3H", "AS", "9C 2S"))
        assertTrue(game.canMove(Pile.column(0), 0, Pile.foundation(0)))
        assertFalse(game.canMove(Pile.Waste, 0, Pile.foundation(0))) // another suit
        assertFalse(game.canMove(Pile.column(1), 0, Pile.foundation(0))) // skips a rank
        assertTrue(game.canMove(Pile.column(2), 0, Pile.foundation(2))) // an ace onto any empty foundation
        assertFalse(game.canMove(Pile.column(3), 1, Pile.foundation(1))) // a 2 onto an empty one
        // Only one card at a time goes up, and a foundation card can come back down, but not across.
        val game2 = table(foundations = listOf("AH 2H", "", "", ""), columns = cols("3S", "3H 2C"))
        assertFalse(game2.canMove(Pile.column(1), 0, Pile.foundation(0)))
        assertTrue(game2.canMove(Pile.foundation(0), 1, Pile.column(0)))
        assertFalse(game2.canMove(Pile.foundation(0), 0, Pile.column(0))) // only the top card
        assertFalse(game2.canMove(Pile.foundation(0), 1, Pile.foundation(1)))
    }

    @Test fun movingTheLastFaceUpCardTurnsTheNextOneOver() {
        val game = table(columns = cols("4C 9D 8S", "9H"), faceDown = intArrayOf(2, 0, 0, 0, 0, 0, 0))
        assertFalse(game.canLift(Pile.column(0), 1)) // face down
        val step = game.move(Pile.column(0), 2, Pile.column(1))!!
        assertTrue(step.turned)
        assertEquals(1, game.faceDown(0))
        assertTrue(game.isFaceUp(Pile.column(0), 1))
        game.undo()
        assertEquals(2, game.faceDown(0))
        assertEquals(cards("4C 9D 8S"), game.cards(Pile.column(0)))
    }

    @Test fun theStockTurnsOneOrThreeAtATimeAndStartsAgain() {
        val three = Klondike.deal(3, Random(3))
        val stock = three.cards(Pile.Stock).toList()
        val step = three.turnStock()!!
        assertEquals(Step(Step.Kind.Draw, Pile.Stock, Pile.Waste, 3), step)
        // Turned one by one, so the third card down is on top of the waste.
        assertEquals(stock.takeLast(3).reversed(), three.cards(Pile.Waste))
        repeat(7) { three.turnStock() }
        assertTrue(three.cards(Pile.Stock).isEmpty())
        assertEquals(Step.Kind.Recycle, three.turnStock()!!.kind)
        assertEquals(stock, three.cards(Pile.Stock))
        assertTrue(three.cards(Pile.Waste).isEmpty())
        // Draw 1 turns 24 times before starting again, and the last few cards of a Draw 3 stock come out together.
        val one = Klondike.deal(1, Random(3))
        repeat(24) { assertEquals(1, one.turnStock()!!.count) }
        assertEquals(Step.Kind.Recycle, one.turnStock()!!.kind)
        val short = table(stock = "2C 3C", draw = 3)
        assertEquals(2, short.turnStock()!!.count)
        assertEquals(Step.Kind.Recycle, short.turnStock()!!.kind)
        assertNull(table().turnStock())
    }

    @Test fun aTapPicksAFoundationThenTheNextColumnRound() {
        val game = table(foundations = listOf("AH", "", "", ""), columns = cols("7H", "", "8S", "2H", "", "8C", "4D KS"), faceDown = intArrayOf(0, 0, 0, 0, 0, 0, 1))
        assertEquals(Pile.foundation(0), game.bestDestination(Pile.column(3), 0))
        assertEquals(Pile.column(2), game.bestDestination(Pile.column(0), 0))
        game.move(Pile.column(0), 0, Pile.column(2))
        // Tapped again, it goes on to the next black 8, wrapping round.
        assertEquals(Pile.column(5), game.bestDestination(Pile.column(2), 1))
        game.move(Pile.column(2), 1, Pile.column(5))
        assertEquals(Pile.column(2), game.bestDestination(Pile.column(5), 1))
        // A king on a face-down card goes to the next empty column (wrapping round from the last); one with nothing
        // under it has nowhere useful to go.
        assertEquals(Pile.column(0), game.bestDestination(Pile.column(6), 1))
        game.move(Pile.column(6), 1, Pile.column(0))
        assertNull(game.bestDestination(Pile.column(0), 0))
        assertTrue(game.destinations(Pile.column(0), 0).isEmpty())
    }

    @Test fun undoTakesEveryStepBackExactly() {
        val random = Random(11)
        val game = Klondike.deal(3, Random(11))
        val layouts = mutableListOf(game.layout())
        repeat(300) {
            val moves = game.legalMoves()
            if (moves.isEmpty() || random.nextInt(4) == 0) game.turnStock() ?: return@repeat
            else moves.random(random).let { (from, i, to) -> game.move(from, i, to)!! }
            layouts += game.layout()
        }
        assertEquals(layouts.size - 1, game.moves)
        while (game.canUndo) {
            layouts.removeAt(layouts.lastIndex)
            game.undo()
            assertEquals(layouts.last(), game.layout())
        }
        assertEquals(Klondike.deal(3, Random(11)).layout(), game.layout())
        assertNull(game.undo())
    }

    @Test fun onceEverythingIsFaceUpTheGameFinishesItself() {
        val foundations = listOf(upTo('C', 9), upTo('D', 9), upTo('H', 9), upTo('S', 9))
        val game = table(foundations = foundations, columns = cols("KS QH JS 10H", "KH QS JH 10S", "KC QD JC 10D", "KD QC JD 10C"))
        assertTrue(game.canAutoFinish)
        var steps = 0
        while (game.autoStep() != null) steps++
        assertEquals(16, steps)
        assertTrue(game.won)
        assertFalse(game.canAutoFinish)
        // Not while a card is face down or the stock has cards.
        assertFalse(table(foundations = foundations, columns = cols("KS QH JS 10H", "KH QS JH 10S", "KC QD JC 10D", "KD QC JD 10C"), faceDown = intArrayOf(1, 0, 0, 0, 0, 0, 0)).canAutoFinish)
        assertFalse(table(stock = "10C", foundations = foundations, columns = cols("KS QH JS 10H", "KH QS JH 10S", "KC QD JC 10D", "KD QC JD")).canAutoFinish)
    }

    @Test fun noMovesLeftOnlyWhenNothingUsefulCanEverHappen() {
        // Two red 5s with nowhere to go, on face-down aces.
        assertTrue(table(columns = cols("AS 5H", "AH 5D"), faceDown = intArrayOf(1, 1, 0, 0, 0, 0, 0)).isStuck())
        // Moving the 8 of hearts across uncovers the 9 of spades for its foundation: nothing useful at first, then something.
        val spades = listOf("", "", "", upTo('S', 8))
        assertFalse(table(foundations = spades, columns = cols("AC 9S 8H", "AD 9C"), faceDown = intArrayOf(1, 1, 0, 0, 0, 0, 0)).isStuck())
        // The 8 of hearts is in the stock under three cards: Draw 1 reaches it, but Draw 3 always turns it under the 2 of clubs.
        val stuckish = { draw: Int -> table(stock = "2C 8H 3C", columns = cols("AD 9S"), faceDown = intArrayOf(1, 0, 0, 0, 0, 0, 0), draw = draw) }
        assertFalse(stuckish(1).isStuck())
        assertTrue(stuckish(3).isStuck())
        // A fresh deal usually has something to do.
        assertFalse(Klondike.deal(1, Random(2)).isStuck())
    }

    @Test fun aSavedGameComesBackWithItsStepsAndTime() {
        val game = Klondike.deal(3, Random(5))
        val random = Random(5)
        repeat(60) {
            val moves = game.legalMoves()
            if (moves.isEmpty() || random.nextBoolean()) game.turnStock() else moves.random(random).let { (f, i, t) -> game.move(f, i, t) }
        }
        game.elapsedMs = 123_456
        game.counted = true
        val back = Klondike.restore(game.save())!!
        assertEquals(game.save(), back.save())
        assertEquals(3, back.draw)
        assertEquals(123_456, back.elapsedMs)
        assertTrue(back.counted)
        assertEquals(game.moves, back.moves)
        while (game.canUndo) {
            game.undo()
            back.undo()
            assertEquals(game.layout(), back.layout())
        }
        // Anything that isn't a whole game is refused, so a new deal starts instead.
        assertNull(Klondike.restore(""))
        assertNull(Klondike.restore("rubbish"))
        assertNull(Klondike.restore(game.save().replaceFirst("a", "")))
        assertNull(Klondike.restore(game.save().replaceFirst("1;3;", "1;2;")))
    }

    @Test fun stepsPackIntoOneIntAndCardsHaveNames() {
        for (step in listOf(
            Step(Step.Kind.Draw, Pile.Stock, Pile.Waste, 3),
            Step(Step.Kind.Recycle, Pile.Waste, Pile.Stock, 24),
            Step(Step.Kind.Move, Pile.column(6), Pile.foundation(3), 1, turned = true),
            Step(Step.Kind.Move, Pile.Waste, Pile.column(0), 13),
        )) assertEquals(step, Step.unpack(step.pack()))
        assertEquals("Queen of hearts", card("QH").name)
        assertEquals("10", card("10S").label)
        for (id in 0 until 52) assertEquals(Card(id), Card.fromLetter(Card(id).letter))
    }
}
