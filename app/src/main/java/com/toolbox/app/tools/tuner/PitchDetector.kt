package com.toolbox.app.tools.tuner

import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * YIN fundamental-frequency estimator (de Cheveigné & Kawahara, 2002).
 * Robust against the octave errors a naive autocorrelation makes on guitar/voice,
 * and small enough to keep in-repo instead of pulling in a DSP library.
 */
class PitchDetector(private val sampleRate: Int, bufferSize: Int, private val threshold: Float = 0.15f) {
    private val half = bufferSize / 2
    private val yin = FloatArray(half)

    /**
     * Returns frequency in Hz, or null if the frame is silent / aperiodic.
     * YIN itself is volume-independent, so [minRms] is only a noise gate; keep it low so a single
     * plucked string a metre from a phone mic (often ~-50 dBFS) still gets through.
     */
    fun detect(buffer: FloatArray, minRms: Float = 0.001f): Float? {
        if (rms(buffer) < minRms) return null

        // Step 1–2: difference function.
        yin[0] = 1f
        for (tau in 1 until half) {
            var sum = 0f
            for (i in 0 until half) {
                val d = buffer[i] - buffer[i + tau]
                sum += d * d
            }
            yin[tau] = sum
        }
        // Step 3: cumulative mean normalised difference.
        var running = 0f
        for (tau in 1 until half) {
            running += yin[tau]
            yin[tau] = if (running == 0f) 1f else yin[tau] * tau / running
        }
        // Step 4: absolute threshold — first dip below threshold, walked down to its local minimum.
        // Skip lags above 4.2 kHz; they're never a musical fundamental and only invite false dips.
        val minTau = (sampleRate / 4200).coerceAtLeast(2)
        var best = minTau
        for (t in minTau until half) if (yin[t] < yin[best]) best = t
        if (yin[best] > 0.4f) return null // nothing periodic in this frame
        // Adaptive threshold: with a weak, noisy note even the true period's dip can sit above the fixed
        // threshold, and the deepest dip is often a multiple of it (reads an octave or a fifth low).
        // Accepting the first dip within 0.1 of the deepest keeps the fundamental.
        val limit = maxOf(threshold, yin[best] + 0.1f)
        var tau = minTau
        while (tau < half && yin[tau] >= limit) tau++
        if (tau >= half) {
            tau = best
        } else {
            // Take the lowest point of the whole dip, not the first local minimum: noise puts small
            // wiggles on the slope that would otherwise stop the search early (and read sharp).
            var t = tau
            while (t < half && yin[t] < limit) { if (yin[t] < yin[tau]) tau = t; t++ }
        }
        // Step 5: parabolic interpolation for sub-sample precision.
        val better = if (tau in 1 until half - 1) {
            val s0 = yin[tau - 1]; val s1 = yin[tau]; val s2 = yin[tau + 1]
            val denom = 2 * (2 * s1 - s2 - s0)
            if (denom == 0f) tau.toFloat() else tau + (s2 - s0) / denom
        } else tau.toFloat()
        val f = sampleRate / better
        return if (f in 25f..4200f) f else null
    }

    fun rms(b: FloatArray): Float {
        var s = 0f
        for (v in b) s += v * v
        return sqrt(s / b.size)
    }
}

private val NOTE_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

data class Note(val midi: Int) {
    val name get() = NOTE_NAMES[((midi % 12) + 12) % 12]
    val octave get() = midi / 12 - 1
    val frequency get() = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
    override fun toString() = "$name$octave"
}

fun midiOf(freq: Float): Double = 69 + 12 * log2(freq / 440.0)
fun nearestNote(freq: Float) = Note(midiOf(freq).roundToInt())
fun centsFrom(freq: Float, note: Note): Float = ((midiOf(freq) - note.midi) * 100).toFloat()

enum class Instrument(val label: String, val strings: List<Note>) {
    Chromatic("Chromatic", emptyList()),
    Guitar("Guitar", listOf(40, 45, 50, 55, 59, 64).map(::Note)),
    Bass("Bass", listOf(28, 33, 38, 43).map(::Note)),
    Ukulele("Ukulele", listOf(67, 60, 64, 69).map(::Note)),
    Violin("Violin", listOf(55, 62, 69, 76).map(::Note)),
}
