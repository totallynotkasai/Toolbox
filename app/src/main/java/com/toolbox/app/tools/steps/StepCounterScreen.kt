package com.toolbox.app.tools.steps

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.PermissionGate
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberSensorListener
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import java.time.format.TextStyle as DateTextStyle

private const val PREF_GOAL = "steps_goal"
private const val PREF_STRIDE = "steps_stride_cm"
private const val PREF_WEIGHT = "steps_weight_kg"
private const val PREF_MILES = "steps_miles"
private const val PREF_SESSION_START = "steps_session_start"   // lifetime count when the session began
private const val PREF_SESSION_MILLIS = "steps_session_millis" // when it began; 0 = not yet
private const val METRES_PER_MILE = 1609.344

/** Countries where walking distances are usually given in miles. */
private val MILES_COUNTRIES = setOf("GB", "US", "LR", "MM")

private data class StepSettings(val goal: Int, val strideCm: Int, val weightKg: Int, val miles: Boolean)

@Composable
fun StepCounterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(SensorManager::class.java) }
    val hasCounter = remember { sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }
    val hasDetector = remember { sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null }

    var goal by rememberPref(PREF_GOAL, 10_000)
    var stride by rememberPref(PREF_STRIDE, 70)
    var weight by rememberPref(PREF_WEIGHT, 70)
    var miles by rememberPref(PREF_MILES, LocalLocale.current.platformLocale.country in MILES_COUNTRIES)
    val settings = StepSettings(goal, stride, weight, miles)
    var editing by rememberSaveable { mutableStateOf(false) }

    ToolScaffold(
        title = "Step Counter",
        onBack = onBack,
        actions = {
            if (hasCounter || hasDetector) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Tune, contentDescription = "Goal and stride settings") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !hasCounter && !hasDetector -> Column(Modifier.padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("This phone has no step sensor, so steps can't be counted.", textAlign = TextAlign.Center)
                }
                else -> ActivityPermissionGate {
                    if (hasCounter) CounterContent(settings, onEditGoal = { editing = true })
                    else DetectorContent(settings, onEditGoal = { editing = true })
                }
            }
        }
    }

    if (editing) {
        SettingsDialog(
            initial = settings,
            onDismiss = { editing = false },
            onSave = { s -> goal = s.goal; stride = s.strideCm; weight = s.weightKg; miles = s.miles; editing = false },
        )
    }
}

/** ACTIVITY_RECOGNITION is a runtime permission from Android 10; below that the step sensors need nothing. */
@Composable
private fun ActivityPermissionGate(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        content()
        return
    }
    Column(Modifier.padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PermissionGate(
            permission = Manifest.permission.ACTIVITY_RECOGNITION,
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            rationale = "To count your steps, allow access to physical activity. Steps stay on this phone.",
            buttonLabel = "Allow physical activity",
        ) {
            content()
        }
    }
}

/** Hardware step counter: counts from midnight, including while the app is closed. */
@Composable
private fun CounterContent(settings: StepSettings, onEditGoal: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val store = remember { StepStore(context) }
    var state by remember { mutableStateOf(store.load()) }
    var today by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var sessionStart by rememberPref(PREF_SESSION_START, 0L)
    var sessionMillis by rememberPref(PREF_SESSION_MILLIS, 0L)

    LaunchedEffect(Unit) { StepScheduler.ensureScheduled(context) }
    // Notice midnight even if no steps arrive.
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); today = LocalDate.now().toEpochDay() }
    }
    rememberSensorListener(Sensor.TYPE_STEP_COUNTER, rate = SensorManager.SENSOR_DELAY_UI) { e ->
        today = LocalDate.now().toEpochDay()
        state = store.record(e.values[0].toLong(), today)
    }
    // The very first session starts with the first reading.
    LaunchedEffect(state.started) {
        if (state.started && sessionMillis == 0L) { sessionStart = state.lifetime; sessionMillis = System.currentTimeMillis() }
    }

    val steps = if (state.started) state.totalFor(today) ?: 0L else 0L
    StepRing(steps, settings, onEditGoal)
    Spacer(Modifier.height(20.dp))

    val sessionSteps = if (sessionMillis == 0L) 0L else (state.lifetime - sessionStart).coerceAtLeast(0L)
    SessionCard(
        steps = sessionSteps,
        sinceMillis = sessionMillis,
        settings = settings,
        onReset = { haptics.click(); sessionStart = state.lifetime; sessionMillis = System.currentTimeMillis() },
    )
    Spacer(Modifier.height(12.dp))
    WeekCard(state, today, settings.goal)
    Spacer(Modifier.height(12.dp))

    val startedAt = store.startedAtMillis
    val firstDay = startedAt != 0L && Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() == today
    Text(
        when {
            !state.started -> "Waiting for the step sensor. Take a few steps."
            firstDay -> "Counting since ${formatTime(startedAt)}. Steps from earlier today can't be recovered. From tomorrow, steps are counted from midnight, even with the app closed."
            else -> "Counted from midnight, even with the app closed. Distance and calories are estimates."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 8.dp),
    )
}

