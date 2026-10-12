package com.toolbox.app.toys.cat

import com.toolbox.app.ui.common.PcmStream
import com.toolbox.app.ui.common.Svf
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural cat: a purr, the whisper of fur under a finger, and two little vocalisations.
 *
 * - The purr is noise gated by a train of soft pulses at ~25 Hz (a real purr is the larynx snapping open and
 *   shut that fast), run through two body resonances at 170 and 330 Hz so a phone speaker can actually play it.
 *   Its loudness follows [purr] and the breathing envelope at [breath]; the out-breath purrs a touch faster.
 * - [brush] is a soft high "shhh" while the fur is being stroked.
 * - [grumble] is a low trilled "mrrrrp"; [chirp] a short rising "mrrp?".
 */
internal class CatAudio : PcmStream("cat-audio") {
    /** 0..1, how contented the cat is. */
    @Volatile var purr = 0f
    /** 0..1 through one breath: the first half is the in-breath. */
    @Volatile var breath = 0f
    /** 0..1, how fast the fur is being stroked right now. */
    @Volatile var brush = 0f

    private val incoming = ConcurrentLinkedQueue<Mrrp>()
    private val playing = ArrayList<Mrrp>()

    fun grumble() {
        incoming += Mrrp(
            f0Start = 150f, f0End = 112f, seconds = 0.55f, rising = false, trillHz = 27f, trillDepth = 0.8f,
            formant1 = 520f, formant2 = 1100f, rough = 0.7f, gain = 0.4f,
        )
    }

    fun chirp() {
        incoming += Mrrp(
            f0Start = 380f, f0End = 660f, seconds = 0.22f, rising = true, trillHz = 34f, trillDepth = 0.45f,
            formant1 = 820f, formant2 = 1750f, rough = 0.12f, gain = 0.42f,
        )
    }

    private val rng = Random(11)
    private val body1 = Svf(170f, 0.55f)
    private val body2 = Svf(330f, 0.8f)
    private val breathHiss = Svf(1200f, 1.2f)
    private val furHiss = Svf(3200f, 1.0f)
    private var env = 0f
    private var brushEnv = 0f
    private var pulsePhase = 0f
    private var pulseHz = 25f

    override fun render(out: FloatArray) {
        while (true) {
            val v = incoming.poll() ?: break
            if (playing.size < 4) playing += v
        }
        val level = purr
        val target = if (level < 0.02f) 0f else level.pow(0.8f) * purrEnvelope(breath)
        val exhale = breath >= 0.5f
        val brushTarget = brush
        val dt = 1f / RATE

        for (i in out.indices) {
            // Slew the envelopes so 60 Hz UI updates don't zipper.
            env += (target - env) * 0.0015f
            brushEnv += (brushTarget - brushEnv) * 0.002f
            val noise = rng.nextFloat() * 2f - 1f
            var s = 0f
            if (env > 0.0005f) {
                pulsePhase += pulseHz * dt
                if (pulsePhase >= 1f) {
                    pulsePhase -= 1f
                    // A little jitter per pulse keeps it organic rather than a motor.
                    pulseHz = (if (exhale) 27f else 23f) * (0.94f + 0.12f * rng.nextFloat())
                }
                val c = 0.5f - 0.5f * cos(TWO_PI * pulsePhase)
                val g = c * c * c * c * c * c // a narrow bump per pulse
                val exc = g * (0.7f + noise)
                s += (body1.process(exc) + 0.6f * body2.process(exc)) * env * PURR_GAIN
                s += breathHiss.process(noise) * env * 0.08f
            }
            if (brushEnv > 0.001f) s += furHiss.process(noise) * brushEnv * 0.15f
            out[i] += s
        }

        for (v in playing) v.render(out, rng)
        playing.removeAll { it.done }
    }

    /** One short voiced sound: a sawtooth with a trill, through two formants. */
    private class Mrrp(
        private val f0Start: Float,
        private val f0End: Float,
        seconds: Float,
        private val rising: Boolean,
        private val trillHz: Float,
        private val trillDepth: Float,
        formant1: Float,
        formant2: Float,
        private val rough: Float,
        private val gain: Float,
    ) {
        private val length = (seconds * RATE).toInt()
        private val attack = (0.03f * RATE).toInt()
        private val release = (0.06f * RATE).toInt()
        private val form1 = Svf(formant1, 0.35f)
        private val form2 = Svf(formant2, 0.5f)
        private var t = 0
        private var phase = 0f
        private var trillPhase = 0f

        val done get() = t >= length

        fun render(out: FloatArray, rng: Random) {
            val dt = 1f / RATE
            for (i in out.indices) {
                if (t >= length) return
                val x = t / length.toFloat()
                // A question lifts at the end; a grumble just sags.
                val shape = if (rising) x * x else x
                val f0 = f0Start + (f0End - f0Start) * shape
                phase += f0 * dt
                if (phase >= 1f) phase -= 1f
                trillPhase += trillHz * dt
                if (trillPhase >= 1f) trillPhase -= 1f
                val trill = 1f - trillDepth * (0.5f + 0.5f * sin(TWO_PI * trillPhase))
                val e = min(1f, t / attack.toFloat()) * min(1f, (length - t) / release.toFloat())
                val exc = (2f * phase - 1f) + rough * (rng.nextFloat() * 2f - 1f)
                out[i] += (form1.process(exc) + 0.5f * form2.process(exc)) * e * trill * gain
                t++
            }
        }
    }

    private companion object {
        const val TWO_PI = (2 * PI).toFloat()
        const val PURR_GAIN = 2.8f
        const val RATE = PcmStream.SAMPLE_RATE
    }
}

