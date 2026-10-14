package com.toolbox.app

import androidx.compose.ui.geometry.Offset
import com.toolbox.app.toys.cat.CatMood
import com.toolbox.app.toys.cat.CatShape
import com.toolbox.app.toys.cat.purrEnvelope
import com.toolbox.app.toys.cat.purrVibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatTest {
    private fun strokeRight(mood: CatMood, strokes: Int) {
        repeat(strokes) {
            mood.startStroke()
            repeat(20) { mood.stroke(0.025f) } // half a cat-width, head to tail
        }
    }

    @Test fun strokingHeadToTailBuildsThePurr() {
        val mood = CatMood()
        strokeRight(mood, 1)
        val one = mood.purr
        assertTrue(one > 0.1f)
        strokeRight(mood, 10)
        assertTrue(mood.purr > one)
        assertTrue(mood.purr <= 1f)
    }

    @Test fun strokingTheWrongWayGrumbles() {
        val mood = CatMood()
        strokeRight(mood, 6)
        val before = mood.purr
        mood.startStroke()
        var grumbled = false
        repeat(10) { if (mood.stroke(-0.025f)) grumbled = true }
        assertTrue(grumbled)
        assertEquals(1f, mood.grumble, 0.001f)
        assertTrue(mood.purr < before * 0.5f)
    }

    @Test fun aSmallWiggleBackIsForgiven() {
        val mood = CatMood()
        mood.startStroke()
        repeat(10) { mood.stroke(0.025f) }
        assertFalse(mood.stroke(-0.05f))
        assertEquals(0f, mood.grumble, 0f)
    }

    @Test fun grumblesOncePerStroke() {
        val mood = CatMood()
        mood.startStroke()
        var count = 0
        repeat(40) { if (mood.stroke(-0.025f)) count++ }
        assertEquals(1, count)
    }

    @Test fun purrFadesSlowlyOnItsOwn() {
        val mood = CatMood()
        strokeRight(mood, 20)
        val full = mood.purr
        repeat(60) { mood.update(1f / 60f) }
        assertTrue(mood.purr > full * 0.9f) // still purring a second later
        repeat(60 * 60) { mood.update(1f / 60f) }
        assertEquals(0f, mood.purr, 0f)
    }

    @Test fun breathingEnvelopeDipsAtTheTurnAndSwellsMidBreath() {
        for (i in 0..100) {
            val e = purrEnvelope(i / 100f)
            assertTrue(e in 0.25f..1f)
        }
        assertEquals(0.25f, purrEnvelope(0f), 0.01f)
        assertEquals(0.25f, purrEnvelope(0.5f), 0.01f)
        assertTrue(purrEnvelope(0.75f) > purrEnvelope(0.25f)) // out-breath is fuller
    }

    @Test fun noPurrNoVibration() {
        assertEquals(0f, purrVibration(0f, 1f), 0f)
        assertTrue(purrVibration(1f, 1f) > purrVibration(0.5f, 1f))
        assertTrue(purrVibration(1f, 1f) <= 1f)
    }

    // The cat's own units: head on the left, centred on (78, 104); the loaf runs right to the rump at about x 200,
    // and the tail lies along the ground in front of it, tip at (122, 190).
    private val earTip = Offset(40f, 47f)
    private val whiskerTip = Offset(10f, 115f)
    private val tailBottom = Offset(165f, 198f)
    private val rump = Offset(200f, 150f)

    @Test fun catSitsInTheMiddleOfItsBox() {
        for ((w, h) in listOf(440f to 352f, 1000f to 800f, 1000f to 400f, 300f to 600f)) {
            val middle = CatShape.toBox(CatShape.MIDDLE, w, h)
            assertEquals(w / 2f, middle.x, 0.01f)
            assertEquals(h / 2f, middle.y, 0.01f)
            // Ear tips, whiskers, rump and tail all fit, with room to spare for flattened ears and a swishing tail.
            for (p in listOf(earTip, whiskerTip, tailBottom, rump)) {
                val b = CatShape.toBox(p, w, h)
                assertTrue("$p in $w × $h", b.x > 0.03f * w && b.x < 0.97f * w && b.y > 0.03f * h && b.y < 0.97f * h)
            }
            // And a touch maps back to the same spot on the cat.
            val back = CatShape.toCat(CatShape.toBox(rump, w, h), w, h)
            assertEquals(rump.x, back.x, 0.01f)
            assertEquals(rump.y, back.y, 0.01f)
        }
    }

    @Test fun tappingTheHeadIsTheFaceAndEarsOnly() {
        assertTrue(CatShape.onHead(CatShape.HEAD))
        assertTrue(CatShape.onHead(earTip))
        assertTrue(CatShape.onHead(Offset(78f, 150f))) // the chin
        assertFalse(CatShape.onHead(Offset(122f, 190f))) // the tail tip
        assertFalse(CatShape.onHead(rump))
        assertFalse(CatShape.onHead(Offset(170f, 115f))) // the back
        assertFalse(CatShape.onHead(Offset(78f, 25f))) // well above the ears
    }

    @Test fun strokesCountAnywhereOnTheCatButNotAroundIt() {
        for (p in listOf(CatShape.HEAD, Offset(160f, 108f), Offset(190f, 128f), rump, Offset(122f, 190f), Offset(66f, 180f))) {
            assertTrue("$p", CatShape.onCat(p))
        }
        // Above the back, beyond the rump, in front of the paws and far below.
        for (p in listOf(Offset(185f, 70f), Offset(222f, 120f), Offset(15f, 185f), Offset(120f, 215f))) {
            assertFalse("$p", CatShape.onCat(p))
        }
    }
}
