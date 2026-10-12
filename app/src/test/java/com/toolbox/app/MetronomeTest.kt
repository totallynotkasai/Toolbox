package com.toolbox.app

import com.toolbox.app.tools.metronome.Accent
import com.toolbox.app.tools.metronome.ClickTrack
import com.toolbox.app.tools.metronome.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeTest {
    private val rate = 48_000
    private val strong = FloatArray(10) { 1f }
    private val light = FloatArray(10) { 0.75f }
    private val normal = FloatArray(10) { 0.5f }
    private val sub = FloatArray(10) { 0.25f }

    private fun track() = ClickTrack(rate, strong, light, normal, sub)

    /** Runs [blocks] blocks of [size] and returns (absolute frame, bar index) for every beat. */
    private fun ClickTrack.beats(blocks: Int, size: Int = 256, out: FloatArray = FloatArray(size)): List<Pair<Long, Int>> {
        val beats = ArrayList<Pair<Long, Int>>()
        for (b in 0 until blocks) fill(out) { index, offset -> beats += (b.toLong() * size + offset) to index }
        return beats
    }

    /** Runs [frames] frames in blocks of [size] and returns the absolute frame where every click starts, and its level. */
    private fun ClickTrack.clicks(frames: Int, size: Int = 256): List<Pair<Long, Float>> {
        val out = FloatArray(size)
        val clicks = ArrayList<Pair<Long, Float>>()
        var previous = 0f
        for (b in 0 until frames / size) {
            fill(out) { _, _ -> }
            for (i in out.indices) {
                // Every test click is a flat run of 10 frames, so a click starts wherever the level changes from silence.
                if (out[i] != 0f && previous == 0f) clicks += (b.toLong() * size + i) to out[i]
                previous = out[i]
            }
        }
        return clicks
    }

    @Test fun beatsAreSampleExactAcrossBlocks() {
        val track = track().apply { bpm = 120 }
        // 120 bpm at 48 kHz is a beat every 24 000 frames, which doesn't divide into 256-frame blocks.
        val frames = track.beats(blocks = 400).map { it.first }
        assertEquals(listOf(0L, 24_000L, 48_000L, 72_000L, 96_000L), frames)
    }

    @Test fun barPositionWrapsAndTheFirstBeatIsAccented() {
        val track = track().apply { bpm = 300; accents = TimeSignature.ThreeFour.accents(true) }
        val out = FloatArray(256)
        val indices = ArrayList<Int>()
        repeat(200) { track.fill(out) { index, _ -> indices += index } }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), indices.take(6))

        val fresh = track()
        fresh.fill(out) { _, _ -> }
        assertEquals(1f, out[0], 0f)
        assertEquals(0f, out[10], 0f) // Silence after the click.
    }

    @Test fun tempoChangesWaitForTheNextBeat() {
        val track = track().apply { bpm = 60 }
        val out = FloatArray(1000)
        val beats = ArrayList<Long>()
        var block = 0L
        fun run(n: Int) = repeat(n) { track.fill(out) { _, offset -> beats += block * 1000 + offset }; block++ }
        run(10) // Mid-way through the first one-second beat.
        track.bpm = 120
        run(100)
        // The first beat keeps its full 48 000 frames; after that they're 24 000 apart.
        assertEquals(listOf(0L, 48_000L, 72_000L, 96_000L), beats.take(4))
    }

    @Test fun subdivisionChangesWaitForTheNextBeat() {
        val track = track().apply { bpm = 60 }
        val out = FloatArray(1000)
        repeat(10) { track.fill(out) { _, _ -> } }
        track.subdivision = 2
        // The first beat has no half-beat click; from the second beat (frame 48 000) there's one every 24 000.
        val clicks = track.clicks(frames = 200_000, size = 1000).map { it.first + 10_000 }
        assertEquals(listOf(48_000L, 72_000L, 96_000L, 120_000L, 144_000L, 168_000L, 192_000L), clicks)
    }

    @Test fun resetStartsANewBar() {
        val track = track().apply { bpm = 300 }
        val out = FloatArray(256)
        repeat(50) { track.fill(out) { _, _ -> } }
        track.reset()
        var first = -1
        track.fill(out) { index, offset -> if (first < 0) { first = index; assertEquals(0, offset) } }
        assertEquals(0, first)
    }

    @Test fun eachSubdivisionClicksAtExactFrames() {
        // 100 bpm is 28 800 frames a beat.
        val expected = mapOf(
            1 to listOf(0L, 28_800L),
            2 to listOf(0L, 14_400L, 28_800L, 43_200L),
            3 to listOf(0L, 9_600L, 19_200L, 28_800L, 38_400L, 48_000L),
            4 to listOf(0L, 7_200L, 14_400L, 21_600L, 28_800L, 36_000L, 43_200L, 50_400L),
        )
        for ((count, frames) in expected) {
            val track = track().apply { bpm = 100; subdivision = count }
            val clicks = track.clicks(frames = 57_344)
            assertEquals("subdivision $count", frames, clicks.map { it.first })
            // Beats click at the beat's level and everything in between at the quieter subdivision level.
            clicks.forEachIndexed { i, (_, level) -> assertEquals(if (i % count == 0) (if (i == 0) 1f else 0.5f) else 0.25f, level, 0f) }
        }
    }

    @Test fun tripletsDoNotDriftOverManyBars() {
        // 7 * 60 * 48 000 / (110 * 3) isn't whole, so a rounded click length would drift by now.
        val track = track().apply { bpm = 110; subdivision = 3 }
        val clicks = track.clicks(frames = 48_000 * 120).map { it.first }
        assertTrue(clicks.size > 600)
        clicks.forEachIndexed { n, frame ->
            assertEquals("click $n", n * 60L * rate / (110 * 3), frame)
        }
        // And the beats land on the same exact grid: beat b at b * 60 * rate / bpm, rounded down.
        val beats = clicks.filterIndexed { n, _ -> n % 3 == 0 }
        beats.forEachIndexed { b, frame -> assertTrue("beat $b", frame - b * 60.0 * rate / 110 in -1.0..0.0) }
    }

    @Test fun sixEightIsStrongOnOneAndLightOnFour() {
        val s = Accent.Strong
        val l = Accent.Light
        val n = Accent.Normal
        assertEquals(listOf(s, n, n, l, n, n), TimeSignature.SixEight.accents(true))
        assertEquals(List(6) { n }, TimeSignature.SixEight.accents(false))
        assertEquals(listOf(s, n, n, n, n, n), TimeSignature.SixFour.accents(true))
        assertEquals(listOf(s, n, n, n), TimeSignature.FourFour.accents(true))
        assertEquals(3, TimeSignature.SixEight.group)
        assertEquals(6, TimeSignature.SixFour.group)

        // And the track plays it: six clicks a bar at their levels.
        val track = track().apply { bpm = 240; accents = TimeSignature.SixEight.accents(true) }
        val levels = track.clicks(frames = 12_000 * 12).map { it.second }
        assertEquals(listOf(1f, 0.5f, 0.5f, 0.75f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 0.75f, 0.5f, 0.5f), levels)
    }

    @Test fun aNewSignatureStartsANewBar() {
        val track = track().apply { bpm = 300 }
        val indices = ArrayList<Int>()
        val out = FloatArray(9_600) // One beat a block.
        repeat(2) { track.fill(out) { index, _ -> indices += index } }
        track.accents = TimeSignature.SixEight.accents(true)
        repeat(3) { track.fill(out) { index, _ -> indices += index } }
        assertEquals(listOf(0, 1, 0, 1, 2), indices)
    }
}
