package com.toolbox.app

import com.toolbox.app.tools.calculator.CalculatorEngine
import com.toolbox.app.tools.calculator.CalculatorEngine.DIVIDE
import com.toolbox.app.tools.calculator.CalculatorEngine.MINUS
import com.toolbox.app.tools.calculator.CalculatorEngine.PLUS
import com.toolbox.app.tools.calculator.CalculatorEngine.TIMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

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
    }

    @Test fun percentAfterPlusOrMinusIsAShare() {
        assertEquals("240", result("200${PLUS}20%"))
        assertEquals("180", result("200${MINUS}10%"))
        assertEquals("40", result("200${TIMES}20%"))
        assertEquals("280", result("200${PLUS}20%${TIMES}2")) // 20% of 200, doubled
        assertEquals("${MINUS}220", result("${MINUS}200${PLUS}10%"))
        assertEquals("0.2", result("20%"))
        assertEquals("15", result("10${PLUS}5${PLUS}0%${PLUS}0")) // 0% of 15 adds nothing
    }

    @Test fun negativeAfterAnOperatorGoesInABracket() {
        assertEquals("5$MINUS($MINUS" + "3", type("5${MINUS}3n"))
        assertEquals("8", result("5${MINUS}3n"))
        assertEquals("5${MINUS}3", type("5${MINUS}3nn"))
        assertEquals("3$PLUS($MINUS" + "4", type("3${PLUS}4n"))
        assertEquals("5$TIMES($MINUS", type("5${TIMES}n"))
        assertEquals("${MINUS}15", result("5${TIMES}n3"))
        assertEquals("($MINUS" + "2", type("(2n"))
        assertEquals("(2${PLUS}3)", type("(2${PLUS}3(n")) // nothing to flip after ")"
    }

    @Test fun roundsHalfUp() {
        assertEquals("0.123456789013", CalculatorEngine.format(BigDecimal("0.1234567890125")))
        assertEquals("2.5", CalculatorEngine.format(BigDecimal("2.5")))
        assertEquals("1", CalculatorEngine.format(BigDecimal("0.9999999999995")))
    }

    @Test fun equalsWaitsForAnUnfinishedSum() {
        assertTrue(CalculatorEngine.isUnfinished(type("5$PLUS")))
        assertTrue(CalculatorEngine.isUnfinished(type("2$TIMES(")))
        assertTrue(CalculatorEngine.isUnfinished(""))
        assertFalse(CalculatorEngine.isUnfinished(type("5$PLUS(3")))
        assertFalse(CalculatorEngine.isUnfinished(type("5.")))
        assertFalse(CalculatorEngine.isUnfinished(type("5%")))
    }

    @Test fun longAnswersUseAnExponentAndKeepEveryDigit() {
        val big = CalculatorEngine.evaluate(type("123456789${TIMES}123456789"))!!
        assertEquals(BigDecimal("15241578750190521"), big)
        val shown = CalculatorEngine.format(big)
        assertEquals("1.52415787502e16", shown)
        // Carrying on from the answer uses its full value, not the 12 digits on screen.
        assertEquals(BigDecimal("15241578750190520"), CalculatorEngine.evaluate("$shown${MINUS}1", big))
        // Without the answer, the shown text still works as a number.
        assertEquals("1.52415787502e16", CalculatorEngine.evaluate(shown)?.let(CalculatorEngine::format))

        // These used to show "0" after "=".
        assertEquals("9.9999997e23", result("99999999${TIMES}99999999${TIMES}99999999"))
        val tiny = CalculatorEngine.evaluate(type("1${DIVIDE}7000000000000"))!!
        assertEquals("1.42857142857e${MINUS}13", CalculatorEngine.format(tiny))
        assertEquals("1.42857142857e${MINUS}12", CalculatorEngine.evaluate("${CalculatorEngine.format(tiny)}${TIMES}10")?.let(CalculatorEngine::format))
    }

    @Test fun carryingOnFromAnAnswerIsExact() {
        val third = CalculatorEngine.evaluate(type("1${DIVIDE}3"))!!
        val shown = CalculatorEngine.format(third)
        assertEquals("1", CalculatorEngine.evaluate("$shown${TIMES}3", third)?.let(CalculatorEngine::format))
        assertEquals("1", CalculatorEngine.preview("$shown${TIMES}3", third)?.let(CalculatorEngine::format))
        assertEquals("0.25", CalculatorEngine.evaluate("$shown%${TIMES}75", third)?.let(CalculatorEngine::format))
        // Typing more digits onto the answer makes it a different number.
        assertEquals("0.3333333333331", CalculatorEngine.evaluate("${shown}1", third)?.toPlainString())
        val negative = third.negate()
        assertEquals("1", CalculatorEngine.evaluate("${CalculatorEngine.format(negative)}${TIMES}${MINUS}3", negative)?.let(CalculatorEngine::format))
    }

    @Test fun exponentNumbersEditSafely() {
        assertEquals("1.5e30", CalculatorEngine.point("1.5e30"))
        assertEquals("${MINUS}1.5e30", CalculatorEngine.negate("1.5e30"))
        assertEquals("2$PLUS($MINUS" + "1e${MINUS}9", CalculatorEngine.negate("2${PLUS}1e${MINUS}9"))
        assertEquals("1e", CalculatorEngine.operator("1e", PLUS)) // half-deleted exponent: finish or delete it first
        assertEquals("1e$MINUS", CalculatorEngine.bracket("1e$MINUS"))
        assertEquals("1e5", CalculatorEngine.digit("1e", '5'))
        assertNull(CalculatorEngine.evaluate("1e"))
        assertEquals("3", CalculatorEngine.preview("2${PLUS}1e")?.let(CalculatorEngine::format)) // like a trailing operator
        assertEquals("2000", CalculatorEngine.evaluate("2e3")?.let(CalculatorEngine::format))
    }

    @Test fun lengthLimit() {
        val full = "1".repeat(64)
        assertEquals(full, CalculatorEngine.percent(full))
        assertEquals(full, CalculatorEngine.digit(full, '2'))
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
