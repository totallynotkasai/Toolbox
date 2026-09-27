package com.toolbox.app.ui.common

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlin.math.tanh

/**
 * Base for a toy's procedural sound: a background thread asks [render] for blocks of mono samples
 * (roughly -1..1) at [SAMPLE_RATE] and streams them into a low-latency AudioTrack, soft-clipped.
 * The same shape as [ToneSynth] and SpinAudio, without the boilerplate.
 *
 * Subclasses keep their own state. Anything the UI thread changes should be @Volatile, or handed over
 * through a thread-safe queue / atomic, and read once at the top of [render].
 * Use [rememberPcmStream] so it only runs while the screen is started.
 */
abstract class PcmStream(private val threadName: String) {
    @Volatile private var thread: Thread? = null

    /** Fill all of [out] with the next samples. Called on the audio thread, ~190 times a second. */
    protected abstract fun render(out: FloatArray)

    fun start() {
        if (thread != null) return
        val t = Thread({ loop() }, threadName).apply { priority = Thread.MAX_PRIORITY }
        thread = t
        t.start()
    }

    fun stop() { thread = null }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        val block = FloatArray(BLOCK)
        val pcm = ShortArray(BLOCK)
        track.play()
        try {
            while (thread === Thread.currentThread()) {
                block.fill(0f)
                render(block)
                for (i in block.indices) pcm[i] = (tanh(block[i]) * Short.MAX_VALUE).toInt().toShort()
                track.write(pcm, 0, BLOCK)
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val BLOCK = 256
    }
}

/** Creates a [PcmStream] once and runs it while this composable is on screen and the app is started. */
@Composable
fun <T : PcmStream> rememberPcmStream(factory: () -> T): T {
    val stream = remember { factory() }
    LifecycleStartEffect(stream) {
        stream.start()
        onStopOrDispose { stream.stop() }
    }
    return stream
}
