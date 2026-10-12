package com.toolbox.app

import com.toolbox.app.tools.meditate.EMPTY
import com.toolbox.app.tools.meditate.FULL
import com.toolbox.app.tools.meditate.Kind
import com.toolbox.app.tools.meditate.Pattern
import com.toolbox.app.tools.meditate.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MeditateTest {
    @Test fun aSessionIsAWholeNumberOfBreathsNearestTheChosenLength() {
        assertEquals(60_000L, Session(Pattern.Calm, 1).totalMs)    // 6 × 10 s exactly
        assertEquals(57_000L, Session(Pattern.Relax, 1).totalMs)   // 3 × 19 s, nearer than 76 s
        assertEquals(608_000L, Session(Pattern.Relax, 10).totalMs) // 32 × 19 s
        // Box breathing drops the hold after the last breath out: 4 × 16 s - 4 s.
        assertEquals(60_000L, Session(Pattern.Square, 1).totalMs)
        assertEquals(124_000L, Session(Pattern.Square, 2).totalMs)
    }

    @Test fun everySessionEndsOnABreathOut() {
        for (p in Pattern.entries) for (m in listOf(1, 2, 5, 10)) {
            val s = Session(p, m)
            assertEquals("$p $m min", Kind.Out, s.phases.last().kind)
            assertEquals(Kind.Out, s.at(s.totalMs).kind)
            assertEquals(0L, s.at(s.totalMs).remainingMs)
            assertEquals(EMPTY, s.sizeAt(s.totalMs), 1e-4f)
        }
    }

    @Test fun thePhaseFollowsTheClock() {
        val s = Session(Pattern.Relax, 1) // in 4, hold 7, out 8
        assertEquals(Kind.In, s.at(0).kind)
        assertEquals(Kind.HoldFull, s.at(4_000).kind)
        assertEquals(Kind.Out, s.at(11_000).kind)
        assertEquals(Kind.In, s.at(19_000).kind)
        assertEquals(3, s.at(19_000).index)
        assertEquals(2_000L, s.at(9_000).phaseLeftMs)
        assertEquals(50_000L, s.at(7_000).remainingMs)
    }

    @Test fun theCircleGrowsOnTheWayInAndShrinksOnTheWayOut() {
        val s = Session(Pattern.Calm, 1)
        assertEquals(EMPTY, s.sizeAt(0), 1e-4f)
        assertEquals(FULL, s.sizeAt(4_000), 1e-4f)
        assertTrue(s.sizeAt(2_000) in EMPTY..FULL)
        assertEquals(EMPTY, s.sizeAt(10_000), 1e-4f)
        // No jumps from one frame to the next anywhere in the session.
        var last = s.sizeAt(0)
        for (t in 16L..s.totalMs step 16L) {
            val now = s.sizeAt(t)
            assertTrue("jump at $t ms", abs(now - last) < 0.01f)
            last = now
        }
    }
}
