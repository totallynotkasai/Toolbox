package com.toolbox.app

import com.toolbox.app.tools.calculator.CalculatorEngine
import com.toolbox.app.tools.calculator.CalculatorEngine.DIVIDE
import com.toolbox.app.tools.calculator.CalculatorEngine.MINUS
import com.toolbox.app.tools.calculator.CalculatorEngine.PLUS
import com.toolbox.app.tools.calculator.CalculatorEngine.TIMES
import com.toolbox.app.tools.calculator.CalculatorHistory
import com.toolbox.app.tools.calculator.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** The Calculator's memory keys and its history of finished sums. */
class CalculatorMemoryTest {
    private fun operand(expr: String, answer: BigDecimal? = null, showingResult: Boolean = false) =
        CalculatorEngine.memoryOperand(expr, answer, showingResult)

    @Test fun memoryTakesTheAnswerOrTheSumSoFar() {
        val third = CalculatorEngine.evaluate("1${DIVIDE}3")!!
        // After "=", every digit of the answer, not the 12 shown.
        assertEquals(third, operand(CalculatorEngine.format(third), third, showingResult = true))
        assertEquals(BigDecimal("15"), operand("12${PLUS}3$TIMES")) // unfinished: like the preview
        assertEquals(BigDecimal("5"), operand("5")) // a lone number counts
        assertEquals(BigDecimal("-7"), operand("${MINUS}7."))
        assertEquals(BigDecimal("1.5e30"), operand("1.5e30"))
        assertEquals(BigDecimal("1e-9"), operand("1e${MINUS}9"))
        assertNull(operand("5${DIVIDE}0")) // an error
        assertNull(operand(""))
        assertNull(operand("($MINUS"))
    }

    @Test fun memoryAddSubtractAndClear() {
        var m = CalculatorEngine.memoryClear()
        m = CalculatorEngine.memoryAdd(m, BigDecimal("0.1"))
        m = CalculatorEngine.memoryAdd(m, BigDecimal("0.2"))
        assertEquals(0, BigDecimal("0.3").compareTo(m))
        m = CalculatorEngine.memorySubtract(m, BigDecimal("2"))
        assertEquals(0, BigDecimal("-1.7").compareTo(m))
        m = CalculatorEngine.memoryAdd(m, BigDecimal("1.5e30")) // exponent numbers work too
        assertEquals(0, BigDecimal("1.5e30").compareTo(m)) // to 20 significant digits
        assertEquals(m, CalculatorEngine.memoryAdd(m, null)) // nothing to add on an error
        assertEquals(m, CalculatorEngine.memorySubtract(m, null))
        assertEquals(0, CalculatorEngine.memoryClear().signum())
    }

    @Test fun memorySavesExactly() {
        val third = CalculatorEngine.evaluate("1${DIVIDE}3")!!
        listOf(third, BigDecimal("-2.5e-30"), BigDecimal("1.5e30"), BigDecimal("0.1")).forEach {
            assertEquals(it, BigDecimal(it.toString()))
        }
    }

    @Test fun recallGoesInAsANumber() {
        val five = BigDecimal("5")
        val minusFive = BigDecimal("-5")
        fun recall(expr: String, value: BigDecimal) = CalculatorEngine.memoryRecall(expr, value)
        assertEquals("5", recall("", five))
        assertEquals("${MINUS}5", recall("", minusFive))
        assertEquals("12${PLUS}5", recall("12$PLUS", five))
        assertEquals("12$PLUS($MINUS" + "5", recall("12$PLUS", minusFive)) // in a bracket, like ±
        assertEquals("($MINUS" + "5", recall("(", minusFive))
        // In place of the number being typed, and its minus sign.
        assertEquals("12${PLUS}5", recall("12${PLUS}3.4", five))
        assertEquals("12${PLUS}5", recall("12$PLUS($MINUS" + "3", five))
        assertEquals("5${TIMES}5", recall("5$TIMES${MINUS}3", five))
        assertEquals("5", recall("${MINUS}3", five))
        assertEquals("5", recall("1.5e${MINUS}9", five))
        // With an implied × after ")" or "%".
        assertEquals("(2)${TIMES}5", recall("(2)", five))
        assertEquals("50%$TIMES($MINUS" + "5", recall("50%", minusFive))
        // Shown as the display shows a number.
        assertEquals("0.333333333333", recall("", CalculatorEngine.evaluate("1${DIVIDE}3")!!))
        assertEquals("1.5e30", recall("", BigDecimal("1.5e30")))
        // Too long to fit: left as it was.
        val full = "1$PLUS".repeat(31) + "1"
        assertEquals(full, recall(full, BigDecimal("123")))
    }

