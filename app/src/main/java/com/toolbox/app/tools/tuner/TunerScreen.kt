package com.toolbox.app.tools.tuner

import android.annotation.SuppressLint
import android.media.MediaRecorder
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.MicEffect
import com.toolbox.app.ui.common.MicPermissionGate
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min

private const val SAMPLE_RATE = 44_100
private const val WINDOW = 4096
private const val HOP = 2048
private const val IN_TUNE_CENTS = 5f

@Composable
fun TunerScreen(onBack: () -> Unit) {
    ToolScaffold(title = "Tuner", onBack = onBack) { padding ->
        ToolBody(padding) {
            MicPermissionGate("The tuner needs the microphone to hear your instrument.") { TunerContent() }
        }
    }
}

@SuppressLint("MissingPermission") // Only composed once RECORD_AUDIO is granted.
@Composable
private fun TunerContent() {
    var instrument by rememberSaveable { mutableStateOf(Instrument.Chromatic) }
    var frequency by remember { mutableStateOf<Float?>(null) }
    var inputLevel by remember { mutableFloatStateOf(0f) }
    var micFailed by remember { mutableStateOf(false) }

    // Analysis state, only touched on the mic thread.
    val analysis = remember { TunerAnalysis() }
    // VOICE_RECOGNITION gives a flat, un-gated signal on most phones. The default MIC path applies
    // noise suppression, which treats a sustained guitar note as "noise" and fades it out.
    // (UNPROCESSED sounded ideal but is unsupported or very quiet on many devices.)
    MicEffect(
        SAMPLE_RATE,
        blockSize = HOP,
        sources = MIC_SOURCES,
        minBufferBytes = WINDOW * 2,
        onError = { micFailed = true },
    ) { hop ->
        val reading = analysis.add(hop)
        withContext(Dispatchers.Main) {
            inputLevel = reading.level
            if (reading.frequency != null) frequency = reading.frequency
            else if (reading.lost) frequency = null
        }
    }

    if (micFailed) {
        Text("The microphone is unavailable. Another app may be using it.", textAlign = TextAlign.Center)
        return
    }

    val f = frequency
    val target: Note? = f?.let { hz ->
        if (instrument == Instrument.Chromatic) nearestNote(hz)
        else instrument.strings.minBy { abs(midiOf(hz) - it.midi) }
    }
    val rawCents = if (f != null && target != null) centsFrom(f, target).coerceIn(-50f, 50f) else 0f
    // The spring doubles as a low-pass filter so the needle doesn't jitter between frames.
    val needle by animateFloatAsState(rawCents, spring(dampingRatio = 0.8f, stiffness = 120f), label = "needle")
    val inTune = f != null && abs(rawCents) <= IN_TUNE_CENTS
    val accent by animateColorAsState(
        when { f == null -> MaterialTheme.colorScheme.outline; inTune -> GoogleColors.Green; else -> GoogleColors.Red },
        label = "accent",
    )

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Instrument.entries.forEach { i -> FilterChip(selected = instrument == i, onClick = { instrument = i }, label = { Text(i.label) }) }
    }
    Spacer(Modifier.height(16.dp))
    // Mic level, so it's obvious whether the phone is hearing the instrument at all.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Mic, contentDescription = "Input level", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { inputLevel },
            modifier = Modifier.width(160.dp),
            drawStopIndicator = {},
        )
    }
    Spacer(Modifier.height(16.dp))

    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            val pivot = Offset(size.width / 2, size.height * 0.95f)
            val r = min(size.width / 2, size.height) * 0.9f
            // Ticks every 10 cents across ±50, mapped to ±60°.
            for (c in -50..50 step 10) {
                rotate(c * 1.2f, pivot) {
                    val long = c == 0
                    drawLine(
                        if (long) GoogleColors.Green else tickColor,
                        Offset(pivot.x, pivot.y - r),
                        Offset(pivot.x, pivot.y - r + (if (long) 28.dp else 14.dp).toPx()),
                        strokeWidth = (if (long) 4.dp else 2.dp).toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            drawArc(track, -150f, 120f, false, topLeft = pivot - Offset(r * 0.8f, r * 0.8f),
                size = androidx.compose.ui.geometry.Size(r * 1.6f, r * 1.6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            rotate(needle * 1.2f, pivot) {
                drawLine(accent, pivot, Offset(pivot.x, pivot.y - r * 0.92f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(accent, 10.dp.toPx(), pivot)
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(target?.name ?: "–", style = MaterialTheme.typography.displayLarge, color = accent)
    Text(
        when {
            f == null -> "Play a note"
            inTune -> "In tune · ${"%.1f".format(f)} Hz"
            rawCents < 0 -> "Too low · ${"%.1f".format(f)} Hz"
            else -> "Too high · ${"%.1f".format(f)} Hz"
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (target != null) Text("${target}  ${"%+.0f".format(rawCents)} cents", style = MaterialTheme.typography.bodyMedium)

    if (instrument != Instrument.Chromatic) {
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            instrument.strings.forEach { s ->
                val active = s == target
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (active) accent else MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.name, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

private val MIC_SOURCES = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)

/** The tuner's per-hop analysis: a sliding window, an adaptive noise gate and a short median filter. */
private class TunerAnalysis {
    class Reading(val level: Float, val frequency: Float?, val lost: Boolean)

    private val detector = PitchDetector(SAMPLE_RATE, WINDOW)
    private val window = FloatArray(WINDOW)
    private val recent = ArrayDeque<Float>()
    private var noiseFloor = 0.0005f
    private var misses = 0

    fun add(hop: ShortArray): Reading {
        // Slide the analysis window left by one hop, append the new samples.
        System.arraycopy(window, HOP, window, 0, WINDOW - HOP)
        for (i in 0 until HOP) window[WINDOW - HOP + i] = hop[i] / 32768f

        // Adaptive gate: track the room's noise floor (falls fast, rises slowly) and only
        // analyse frames clearly above it, so a quiet room lets a quiet string through.
        val rms = detector.rms(window)
        noiseFloor = if (rms < noiseFloor) rms else noiseFloor + (rms - noiseFloor) * 0.01f
        val f = detector.detect(window, minRms = maxOf(0.0006f, noiseFloor * 2f))

        // Median of the last 3 readings drops one-frame octave slips as a string's attack fades.
        val out = f?.let {
            recent.addLast(it)
            if (recent.size > 3) recent.removeFirst()
            recent.sorted()[recent.size / 2]
        }
        val level = (20 * log10(rms.coerceAtLeast(1e-5f)) + 70f).coerceIn(0f, 60f) / 60f
        if (out != null) {
            misses = 0
            return Reading(level, out, lost = false)
        }
        // Hold ~1 s while a note rings out.
        val lost = ++misses > 25
        if (lost) recent.clear()
        return Reading(level, null, lost)
    }
}
