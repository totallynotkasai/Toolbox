package com.toolbox.app.tools.spinner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural sound for the spinners, synthesised on a background thread into a streaming AudioTrack
 * (no audio files to ship, and the sound can follow the physics continuously).
 *
 * - [tick] queues a short wooden "tock", for the wheel's clicker.
 * - [speed] (degrees/second) drives a whirr: airy noise whose brightness and loudness rise with speed,
 *   pulsing each time one of the [arms] sweeps past, plus a faint bearing rattle.
 */
class SpinAudio {
    @Volatile var speed: Float = 0f
    @Volatile var arms: Int = 3
    private val pendingTicks = AtomicInteger(0)
    @Volatile private var thread: Thread? = null

    private val sampleRate = 48_000

    fun tick() { pendingTicks.incrementAndGet() }

    fun start() {
        if (thread != null) return
        val t = Thread({ loop() }, "spin-audio").apply { priority = Thread.MAX_PRIORITY }
        thread = t
        t.start()
    }

    fun stop() { thread = null }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        val buf = ShortArray(256)
        val tickDecay = exp(-1.0 / (0.012 * sampleRate)).toFloat()
        val rattleDecay = exp(-1.0 / (0.002 * sampleRate)).toFloat()
        var tickEnv = 0f
        var tickPhase = 0.0
        var rattleEnv = 0f
        var armPhase = 0.0
        var lp1 = 0f
        var lp2 = 0f
        var smoothSpeed = 0f

        track.play()
        try {
            while (thread === Thread.currentThread()) {
                if (pendingTicks.getAndSet(0) > 0) { tickEnv = 1f; tickPhase = 0.0 }
                smoothSpeed += (abs(speed) - smoothSpeed) * 0.25f
                val norm = min(1f, smoothSpeed / 3000f)
                val whirrGain = norm.pow(0.7f) * 0.35f
                // Filter cutoff opens up as the spinner speeds up: dull hush -> bright whoosh.
                val cutoff = 0.02f + 0.35f * norm
                val armRate = smoothSpeed / 360.0 * arms

                for (i in buf.indices) {
                    var s = 0f
                    if (tickEnv > 0.0005f) {
                        // Two detuned partials + a click of noise reads as plastic/wood.
                        tickPhase += 1.0 / sampleRate
                        val body = sin(2 * PI * 1900 * tickPhase) * 0.6 + sin(2 * PI * 3100 * tickPhase) * 0.25
                        s += tickEnv * (body.toFloat() + (Random.nextFloat() - 0.5f) * 0.5f) * 0.55f
                        tickEnv *= tickDecay
                    }
                    if (whirrGain > 0.001f) {
                        armPhase += armRate / sampleRate
                        if (armPhase >= 1.0) { armPhase -= 1.0; rattleEnv = 1f }
                        val noise = Random.nextFloat() * 2f - 1f
                        lp1 += (noise - lp1) * cutoff
                        lp2 += (lp1 - lp2) * cutoff
                        val pulse = 0.55f + 0.45f * sin(2 * PI * armPhase).toFloat()
                        s += lp2 * whirrGain * pulse * 2.2f
                        s += rattleEnv * noise * whirrGain * 0.12f
                        rattleEnv *= rattleDecay
                    }
                    buf[i] = (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }
}
