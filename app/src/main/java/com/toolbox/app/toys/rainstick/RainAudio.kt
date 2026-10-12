package com.toolbox.app.toys.rainstick

import com.toolbox.app.ui.common.PcmStream
import com.toolbox.app.ui.common.Svf
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The rain: every collision the sim reports becomes one tiny click, a damped sine a few milliseconds long with
 * a breath of noise on the front, at a random pitch. Pegs are bright seed-on-thorn ticks, grain on grain even
 * smaller and higher, and hits on the wall a lower hollow "tock". A frame's clicks are scattered across the
 * next ~16 ms so they never land in lockstep; hundreds a second then blur into rain rather than white noise,
 * because each one is still a little pitched tick.
 *
 * Voices live in fixed arrays (round robin, oldest overwritten) and each is a two-multiply resonator, so a
 * busy pour costs next to nothing.
 */
internal class RainAudio : PcmStream("rainstick-audio") {
    private val incoming = ConcurrentLinkedQueue<FloatArray>()

    /** Queues one frame's collision codes (see [RainStickSim.hits]). Safe from the UI thread. */
    fun post(hits: FloatArray) {
        if (incoming.size < 8) incoming += hits
    }

    private val active = BooleanArray(VOICES)
    private val delay = IntArray(VOICES)
    private val age = IntArray(VOICES)
    private val life = IntArray(VOICES)
    private val a1 = FloatArray(VOICES)
    private val a2 = FloatArray(VOICES)
    private val y1 = FloatArray(VOICES)
    private val y2 = FloatArray(VOICES)
    private val noiseAmp = FloatArray(VOICES)
    private var next = 0
    private var seed = 0x2545F491
    private var lp = 0f
    private val tube = Svf(760f, 0.5f)

    /** Cheap xorshift, 0 until 1. */
    private fun rand(): Float {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 17)
        seed = seed xor (seed shl 5)
        return (seed ushr 8) * (1f / (1 shl 24))
    }

    private fun spawn(code: Float) {
        val type = RainStickSim.hitType(code)
        val intensity = RainStickSim.hitIntensity(code).coerceIn(0f, 1f)
        val fLo: Float
        val fHi: Float
        val tauLo: Float
        val tauHi: Float
        val gain: Float
        when (type) {
            RainStickSim.TYPE_GRAIN -> { fLo = 4500f; fHi = 9000f; tauLo = 0.0008f; tauHi = 0.0018f; gain = 0.02f + 0.09f * intensity.pow(0.8f) }
            RainStickSim.TYPE_WALL -> { fLo = 1300f; fHi = 2600f; tauLo = 0.0025f; tauHi = 0.006f; gain = 0.03f + 0.15f * intensity.pow(0.8f) }
            else -> { fLo = 2800f; fHi = 6500f; tauLo = 0.0012f; tauHi = 0.0035f; gain = 0.03f + 0.17f * intensity.pow(0.8f) }
        }
        val f = fLo * (fHi / fLo).pow(rand())
        val tau = (tauLo + (tauHi - tauLo) * rand()) * RATE
        val r = exp(-1f / tau)
        val w = 2f * PI.toFloat() * f / RATE
        val a = gain * (0.7f + 0.6f * rand())

        val k = next
        next = (next + 1) % VOICES
        // y[n] = 2r·cos(w)·y[n-1] − r²·y[n-2], seeded to ring out as a·rⁿ·sin(w(n+1)).
        a1[k] = 2f * r * cos(w)
        a2[k] = -r * r
        y2[k] = 0f
        y1[k] = a * sin(w)
        noiseAmp[k] = a * 0.5f
        life[k] = (tau * 6.9f).toInt() + CLICK // ~-60 dB
        age[k] = 0
        delay[k] = (rand() * SPREAD).toInt()
        active[k] = true
    }

    override fun render(out: FloatArray) {
        while (true) {
            val frame = incoming.poll() ?: break
            for (code in frame) spawn(code)
        }

        for (k in 0 until VOICES) {
            if (!active[k]) continue
            var d = delay[k]
            var p1 = y1[k]
            var p2 = y2[k]
            var t = age[k]
            val c1 = a1[k]
            val c2 = a2[k]
            val end = life[k]
            val na = noiseAmp[k]
            for (i in out.indices) {
                if (d > 0) { d--; continue }
                val y = c1 * p1 + c2 * p2
                p2 = p1
                p1 = y
                var s = y
                if (t < CLICK) s += (rand() * 2f - 1f) * na * (1f - t / CLICK.toFloat())
                out[i] += s
                t++
                if (t >= end) { active[k] = false; break }
            }
            delay[k] = d
            y1[k] = p1
            y2[k] = p2
            age[k] = t
        }

        // Take the fizz off the top, and add a little hollow body resonance from the tube.
        for (i in out.indices) {
            lp += (out[i] - lp) * 0.6f
            out[i] = (lp + tube.process(lp) * 0.35f) * MASTER
        }
    }

    private companion object {
        const val RATE = PcmStream.SAMPLE_RATE
        const val VOICES = 96
        const val CLICK = 10        // samples of noise transient
        const val SPREAD = 800      // samples (~16 ms) to scatter one frame's clicks over
        const val MASTER = 1.5f
    }
}

