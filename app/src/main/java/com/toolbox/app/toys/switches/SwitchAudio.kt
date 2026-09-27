package com.toolbox.app.toys.switches

import com.toolbox.app.ui.common.PcmStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private val SR = PcmStream.SAMPLE_RATE.toDouble()
private const val MAX_VOICES = 16

/** Gap between the toggle's faint pre-click and its firm snap; the vibration follows the same timing. */
internal const val TOGGLE_SNAP_MS = 24L

/**
 * Procedural sounds for the switch panel. Every sound is one or two [Knock]s: a short crack of noise that
 * excites a few damped resonances, tuned per control:
 * - toggle: tiny bright pre-click, then a firm metallic snap.
 * - rocker: a deep, dull plastic "thock".
 * - arcade button: a solid click down and a lighter, higher click up.
 * - clicky pen: click-in high and sharp, click-out lower with a little spring tick after it.
 * - detent: a small tick whose pitch rises along the slider.
 */
internal class SwitchAudio : PcmStream("switch-audio") {
    private val incoming = ConcurrentLinkedQueue<Knock>()
    private val voices = ArrayList<Knock>()

    fun toggle(on: Boolean) {
        val p = if (on) 1.0 else 0.92
        incoming += Knock(0.0, 0.1, d(5200.0 * p), d(2.5), d(1.0), crackMs = 0.4, crackGain = 0.5)
        incoming += Knock(
            TOGGLE_SNAP_MS.toDouble(), 0.42, d(2500.0 * p, 5100.0 * p, 880.0 * p), d(8.0, 3.5, 16.0), d(1.0, 0.45, 0.7),
            crackMs = 1.2, crackGain = 0.9,
        )
    }

    fun rocker(on: Boolean) {
        val p = if (on) 1.0 else 0.9
        incoming += Knock(
            0.0, 0.55, d(190.0 * p, 430.0 * p, 1150.0), d(34.0, 20.0, 7.0), d(1.0, 0.6, 0.3),
            crackMs = 2.5, crackGain = 0.8, crackCut = 0.18,
        )
    }

    fun arcadeDown() {
        incoming += Knock(
            0.0, 0.45, d(1250.0, 2900.0, 240.0), d(9.0, 4.0, 22.0), d(1.0, 0.5, 0.8),
            crackMs = 1.0, crackGain = 0.8, crackCut = 0.6,
        )
    }

    fun arcadeUp() {
        incoming += Knock(0.0, 0.22, d(1900.0, 4300.0), d(5.0, 2.5), d(1.0, 0.5), crackMs = 0.6, crackGain = 0.6)
    }

    fun penIn() {
        incoming += Knock(0.0, 0.38, d(3900.0, 6400.0), d(4.0, 2.0), d(1.0, 0.5), crackMs = 0.5, crackGain = 0.9)
    }

    fun penOut() {
        incoming += Knock(0.0, 0.3, d(2500.0, 4100.0), d(5.0, 3.0), d(1.0, 0.4), crackMs = 0.7, crackGain = 0.7, crackCut = 0.5)
        incoming += Knock(14.0, 0.08, d(3300.0), d(2.0), d(1.0), crackMs = 0.3, crackGain = 0.4)
    }

    /** Detent [index] (0-based) along the slider; higher detents tick a little higher. */
    fun detent(index: Int) {
        incoming += Knock(0.0, 0.26, d(2300.0 + 110.0 * index, 5000.0), d(2.5, 1.2), d(1.0, 0.35), crackMs = 0.35, crackGain = 0.6)
    }

    override fun render(out: FloatArray) {
        while (true) {
            val v = incoming.poll() ?: break
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices += v
        }
        for (v in voices) for (i in out.indices) out[i] += v.next()
        voices.removeAll { it.done }
    }

    private fun d(vararg v: Double): DoubleArray = v
}

/**
 * A mechanical click: after [delayMs], a [crackMs] burst of noise (low-passed by [crackCut], 1 = raw and bright)
 * plus damped sine partials at [freqs] Hz, each ringing for about [decaysMs] with starting level [amps].
 * Pitches get a small random detune so repeated clicks don't sound machine-identical.
 */
private class Knock(
    delayMs: Double,
    private val gain: Double,
    freqs: DoubleArray,
    decaysMs: DoubleArray,
    amps: DoubleArray,
    crackMs: Double = 0.8,
    private val crackGain: Double = 0.7,
    private val crackCut: Double = 1.0,
) {
    private val delay = (delayMs * SR / 1000).toInt()
    private val jitter = 1 + (Random.nextDouble() - 0.5) * 0.05
    private val inc = DoubleArray(freqs.size) { freqs[it] * jitter / SR }
    private val decays = DoubleArray(freqs.size) { exp(-1000.0 / (decaysMs[it] * SR)) }
    private val envs = amps.copyOf()
    private val phases = DoubleArray(freqs.size)
    private val crackLen = max(1, (crackMs * SR / 1000).toInt())
    private var lp = 0.0
    private var t = 0

    val done get() = t > delay + crackLen && envs.all { it < 1e-4 }

    fun next(): Float {
        if (t < delay) { t++; return 0f }
        var s = 0.0
        for (k in inc.indices) {
            phases[k] += inc[k]
            if (phases[k] >= 1) phases[k] -= 1
            s += sin(2 * PI * phases[k]) * envs[k]
            envs[k] *= decays[k]
        }
        val ct = t - delay
        if (ct < crackLen) {
            lp += (Random.nextDouble() * 2 - 1 - lp) * crackCut
            s += lp * crackGain * (1 - ct.toDouble() / crackLen)
        }
        t++
        return (s * gain).toFloat()
    }
}
