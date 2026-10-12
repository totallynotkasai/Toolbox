package com.toolbox.app.tools.soundmeter

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.KeepScreenOn
import com.toolbox.app.ui.common.MicEffect
import com.toolbox.app.ui.common.MicPermissionGate
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.supportsUnprocessedMic
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SAMPLE_RATE = 44_100
private const val BLOCK = SAMPLE_RATE / 10 // ~100 ms per reading
private const val HISTORY_SIZE = 300       // 30 s of readings
private const val DEFAULT_OFFSET = 90f
private const val GAUGE_MIN = 20f
private const val GAUGE_MAX = 120f
private const val GAUGE_START = 150f       // degrees, clockwise from 3 o'clock
private const val GAUGE_SWEEP = 240f
private const val PREF_OFFSET = "soundmeter_offset"

private val ZONE_COLORS = mapOf(
    Zone.Quiet to GoogleColors.Green,
    Zone.Moderate to GoogleColors.Blue,
    Zone.Loud to GoogleColors.Yellow,
    Zone.Harmful to GoogleColors.Red,
)

@Composable
fun SoundMeterScreen(onBack: () -> Unit) {
    var calibrating by rememberSaveable { mutableStateOf(false) }
    ToolScaffold(
        title = "Sound Meter",
        onBack = onBack,
        actions = {
            IconButton(onClick = { calibrating = true }) { Icon(Icons.Filled.Tune, contentDescription = "Calibrate") }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MicPermissionGate("The sound meter needs the microphone to measure how loud it is around you.") {
                SoundMeterContent(calibrating, onCalibrationDone = { calibrating = false })
            }
        }
    }
}

@SuppressLint("MissingPermission") // Only composed once RECORD_AUDIO is granted.
@Composable
private fun SoundMeterContent(calibrating: Boolean, onCalibrationDone: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var offset by rememberPref(PREF_OFFSET, DEFAULT_OFFSET)
    var running by rememberSaveable { mutableStateOf(true) }
    var micFailed by remember { mutableStateOf(false) }
    val unprocessed = remember { supportsUnprocessedMic(context) }

    // Everything is kept in dBFS; the calibration offset is only added for display.
    var level by remember { mutableFloatStateOf(Float.NaN) }
    val stats = remember { LevelStats() }
    val peak = remember { PeakHold() }
    var peakValue by remember { mutableFloatStateOf(Float.NaN) }
    var history by remember { mutableStateOf(FloatArray(0)) }
    var tick by remember { mutableIntStateOf(0) } // Bumped per reading so the stats text recomposes.

    KeepScreenOn(running)

    val sources = remember(unprocessed) {
        buildList {
            // UNPROCESSED skips AGC and noise suppression, which would flatten loud sounds and hide quiet ones.
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }.toIntArray()
    }
    MicEffect(SAMPLE_RATE, BLOCK, sources, enabled = running, onError = { micFailed = true }) { buffer ->
        val db = rmsDbfs(buffer)
        val now = SystemClock.uptimeMillis()
        withContext(Dispatchers.Main) {
            level = db
            stats.add(db)
            peakValue = peak.update(db, now)
            history = (if (history.size >= HISTORY_SIZE) history.copyOfRange(1, history.size) else history) + db
            tick++
        }
    }

    if (micFailed) {
        Text("The microphone is unavailable. Another app may be using it.", textAlign = TextAlign.Center)
        return
    }

    fun display(dbfs: Float) = (dbfs + offset).coerceAtLeast(0f)
    val shown = if (level.isNaN()) Float.NaN else display(level)

    Gauge(
        db = shown,
        peak = if (peakValue.isNaN()) Float.NaN else display(peakValue),
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1.25f),
    )
    Spacer(Modifier.height(8.dp))
    tick.let { // Read so this block recomposes with each reading.
        StatsRow(
            min = stats.min.takeUnless { it.isNaN() }?.let(::display),
            avg = stats.leq.takeUnless { it.isNaN() }?.let(::display),
            max = stats.max.takeUnless { it.isNaN() }?.let(::display),
        )
    }
    Spacer(Modifier.height(16.dp))
    HistoryGraph(history, offset, Modifier.widthIn(max = 480.dp).fillMaxWidth().height(140.dp))
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = { haptics.click(); running = !running }) {
            Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Pause" else "Resume")
        }
        OutlinedButton(onClick = {
            haptics.click(); stats.reset(); peak.reset(); peakValue = Float.NaN; history = FloatArray(0); tick++
        }) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset")
        }
    }
    Spacer(Modifier.height(20.dp))
    ReferenceScale(shown, Modifier.widthIn(max = 420.dp).fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    Text(
        "Phone microphones aren't calibrated, so readings are approximate. Use Calibrate to match a known meter.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp),
    )

    if (calibrating) {
        CalibrationSheet(
            offset = offset,
            live = shown,
            unprocessed = unprocessed,
            onChange = { offset = it },
            onDismiss = onCalibrationDone,
        )
    }
}

