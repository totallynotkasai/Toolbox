package com.toolbox.app.toys.infinitycube

import com.toolbox.app.ui.common.PcmStream
import com.toolbox.app.ui.common.Ping
import com.toolbox.app.ui.common.jitter
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Procedural sound for the infinity cube, all one-shots queued from the UI thread:
 * - [snap]: the cube closing, several hard plastic faces meeting at once: a quick flam of clacks over a soft thump.
 * - [clack]: it opening out flat, a single lighter clack of faces meeting.
 * - [tick]: a short light tick for each position a fast fold passes through.
 *
 * Sounds asked for while stopped are dropped, so nothing bursts out on returning to the app, and after ~2 s of
 * silence the track pauses until the next one.
 */
internal class CubeAudio : PcmStream("cube-audio") {
    private val incoming = ConcurrentLinkedQueue<Ping>()
    private val voices = ArrayList<Ping>() // Audio thread only.

    /** [strength] 0..1. */
    fun snap(strength: Float = 1f) {
        val s = strength.coerceIn(0f, 1f)
        // Faces never meet at quite the same instant, so the clacks are a few milliseconds apart.
        intArrayOf(0, 140, 310).forEachIndexed { k, delay ->
            val j = jitter(0.08)
            queue(
                Ping(
                    freqs = doubleArrayOf(1350.0 * j, 2900.0 * j, 4700.0 * j),
                    decays = doubleArrayOf(0.011, 0.005, 0.0025),
                    amps = floatArrayOf(1f, 0.55f, 0.3f),
                    gain = 0.3f * s * (1f - 0.25f * k),
                    clickSeconds = 0.0012,
                    clickGain = 0.9f,
                    delay = delay,
                ),
            )
        }
        queue(
            Ping(
                freqs = doubleArrayOf(190.0, 520.0),
                decays = doubleArrayOf(0.028, 0.012),
                amps = floatArrayOf(1f, 0.4f),
                gain = 0.22f * s,
                clickSeconds = 0.002,
                clickGain = 0.3f,
            ),
        )
    }

    /** [strength] 0..1. */
    fun clack(strength: Float = 1f) {
        val j = jitter(0.08)
        queue(
            Ping(
                freqs = doubleArrayOf(1600.0 * j, 3400.0 * j, 5600.0 * j),
                decays = doubleArrayOf(0.008, 0.004, 0.002),
                amps = floatArrayOf(1f, 0.5f, 0.3f),
                gain = 0.2f * strength.coerceIn(0f, 1f),
                clickSeconds = 0.001,
                clickGain = 0.8f,
            ),
        )
    }

    /** [strength] 0..1. */
    fun tick(strength: Float = 1f) {
        val j = jitter(0.1)
        queue(
            Ping(
                freqs = doubleArrayOf(3300.0 * j, 6100.0 * j),
                decays = doubleArrayOf(0.003, 0.0015),
                amps = floatArrayOf(1f, 0.4f),
                gain = 0.12f * strength.coerceIn(0f, 1f),
                clickSeconds = 0.0006,
                clickGain = 0.7f,
            ),
        )
    }

    private fun queue(p: Ping) {
        if (!isRunning) return
        incoming += p
        wake()
    }

    /** Starts with an empty queue. */
    override fun start() {
        if (!isRunning) incoming.clear()
        super.start()
    }

    override fun isQuiet(): Boolean = voices.isEmpty() && incoming.isEmpty()

    override fun onStreamStart() {
        voices.clear()
    }

    override fun render(out: FloatArray) {
        while (true) {
            val v = incoming.poll() ?: break
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices += v
        }
        if (voices.isEmpty()) return
        for (i in out.indices) {
            var s = 0f
            for (v in voices) s += v.next()
            out[i] = s
        }
        voices.removeAll { it.done }
    }

    private companion object {
        const val MAX_VOICES = 16
    }
}
