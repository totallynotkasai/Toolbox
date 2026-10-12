package com.toolbox.app

import com.toolbox.app.toys.cat.catMoodState
import com.toolbox.app.toys.dial.dialStateText
import com.toolbox.app.toys.zip.zipStateText
import org.junit.Assert.assertEquals
import org.junit.Test

class ToysA11yTest {
    @Test fun catMoodComesInBroadSteps() {
        assertEquals("Calm", catMoodState(0f, 0f))
        assertEquals("Purring", catMoodState(0.3f, 0f))
        assertEquals("Purring", catMoodState(0.5f, 0f))
        assertEquals("Purring loudly", catMoodState(0.9f, 0f))
        // Cross outranks a purr that hasn't faded yet.
        assertEquals("Grumpy", catMoodState(0.9f, 1f))
        assertEquals("Calm", catMoodState(0.01f, 0.1f))
    }

    @Test fun dialSaysItsNotchAndAngle() {
        assertEquals("Notch 5, 30 degrees", dialStateText(5, 30f))
        assertEquals("Notch -2, 300 degrees", dialStateText(-2, -60f))
        // A whole turn round is back at 0, never "360 degrees".
        assertEquals("Notch 12, 0 degrees", dialStateText(12, 360f))
        assertEquals("0 degrees", dialStateText(null, 359.8f))
        assertEquals("90 degrees", dialStateText(null, 810f))
    }

    @Test fun zipSaysHowOpenInBroadSteps() {
        assertEquals("Zipped up", zipStateText(0f))
        assertEquals("Partly open", zipStateText(0.01f))
        assertEquals("Mostly open", zipStateText(0.5f))
        assertEquals("Mostly open", zipStateText(0.99f))
        assertEquals("Wide open", zipStateText(1f))
    }
}
