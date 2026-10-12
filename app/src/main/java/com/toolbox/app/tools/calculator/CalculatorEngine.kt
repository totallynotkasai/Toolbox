package com.toolbox.app.tools.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Keypad logic and arithmetic for the calculator, pure so it can be unit-tested.
 *
 * The expression is kept as the text the user sees, using the display symbols below. Arithmetic is done in
 * [BigDecimal] so that 0.1 + 0.2 is 0.3. Multiplication and division bind tighter than + and −, a minus can be
 * unary ("5×−3"), and unclosed brackets are closed at the end. `%` works like a phone calculator: straight after
 * + or − it is a share of the total before it ("200+20%" is 240), anywhere else it divides by 100 ("50%×50" is 25).
 * A number can carry an exponent ("1.5e30", "2e−9"), which is how a long answer is shown.
 */
object CalculatorEngine {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    private const val EXP = 'e'
    private val OPERATORS = setOf(PLUS, MINUS, TIMES, DIVIDE)
    private val MATH = MathContext(20, RoundingMode.HALF_UP)
    private val SHOWN = MathContext(12, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal(100)
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
        val number = currentNumber(expr)
        if (expr.length >= MAX_LENGTH || '.' in number || EXP in number) return expr
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
            expr.length >= MAX_LENGTH || hasDanglingExponent(expr) -> expr
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
        return if (expr.length < MAX_LENGTH && (last.isDigit() || last == ')')) "$expr%" else expr
    }

    /** One key for both brackets: closes an open bracket when a number is in progress, otherwise opens one. */
    fun bracket(expr: String): String {
        if (expr.length >= MAX_LENGTH || hasDanglingExponent(expr)) return expr
        val open = expr.count { it == '(' } - expr.count { it == ')' }
        val last = expr.lastOrNull()
        return when {
            open > 0 && last != null && (last.isDigit() || last == ')' || last == '%') -> "$expr)"
            last != null && (last.isDigit() || last == ')' || last == '%' || last == '.') -> "$expr$TIMES("
            else -> "$expr("
        }
    }

    /**
     * Flips the sign of the number being typed, or of the one about to be typed. After an operator the negative
     * number goes in a bracket, so "5−3" becomes "5−(−3" rather than "5−−3"; pressing it again takes the bracket away.
     */
    fun negate(expr: String): String {
        val start = numberStart(expr)
        // Nothing to flip straight after ")" or "%".
        if (start == expr.length && expr.isNotEmpty() && expr.last() !in OPERATORS && expr.last() != '(') return expr
        val before = expr.getOrNull(start - 1)
        val unary = before == MINUS && (start == 1 || expr[start - 2] in OPERATORS || expr[start - 2] == '(')
        return when {
            unary && start >= 3 && expr[start - 2] == '(' && expr[start - 3] in OPERATORS -> expr.removeRange(start - 2, start)
            unary -> expr.removeRange(start - 1, start)
            expr.length + 2 > MAX_LENGTH -> expr
            before == null || before == '(' -> expr.substring(0, start) + MINUS + expr.substring(start)
            else -> expr.substring(0, start) + "($MINUS" + expr.substring(start)
        }
    }

    fun backspace(expr: String): String = expr.dropLast(1)

    /** True when "=" has nothing to finish yet, as in "5+" or "2×(". The key is then ignored rather than an error. */
    fun isUnfinished(expr: String): Boolean {
        val last = expr.lastOrNull() ?: return true
        return last in OPERATORS || last == '(' || last == EXP
    }

