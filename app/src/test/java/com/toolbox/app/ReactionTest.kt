package com.toolbox.app

import com.toolbox.app.games.reaction.reactionMs
import com.toolbox.app.games.reaction.reactionRating
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionTest {
    @Test fun frameNanosAndUptimeMillisShareAClock() {
        assertEquals(250L, reactionMs(goFrameNanos = 10_000_000_000L, tapUptimeMs = 10_250L))
        assertEquals(-5L, reactionMs(goFrameNanos = 10_005_000_000L, tapUptimeMs = 10_000L))
    }

    @Test fun ratings() {
        assertEquals("Lightning fast!", reactionRating(180))
        assertEquals("Great", reactionRating(230))
        assertEquals("Keep practising", reactionRating(450))
    }
}
