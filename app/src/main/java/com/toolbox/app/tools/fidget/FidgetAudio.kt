package com.toolbox.app.tools.fidget

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Procedural fidget-spinner sound, synthesised on a background thread into a streaming AudioTrack.
 *
 * Layers, all driven by [speed] (degrees/second):
 * - an airy "vvvv" whoosh, band-passed noise gated each time an arm sweeps past. Slow spins give
 *   distinct whoomps; fast ones blur into a continuous buzz.
 * - a soft bearing whine whose pitch climbs with speed, the part that makes a fast spin satisfying.
 * - a faint purr of tiny bearing clicks.
 *
 * One-shots: [flick] is a "fwip" on release, [grab] a muted thump when a finger stops a spin.
 */
class FidgetAudio(private val maxSpeed: Float, private val arms: Int = 3) {
    @Volatile var speed: Float = 0f
    @Volatile private var flickStrength = 0f
    @Volatile private var grabStrength = 0f
    @Volatile private var thread: Thread? = null

    private val sampleRate = 48_000

    /** [strength] 0..1, usually release speed / max speed. */
    fun flick(strength: Float) { flickStrength = strength.coerceIn(0.15f, 1f) }

    /** [strength] 0..1, usually the speed the finger stopped / max speed. */
    fun grab(strength: Float) { if (strength > 0.02f) grabStrength = strength.coerceAtMost(1f) }

    fun start() {
        if (thread != null) return
        val t = Thread({ loop() }, "fidget-audio").apply { priority = Thread.MAX_PRIORITY }
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
        val dt = 1.0 / sampleRate
        val purrDecay = exp(-1.0 / (0.0008 * sampleRate)).toFloat()
        val flickDecay = exp(-1.0 / (0.07 * sampleRate)).toFloat()
        val grabDecay = exp(-1.0 / (0.03 * sampleRate)).toFloat()

        var smoothSpeed = 0f
        var armPhase = 0.0
        var whinePhase = 0.0
        var airFast = 0f
        var airFast2 = 0f
        var airSlow = 0f
        var purrEnv = 0f
        var lastNoise = 0f
        var flickEnv = 0f
        var flickGain = 0f
        var flickLp1 = 0f
        var flickLp2 = 0f
        var grabEnv = 0f
        var grabGain = 0f
        var grabPhase = 0.0

        track.play()
        try {
            while (thread === Thread.currentThread()) {
                if (flickStrength > 0f) { flickGain = flickStrength; flickEnv = 1f; flickStrength = 0f }
                if (grabStrength > 0f) { grabGain = grabStrength; grabEnv = 1f; grabPhase = 0.0; grabStrength = 0f }

                smoothSpeed += (abs(speed) - smoothSpeed) * 0.2f
                val pitch = min(1f, smoothSpeed / maxSpeed)       // 0..1 across the full range
                val loud = min(1f, smoothSpeed / (maxSpeed * 0.55f)) // reaches full volume a bit earlier
                val armRate = smoothSpeed / 360.0 * arms
                val airGain = loud.pow(0.6f) * 0.42f
                val airCut = 0.025f + 0.22f * pitch
                val gateSharpness = 6f - 4.8f * pitch // distinct whoomps when slow, a smooth buzz when fast
                val whineHz = 230.0 + 620.0 * pitch
                val whineGain = pitch.pow(1.2f) * 0.085f
                val purrRate = (25f + 450f * pitch) / sampleRate
                val purrGain = sqrt(loud) * 0.07f

                for (i in buf.indices) {
                    var s = 0f
                    val noise = Random.nextFloat() * 2f - 1f

                    if (airGain > 0.001f) {
                        armPhase += armRate * dt
                        if (armPhase >= 1.0) armPhase -= 1.0
                        val lobe = (0.5f + 0.5f * cos(2 * PI * armPhase).toFloat()).pow(gateSharpness)
                        airFast += (noise - airFast) * airCut
                        airFast2 += (airFast - airFast2) * airCut
                        airSlow += (airFast2 - airSlow) * 0.004f
                        s += (airFast2 - airSlow) * airGain * (0.3f + 0.7f * lobe) * 2.6f

                        whinePhase += whineHz * dt
                        if (whinePhase >= 1.0) whinePhase -= 1.0
                        val w = 2 * PI * whinePhase
                        s += (sin(w) + 0.3 * sin(2 * w) + 0.12 * sin(3 * w)).toFloat() * whineGain * (0.85f + 0.15f * lobe)

                        if (Random.nextFloat() < purrRate) purrEnv = 0.6f + 0.4f * Random.nextFloat()
                        s += (noise - lastNoise) * purrEnv * purrGain
                        purrEnv *= purrDecay
                    }
                    lastNoise = noise

                    if (flickEnv > 0.001f) {
                        // Noise through a low-pass that closes as the burst fades: "fwip".
                        val cut = 0.03f + 0.3f * flickEnv
                        flickLp1 += (noise - flickLp1) * cut
                        flickLp2 += (flickLp1 - flickLp2) * cut
                        s += flickLp2 * flickEnv * flickGain * 1.1f
                        flickEnv *= flickDecay
                    }
                    if (grabEnv > 0.001f) {
                        grabPhase += 150.0 * dt
                        s += sin(2 * PI * grabPhase).toFloat() * grabEnv * grabGain * 0.35f
                        grabEnv *= grabDecay
                    }

                    buf[i] = (tanh(s * 1.3f) * 0.9f * Short.MAX_VALUE).toInt().toShort()
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
