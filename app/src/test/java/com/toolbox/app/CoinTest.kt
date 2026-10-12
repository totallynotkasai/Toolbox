package com.toolbox.app

import com.toolbox.app.tools.coin.CoinStats
import com.toolbox.app.tools.coin.CoinPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoinStatsTest {
    @Test fun recordsCountsStreakAndHistory() {
        var s = CoinStats()
        for (h in listOf(true, false, true, true, true)) s = s.record(h)
        assertEquals(4, s.heads)
        assertEquals(1, s.tails)
        assertEquals(3, s.streak)
        assertEquals("HTHHH", s.history)
        assertEquals("3 heads in a row", s.streakText())
        assertNull(s.record(false).streakText())
    }

    @Test fun historyKeepsTheLastTwentyButStreakKeepsCounting() {
        var s = CoinStats()
        repeat(24) { s = s.record(false) }
        assertEquals(20, CoinStats.HISTORY_MAX)
        assertEquals(CoinStats.HISTORY_MAX, s.history.length)
        assertEquals(24, s.streak)
        assertEquals("24 tails in a row", s.streakText())
    }

    @Test fun anOlderSavedHistoryOfTenStillReads() {
        assertEquals(CoinStats(5, 5, 1, "HTHTHTHTHT"), CoinStats.decode("5,5,1,HTHTHTHTHT"))
    }

    @Test fun encodeRoundTripsAndBadInputIsEmpty() {
        val s = CoinStats(12, 9, 2, "HTTHHTTHTT")
        assertEquals(s, CoinStats.decode(s.encode()))
        assertEquals(CoinStats(), CoinStats.decode(""))
        assertEquals(CoinStats(), CoinStats.decode("a,b,c,d"))
        assertEquals(CoinStats(), CoinStats.decode("1,2,3"))
    }

    @Test fun percentagesAreRoundedAndAddUpTo100() {
        assertNull(CoinStats().percentages())
        assertEquals(33 to 67, CoinStats(1, 2).percentages()) // not 33 / 66
        assertEquals(13 to 87, CoinStats(1, 7).percentages()) // 12.5 rounds up, and tails isn't rounded up too
        assertEquals(100 to 0, CoinStats(3, 0).percentages())
        for (h in 0..40) for (t in 0..40) {
            val p = CoinStats(h, t).percentages() ?: continue
            assertEquals(100, p.first + p.second)
        }
    }

    @Test fun combiningAddsTheCountsAndKeepsTheBusierHistory() {
        val quick = CoinStats(3, 2, 1, "HTHHT")
        val thrown = CoinStats(10, 12, 4, "HHTTTT")
        assertEquals(CoinStats(13, 14, 4, "HHTTTT"), quick.combine(thrown))
        assertEquals(CoinStats(13, 14, 4, "HHTTTT"), thrown.combine(quick))
        assertEquals(thrown, CoinStats().combine(thrown))
        assertEquals(quick, quick.combine(CoinStats()))
    }
}

class CoinPhysicsTest {
    /** Runs a throw to the end at 60 fps; returns every event. */
    private fun throwToRest(coin: CoinPhysics, power: Float, alongX: Float = 0f, alongZ: Float = 0f): List<CoinPhysics.Event> {
        assertTrue(coin.toss(power, alongX, alongZ))
        return runToRest(coin)
    }

    private fun runToRest(coin: CoinPhysics): List<CoinPhysics.Event> {
        val events = ArrayList<CoinPhysics.Event>()
        var frames = 0
        while (coin.phase != CoinPhysics.Phase.Resting) {
            events += coin.step(1f / 60f)
            assertTrue("never settled", ++frames < 60 * 15)
        }
        return events
    }

    @Test fun fairAcrossTheForceRangeAndDirections() {
        val gestures = Random(2026)
        var heads = 0
        val n = 4000
        repeat(n) { i ->
            val coin = CoinPhysics(seed = i.toLong(), startHeads = true)
            val a = gestures.nextFloat() * 6.2832f
            val along = gestures.nextFloat()
            throwToRest(coin, gestures.nextFloat(), kotlin.math.cos(a) * along, kotlin.math.sin(a) * along)
            if (coin.heads) heads++
        }
        val share = heads.toDouble() / n
        println("heads share over $n throws: $share")
        assertTrue("heads share $share", share in 0.47..0.53)
    }

    @Test fun sameSeedSameThrowSameResult() {
        val a = CoinPhysics(seed = 42)
        val b = CoinPhysics(seed = 42)
        assertEquals(throwToRest(a, 0.6f, 0.2f, -0.3f), throwToRest(b, 0.6f, 0.2f, -0.3f))
        assertEquals(a.heads, b.heads)
        assertEquals(a.position, b.position)
    }

    @Test fun harderThrowsGoHigherAndTurnOverMore() {
        val weak = CoinPhysics(seed = 1).also { throwToRest(it, 0f) }
        val hard = CoinPhysics(seed = 1).also { throwToRest(it, 1f) }
        assertTrue("peaks ${weak.peak} ${hard.peak}", hard.peak > weak.peak * 2.5f)
        assertTrue("flips ${weak.flips} ${hard.flips}", hard.flips > weak.flips * 2)
        assertTrue("even a lazy flip turns over a few times", weak.flips >= 2)
        assertTrue(hard.peak <= hard.ceiling + 0.01f)
    }

