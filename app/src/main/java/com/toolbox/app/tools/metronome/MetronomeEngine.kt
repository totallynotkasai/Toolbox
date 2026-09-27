package com.toolbox.app.tools.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sample-accurate metronome. Rather than sleeping between clicks (which drifts and jitters),
 * we stream PCM into an AudioTrack: each beat is exactly `sampleRate * 60 / bpm` frames of
 * click-then-silence, so timing is locked to the audio clock.
 *
 * UI beat callbacks are posted with the track's current output latency so the visuals line up
 * with what you hear.
 */
class MetronomeEngine(private val onBeat: (beatIndex: Int) -> Unit) {
    @Volatile var bpm: Int = 100
    @Volatile var beatsPerBar: Int = 4
    @Volatile var accentFirstBeat: Boolean = true

    private val sampleRate = 48_000
    private val accentClick = click(freq = 1760.0, ms = 35)
    private val normalClick = click(freq = 1200.0, ms = 30)
    private val silence = ShortArray(sampleRate / 20) // 50 ms scratch buffer of zeros
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var thread: Thread? = null

    val isRunning get() = thread != null

    fun start() {
        if (thread != null) return
        val t = Thread({ loop() }, "metronome").apply { priority = Thread.MAX_PRIORITY }
        thread = t
        t.start()
    }

    fun stop() {
        val t = thread ?: return
        thread = null
        t.interrupt()
        main.removeCallbacksAndMessages(null)
    }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        track.play()
        var framesWritten = 0L
        var beat = 0
        try {
            while (thread === Thread.currentThread()) {
                val beatFrames = sampleRate * 60 / bpm.coerceIn(20, 300)
                val index = beat % beatsPerBar.coerceAtLeast(1)
                val clickBuf = if (index == 0 && accentFirstBeat) accentClick else normalClick

                // Frames already queued but not yet played = how far ahead of the speaker we are.
                val latencyMs = ((framesWritten - track.playbackHeadPosition.toLong()) * 1000 / sampleRate).coerceAtLeast(0)
                main.postDelayed({ if (thread != null) onBeat(index) }, latencyMs)

                framesWritten += writeFully(track, clickBuf, clickBuf.size.coerceAtMost(beatFrames))
                var remaining = beatFrames - clickBuf.size.coerceAtMost(beatFrames)
                while (remaining > 0 && thread === Thread.currentThread()) {
                    val n = remaining.coerceAtMost(silence.size)
                    framesWritten += writeFully(track, silence, n)
                    remaining -= n
                }
                beat++
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }

    private fun writeFully(track: AudioTrack, buf: ShortArray, count: Int): Int {
        var off = 0
        while (off < count) {
            val n = track.write(buf, off, count - off)
            if (n <= 0) break
            off += n
        }
        return off
    }

    /** Short sine burst with a fast exponential decay: a woodblock-ish tick that cuts through. */
    private fun click(freq: Double, ms: Int): ShortArray {
        val n = sampleRate * ms / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 120.0)
            (sin(2 * PI * freq * t) * env * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
    }
}
