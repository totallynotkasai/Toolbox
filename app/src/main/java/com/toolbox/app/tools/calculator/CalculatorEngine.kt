package com.toolbox.app.tools.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Keypad logic and arithmetic for the calculator, pure so it can be unit-tested.
 *
 * The expression is kept as the text the user sees, using the display symbols below. Arithmetic is done in
 * [BigDecimal] so that 0.1 + 0.2 is 0.3. Multiplication and division bind tighter than + and −, a minus can be
 * unary ("5×−3"), `%` divides the number before it by 100, and unclosed brackets are closed at the end.
 */
object CalculatorEngine {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    private val OPERATORS = setOf(PLUS, MINUS, TIMES, DIVIDE)
    private val MATH = MathContext(20, RoundingMode.HALF_EVEN)
    private const val MAX_LENGTH = 64

    /** Appends a digit, starting a fresh number after a closing bracket or % with an implied ×. */
    fun digit(expr: String, d: Char): String {
        if (expr.length >= MAX_LENGTH) return expr
        val last = expr.lastOrNull()
        // "0" then "5" becomes "5", not "05".
        if (currentNumber(expr) == "0") return expr.dropLast(1) + d
        return if (last == ')' || last == '%') "$expr$TIMES$d" else expr + d
    }

    fun point(expr: String): String {
        if (expr.length >= MAX_LENGTH || '.' in currentNumber(expr)) return expr
        val last = expr.lastOrNull()
        return when {
            last == null || last in OPERATORS || last == '(' -> expr + "0."
            last == ')' || last == '%' -> "$expr${TIMES}0."
            else -> "$expr."
        }
    }

    /** Adds a binary operator, replacing one that's already at the end. A minus may also start a negative number. */
    fun operator(expr: String, op: Char): String {
        val last = expr.lastOrNull()
        return when {
            expr.length >= MAX_LENGTH -> expr
            last == null || last == '(' -> if (op == MINUS) expr + MINUS else expr
            last in OPERATORS -> {
                // "5×" then "−" reads as "5×−" (a negative number); otherwise the new operator replaces the old ones.
                if (op == MINUS && (last == TIMES || last == DIVIDE)) return expr + MINUS
                val trimmed = expr.trimEnd { it in OPERATORS }
                if (trimmed.isEmpty() || trimmed.last() == '(') trimmed + (if (op == MINUS) "$MINUS" else "") else trimmed + op
            }
            last == '.' -> expr.dropLast(1) + op
            else -> expr + op
        }
    }

    fun percent(expr: String): String {
        val last = expr.lastOrNull() ?: return expr
        return if (last.isDigit() || last == ')') "$expr%" else expr
    }

    /** One key for both brackets: closes an open bracket when a number is in progress, otherwise opens one. */
    fun bracket(expr: String): String {
        if (expr.length >= MAX_LENGTH) return expr
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        val last = expr.lastOrNull()
        return when {
            open > 0 && last != null && (last.isDigit() || last == ')' || last == '%') -> "$expr)"
            last != null && (last.isDigit() || last == ')' || last == '%' || last == '.') -> "$expr$TIMES("
            else -> "$expr("
        }
    }

    /** Flips the sign of the number being typed (or the last one) by adding or removing a unary minus before it. */
    fun negate(expr: String): String {
        var start = expr.length
        while (start > 0 && (expr[start - 1].isDigit() || expr[start - 1] == '.')) start--
        if (start == expr.length) return if (expr.isEmpty() || expr.last() in OPERATORS || expr.last() == '(') operator(expr, MINUS) else expr
        val before = expr.getOrNull(start - 1)
        val unary = before == MINUS && (start == 1 || expr[start - 2] in OPERATORS || expr[start - 2] == '(')
        return if (unary) expr.removeRange(start - 1, start) else expr.substring(0, start) + MINUS + expr.substring(start)
    }

    fun backspace(expr: String): String = expr.dropLast(1)

    /** The value of [expr], or null if it's incomplete, empty or divides by zero. */
    fun evaluate(expr: String): BigDecimal? {
        if (expr.isEmpty()) return null
        return try {
            val p = Parser(expr)
            val v = p.expression()
            if (!p.atEnd()) null else v
        } catch (_: ArithmeticException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /** The value to preview while typing: ignores a trailing operator or point, so "12+3×" shows 15. */
    fun preview(expr: String): BigDecimal? {
        var e = expr
        while (e.isNotEmpty() && (e.last() in OPERATORS || e.last() == '.' || e.last() == '(')) e = e.dropLast(1)
        if (e.trimStart(MINUS).none { it in OPERATORS || it == '%' || it == '(' }) return null // nothing to work out yet
        return evaluate(e)
    }

    /** Up to 12 significant digits, without trailing zeros; very large or small numbers use an exponent. */
    fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        if (rounded.signum() == 0) return "0"
        val exponent = rounded.precision() - rounded.scale() - 1
        val text = if (exponent >= 12 || exponent < -8) {
            rounded.toString().replace("E+", "e").replace("E", "e")
        } else {
            rounded.toPlainString()
        }
        return text.replace('-', MINUS)
    }

    /** Result text that can carry on being edited, e.g. after "=". */
    fun resultExpression(value: BigDecimal): String {
        val plain = value.round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()
        return if (plain.length > 24) "" else plain.replace('-', MINUS)
    }

    private fun currentNumber(expr: String): String = expr.takeLastWhile { it.isDigit() || it == '.' }

    private class Parser(val s: String) {
        var i = 0

        fun atEnd() = i >= s.length

        fun expression(): BigDecimal {
            var v = term()
            while (!atEnd() && (s[i] == PLUS || s[i] == MINUS)) {
                val op = s[i++]
                val r = term()
                v = if (op == PLUS) v.add(r, MATH) else v.subtract(r, MATH)
            }
            return v
        }

        fun term(): BigDecimal {
            var v = factor()
            while (!atEnd() && (s[i] == TIMES || s[i] == DIVIDE)) {
                val op = s[i++]
                val r = factor()
                v = if (op == TIMES) v.multiply(r, MATH) else v.divide(r, MATH)
            }
            return v
        }

        fun factor(): BigDecimal {
            check(!atEnd())
            if (s[i] == MINUS) { i++; return factor().negate() }
            var v = primary()
            while (!atEnd() && s[i] == '%') { i++; v = v.divide(BigDecimal(100), MATH) }
            return v
        }

        fun primary(): BigDecimal {
            check(!atEnd())
            if (s[i] == '(') {
                i++
                val v = expression()
                if (!atEnd()) { check(s[i] == ')'); i++ } // a missing ")" at the very end is fine
                return v
            }
            val start = i
            while (!atEnd() && (s[i].isDigit() || s[i] == '.')) i++
            check(i > start)
            return BigDecimal(s.substring(start, i).let { if (it.endsWith('.')) it.dropLast(1) else it })
        }
    }
}
