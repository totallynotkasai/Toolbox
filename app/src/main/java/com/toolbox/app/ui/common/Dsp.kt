package com.toolbox.app.ui.common

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Small building blocks shared by the PcmStream toys. Not thread-safe: use them on the audio thread.

/**
 * One struck, decaying sound: a few damped sine partials plus a burst of noise, optionally delayed by
 * [delay] samples and starting [bend] sharp then gliding down to pitch.
 */
class Ping(
    private val freqs: DoubleArray,
    decays: DoubleArray, // seconds for each partial to fall to 1/e
    amps: FloatArray,
    private val gain: Float,
    clickSeconds: Double = 0.001,
    private val clickGain: Float = 0.5f,
    private var bend: Double = 0.0,
    bendSeconds: Double = 0.01,
    private var delay: Int = 0,
) {
    private val sr = PcmStream.SAMPLE_RATE
    private val decay = FloatArray(freqs.size) { exp(-1.0 / (decays[it] * sr)).toFloat() }
    private val env = amps.copyOf()
    private val phase = DoubleArray(freqs.size)
    private val clickLen = max(1, (clickSeconds * sr).toInt())
    private val bendDecay = exp(-1.0 / (bendSeconds * sr))
    private var t = 0

    var done = false
        private set

    fun next(): Float {
        if (done) return 0f
        if (delay > 0) { delay--; return 0f }
        val k = 1.0 + bend
        bend *= bendDecay
        var s = 0.0
        var loudest = 0f
        for (j in freqs.indices) {
            phase[j] += freqs[j] * k / sr
            if (phase[j] >= 1.0) phase[j] -= 1.0
            s += sin(2 * PI * phase[j]) * env[j]
            env[j] *= decay[j]
            if (env[j] > loudest) loudest = env[j]
        }
        if (t < clickLen) s += (Random.nextFloat() * 2f - 1f) * clickGain * (1f - t / clickLen.toFloat())
        t++
        if (t > clickLen && loudest < 0.0005f) done = true
        return (s * gain).toFloat()
    }
}

/** Chamberlin state-variable filter, band-pass output; [damp] is 1/Q. */
class Svf(fc: Float, private val damp: Float) {
    private val f = 2f * sin(PI.toFloat() * fc / PcmStream.SAMPLE_RATE)
    private var low = 0f
    private var bp = 0f

    fun process(x: Float): Float {
        low += f * bp
        val high = x - low - damp * bp
        bp += f * high
        return bp
    }
}

/** A random multiplier within ±[amount]/2 of 1, so repeated hits don't all sound identical. */
fun jitter(amount: Double): Double = 1.0 + (Random.nextDouble() - 0.5) * amount
