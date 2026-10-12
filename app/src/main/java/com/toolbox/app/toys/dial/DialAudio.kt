package com.toolbox.app.toys.dial

import com.toolbox.app.ui.common.PcmStream
import com.toolbox.app.ui.common.Ping
import com.toolbox.app.ui.common.jitter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural sound for the rotary dial.
 *
 * One-shots, queued from the UI thread:
 * - [tick]: the light, bright detent of a fine-toothed encoder.
 * - [clunk]: a deep chunky detent, a spring-loaded ball dropping into a notch, with a short downward pitch bend.
 * - [grab]: a soft muted thump when a finger catches a free-spinning knob.
 *
 * Continuous: [whirr] (degrees/second) drives a soft airy bearing whirr and a low hum for Free mode, with a
 * faint flutter as the knurled ridges sweep past.
 */
internal class DialAudio : PcmStream("dial-audio") {
    @Volatile var whirr = 0f

    private val incoming = ConcurrentLinkedQueue<Ping>()
    private val voices = ArrayList<Ping>()

    private var smooth = 0f
    private var lp1 = 0f
    private var lp2 = 0f
    private var slow = 0f
    private var humPhase = 0.0
    private var ridgePhase = 0.0

    /** [strength] 0..1. */
    fun tick(strength: Float = 1f) {
        val j = jitter(0.05)
        incoming += Ping(
            freqs = doubleArrayOf(3900.0 * j, 6300.0 * j, 1500.0 * j),
            decays = doubleArrayOf(0.004, 0.0022, 0.009),
            amps = floatArrayOf(1f, 0.45f, 0.4f),
            gain = 0.17f * strength.coerceIn(0f, 1f),
            clickSeconds = 0.0007,
            clickGain = 0.8f,
        )
    }

    /** [strength] 0..1. */
    fun clunk(strength: Float = 1f) {
        val j = jitter(0.04)
        val s = strength.coerceIn(0f, 1f)
        // The ball leaving the old notch: a small dry tick just before the drop.
        incoming += Ping(
            freqs = doubleArrayOf(2400.0 * j, 5200.0 * j),
            decays = doubleArrayOf(0.003, 0.0015),
            amps = floatArrayOf(1f, 0.5f),
            gain = 0.07f * s,
            clickSeconds = 0.0005,
            clickGain = 0.5f,
        )
        // Landing in the new one: a heavy body thunk with some mid-range knock and a crisp edge.
        incoming += Ping(
            freqs = doubleArrayOf(165.0 * j, 420.0 * j, 1150.0 * j, 2700.0 * j),
            decays = doubleArrayOf(0.045, 0.025, 0.012, 0.005),
            amps = floatArrayOf(1f, 0.55f, 0.4f, 0.22f),
            gain = 0.42f * s,
            clickSeconds = 0.002,
            clickGain = 0.7f,
            bend = 0.25,
            bendSeconds = 0.01,
            delay = 190, // ~4 ms after the tick
        )
    }

    /** [strength] 0..1, usually the speed the finger stopped relative to a fast spin. */
    fun grab(strength: Float) {
        incoming += Ping(
            freqs = doubleArrayOf(140.0, 380.0),
            decays = doubleArrayOf(0.03, 0.012),
            amps = floatArrayOf(1f, 0.4f),
            gain = 0.1f + 0.25f * strength.coerceIn(0f, 1f),
            clickSeconds = 0.001,
            clickGain = 0.25f,
        )
    }

    override fun render(out: FloatArray) {
        while (true) {
            val v = incoming.poll() ?: break
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices += v
        }
        val target = min(1f, abs(whirr) / FULL_WHIRR)
        val ridgeRate = abs(whirr) / 360.0 * 18.0

        for (i in out.indices) {
            smooth += (target - smooth) * 0.0006f // ~35 ms glide so speed changes don't zipper
            var s = 0f
            for (v in voices) s += v.next()

            if (smooth > 0.001f) {
                // Airy band of noise that opens up as it speeds up.
                val noise = Random.nextFloat() * 2f - 1f
                val cut = 0.03f + 0.18f * smooth
                lp1 += (noise - lp1) * cut
                lp2 += (lp1 - lp2) * cut
                slow += (lp2 - slow) * 0.01f
                ridgePhase += ridgeRate / SR
                if (ridgePhase >= 1.0) ridgePhase -= 1.0
                val flutter = 0.8f + 0.2f * sin(2 * PI * ridgePhase).toFloat()
                s += (lp2 - slow) * smooth.pow(0.7f) * 0.9f * flutter

                // Soft bearing hum, pitch rising with speed.
                humPhase += (70.0 + 220.0 * smooth) / SR
                if (humPhase >= 1.0) humPhase -= 1.0
                s += sin(2 * PI * humPhase).toFloat() * smooth * 0.05f
            }
            out[i] = s
        }
        voices.removeAll { it.done }
    }

    private companion object {
        const val SR = PcmStream.SAMPLE_RATE
        const val MAX_VOICES = 24
        const val FULL_WHIRR = 2880f // deg/s at which the whirr is at full volume
    }
}

