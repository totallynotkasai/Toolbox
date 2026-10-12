package com.toolbox.app.tools.spinner

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val MAX_OPTIONS = 20
private const val SEP = "\u001F"
private val DARK_TEXT = Color(0xFF202124)

private fun numbers(n: Int) = (1..n).map { "$it" }

/** Wheel options live in SharedPreferences so a custom list survives leaving the screen. */
private class OptionStore(context: Context) {
    private val prefs = context.getSharedPreferences("spinner", Context.MODE_PRIVATE)
    fun load(): List<String> = prefs.getString("options", null)?.split(SEP)?.takeIf { it.size >= 2 } ?: numbers(8)
    fun save(options: List<String>) = prefs.edit().putString("options", options.joinToString(SEP)).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinnerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val store = OptionStore(LocalContext.current)
    var options by remember { mutableStateOf(store.load()) }
    val angle = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    val synth = rememberToneSynth()

    fun update(newOptions: List<String>) { options = newOptions; store.save(newOptions); result = null }

    val slices = options.size
    val sweep = 360f / slices
    fun sliceUnderPointer(a: Float): Int {
        val wheelAngle = ((-a % 360f) + 360f) % 360f
        return floor(wheelAngle / sweep).toInt().coerceIn(0, slices - 1)
    }

    // Clicker: tick each time a slice boundary passes the pointer.
    LaunchedEffect(slices) {
        var last = floor(angle.value / sweep)
        snapshotFlow { angle.value }.collect {
            val now = floor(it / sweep)
            if (now != last) { last = now; synth.tick(); haptics.tick() }
        }
    }

    fun land() {
        spinning = false
        result = sliceUnderPointer(angle.value)
        haptics.heavy()
    }

    fun settle(velocity: Float) {
        scope.launch {
            spinning = true
            result = null
            angle.animateDecay(velocity, exponentialDecay(frictionMultiplier = 0.6f))
            land()
        }
    }

    fun spin() {
        if (spinning) return
        scope.launch {
            spinning = true
            result = null
            val target = angle.value + 360f * (4 + Random.nextInt(3)) + Random.nextFloat() * 360f
            angle.animateTo(target, tween(3600, easing = CubicBezierEasing(0.1f, 0.6f, 0.2f, 1f)))
            land()
        }
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value && !editing) { spin() } // spin() ignores shakes mid-spin