    @Test fun flatThrowsTravelAcrossTheTable() {
        var far = 0
        repeat(20) { i ->
            val coin = CoinPhysics(seed = i.toLong(), halfWidth = 8f, halfDepth = 8f)
            throwToRest(coin, 0.7f, 1f, 0f)
            if (coin.x > 3f) far++
        }
        assertTrue("only $far of 20 flat throws went far", far >= 15)
    }

    @Test fun settlesFlatWithOneSettledEventAndSomeRattle() {
        var rattles = 0
        for (seed in 0L until 100) {
            val coin = CoinPhysics(seed)
            val events = throwToRest(coin, (seed % 11) / 10f, (seed % 3 - 1) * 0.5f, 0f)
            assertTrue(coin.bounces >= 1)
            val settled = events.filterIsInstance<CoinPhysics.Event.Settled>()
            assertEquals(1, settled.size)
            assertEquals(coin.heads, settled.single().heads)
            assertTrue(events.last() is CoinPhysics.Event.Settled)
            assertEquals(1f, kotlin.math.abs(coin.pose().column(2).y), 1e-3f)
            assertEquals(CoinPhysics.REST_Y, coin.y, 1e-4f)
            rattles += events.count { it is CoinPhysics.Event.Rattle }
        }
        assertTrue("rattle ticks: $rattles", rattles > 40)
    }

    @Test fun staysBetweenTheWallsAndIgnoresTossesMidAir() {
        repeat(20) { i ->
            val coin = CoinPhysics(seed = i.toLong(), halfWidth = 2.5f, halfDepth = 2.5f)
            assertTrue(coin.toss(1f, 1f, 0.3f))
            assertFalse(coin.toss(1f))
            while (coin.phase != CoinPhysics.Phase.Resting) {
                coin.step(1f / 60f)
                assertTrue("x ${coin.x}", kotlin.math.abs(coin.x) < 2.5f)
                assertTrue("z ${coin.z}", kotlin.math.abs(coin.z) < 2.5f)
                assertTrue("y ${coin.y}", coin.y > 0f)
            }
        }
    }

    @Test fun heldCoinFollowsTheFingerAndThrowsFromThere() {
        val coin = CoinPhysics(seed = 5, halfWidth = 4f, halfDepth = 4f)
        assertTrue(coin.grab())
        assertFalse(coin.grab())
        coin.hold(1.5f, -1f)
        repeat(30) { coin.step(1f / 60f) }
        assertEquals(CoinPhysics.Phase.Held, coin.phase)
        assertEquals(1.5f, coin.x, 0.05f)
        assertEquals(-1f, coin.z, 0.05f)
        assertEquals(CoinPhysics.HOLD_Y, coin.y, 0.05f)
        coin.hold(100f, 0f) // can't be carried off the table
        repeat(30) { coin.step(1f / 60f) }
        assertTrue(coin.x < 4f)
        assertTrue(coin.toss(1f, 0f, -0.1f))
        runToRest(coin)
        assertTrue("peak ${coin.peak}", coin.peak <= coin.ceiling + 0.01f)
        assertTrue(coin.flips > 3)
    }

    @Test fun anEmptyTableIsIgnored() {
        // A stage squashed to nothing gives an infinitely wide table with a NaN depth and ceiling.
        val coin = CoinPhysics(seed = 9)
        coin.resize(Float.POSITIVE_INFINITY, Float.NaN, Float.NaN)
        coin.resize(0f, 0f, 0f)
        assertEquals(4f, coin.halfWidth)
        assertEquals(4f, coin.halfDepth)
        throwToRest(coin, 0.6f, 0.3f, 0f)
        assertTrue("position ${coin.position}", coin.x.isFinite() && coin.y.isFinite() && coin.z.isFinite())
        assertTrue(kotlin.math.abs(coin.x) < 4f && kotlin.math.abs(coin.z) < 4f)
    }

    @Test fun layingItDownShowsTheOtherModesFace() {
        val coin = CoinPhysics(seed = 3, startHeads = true)
        coin.lay(false)
        assertFalse(coin.heads)
        assertEquals(-1f, coin.pose().column(2).y, 1e-4f)
        assertEquals(CoinPhysics.REST_Y, coin.y, 1e-4f)
        coin.lay(true)
        assertTrue(coin.heads)
        assertEquals(1f, coin.pose().column(2).y, 1e-4f)
        assertTrue(coin.toss(0.5f))
        coin.lay(false) // ignored in the air
        assertEquals(CoinPhysics.Phase.Flying, coin.phase)
    }

    @Test fun puttingItDownKeepsTheFace() {
        for (seed in 0L until 50) {
            val coin = CoinPhysics(seed, startHeads = seed % 2 == 0L)
            assertTrue(coin.grab())
            coin.hold((seed % 5 - 2) * 0.5f, 0f)
            repeat(40) { coin.step(1f / 60f) }
            assertTrue(coin.drop())
            runToRest(coin)
            assertEquals(seed % 2 == 0L, coin.heads)
        }
    }
}
