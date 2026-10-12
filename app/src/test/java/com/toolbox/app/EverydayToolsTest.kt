package com.toolbox.app

import com.toolbox.app.tools.eightball.EIGHT_BALL_ANSWERS
import com.toolbox.app.tools.eightball.fitAnswer
import com.toolbox.app.tools.eightball.triangleWidthAt
import com.toolbox.app.tools.random.rangeProblem
import com.toolbox.app.tools.spinner.MIN_FLING
import com.toolbox.app.tools.spinner.MIN_TURN
import com.toolbox.app.tools.spinner.flingFor
import com.toolbox.app.tools.spinner.parseRemoved
import com.toolbox.app.tools.spinner.remaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomNumberTest {
    @Test fun eachProblemHasItsOwnMessage() {
        assertNull(rangeProblem("1", "100"))
        assertNull(rangeProblem("-5", "-5"))
        assertEquals("Enter a minimum", rangeProblem("", "100")?.message)
        assertEquals("Enter a minimum", rangeProblem("-", "100")?.message)
        assertEquals("Enter a maximum", rangeProblem("1", "")?.message)
        assertEquals("Enter a minimum and a maximum", rangeProblem("", "-")?.message)
        val backwards = rangeProblem("10", "1")!!
        assertEquals("The minimum is bigger than the maximum", backwards.message)
        assertTrue(backwards.min && backwards.max)
        val noMax = rangeProblem("1", "")!!
        assertTrue(!noMax.min && noMax.max)
    }
}

class SpinnerTest {
    @Test fun aSlowReleaseIsNotAFlick() {
        assertNull(flingFor(0f, 0f, 0.5f))
        assertNull(flingFor(MIN_FLING - 1f, 10f, 0.5f))
        assertNull(flingFor(-(MIN_FLING - 1f), -10f, 0.5f))
    }

    @Test fun everyFlickTurnsAtLeastTheMinimumPlusARandomPart() {
        val gentle = flingFor(MIN_FLING, 100f, 0f)!!
        assertEquals(MIN_TURN, gentle.degrees, 0.001f)
        val withExtra = flingFor(MIN_FLING, 100f, 1.25f)!!
        assertEquals(MIN_TURN + 450f, withExtra.degrees, 0.001f)
        // A hard flick coasts further than the minimum, and keeps its direction.
        val hard = flingFor(-4000f, -1600f, 0.5f)!!
        assertEquals(-(1600f + 180f), hard.degrees, 0.001f)
    }

    @Test fun whereItStopsDoesNotDependOnTheFlick() {
        // For any flick, the random part alone decides where in the turn the wheel stops.
        for (coast in listOf(0f, 200f, 719f, 1000f, 1587f)) {
            val a = flingFor(1000f, coast, 0f)!!.degrees
            val b = flingFor(1000f, coast, 0.25f)!!.degrees
            assertEquals(90f, b - a, 0.001f)
        }
    }

    @Test fun durationsMatchTheFlickWithinLimits() {
        assertEquals(1500, flingFor(4000f, 0f, 0f)!!.millis)
        assertEquals(4500, flingFor(MIN_FLING, 0f, 1.9f)!!.millis)
        assertEquals(2700, flingFor(1600f, 0f, 0f)!!.millis) // 6 × 720° ÷ 1600°/s
    }

    @Test fun removalsAreByPositionAndForgetMissingOnes() {
        val options = listOf("Ann", "Bob", "Cat", "Bob")
        assertEquals(listOf("Ann", "Cat", "Bob"), remaining(options, setOf(1)))
        assertEquals(setOf(0, 3), parseRemoved("0,3", 4))
        assertEquals(setOf(1), parseRemoved("1,7,x,", 4))
        assertEquals(emptySet<Int>(), parseRemoved("", 4))
    }
}

class EightBallFitTest {
    /** Roughly a bold capital's width plus the 0.04 em letter spacing, and a narrower space. */
    private fun width(s: String) = s.sumOf { if (it == ' ') 0.3 else 0.72 }.toFloat()

    @Test fun everyAnswerFitsInsideTheTriangleWithWholeWords() {
        for (answer in EIGHT_BALL_ANSWERS.map { it.uppercase() }) {
            val fit = fitAnswer(answer, ::width)
            assertEquals(answer, fit.lines.joinToString(" "))
            assertTrue("$answer: ${fit.lines.size} lines", fit.lines.size <= 4)
            assertTrue("$answer: font ${fit.fontSize}", fit.fontSize >= 0.1f)
            val lineHeight = fit.fontSize * 1.1f
            fit.lines.forEachIndexed { i, line ->
                val top = fit.centres[i] - lineHeight / 2
                assertTrue("$answer: \"$line\" too wide", width(line) * fit.fontSize <= triangleWidthAt(top) - 0.1f + 1e-4f)
            }
            assertTrue("$answer: below the base", fit.centres.last() + lineHeight / 2 <= 0.5f)
        }
    }

    @Test fun shortAnswersKeepTheLargestSize() {
        val yes = fitAnswer("YES", ::width)
        assertEquals(0.155f, yes.fontSize, 1e-6f)
        assertEquals(listOf("YES"), yes.lines)
        assertNotNull(yes.centres.single())
    }

    @Test fun theLongestAnswerShrinksInsteadOfBreakingAWord() {
        val fit = fitAnswer("CONCENTRATE AND ASK AGAIN", ::width)
        // At the old fixed size "CONCENTRATE" alone is wider than the triangle where the text starts.
        assertTrue(fit.fontSize < 0.155f)
        assertEquals("CONCENTRATE AND ASK AGAIN", fit.lines.joinToString(" "))
    }
}