/** Step detector only: one event per step, delivered only while someone is listening. */
@Composable
private fun DetectorContent(settings: StepSettings, onEditGoal: () -> Unit) {
    var steps by rememberSaveable { mutableLongStateOf(0L) }
    val haptics = rememberHaptics()
    rememberSensorListener(Sensor.TYPE_STEP_DETECTOR, rate = SensorManager.SENSOR_DELAY_UI) { steps++ }
    StepRing(steps, settings, onEditGoal)
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { haptics.click(); steps = 0 }) {
        Icon(Icons.Filled.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reset")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "This phone only has a step detector, so steps are counted while this screen is open.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp),
    )
}

@Composable
private fun StepRing(steps: Long, settings: StepSettings, onEditGoal: () -> Unit) {
    val goal = settings.goal.coerceAtLeast(1)
    val reached = steps >= goal
    val progress by animateFloatAsState((steps.toFloat() / goal).coerceIn(0f, 1f), tween(600), label = "progress")
    val color by animateColorAsState(if (reached) GoogleColors.Green else GoogleColors.Blue, label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val metres = distanceMetres(steps, settings.strideCm)

    Box(
        Modifier.widthIn(max = 280.dp).fillMaxWidth().height(280.dp)
            .semantics(mergeDescendants = true) { contentDescription = "%,d of %,d steps today".format(steps, goal) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(260.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress > 0f) drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = color, modifier = Modifier.size(28.dp))
            Text("%,d".format(steps), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium)
            Text(
                if (reached) "Goal reached!" else "of %,d".format(goal),
                style = MaterialTheme.typography.titleMedium,
                color = if (reached) GoogleColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Change goal", onClick = onEditGoal)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
    Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Figure("Distance", formatDistance(metres, settings.miles))
        Figure("Calories", "≈ ${walkingKcal(metres, settings.weightKg).roundToInt()} kcal")
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SessionCard(steps: Long, sinceMillis: Long, settings: StepSettings, onReset: () -> Unit) {
    Card(
        Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("This session", style = MaterialTheme.typography.titleSmall)
                Text("%,d steps  ·  %s".format(steps, formatDistance(distanceMetres(steps, settings.strideCm), settings.miles)),
                    style = MaterialTheme.typography.titleLarge)
                if (sinceMillis != 0L) {
                    Text("Since ${formatSince(sinceMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onReset) {
                Icon(Icons.Filled.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reset")
            }
        }
    }
}

/** Last 7 days as bars, today on the right. Tap a bar to see its total; the dashed line is the goal. */
@Composable
private fun WeekCard(state: StepState, today: Long, goal: Int) {
    val days = (6 downTo 0).map { today - it }
    val totals = days.map { state.totalFor(it) ?: if (it == today && state.started) 0L else null }
    var selected by rememberSaveable { mutableIntStateOf(6) }
    val known = totals.filterNotNull()
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val bar = GoogleColors.Blue
    val grid = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    val locale = LocalLocale.current.platformLocale
    val letters = remember(today, measurer, labelColor, ink) {
        days.mapIndexed { i, d ->
            measurer.measure(
                LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).take(3),
                TextStyle(color = if (i == 6) ink else labelColor, fontSize = 11.sp, fontWeight = if (i == 6) FontWeight.Bold else FontWeight.Normal),
            )
        }
    }

    Card(
        Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 7 days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (known.isNotEmpty()) {
                    Text("Avg %,d / day".format(known.sum() / known.size), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val sel = selected.coerceIn(0, 6)
            val selDate = LocalDate.ofEpochDay(days[sel])
            Text(
                (if (sel == 6) "Today" else selDate.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))) + ": " +
                    (totals[sel]?.let { "%,d steps".format(it) } ?: "no data"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            val maxValue = maxOf(goal.toLong(), known.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat() * 1.1f
            Canvas(
                Modifier.fillMaxWidth().height(140.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { p -> selected = (p.x / (size.width / 7f)).toInt().coerceIn(0, 6) }
                    }
                    .semantics {
                        contentDescription = days.indices.joinToString("; ") { i ->
                            val name = LocalDate.ofEpochDay(days[i]).dayOfWeek.getDisplayName(DateTextStyle.FULL, locale)
                            "$name ${totals[i]?.let { "%,d steps".format(it) } ?: "no data"}"
                        }
                    },
            ) {
                val labelH = 18.dp.toPx()
                val chartH = size.height - labelH
                val slot = size.width / 7f
                val barW = slot * 0.55f
                fun y(v: Float) = chartH * (1f - v / maxValue)

                totals.forEachIndexed { i, v ->
                    val x = slot * i + (slot - barW) / 2
                    val color = when {
                        v == null -> track
                        v >= goal -> GoogleColors.Green
                        else -> bar
                    }
                    val alpha = if (i == sel) 1f else 0.6f
                    val top = if (v == null || v == 0L) chartH - 3.dp.toPx() else y(v.toFloat()).coerceAtMost(chartH - 3.dp.toPx())
                    drawRoundRect(color.copy(alpha = if (v == null) 1f else alpha), Offset(x, top), Size(barW, chartH - top),
                        cornerRadius = CornerRadius(4.dp.toPx()))
                    val l = letters[i]
                    drawText(l, topLeft = Offset(slot * i + (slot - l.size.width) / 2, chartH + (labelH - l.size.height) / 2 + 2.dp.toPx()))
                }
                drawLine(grid, Offset(0f, y(goal.toFloat())), Offset(size.width, y(goal.toFloat())), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            }
        }
    }
}

@Composable
private fun SettingsDialog(initial: StepSettings, onDismiss: () -> Unit, onSave: (StepSettings) -> Unit) {
    var goal by rememberSaveable { mutableStateOf(initial.goal.toString()) }
    var stride by rememberSaveable { mutableStateOf(initial.strideCm.toString()) }
    var weight by rememberSaveable { mutableStateOf(initial.weightKg.toString()) }
    var miles by rememberSaveable { mutableStateOf(initial.miles) }
    val goalOk = goal.toIntOrNull()?.let { it in 100..100_000 } == true
    val strideOk = stride.toIntOrNull()?.let { it in 30..150 } == true
    val weightOk = weight.toIntOrNull()?.let { it in 20..300 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Step settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Daily goal (steps)", goal, goalOk, "100 to 100,000") { goal = it }
                NumberField("Stride length (cm)", stride, strideOk, "30 to 150 cm. About 40% of your height.") { stride = it }
                NumberField("Weight (kg)", weight, weightOk, "20 to 300 kg, for the calorie estimate") { weight = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show distance in miles", modifier = Modifier.weight(1f))
                    Switch(checked = miles, onCheckedChange = { miles = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(StepSettings(goal.toInt(), stride.toInt(), weight.toInt(), miles)) },
                enabled = goalOk && strideOk && weightOk,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, ok: Boolean, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        singleLine = true,
        isError = !ok,
        supportingText = { Text(hint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatDistance(metres: Double, miles: Boolean): String {
    val v = if (miles) metres / METRES_PER_MILE else metres / 1000.0
    val unit = if (miles) "mi" else "km"
    return if (v < 10) "%.2f %s".format(v, unit) else "%.1f %s".format(v, unit)
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "14:05" for today, "Tue 14:05" within the week, otherwise a date. */
private fun formatSince(millis: Long): String {
    val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val days = LocalDate.now().toEpochDay() - at.toLocalDate().toEpochDay()
    return when {
        days == 0L -> formatTime(millis)
        days in 1..6 -> at.format(DateTimeFormatter.ofPattern("EEE ", Locale.getDefault())) + formatTime(millis)
        else -> at.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

