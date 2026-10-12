package com.toolbox.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.toolbox.app.home.searchSummary
import com.toolbox.app.ui.theme.GoogleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** The light-mode accent tones are readable on the app's light surfaces (WCAG: 3:1 for graphics, 4.5:1 for text). */
class AccentContrastTest {
    private val white = Color(0xFFFFFFFF)
    private val card = Color(0xFFF0F4F9) // surfaceContainer: home tiles

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun name(c: Color) = "#%06X".format(c.value.toLong().ushr(32).toInt() and 0xFFFFFF)

    @Test fun inkIsTextContrastOnWhite() {
        for (accent in GoogleColors.all) {
            val ink = GoogleColors.ink(accent, light = true)
            assertTrue("${name(ink)} on white", contrast(ink, white) >= 4.5f)
        }
    }

    @Test fun inkIsGraphicContrastOnCards() {
        for (accent in GoogleColors.all) {
            val ink = GoogleColors.ink(accent, light = true)
            assertTrue("${name(ink)} on a card", contrast(ink, card) >= 3f)
        }
    }

    @Test fun tileGlyphShowsOnItsTint() {
        // A home tile draws the glyph in the ink over a 16% tint of the bright accent.
        for (accent in GoogleColors.all) {
            val tint = accent.copy(alpha = 0.16f).compositeOver(card)
            val ink = GoogleColors.ink(accent, light = true)
            assertTrue("${name(ink)} on its tile", contrast(ink, tint) >= 3f)
        }
    }

    @Test fun darkModeKeepsTheBrightAccents() {
        for (accent in GoogleColors.all) assertEquals(accent, GoogleColors.ink(accent, light = false))
    }

    @Test fun otherColoursAreUnchanged() {
        val grey = Color(0xFF5F6368)
        assertEquals(grey, GoogleColors.ink(grey, light = true))
        assertEquals(GoogleColors.Blue, GoogleColors.ink(GoogleColors.Blue, light = true))
    }
}

class SearchSummaryTest {
    @Test fun countsMatches() {
        assertEquals("Nothing matches", searchSummary(0))
        assertEquals("1 match", searchSummary(1))
        assertEquals("12 matches", searchSummary(12))
    }
}
