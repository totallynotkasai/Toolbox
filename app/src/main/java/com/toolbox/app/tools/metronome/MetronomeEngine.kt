package com.toolbox.app.tools.metronome

import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import com.toolbox.app.ui.common.PcmStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sample-accurate metronome on [PcmStream]. Rather than sleeping between clicks (which drifts and jitters),
 * [ClickTrack] places each beat exactly `sampleRate * 60 / bpm` frames after the last, so timing is locked
 * to the audio clock.
 *
 * UI beat callbacks are posted with the track's current output latency so the visuals line up
 * with what you hear.
 */
class MetronomeEngine(private val onBeat: (beatIndex: Int) -> Unit) :
    PcmStream("metronome", AudioAttributes.USAGE_MEDIA) {
    @Volatile var bpm: Int = 100
    @Volatile var beatsPerBar: Int = 4
    @Volatile var accentFirstBeat: Boolean = true

    private val clicks = ClickTrack(SAMPLE_RATE, accent = click(freq = 1760.0, ms = 35), normal = click(freq = 1200.0, ms = 30))
    private val main = Handler(Looper.getMainLooper())

    override fun onStreamStart() = clicks.reset()

    override fun render(out: FloatArray) {
        clicks.bpm = bpm
        clicks.beatsPerBar = beatsPerBar
        clicks.accentFirstBeat = accentFirstBeat
        val queued = queuedFrames()
        clicks.fill(out) { index, offset ->
            // Frames already queued but not yet played = how far ahead of the speaker this block is.
            val latencyMs = (queued + offset) * 1000 / SAMPLE_RATE
            main.postDelayed({ if (isRunning) onBeat(index) }, latencyMs)
        }
    }

    // The clicks are pre-scaled to 0.9, so there's nothing to soft-clip.
    override fun clip(x: Float) = x

    override fun stop() {
        super.stop()
        main.removeCallbacksAndMessages(null)
    }

    /** Short sine burst with a fast exponential decay: a woodblock-ish tick that cuts through. */
    private fun click(freq: Double, ms: Int): FloatArray {
        val n = SAMPLE_RATE * ms / 1000
        return FloatArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            (sin(2 * PI * freq * t) * exp(-t * 120.0) * 0.9).toFloat()
        }
    }
}

/**
 * Lays [accent] / [normal] clicks into consecutive blocks at sample-exact beat positions. Tempo and bar
 * changes take effect from the next beat, so a beat that is already sounding is never cut short.
 */
internal class ClickTrack(private val sampleRate: Int, private val accent: FloatArray, private val normal: FloatArray) {
    var bpm = 100
    var beatsPerBar = 4
    var accentFirstBeat = true

    private var beat = 0       // Beats started so far.
    private var pos = 0        // Frames into the current beat.
    private var beatFrames = 0 // Length of the current beat; 0 until the first one starts.
    private var click = normal

    fun reset() {
        beat = 0
        pos = 0
        beatFrames = 0
    }

    /** Fills [out], calling [onBeat] with the bar position and the offset into [out] of every beat that starts in it. */
    fun fill(out: FloatArray, onBeat: (index: Int, offset: Int) -> Unit) {
        for (i in out.indices) {
            if (pos >= beatFrames) {
                pos = 0
                beatFrames = sampleRate * 60 / bpm.coerceIn(MIN_BPM, MAX_BPM)
                val index = beat % beatsPerBar.coerceAtLeast(1)
                click = if (index == 0 && accentFirstBeat) accent else normal
                beat++
                onBeat(index, i)
            }
            out[i] = if (pos < click.size) click[pos] else 0f
            pos++
        }
    }

    private companion object {
        const val MIN_BPM = 20
        const val MAX_BPM = 300
    }
}
