package com.toolbox.app.ui.common

import androidx.compose.runtime.Composable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small polyphonic synth for one-shot sounds, built on [PcmStream] (no audio files, low latency).
 *
 * - [play]: a clean tone with a short attack/release envelope (Simon pads).
 * - [buzz]: a harsh low "wrong" buzz.
 * - [clink]: a metallic coin clink made of a few inharmonic partials; [clink]'s intensity (0–1) sets loudness,
 *   ring time and a touch of pitch.
 * - [rattle]: a very short metallic tick, repeated quickly for a coin spinning down on a table.
 * - [knock]: a dull plastic clack, for dice hitting the table or each other.
 * - [tick]: a short wooden "tock", for the wheel spinner's clicker.
 *
 * Calls are thread-safe and cheap: they only queue a voice for the audio thread. Sounds asked for while stopped
 * are dropped, and anything still queued is cleared on [stop], so nothing bursts out on returning to the app.
 * After ~2 s of silence the track is paused until the next sound.
 * In Compose, prefer [rememberToneSynth], which runs the synth only while the screen is started.
 */
class ToneSynth : PcmStream("tone-synth") {
    private val sampleRate = SAMPLE_RATE
    private val incoming = ConcurrentLinkedQueue<Voice>()
    private val releaseAll = AtomicBoolean(false)
    private val voices = ArrayList<Voice>() // Audio thread only.

    fun play(freqHz: Float, ms: Int, volume: Float = 0.5f) {
        queue(ToneVoice(freqHz.toDouble(), samples(ms / 1000.0), volume.coerceIn(0f, 1f)))
    }

    fun buzz(ms: Int = 500) { queue(BuzzVoice(samples(ms / 1000.0))) }

    fun clink(intensity: Float = 1f) { queue(ClinkVoice(intensity.coerceIn(0f, 1f))) }

    /** A very short metallic tick, for a coin rattling as it spins down on a table. */
    fun rattle(intensity: Float = 0.5f) { queue(RattleVoice(intensity.coerceIn(0f, 1f))) }

    /** A short hollow clack, for a die landing; [intensity] 0–1 sets loudness and brightness. */
    fun knock(intensity: Float = 0.5f) { queue(KnockVoice(intensity.coerceIn(0f, 1f))) }

    /** A short wooden "tock", for a wheel's clicker. */
    fun tick() { queue(TickVoice()) }

    /** Fades out everything that is sounding, e.g. when a game ends mid-sequence. */
    fun silence() {
        incoming.clear()
        releaseAll.set(true)
    }

    private fun queue(v: Voice) {
        if (!isRunning) return
        incoming += v
        wake()
    }

    /** Starts the synth with an empty queue. */
    override fun start() {
        if (!isRunning) incoming.clear()
        super.start()
    }

    /** Stops the synth and drops anything still queued. */
    override fun stop() {
        super.stop()
        incoming.clear()
    }

    private fun samples(seconds: Double) = (seconds * sampleRate).toInt()

    override fun onStreamStart() {
        voices.clear()
        releaseAll.set(false)
    }

    override fun isQuiet() = voices.isEmpty() && incoming.isEmpty()

    override fun render(out: FloatArray) {
        if (releaseAll.getAndSet(false)) voices.forEach { it.release() }
        while (true) {
            val v = incoming.poll() ?: break
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices += v
        }
        for (i in out.indices) {
            var s = 0f
            for (v in voices) s += v.next()
            out[i] = s
        }
        voices.removeAll { it.done }
    }

    private abstract inner class Voice {
        protected var t = 0 // samples since start
        abstract val done: Boolean
        abstract fun next(): Float
        abstract fun release()
    }

    /** Linear attack, hold for [length] samples, linear release. */
    private abstract inner class EnvelopedVoice(private var length: Int, private val attack: Int, private val releaseLen: Int) : Voice() {
        override val done get() = t >= length + releaseLen
        protected fun envelope(): Float = when {
            t < attack -> t / attack.toFloat()
            t < length -> 1f
            else -> (1f - (t - length) / releaseLen.toFloat()).coerceAtLeast(0f)
        }
        override fun release() { if (t < length) length = t }
    }

    private inner class ToneVoice(private val freq: Double, length: Int, private val volume: Float) :
        EnvelopedVoice(length, samples(0.005), samples(0.06)) {
        private var phase = 0.0
        override fun next(): Float {
            if (done) return 0f
            phase += freq / sampleRate
            // A little 2nd and 3rd harmonic keeps it from sounding like a test tone.
            val x = 2 * PI * phase
            val wave = sin(x) + 0.18 * sin(2 * x) + 0.08 * sin(3 * x)
            val out = (wave * envelope() * volume * 0.6).toFloat()
            t++
            return out
        }
    }