@Composable
private fun Gauge(db: Float, peak: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val valid = !db.isNaN()
    val zone = zoneFor(if (valid) db else 0f)
    // A light spring smooths the 10 Hz readings into a needle-like motion without lagging real changes.
    val animated by animateFloatAsState(if (valid) db else GAUGE_MIN, spring(dampingRatio = 0.9f, stiffness = 300f), label = "db")
    val color by animateColorAsState(if (valid) ZONE_COLORS.getValue(zone) else track, label = "zone")
    val tickLabels = remember(measurer, tickColor) {
        (GAUGE_MIN.toInt()..GAUGE_MAX.toInt() step 20).associateWith {
            measurer.measure("$it", TextStyle(color = tickColor, fontSize = 11.sp))
        }
    }

    Box(modifier.semantics { contentDescription = if (valid) "${db.roundToInt()} decibels, ${zone.label}" else "Listening" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val r = minOf(size.width / 2, size.height / 1.5f) - stroke
            val c = Offset(size.width / 2, r + stroke)
            val box = Size(r * 2, r * 2)
            val topLeft = c - Offset(r, r)
            fun fraction(v: Float) = ((v - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0f, 1f)

            drawArc(track, GAUGE_START, GAUGE_SWEEP, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
            if (valid && fraction(animated) > 0f) {
                drawArc(color, GAUGE_START, GAUGE_SWEEP * fraction(animated), false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Scale ticks and numbers every 20 dB, inside the arc.
            tickLabels.forEach { (v, layout) ->
                val angle = GAUGE_START + GAUGE_SWEEP * fraction(v.toFloat())
                rotate(angle + 90f, c) {
                    drawLine(tickColor, Offset(c.x, c.y - r + stroke * 0.8f), Offset(c.x, c.y - r + stroke * 0.8f + 6.dp.toPx()), 1.5.dp.toPx())
                }
                val rad = Math.toRadians(angle.toDouble())
                val lr = r - stroke * 0.8f - 16.dp.toPx()
                val p = Offset(c.x + lr * cos(rad).toFloat(), c.y + lr * sin(rad).toFloat())
                drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
            // Peak-hold marker just outside the arc.
            if (!peak.isNaN()) {
                rotate(GAUGE_START + GAUGE_SWEEP * fraction(peak) + 90f, c) {
                    drawLine(ink, Offset(c.x, c.y - r - stroke / 2 - 4.dp.toPx()), Offset(c.x, c.y - r + stroke / 2 + 2.dp.toPx()),
                        3.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (valid) "${db.roundToInt()}" else "–", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Medium)
                Text(" dB", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(if (valid) zone.label else "Listening…", style = MaterialTheme.typography.titleMedium, color = if (valid) color else tickColor)
        }
    }
}

@Composable
private fun StatsRow(min: Float?, avg: Float?, max: Float?) {
    Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("Min", min)
        Stat("Avg", avg)
        Stat("Max", max)
    }
}

@Composable
private fun Stat(label: String, value: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.let { "${it.roundToInt()} dB" } ?: "–", style = MaterialTheme.typography.titleLarge)
    }
}

/** Last 30 s of readings, newest on the right, with the 85 dB hearing-risk line dashed in red. */
@Composable
private fun HistoryGraph(history: FloatArray, offset: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surfaceContainer
    val labels = remember(measurer, labelColor) {
        listOf(40, 60, 80, 100).associateWith { measurer.measure("$it", TextStyle(color = labelColor, fontSize = 10.sp)) }
    }
    val axis = remember(measurer, labelColor) { measurer.measure("30 s", TextStyle(color = labelColor, fontSize = 10.sp)) }

    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(bg).semantics { contentDescription = "Level over the last 30 seconds" }) {
        val pad = 8.dp.toPx()
        val left = 28.dp.toPx()
        val w = size.width - left - pad
        val h = size.height - pad * 2
        fun y(db: Float) = pad + h * (1f - ((db - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0f, 1f))

        labels.forEach { (v, layout) ->
            drawLine(grid, Offset(left, y(v.toFloat())), Offset(left + w, y(v.toFloat())), 1.dp.toPx())
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y(v.toFloat()) - layout.size.height / 2f))
        }
        drawLine(GoogleColors.Red.copy(alpha = 0.7f), Offset(left, y(85f)), Offset(left + w, y(85f)), 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        drawText(axis, topLeft = Offset(left + 4.dp.toPx(), size.height - pad - axis.size.height))

        if (history.size >= 2) {
            val step = w / (HISTORY_SIZE - 1)
            val startX = left + w - step * (history.size - 1)
            val path = Path()
            history.forEachIndexed { i, v ->
                val p = Offset(startX + step * i, y((v + offset).coerceAtLeast(0f)))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(left + w, pad + h); lineTo(startX, pad + h); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0.02f)), pad, pad + h))
            drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Everyday reference sounds, with the one closest below the current reading highlighted. */
@Composable
private fun ReferenceScale(db: Float, modifier: Modifier) {
    val current = if (db.isNaN()) null else referenceFor(db)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("For comparison", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            ReferenceLevel.entries.forEach { ref ->
                val active = ref == current
                val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "ref")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ZONE_COLORS.getValue(zoneFor(ref.db.toFloat()))))
                    Spacer(Modifier.width(12.dp))
                    Text("${ref.db} dB", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                    Text(ref.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (active) Text("≈ now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationSheet(offset: Float, live: Float, unprocessed: Boolean, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Calibrate", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Text(
                "Place the phone next to a sound level meter (or a source of known loudness) and move the slider until the readings match.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (live.isNaN()) "–" else "${live.roundToInt()}", style = MaterialTheme.typography.displaySmall)
                Text(" dB now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Text("Offset +${offset.roundToInt()} dB", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
            }
            Slider(
                value = offset,
                onValueChange = { onChange(it.roundToInt().toFloat()) },
                valueRange = 60f..120f,
                steps = 59,
                modifier = Modifier.semantics { contentDescription = "Calibration offset" },
            )
            TextButton(onClick = { onChange(DEFAULT_OFFSET) }, enabled = offset != DEFAULT_OFFSET) {
                Text("Reset to +${DEFAULT_OFFSET.roundToInt()} dB")
            }
            Text(
                if (unprocessed) "Using the phone's unprocessed microphone, the most accurate option."
                else "This phone has no unprocessed microphone input, so very loud or very quiet sounds may read less accurately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
