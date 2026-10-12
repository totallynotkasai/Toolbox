package com.toolbox.app

import com.toolbox.app.tools.coin.coinTableDescription
import com.toolbox.app.tools.coin.hintLines
import com.toolbox.app.tools.spinner.wheelDescription
import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionsA11yTest {
    @Test fun wheelListsItsOptionsInOrder() {
        assertEquals("Wheel with 3 options: Yes, No, Maybe", wheelDescription(listOf("Yes", "No", "Maybe")))
    }

    @Test fun coinSaysWhichFaceIsUp() {
        assertEquals("Coin, heads showing", coinTableDescription(true))
        assertEquals("Coin, tails showing", coinTableDescription(false))
        assertEquals("Coin", coinTableDescription(null))
    }

    @Test fun hintKeepsTwoLinesAndGrowsWithLargeText() {
        assertEquals(2, hintLines(0.85f))
        assertEquals(2, hintLines(1f))
        assertEquals(3, hintLines(1.3f))
        assertEquals(4, hintLines(2f))
    }
}