    private inner class BuzzVoice(length: Int) : EnvelopedVoice(length, samples(0.005), samples(0.05)) {
        private var p1 = 0.0
        private var p2 = 0.0
        override fun next(): Float {
            if (done) return 0f
            // Two slightly detuned sawtooths beat against each other: an unmistakable "wrong".
            p1 = (p1 + 55.0 / sampleRate) % 1.0
            p2 = (p2 + 58.5 / sampleRate) % 1.0
            val saw = (2 * p1 - 1) + (2 * p2 - 1)
            val out = (saw * 0.22 * envelope()).toFloat()
            t++
            return out
        }
    }

    private inner class ClinkVoice(intensity: Float) : Voice() {
        // Ratios of a struck metal disc are inharmonic; the random detune stops repeat clinks sounding identical.
        private val base = 2300.0 * (1 + intensity * 0.06) * (1 + (Random.nextDouble() - 0.5) * 0.06)
        private val freqs = doubleArrayOf(1.0, 2.32, 3.85, 5.61).map { it * base }
        private val amps = floatArrayOf(1f, 0.6f, 0.45f, 0.3f)
        private val ring = 0.5 + 0.5 * intensity // harder hits ring longer
        private val decays = doubleArrayOf(0.35, 0.22, 0.12, 0.07).map { exp(-1.0 / (it * ring * sampleRate)).toFloat() }
        private val envs = amps.copyOf()
        private val phases = DoubleArray(4)
        private val gain = 0.28f * intensity.pow(0.7f).coerceAtLeast(0.08f)
        private val clickLen = samples(0.003)

        override val done get() = envs[0] < 0.0005f
        override fun next(): Float {
            if (done) return 0f
            var s = 0.0
            for (k in 0 until 4) {
                phases[k] += freqs[k] / sampleRate
                s += sin(2 * PI * phases[k]) * envs[k]
                envs[k] *= decays[k]
            }
            if (t < clickLen) s += (Random.nextFloat() - 0.5f) * (1f - t / clickLen.toFloat())
            t++
            return (s * gain).toFloat()
        }
        override fun release() { for (k in envs.indices) envs[k] = minOf(envs[k], 0.01f) }
    }

    private inner class RattleVoice(intensity: Float) : Voice() {
        private val freq = 3400.0 * (1 + (Random.nextDouble() - 0.5) * 0.1)
        private val decay = exp(-1.0 / (0.012 * sampleRate)).toFloat()
        private var env = 1f
        private var phase = 0.0
        private val gain = 0.05f + 0.2f * intensity
        private val clickLen = samples(0.0015)

        override val done get() = env < 0.001f
        override fun next(): Float {
            if (done) return 0f
            phase += freq / sampleRate
            var s = sin(2 * PI * phase).toFloat() * env
            if (t < clickLen) s += (Random.nextFloat() - 0.5f) * 1.5f * (1f - t / clickLen.toFloat())
            env *= decay
            t++
            return s * gain
        }
        override fun release() { env = minOf(env, 0.01f) }
    }

    private inner class KnockVoice(intensity: Float) : Voice() {
        // Two damped body resonances plus a tick of noise: reads as a plastic cube on a wooden table.
        private val jitter = 1 + (Random.nextDouble() - 0.5) * 0.12
        private val freqs = doubleArrayOf(820.0 * jitter, 1930.0 * jitter * (1 + intensity * 0.1))
        private val decays = doubleArrayOf(0.022, 0.011).map { exp(-1.0 / (it * sampleRate)).toFloat() }
        private val envs = floatArrayOf(1f, 0.35f + 0.4f * intensity)
        private val phases = DoubleArray(2)
        private val gain = 0.32f * intensity.pow(0.8f).coerceAtLeast(0.06f)
        private val clickLen = samples(0.002)

        override val done get() = envs[0] < 0.001f
        override fun next(): Float {
            if (done) return 0f
            var s = 0.0
            for (k in 0 until 2) {
                phases[k] += freqs[k] / sampleRate
                s += sin(2 * PI * phases[k]) * envs[k]
                envs[k] *= decays[k]
            }
            if (t < clickLen) s += (Random.nextFloat() - 0.5f) * 1.2f * (1f - t / clickLen.toFloat())
            t++
            return (s * gain).toFloat()
        }
        override fun release() { for (k in envs.indices) envs[k] = minOf(envs[k], 0.01f) }
    }

    private inner class TickVoice : Voice() {
        // Two detuned partials and a click of noise read as plastic or wood.
        private val decay = exp(-1.0 / (0.012 * sampleRate)).toFloat()
        private var env = 1f

        override val done get() = env < 0.0005f
        override fun next(): Float {
            if (done) return 0f
            val time = t.toDouble() / sampleRate
            val body = sin(2 * PI * 1900 * time) * 0.6 + sin(2 * PI * 3100 * time) * 0.25
            val out = env * (body.toFloat() + (Random.nextFloat() - 0.5f) * 0.5f) * 0.55f
            env *= decay
            t++
            return out
        }
        override fun release() { env = minOf(env, 0.01f) }
    }

    private companion object { const val MAX_VOICES = 16 }
}

/** A [ToneSynth] that runs while this composable is on screen and the app is started. */
@Composable
fun rememberToneSynth(): ToneSynth = rememberPcmStream { ToneSynth() }