    ToolScaffold(
        title = "Spinner",
        onBack = onBack,
        actions = {
            IconButton(onClick = { editing = true }, enabled = !spinning) { Icon(Icons.Filled.Edit, contentDescription = "Edit options") }
        },
    ) { padding ->
        ToolBody(padding) {
            val pointerColor = MaterialTheme.colorScheme.onSurface
            Box(
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(slices) {
                        val tracker = VelocityTracker1D(isDataDifferential = true)
                        var prev = 0f
                        detectDragGestures(
                            onDragStart = { pos ->
                                scope.launch { angle.stop() }
                                tracker.resetTracking()
                                prev = angleOf(pos, size.width / 2f, size.height / 2f)
                            },
                            onDrag = { change, _ ->
                                val a = angleOf(change.position, size.width / 2f, size.height / 2f)
                                var d = a - prev
                                if (d > 180f) d -= 360f
                                if (d < -180f) d += 360f
                                prev = a
                                tracker.addDataPoint(change.uptimeMillis, d)
                                scope.launch { angle.snapTo(angle.value + d) }
                            },
                            onDragEnd = { settle(tracker.calculateVelocity().coerceIn(-4000f, 4000f)) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    drawWheel(options, sweep, angle.value, measurer)
                    drawPointer(pointerColor)
                }
            }

            Spacer(Modifier.height(20.dp))
            val r = result
            Text(
                when {
                    r != null -> options[r]
                    spinning -> " "
                    else -> if (shake.value) "Tap Spin, flick the wheel or shake" else "Tap Spin or flick the wheel"
                },
                style = if (r != null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Handy for picking an order (who goes first, second...): drop the winner and spin again.
            TextButton(
                onClick = { if (r != null) update(options.filterIndexed { i, _ -> i != r }) },
                enabled = r != null && slices > 2 && !spinning,
            ) { Text(if (r != null && slices > 2) "Remove \"${options[r].take(16)}\" from wheel" else " ") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalButton(onClick = { editing = true }, enabled = !spinning) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Edit options")
                }
                Button(onClick = ::spin, enabled = !spinning) { Text("Spin") }
            }
        }
    }

    if (editing) {
        OptionsSheet(
            initial = options,
            onDismiss = { edited -> update(edited); editing = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(initial: List<String>, onDismiss: (List<String>) -> Unit) {
    val items = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    // Blank entries fall back to their position number so the wheel never shows an empty slice.
    fun cleaned() = items.mapIndexed { i, s -> s.trim().ifEmpty { "${i + 1}" } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = { onDismiss(cleaned()) }, sheetState = sheetState) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Wheel options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDismiss(cleaned()) }) { Text("Done") }
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = { items.clear(); items.addAll(numbers(8)) }, label = { Text("Numbers 1–8") })
            SuggestionChip(onClick = { items.clear(); items.addAll(listOf("Yes", "No")) }, label = { Text("Yes / No") })
            SuggestionChip(onClick = { items.clear(); items.addAll(listOf("", "")) }, label = { Text("Clear") })
        }
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { i, _ -> i }) { i, text ->
                Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 40) items[i] = it },
                        placeholder = { Text("Option ${i + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { items.removeAt(i) }, enabled = items.size > 2) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove option ${i + 1}")
                    }
                }
            }
            item {
                TextButton(
                    onClick = { items.add("") },
                    enabled = items.size < MAX_OPTIONS,
                    modifier = Modifier.padding(start = 16.dp).navigationBarsPadding(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(if (items.size < MAX_OPTIONS) "Add option" else "Maximum $MAX_OPTIONS options")
                }
            }
        }
    }
}

private fun angleOf(p: Offset, cx: Float, cy: Float): Float =
    Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()

private fun DrawScope.drawWheel(options: List<String>, sweep: Float, rotation: Float, measurer: TextMeasurer) {
    val n = options.size
    val r = size.minDimension / 2 * 0.92f
    val topLeft = center - Offset(r, r)
    val fontSize = when { n <= 6 -> 20.sp; n <= 12 -> 16.sp; else -> 13.sp }
    val maxLabel = (r * 0.66f).toInt()

    rotate(rotation) {
        for (i in 0 until n) {
            // Cycle Google's four colours; nudge the last slice if it would touch its twin across the seam.
            var c = i % 4
            if (i == n - 1 && c == 0 && n > 1) c = 2
            val color = GoogleColors.all[c]
            val start = -90f + sweep * i
            drawArc(color, start, sweep, useCenter = true, topLeft = topLeft, size = Size(r * 2, r * 2))

            // Labels run along the slice's radius, flipped on the left half so they're never upside down.
            val mid = start + sweep / 2
            val absolute = (((mid + rotation) % 360f) + 360f) % 360f
            val flip = absolute > 90f && absolute < 270f
            val layout = measurer.measure(
                options[i],
                TextStyle(color = if (color == GoogleColors.Yellow) DARK_TEXT else Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxLabel),
            )
            rotate(if (flip) mid + 180f else mid, pivot = center) {
                val x = if (flip) center.x - r * 0.9f else center.x + r * 0.9f - layout.size.width
                drawText(layout, topLeft = Offset(x, center.y - layout.size.height / 2f))
            }
        }
    }
    drawCircle(Color.White, r * 0.1f, center)
    drawCircle(Color(0x33000000), r * 0.1f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
}

private fun DrawScope.drawPointer(color: Color) {
    val r = size.minDimension / 2 * 0.92f
    val tip = Offset(center.x, center.y - r + 18.dp.toPx())
    val half = max(12.dp.toPx(), min(size.width, size.height) * 0.04f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - half, center.y - r - 10.dp.toPx())
        lineTo(tip.x + half, center.y - r - 10.dp.toPx())
        close()
    }
    drawPath(path, color)
}
