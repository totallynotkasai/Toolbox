package com.toolbox.app

import com.toolbox.app.tools.metronome.ClickTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class MetronomeTest {
    private val rate = 48_000
    private val accent = FloatArray(10) { 1f }
    private val normal = FloatArray(10) { 0.5f }

    /** Runs [blocks] blocks of [size] and returns (absolute frame, bar index) for every beat. */
    private fun ClickTrack.beats(blocks: Int, size: Int = 256, out: FloatArray = FloatArray(size)): List<Pair<Long, Int>> {
        val beats = ArrayList<Pair<Long, Int>>()
        for (b in 0 until blocks) fill(out) { index, offset -> beats += (b.toLong() * size + offset) to index }
        return beats
    }

    @Test fun beatsAreSampleExactAcrossBlocks() {
        val track = ClickTrack(rate, accent, normal).apply { bpm = 120 }
        // 120 bpm at 48 kHz is a beat every 24 000 frames, which doesn't divide into 256-frame blocks.
        val frames = track.beats(blocks = 400).map { it.first }
        assertEquals(listOf(0L, 24_000L, 48_000L, 72_000L, 96_000L), frames)
    }

    @Test fun barPositionWrapsAndTheFirstBeatIsAccented() {
        val track = ClickTrack(rate, accent, normal).apply { bpm = 300; beatsPerBar = 3 }
        val out = FloatArray(256)
        val indices = ArrayList<Int>()
        repeat(200) { track.fill(out) { index, _ -> indices += index } }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), indices.take(6))

        val fresh = ClickTrack(rate, accent, normal)
        fresh.fill(out) { _, _ -> }
        assertEquals(1f, out[0], 0f)
        assertEquals(0f, out[10], 0f) // Silence after the click.
    }

    @Test fun tempoChangesWaitForTheNextBeat() {
        val track = ClickTrack(rate, accent, normal).apply { bpm = 60 }
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

    @Test fun resetStartsANewBar() {
        val track = ClickTrack(rate, accent, normal).apply { bpm = 300 }
        val out = FloatArray(256)
        repeat(50) { track.fill(out) { _, _ -> } }
        track.reset()
        var first = -1
        track.fill(out) { index, offset -> if (first < 0) { first = index; assertEquals(0, offset) } }
        assertEquals(0, first)
    }
}