    /**
     * The value of [expr], or null if it's incomplete, empty or divides by zero. While [expr] still starts with the
     * shown form of the last [answer], the answer's full value is used in its place, so 1÷3 = then ×3 gives exactly 1.
     */
    fun evaluate(expr: String, answer: BigDecimal? = null): BigDecimal? {
        if (expr.isEmpty()) return null
        return try {
            val p = Parser(expr, answer?.let { format(it) to it })
            val v = p.expression()
            if (!p.atEnd()) null else v
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /** The value to preview while typing: ignores a trailing operator or point, so "12+3×" shows 15. */
    fun preview(expr: String, answer: BigDecimal? = null): BigDecimal? {
        var e = expr
        while (e.isNotEmpty() && (e.last() in OPERATORS || e.last() == '.' || e.last() == '(' || e.last() == EXP)) e = e.dropLast(1)
        if (e.trimStart(MINUS).none { it in OPERATORS || it == '%' || it == '(' }) return null // nothing to work out yet
        return evaluate(e, answer)
    }

    /**
     * Up to 12 significant digits, rounded half up, without trailing zeros. Numbers with 13 or more digits before the
     * point, or smaller than 0.00000001, use an exponent ("1.52415787502e16"). The keypad can carry on from the text,
     * because it is also how an answer shows after "=".
     */
    fun format(value: BigDecimal): String {
        val rounded = value.round(SHOWN).stripTrailingZeros()
        if (rounded.signum() == 0) return "0"
        val exponent = rounded.precision() - rounded.scale() - 1
        val text = if (exponent >= 12 || exponent < -8) {
            rounded.toString().replace("E+", "e").replace("E", "e")
        } else {
            rounded.toPlainString()
        }
        return text.replace('-', MINUS)
    }

    private fun hasDanglingExponent(expr: String) = expr.endsWith(EXP) || expr.endsWith("$EXP$MINUS")

    /** Where the number at the end of [expr] starts, counting an exponent as part of it; [expr]'s length if none. */
    private fun numberStart(expr: String): Int {
        fun digitsBack(from: Int): Int {
            var i = from
            while (i > 0 && (expr[i - 1].isDigit() || expr[i - 1] == '.')) i--
            return i
        }
        val i = digitsBack(expr.length)
        var j = i
        if (j >= 2 && expr[j - 1] == MINUS && expr[j - 2] == EXP) j--
        return if (j >= 1 && expr[j - 1] == EXP) digitsBack(j - 1) else i
    }

    private fun currentNumber(expr: String): String = expr.substring(numberStart(expr))

    private class Parser(val s: String, val answer: Pair<String, BigDecimal>?) {
        var i = 0

        fun atEnd() = i >= s.length

        fun expression(): BigDecimal {
            var v = term(null)
            while (!atEnd() && (s[i] == PLUS || s[i] == MINUS)) {
                val op = s[i++]
                val r = term(v)
                v = if (op == PLUS) v.add(r, MATH) else v.subtract(r, MATH)
            }
            return v
        }

        /** [base] is the total before a + or −, which a % straight after it takes a share of. */
        fun term(base: BigDecimal?): BigDecimal {
            var v = factor(base)
            while (!atEnd() && (s[i] == TIMES || s[i] == DIVIDE)) {
                val op = s[i++]
                val r = factor(null)
                v = if (op == TIMES) v.multiply(r, MATH) else v.divide(r, MATH)
            }
            return v
        }

        fun factor(base: BigDecimal?): BigDecimal {
            check(!atEnd())
            var v = leadingAnswer() ?: run {
                if (s[i] == MINUS) { i++; return factor(base).negate() }
                primary()
            }
            var share = base
            while (!atEnd() && s[i] == '%') {
                i++
                v = (if (share != null) share.multiply(v, MATH) else v).divide(HUNDRED, MATH)
                share = null // only the first % takes a share; another one divides by 100 again
            }
            return v
        }

        /** The full value of the last answer while the expression still starts with it as shown. */
        fun leadingAnswer(): BigDecimal? {
            val (shown, value) = answer ?: return null
            if (i != 0 || !s.startsWith(shown)) return null
            val next = s.getOrNull(shown.length)
            if (next != null && (next.isDigit() || next == '.' || next == EXP)) return null
            i = shown.length
            return value
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
            val mantissa = s.substring(start, i).removeSuffix(".")
            if (atEnd() || s[i] != EXP) return BigDecimal(mantissa)
            i++
            val negative = !atEnd() && s[i] == MINUS
            if (negative) i++
            val digits = i
            while (!atEnd() && s[i].isDigit()) i++
            check(i > digits)
            return BigDecimal(mantissa + "e" + (if (negative) "-" else "") + s.substring(digits, i))
        }
    }
}
