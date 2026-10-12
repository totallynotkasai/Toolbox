package com.toolbox.app.tools.metronome

import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import com.toolbox.app.ui.common.PcmStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** How hard a beat is clicked: the bar's first beat, a lighter secondary accent (beat 4 of 6/8), or a plain beat. */
enum class Accent { Strong, Light, Normal }

/** The time signatures on offer. Every one clicks [beats] times a bar, so in 6/8 the tempo counts quavers. */
enum class TimeSignature(val label: String, val beats: Int, val description: String) {
    TwoFour("2/4", 2, "2 beats per bar"),
    ThreeFour("3/4", 3, "3 beats per bar"),
    FourFour("4/4", 4, "4 beats per bar"),
    SixFour("6/4", 6, "6 beats per bar"),
    SixEight("6/8", 6, "6 8 time, 6 quavers per bar");

    /** Beats per group of dots: 6/8 shows two groups of three. */
    val group: Int get() = if (this == SixEight) 3 else beats

    /** Each beat's accent; all plain when [accentOn] is off. 6/8 is strong on 1 and light on 4. */
    fun accents(accentOn: Boolean): List<Accent> = List(beats) { i ->
        when {
            !accentOn -> Accent.Normal
            i == 0 -> Accent.Strong
            i % group == 0 -> Accent.Light
            else -> Accent.Normal
        }
    }
}

/** Clicks per beat. [label] is what the chip shows and [description] what TalkBack says. */
enum class Subdivision(val count: Int, val label: String, val description: String) {
    Off(1, "Off", "No subdivisions"),
    Half(2, "½", "2 per beat"),
    Triplets(3, "Triplets", "Triplets"),
    Quarter(4, "¼", "4 per beat"),
}

/**
 * Sample-accurate metronome on [PcmStream]. Rather than sleeping between clicks (which drifts and jitters),
 * [ClickTrack] places every click at an exact frame worked out from the tempo, so timing is locked
 * to the audio clock.
 *
 * UI beat callbacks are posted with the track's current output latency so the visuals line up
 * with what you hear.
 */
class MetronomeEngine(private val onBeat: (beatIndex: Int) -> Unit) :
    PcmStream("metronome", AudioAttributes.USAGE_MEDIA) {
    @Volatile var bpm: Int = 100
    @Volatile var accents: List<Accent> = TimeSignature.FourFour.accents(true)
    @Volatile var subdivision: Int = 1

    private val clicks = ClickTrack(
        SAMPLE_RATE,
        strong = click(freq = 1760.0, ms = 35),
        light = click(freq = 1480.0, ms = 32),
        normal = click(freq = 1200.0, ms = 30),
        sub = click(freq = 1200.0, ms = 20, gain = 0.35),
    )
    private val main = Handler(Looper.getMainLooper())

    override fun onStreamStart() = clicks.reset()

    override fun render(out: FloatArray) {
        clicks.bpm = bpm
        clicks.accents = accents
        clicks.subdivision = subdivision
        val queued = queuedFrames()
        clicks.fill(out) { index, offset ->
            // Frames already queued but not yet played = how far ahead of the speaker this block is.
            val latencyMs = (queued + offset) * 1000 / SAMPLE_RATE
            main.postDelayed({ if (isRunning) onBeat(index) }, latencyMs)
        }
    }

    // The clicks are pre-scaled to 0.9 or less, so there's nothing to soft-clip.
    override fun clip(x: Float) = x

    override fun stop() {
        super.stop()
        main.removeCallbacksAndMessages(null)
    }

    /** Short sine burst with a fast exponential decay: a woodblock-ish tick that cuts through. */
    private fun click(freq: Double, ms: Int, gain: Double = 0.9): FloatArray {
        val n = SAMPLE_RATE * ms / 1000
        return FloatArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            (sin(2 * PI * freq * t) * exp(-t * 120.0) * gain).toFloat()
        }
    }
}

/**
 * Lays clicks into consecutive blocks at sample-exact positions: [strong], [light] or [normal] on each beat, as
 * [accents] says, and the quieter [sub] between beats when [subdivision] is above 1.
 *
 * Click `n` sits at frame `n * 60 * sampleRate / (bpm * subdivision)`, counted from where the current tempo
 * started, so beats and triplets never drift however long it plays. Tempo, subdivision and bar changes take
 * effect from the next beat, so a beat that is already sounding is never cut short.
 */
internal class ClickTrack(
    private val sampleRate: Int,
    private val strong: FloatArray,
    private val light: FloatArray,
    private val normal: FloatArray,
    private val sub: FloatArray,
) {
    var bpm = 100
    var accents: List<Accent> = TimeSignature.FourFour.accents(true)
    var subdivision = 1

    private var runBpm = 0      // Tempo and subdivision of the current run; 0 until the first beat starts one.
    private var runSub = 1
    private var runFrame = 0L   // Frames since the current run started.
    private var tick = 0L       // Clicks started in the current run.
    private var nextAt = 0L     // runFrame of the next click.
    private var beat = 0        // Beats started in the current bar pattern.
    private var barSize = 0
    private var click = normal
    private var clickPos = Int.MAX_VALUE

    fun reset() {
        runBpm = 0
        runSub = 1
        runFrame = 0
        tick = 0
        nextAt = 0
        beat = 0
        clickPos = Int.MAX_VALUE
    }

    /** Fills [out], calling [onBeat] with the bar position and the offset into [out] of every beat that starts in it. */
    fun fill(out: FloatArray, onBeat: (index: Int, offset: Int) -> Unit) {
        for (i in out.indices) {
            if (runFrame == nextAt) {
                if (tick % runSub == 0L) {
                    val b = bpm.coerceIn(MIN_BPM, MAX_BPM)
                    val s = subdivision.coerceIn(1, MAX_SUBDIVISION)
                    if (b != runBpm || s != runSub) {
                        runBpm = b
                        runSub = s
                        runFrame = 0
                        tick = 0
                    }
                    val bar = accents
                    // A new signature starts a new bar rather than landing part-way through one.
                    if (bar.size != barSize) { barSize = bar.size; beat = 0 }
                    val index = if (bar.isEmpty()) 0 else beat % bar.size
                    click = when (bar.getOrNull(index)) {
                        Accent.Strong -> strong
                        Accent.Light -> light
                        else -> normal
                    }
                    beat = (index + 1) % bar.size.coerceAtLeast(1)
                    onBeat(index, i)
                } else {
                    click = sub
                }
                clickPos = 0
                tick++
                nextAt = tick * 60 * sampleRate / (runBpm.toLong() * runSub)
            }
            out[i] = if (clickPos < click.size) click[clickPos++] else 0f
            runFrame++
        }
    }

    private companion object {
        const val MIN_BPM = 20
        const val MAX_BPM = 300
        const val MAX_SUBDIVISION = 4
    }
}