    @Test fun onlyRealSumsCount() {
        assertTrue(CalculatorEngine.isSum("2${PLUS}2"))
        assertTrue(CalculatorEngine.isSum("50%"))
        assertTrue(CalculatorEngine.isSum("(5"))
        assertFalse(CalculatorEngine.isSum("5"))
        assertFalse(CalculatorEngine.isSum("${MINUS}5"))
        assertFalse(CalculatorEngine.isSum("1.5e${MINUS}9"))
        assertNull(CalculatorEngine.preview("1e${MINUS}9")) // no "= 1e−9" under a lone number
    }

    @Test fun historyRoundTripsEveryOperatorAndExponent() {
        val third = CalculatorEngine.evaluate("1${DIVIDE}3")!!
        val entries = listOf(
            HistoryEntry("12${PLUS}3${TIMES}4${MINUS}6${DIVIDE}2", BigDecimal("21")),
            HistoryEntry("200${PLUS}20%", BigDecimal("240")),
            HistoryEntry("(2${PLUS}3)$TIMES($MINUS" + "4", BigDecimal("-20")),
            HistoryEntry("1.5e30${TIMES}2e${MINUS}9", BigDecimal("3.0E+21")),
            HistoryEntry("1${DIVIDE}7000000000000", BigDecimal("1.4285714285714285714E-13")),
            HistoryEntry("0.333333333333${TIMES}3", BigDecimal.ONE, base = third),
        )
        assertEquals(entries, CalculatorHistory.decode(CalculatorHistory.encode(entries)))
        assertEquals(emptyList<HistoryEntry>(), CalculatorHistory.decode(""))
    }

    @Test fun historyKeepsTheNewest50() {
        var list = emptyList<HistoryEntry>()
        for (n in 1..60) list = CalculatorHistory.add(list, HistoryEntry("$n${PLUS}1", BigDecimal(n + 1)))
        assertEquals(CalculatorHistory.CAP, list.size)
        assertEquals("60${PLUS}1", list.first().sum)
        assertEquals("11${PLUS}1", list.last().sum)
        // The same sum twice in a row is kept once.
        assertEquals(list, CalculatorHistory.add(list, HistoryEntry("60${PLUS}1", BigDecimal(61))))
    }

    @Test fun historySkipsLinesThatDontRead() {
        val text = "2${PLUS}2=4\nrubbish\n=5\n3${TIMES}3=nine\n1${DIVIDE}4=0.25"
        assertEquals(listOf(HistoryEntry("2${PLUS}2", BigDecimal("4")), HistoryEntry("1${DIVIDE}4", BigDecimal("0.25"))), CalculatorHistory.decode(text))
    }

    @Test fun reusingAnEntryWorksItOutTheSameWay() {
        val third = CalculatorEngine.evaluate("1${DIVIDE}3")!!
        val sum = "${CalculatorEngine.format(third)}${TIMES}3"
        val entry = HistoryEntry(sum, CalculatorEngine.evaluate(sum, third)!!, base = third)
        val back = CalculatorHistory.decode(CalculatorHistory.encode(listOf(entry))).single()
        // "Edit sum" brings back the sum with the answer it carried on from, so = gives 1 again, not 0.999999999999.
        assertEquals("1", CalculatorEngine.evaluate(back.sum, back.base)?.let(CalculatorEngine::format))
        // Using the answer keeps every digit.
        assertEquals(entry.answer, back.answer)
    }

    @Test fun spokenSums() {
        assertEquals("12 plus 3 times minus 4", CalculatorEngine.spoken("12${PLUS}3$TIMES${MINUS}4"))
        assertEquals("open bracket 2 divided by 5 close bracket 50 percent", CalculatorEngine.spoken("(2${DIVIDE}5)50%"))
        assertEquals("1.5 times 10 to the power minus 9", CalculatorEngine.spoken("1.5e${MINUS}9"))
    }
}
