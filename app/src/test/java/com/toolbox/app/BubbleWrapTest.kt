package com.toolbox.app

import com.toolbox.app.toys.bubblewrap.BubbleSheet
import com.toolbox.app.toys.bubblewrap.HexGrid
import com.toolbox.app.toys.bubblewrap.Press
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BubbleWrapTest {
    private val grid = HexGrid(1080f, 1700f, 150f, margin = 16f)

    @Test fun gridFitsInsideTheMargins() {
        val half = grid.pitch / 2
        for (i in 0 until grid.count) {
            assertTrue(grid.x(i) - half >= 16f - 0.01f && grid.x(i) + half <= 1080f - 16f + 0.01f)
            assertTrue(grid.y(i) - half >= 16f - 0.01f && grid.y(i) + half <= 1700f - 16f + 0.01f)
        }
        assertTrue(grid.pitch >= 150f)
    }

    @Test fun hitFindsTheBubbleUnderTheFinger() {
        for (i in 0 until grid.count) assertEquals(i, grid.hit(grid.x(i) + 3f, grid.y(i) - 3f))
        assertEquals(-1, grid.hit(-50f, -50f))
    }

    @Test fun ordinaryBubblePopsOnce() {
        val sheet = BubbleSheet(240, random = Random(7))
        val i = sheet.stubborn.indexOfFirst { !it }
        assertEquals(Press.Pop, sheet.press(i))
        assertEquals(Press.None, sheet.press(i))
        assertEquals(1, sheet.poppedCount)
    }

    @Test fun stubbornBubbleNeedsTwoPresses() {
        val sheet = BubbleSheet(240, random = Random(7))
        val i = sheet.stubborn.indexOfFirst { it }
        assertTrue(i >= 0)
        assertEquals(Press.Squeak, sheet.press(i))
        assertFalse(sheet.popped[i])
        assertEquals(Press.Pop, sheet.press(i))
    }

    @Test fun sheetIsDoneWhenEveryBubbleIsPopped() {
        val sheet = BubbleSheet(30, random = Random(3))
        for (i in 0 until sheet.count) {
            while (!sheet.popped[i]) sheet.press(i)
            assertEquals(i == sheet.count - 1, sheet.allPopped)
        }
    }
}
