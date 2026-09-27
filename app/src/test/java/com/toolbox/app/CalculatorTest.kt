package com.toolbox.app

import com.toolbox.app.tools.calculator.CalculatorEngine
import com.toolbox.app.tools.calculator.CalculatorEngine.DIVIDE
import com.toolbox.app.tools.calculator.CalculatorEngine.MINUS
import com.toolbox.app.tools.calculator.CalculatorEngine.PLUS
import com.toolbox.app.tools.calculator.CalculatorEngine.TIMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    /** Types [keys] as if on the keypad: digits, ".", operators, "%", "(" for the bracket key and "n" for ±. */
    private fun type(keys: String): String = keys.fold("") { e, k ->
        when (k) {
            in '0'..'9' -> CalculatorEngine.digit(e, k)
            '.' -> CalculatorEngine.point(e)
            '%' -> CalculatorEngine.percent(e)
            '(' -> CalculatorEngine.bracket(e)
            'n' -> CalculatorEngine.negate(e)
            else -> CalculatorEngine.operator(e, k)
        }
    }

    private fun result(keys: String) = CalculatorEngine.evaluate(type(keys))?.let(CalculatorEngine::format)

    @Test fun precedenceAndDecimals() {
        assertEquals("14", result("2${PLUS}3${TIMES}4"))
        assertEquals("0.3", result("0.1${PLUS}0.2"))
        assertEquals("2.5", result("5${DIVIDE}2"))
        assertEquals("0.333333333333", result("1${DIVIDE}3"))
    }

    @Test fun bracketsOpenCloseAndAutoClose() {
        assertEquals("(2${PLUS}3)${TIMES}4", type("(2${PLUS}3(${TIMES}4"))
        assertEquals("20", result("(2${PLUS}3(${TIMES}4"))
        assertEquals("10", result("2(5")) // "2×(5" with the bracket left open
    }

    @Test fun operatorsReplaceAndMinusGoesNegative() {
        assertEquals("5$TIMES", type("5${PLUS}$TIMES"))
        assertEquals("5$TIMES$MINUS", type("5$TIMES$MINUS"))
        assertEquals("5$PLUS", type("5$TIMES$MINUS$PLUS"))
        assertEquals("${MINUS}15", result("5$TIMES${MINUS}3"))
        assertEquals("$MINUS", type("$MINUS"))
        assertEquals("", type("$TIMES"))
    }

    @Test fun percentAndNegate() {
        assertEquals("25", result("50%${TIMES}50"))
        assertEquals("${MINUS}7", result("7n"))
        assertEquals("7", type("7nn"))
        assertEquals("3$PLUS${MINUS}4", type("3${PLUS}4n"))
    }

    @Test fun inputTidying() {
        assertEquals("5", type("05"))
        assertEquals("0.5", type(".5"))
        assertEquals("1.5", type("1.5."))
        assertEquals("3${TIMES}0.", type("3${TIMES}."))
    }

    @Test fun errorsAndPreview() {
        assertNull(CalculatorEngine.evaluate(type("1${DIVIDE}0")))
        assertNull(CalculatorEngine.evaluate(""))
        assertEquals("15", CalculatorEngine.preview("12${PLUS}3$TIMES")?.let(CalculatorEngine::format))
        assertNull(CalculatorEngine.preview("12")) // nothing to work out yet
        assertNull(CalculatorEngine.preview("${MINUS}12"))
    }

    @Test fun formatsLargeAndSmall() {
        assertEquals("1e15", result("1000000${TIMES}1000000000"))
        assertEquals("0.001", result("1${DIVIDE}1000"))
    }
}

class FavouritesTest {
    @Test fun toggleAddsAndRemoves() {
        val one = Favourites.toggle("", "coin")
        assertEquals(setOf("coin"), Favourites.parse(one))
        val two = Favourites.toggle(one, "snake")
        assertEquals(setOf("coin", "snake"), Favourites.parse(two))
        assertEquals(setOf("snake"), Favourites.parse(Favourites.toggle(two, "coin")))
    }

    @Test fun parseIgnoresBlanksAndRepeats() {
        assertEquals(setOf("a", "b"), Favourites.parse(" a,,b,a, "))
    }

    @Test fun everyToolHasAUniqueRouteAndIsSortedByTitle() {
        assertEquals(Tool.entries.size, Tool.entries.map { it.route }.toSet().size)
        val titles = Tool.sorted.map { it.title.lowercase() }
        assertEquals(titles.sorted(), titles)
    }
}
