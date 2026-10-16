package com.toolbox.app.tools.soundscapes

import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import com.toolbox.app.ui.common.PcmStream
import kotlin.math.max
import kotlin.math.min

/**
 * Plays [Soundscapes.mix] in stereo, reading it afresh every block (about 21 ms), so a slider moves the sound at once.
 * Play fades in and [fadeOut] fades out, then [onFadedOut] is called on the main thread, where the stream can be
 * stopped. The sleep timer's last half minute fades it to silence, and [onSleepEnd] is called when it runs out.
 */
internal class SoundscapeEngine(
    private val onFadedOut: () -> Unit,
    private val onSleepEnd: () -> Unit,
) : PcmStream(
    "soundscapes",
    AudioAttributes.USAGE_MEDIA,
    AudioAttributes.CONTENT_TYPE_MUSIC,
    channels = 2,
    frames = FRAMES,
    lowLatency = false,
) {
    private val mixer = Mixer(SAMPLE_RATE, seed = System.nanoTime().toInt() or 1)
    private val main = Handler(Looper.getMainLooper())

    /** Fading in (or playing), or fading out. */
    @Volatile private var playing = false

    // Audio thread only.
    private var level = 0f
    private var fadedOut = false
    private var sleptOut = false

    fun play() {
        playing = true
        start()
    }

    fun fadeOut() {
        playing = false
    }

    override fun clip(x: Float): Float = softClip(x)

    override fun onStreamStart() {
        level = 0f
        fadedOut = false
        sleptOut = false
    }

    override fun render(out: FloatArray) {
        val wanted = playing
        val left = Soundscapes.playback.value.sleep.left(Soundscapes.now())
        // Once the sleep timer has run out it stays silent, even though Stop puts the timer back to its full length,
        // until Play starts it again (from silence).
        if (sleptOut && wanted && left != 0L) {
            sleptOut = false
            level = 0f
        }
        val from = level
        level = if (wanted) min(1f, level + frames / (FADE_IN_SECONDS * SAMPLE_RATE)) else max(0f, level - frames / (FADE_OUT_SECONDS * SAMPLE_RATE))
        if (!sleptOut) {
            mixer.render(out, frames, Soundscapes.mix.value)
            val sleepFrom = SleepTimer.fade(left)
            val sleepTo = SleepTimer.fade(left?.let { it - BLOCK_MS })
            for (f in 0 until frames) {
                val t = (f + 1f) / frames
                val fade = from + (level - from) * t
                // Squared, so the fade sounds even rather than rushing at the start.
                val g = fade * fade * (sleepFrom + (sleepTo - sleepFrom) * t)
                out[2 * f] *= g
                out[2 * f + 1] *= g
            }
        }
        if (wanted) {
            fadedOut = false
            if (left == 0L && !sleptOut) {
                sleptOut = true
                main.post(onSleepEnd)
            }
        } else if (level == 0f && !fadedOut) {
            fadedOut = true
            main.post(onFadedOut)
        }
    }

    private companion object {
        const val FRAMES = 1024
        const val BLOCK_MS = FRAMES * 1000L / PcmStream.SAMPLE_RATE
        const val FADE_IN_SECONDS = 1.2f
        const val FADE_OUT_SECONDS = 0.4f
    }
}
