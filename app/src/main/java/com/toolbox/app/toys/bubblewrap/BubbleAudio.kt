package com.toolbox.app.toys.bubblewrap

import com.toolbox.app.ui.common.PcmStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private val SR = PcmStream.SAMPLE_RATE.toDouble()
private const val MAX_VOICES = 14

/**
 * Procedural bubble-wrap sounds, all synthesised (no audio files):
 * - [pop]: a sharp crack of band-passed noise over a tiny low "thup"; centre pitch, ring time and body
 *   are randomised so a run of pops never sounds like a loop.
 * - [squeak]: a rubbery squeak for a stubborn bubble that squashes but won't give yet.
 * - [rustle]: a crinkle of plastic film as a fresh sheet rolls in.
 *
 * Calls are cheap and thread-safe: they only queue a voice for the audio thread.
 */
internal class BubbleAudio : PcmStream("bubble-audio") {
    private val incoming = ConcurrentLinkedQueue<Voice>()
    private val voices = ArrayList<Voice>()

    /** [strength] 0..1 nudges loudness; everything else is random per pop. */
    fun pop(strength: Float) { incoming += PopVoice(strength.coerceIn(0f, 1f).toDouble()) }

    fun squeak() { incoming += SqueakVoice() }

    fun rustle() { incoming += RustleVoice() }

    override fun render(out: FloatArray) {
        while (true) {
            val v = incoming.poll() ?: break
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices += v
        }
        for (v in voices) for (i in out.indices) out[i] += v.next()
        voices.removeAll { it.done }
    }
}

private abstract class Voice {
    protected var t = 0 // samples since start
    abstract val done: Boolean
    abstract fun next(): Float
}

private class PopVoice(strength: Double) : Voice() {
    // Chamberlin state-variable band-pass on white noise: a bright "pk" rather than a hiss.
    private val centre = 1500.0 * 2.0.pow(Random.nextDouble() * 1.2) // ~1.5–3.4 kHz
    private val f = 2 * sin(PI * centre / SR)
    private val damping = 0.7 + Random.nextDouble() * 0.5
    private var low = 0.0
    private var band = 0.0
    private var env = 1.0
    private val decay = exp(-1.0 / ((0.005 + Random.nextDouble() * 0.006) * SR))
    private val crackLen = (0.0012 * SR).toInt()
    // The air leaving the bubble: a very short low thump whose pitch sags as it dies.
    private val bodyHz = 120.0 + Random.nextDouble() * 70.0
    private var bodyPhase = 0.0
    private var bodyEnv = 1.0
    private val bodyDecay = exp(-1.0 / (0.016 * SR))
    private val gain = 0.5 + 0.45 * strength

    override val done get() = env < 1e-4 && bodyEnv < 1e-4

    override fun next(): Float {
        if (done) return 0f
        val noise = Random.nextDouble() * 2 - 1
        val high = noise - low - damping * band
        band += f * high
        low += f * band
        var s = band * env * 1.5
        if (t < crackLen) s += noise * (1.0 - t.toDouble() / crackLen) * 0.7
        bodyPhase += bodyHz * (0.75 + 0.25 * bodyEnv) / SR
        if (bodyPhase >= 1) bodyPhase -= 1
        s += sin(2 * PI * bodyPhase) * bodyEnv * 0.3
        env *= decay
        bodyEnv *= bodyDecay
        t++
        return (s * gain).toFloat()
    }
}

private class SqueakVoice : Voice() {
    private val len = (0.15 * SR).toInt()
    private val base = 560.0 + Random.nextDouble() * 180.0
    private var phase = 0.0

    override val done get() = t >= len

    override fun next(): Float {
        if (done) return 0f
        val x = t.toDouble() / len
        val time = t / SR
        val env = minOf(1.0, time / 0.012) * (1 - x).pow(1.4)
        // Glides up then sags, with a fast wobble: rubber dragging against itself.
        val hz = base * (1 + 0.5 * x - 0.3 * x * x) * (1 + 0.04 * sin(2 * PI * 31 * time))
        phase += hz / SR
        if (phase >= 1) phase -= 1
        val w = 2 * PI * phase
        val tone = sin(w) + 0.45 * sin(2 * w) + 0.2 * sin(3 * w)
        val chatter = 0.55 + 0.45 * sin(2 * PI * 47 * time) // stick-slip flutter
        t++
        return (tone * env * chatter * 0.2).toFloat()
    }
}

private class RustleVoice : Voice() {
    private val len = (0.5 * SR).toInt()
    private var crackEnv = 0.0
    private var crackGain = 0.0
    private val crackDecay = exp(-1.0 / (0.0012 * SR))
    private var lastNoise = 0.0
    private var lp = 0.0

    override val done get() = t >= len

    override fun next(): Float {
        if (done) return 0f
        val x = t.toDouble() / len
        val shape = sin(PI * x).pow(1.5) // swells then fades as the sheet slides past
        val noise = Random.nextDouble() * 2 - 1
        // Crinkle: sparse tiny snaps of differentiated (very bright) noise.
        if (Random.nextDouble() < 0.025 * shape) {
            crackEnv = 1.0
            crackGain = 0.3 + 0.7 * Random.nextDouble()
        }
        var s = (noise - lastNoise) * crackEnv * crackGain * 0.35
        crackEnv *= crackDecay
        // A soft whoosh of film under it.
        lp += (noise - lp) * 0.05
        s += lp * shape * 0.18
        lastNoise = noise
        t++
        return s.toFloat()
    }
}
