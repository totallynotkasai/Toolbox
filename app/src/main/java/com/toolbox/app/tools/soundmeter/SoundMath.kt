package com.toolbox.app.tools.soundmeter

import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Quietest level reported, so digital silence doesn't give -Infinity. */
const val FLOOR_DBFS = -120f

/** The calibration slider's range, in dB added to the dBFS reading. */
const val OFFSET_MIN = 60f
const val OFFSET_MAX = 150f

/**
 * Starting calibration for a mic source (a MediaRecorder.AudioSource value), from the levels Android's
 * compatibility spec asks phones to record: on UNPROCESSED, 94 dB SPL at 1 kHz reads an RMS of 520 (about −36 dBFS);
 * on VOICE_RECOGNITION, 90 dB SPL reads 2500 (about −22 dBFS). The plain MIC path has no target, so it shares the
 * voice recognition figure.
 */
fun defaultOffset(source: Int): Float = if (source == MediaRecorder.AudioSource.UNPROCESSED) 130f else 112f

/** Each source is calibrated separately, because they record at levels about 18 dB apart. */
fun offsetPrefKey(source: Int): String = "soundmeter_offset_" + when (source) {
    MediaRecorder.AudioSource.UNPROCESSED -> "unprocessed"
    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "voice"
    else -> "mic"
}

/**
 * RMS level of the first [count] 16-bit samples in dB relative to full scale (0 dBFS = the loudest possible signal).
 * The mean is removed first, because some mics carry a DC offset that would otherwise read as a constant noise.
 */
fun rmsDbfs(samples: ShortArray, count: Int = samples.size): Float {
    if (count <= 0) return FLOOR_DBFS
    var sum = 0.0
    for (i in 0 until count) sum += samples[i]
    val mean = sum / count
    var sq = 0.0
    for (i in 0 until count) { val d = samples[i] - mean; sq += d * d }
    val rms = sqrt(sq / count)
    if (rms <= 0.0) return FLOOR_DBFS
    return (20 * log10(rms / 32768.0)).toFloat().coerceAtLeast(FLOOR_DBFS)
}

/**
 * Running Min / Max / Leq over a measurement. Levels go in as dBFS so a later calibration change shifts every figure
 * consistently. Leq is the energy average (10·log10 of the mean power), which is what sound level meters show as
 * the average: a few loud moments pull it up far more than a plain mean of the dB values would.
 */
class LevelStats {
    var min = Float.NaN; private set
    var max = Float.NaN; private set
    var count = 0; private set
    private var energy = 0.0

    val leq: Float get() = if (count == 0) Float.NaN else (10 * log10(energy / count)).toFloat()

    fun add(db: Float) {
        if (count == 0 || db < min) min = db
        if (count == 0 || db > max) max = db
        energy += 10.0.pow(db / 10.0)
        count++
    }

    fun reset() { min = Float.NaN; max = Float.NaN; count = 0; energy = 0.0 }
}

/**
 * Peak-hold marker: jumps up to any new peak, stays there for [holdMs], then falls at [fallDbPerSec]
 * until it meets the live level again.
 */
class PeakHold(private val holdMs: Long = 1500, private val fallDbPerSec: Float = 20f) {
    var value = Float.NaN; private set
    private var heldAt = 0L
    private var lastMs = 0L

    fun update(db: Float, nowMs: Long): Float {
        if (value.isNaN() || db >= value) {
            value = db; heldAt = nowMs
        } else if (nowMs - heldAt > holdMs) {
            value = maxOf(db, value - fallDbPerSec * (nowMs - maxOf(lastMs, heldAt + holdMs)) / 1000f)
        }
        lastMs = nowMs
        return value
    }

    fun reset() { value = Float.NaN }
}

/** Everyday sounds for the reference scale, loudest first. */
enum class ReferenceLevel(val db: Int, val label: String) {
    Pain(120, "Threshold of pain"),
    Concert(100, "Rock concert"),
    Traffic(80, "Busy traffic"),
    Conversation(60, "Conversation"),
    Whisper(30, "Whisper"),
}

/** The loudest reference at or below [db], or null if it is quieter than a whisper. */
fun referenceFor(db: Float): ReferenceLevel? = ReferenceLevel.entries.firstOrNull { db >= it.db }

/** Rough hearing-risk bands: long exposure above 85 dB can damage hearing. */
enum class Zone(val label: String) { Quiet("Quiet"), Moderate("Moderate"), Loud("Loud"), Harmful("Risk to hearing") }

fun zoneFor(db: Float): Zone = when {
    db < 50f -> Zone.Quiet
    db < 70f -> Zone.Moderate
    db < 85f -> Zone.Loud
    else -> Zone.Harmful
}
