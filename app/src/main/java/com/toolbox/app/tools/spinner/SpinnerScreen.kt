package com.toolbox.app.tools.spinner

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.random.Random

private const val MAX_OPTIONS = 20
private const val MAX_LENGTH = 40
private const val SEP = "\u001F"
private val DARK_TEXT = Color(0xFF202124)
private val SPIN_EASING = CubicBezierEasing(0.1f, 0.6f, 0.2f, 1f)
private val COAST = exponentialDecay<Float>(frictionMultiplier = 0.6f)

private fun numbers(n: Int) = (1..n).map { "$it" }

/** Wheel options live in SharedPreferences so a custom list survives leaving the screen. */
private class OptionStore(context: Context) {
    private val prefs = context.getSharedPreferences("spinner", Context.MODE_PRIVATE)
    fun load(): List<String> = prefs.getString("options", null)?.split(SEP)?.takeIf { it.size >= 2 } ?: numbers(8)
    fun save(options: List<String>) = prefs.edit { putString("options", options.joinToString(SEP)) }
}

/** Slower than this (degrees a second), letting go of the wheel leaves it where it is, so it can't be aimed. */
internal const val MIN_FLING = 300f

/** However gently it is flicked, the wheel turns at least this far, plus up to two random turns. */
internal const val MIN_TURN = 720f

/** A flick's turn in degrees, signed like the flick, and how long it takes. */
internal data class Fling(val degrees: Float, val millis: Int)

/**
 * The turn for a flick at [velocity] degrees a second that would [coast] that far by itself. [extra] is a random
 * number in [0, 2) of extra turns. Any part of a turn is equally likely, so where the wheel stops is fair however it
 * was flicked. Null for a release too slow to count as a flick.
 */
internal fun flingFor(velocity: Float, coast: Float, extra: Float): Fling? {
    if (abs(velocity) < MIN_FLING) return null
    val total = max(abs(coast), MIN_TURN) + extra * 360f
    // The easing starts at 6× its average speed; matching that to the flick avoids a jolt as the finger lifts.
    val millis = (6f * total / abs(velocity) * 1000f).toInt().coerceIn(1500, 4500)
    return Fling(sign(velocity) * total, millis)
}

/** The options still on the wheel after the session's removals. */
internal fun remaining(options: List<String>, removed: Set<Int>): List<String> =
    options.filterIndexed { i, _ -> i !in removed }

/** Removed positions, stored as "0,3" so they survive a recreation; positions no longer on the list are dropped. */
internal fun parseRemoved(text: String, size: Int): Set<Int> =
    text.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0 until size }.toSet()

/** What TalkBack says for the wheel, which is only drawn: how many options it has and what they are. */
internal fun wheelDescription(options: List<String>): String =
    "Wheel with ${options.size} options: ${options.joinToString(", ")}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinnerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val store = OptionStore(LocalContext.current)
    var options by remember { mutableStateOf(store.load()) }
    // "Remove from wheel" only lasts while the screen is open, so the saved list is never lost to it.
    var removedText by rememberSaveable { mutableStateOf("") }
    val removed = parseRemoved(removedText, options.size)
    val wheel = remaining(options, removed)
    val angle = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Int?>(null) } // a position on [wheel]
    var editing by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    val synth = rememberToneSynth()

    fun update(newOptions: List<String>) {
        options = newOptions
        store.save(newOptions)
        removedText = ""
        result = null
    }

    val slices = wheel.size
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

    /** Runs [turn] as the wheel's spin and lands on a result, unless a grab or leaving the screen cuts it short. */
    fun animate(turn: suspend () -> Unit) {
        val mine = ++run
        scope.launch {
            spinning = true
            result = null
            try {
                turn()
                result = sliceUnderPointer(angle.value)
                haptics.heavy()
            } finally {
                if (run == mine) spinning = false
            }
        }
    }

    fun spin() {
        if (spinning) return
        animate {
            val target = angle.value + 360f * (4 + Random.nextInt(3)) + Random.nextFloat() * 360f
            angle.animateTo(target, tween(3600, easing = SPIN_EASING))
        }
    }

    fun release(velocity: Float) {
        val coast = COAST.calculateTargetValue(angle.value, velocity) - angle.value
        val fling = flingFor(velocity, coast, Random.nextFloat() * 2f) ?: return
        animate { angle.animateTo(angle.value + fling.degrees, tween(fling.millis, easing = SPIN_EASING)) }
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
                    // Flicking can't be done with TalkBack, so a double tap spins it, as the Spin button does.
                    .semantics {
                        contentDescription = wheelDescription(wheel)
                        role = Role.Button
                        if (spinning) disabled() else onClick(label = "Spin") { spin(); true }
                    }
                    .pointerInput(slices) {
                        val tracker = VelocityTracker1D(isDataDifferential = true)
                        var prev = 0f
                        detectDragGestures(
                            onDragStart = { pos ->
                                // Grabbing the wheel stops it, with no result; only a flick or Spin picks one.
                                run++
                                spinning = false
                                result = null
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
                            onDragEnd = { release(tracker.calculateVelocity().coerceIn(-4000f, 4000f)) },
                            onDragCancel = {}, // a cancelled drag leaves the wheel where it is, like a slow release
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Spacer(
                    Modifier.fillMaxWidth().aspectRatio(1f).drawWithCache {
                        // The labels are laid out once per list and size, not on every frame of a spin.
                        val labels = layoutLabels(wheel, measurer, size.minDimension / 2 * 0.92f)
                        onDrawBehind {
                            drawWheel(labels, sweep, angle.value)
                            drawPointer(pointerColor)
                        }
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            val r = result
            Text(
                when {
                    r != null -> wheel[r]
                    spinning -> " "
                    else -> if (shake.value) "Tap Spin, flick the wheel or shake" else "Tap Spin or flick the wheel"
                },
                style = if (r != null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // The result is read out when the wheel stops; a blank line while it spins says nothing.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            // Handy for picking an order (who goes first, second...): drop the winner and spin again.
            FlowRow(Modifier.heightIn(min = 48.dp), horizontalArrangement = Arrangement.Center) {
                if (r != null && slices > 2 && !spinning) {
                    TextButton(onClick = {
                        removedText = (removed + options.indices.filter { it !in removed }[r]).joinToString(",")
                        result = null
                    }) { Text("Remove \"${wheel[r].take(16)}\"") }
                }
                if (removed.isNotEmpty() && !spinning) {
                    TextButton(onClick = { removedText = ""; result = null }) { Text("Restore all") }
                }
            }
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
            onDismiss = { edited ->
                if (edited != options) update(edited)
                editing = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(initial: List<String>, onDismiss: (List<String>) -> Unit) {
    val items = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    // The list from before a preset or Clear replaced it, for Undo.
    var undo by remember { mutableStateOf<List<String>?>(null) }
    // Blank entries fall back to their position number so the wheel never shows an empty slice.
    fun cleaned() = items.mapIndexed { i, s -> s.trim().ifEmpty { "${i + 1}" } }
    fun replace(with: List<String>) {
        if (items.toList() == with) return
        undo = items.toList()
        items.clear()
        items.addAll(with)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = { onDismiss(cleaned()) }, sheetState = sheetState) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Wheel options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            undo?.let { previous ->
                TextButton(onClick = { items.clear(); items.addAll(previous); undo = null }) { Text("Undo") }
            }
            TextButton(onClick = { onDismiss(cleaned()) }) { Text("Done") }
        }
        FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip(onClick = { replace(numbers(8)) }, label = { Text("Numbers 1–8") })
            SuggestionChip(onClick = { replace(listOf("Yes", "No")) }, label = { Text("Yes / No") })
            SuggestionChip(onClick = { replace(listOf("", "")) }, label = { Text("Clear") })
        }
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items, key = { i, _ -> i }) { i, text ->
                Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { items[i] = it.take(MAX_LENGTH) },
                        // A label rather than a placeholder, so a filled-in field still says which option it is.
                        label = { Text("Option ${i + 1}") },
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

/** One slice's colour and its label, laid out ready to draw. */
private class Slice(val color: Color, val label: TextLayoutResult)

private fun layoutLabels(options: List<String>, measurer: TextMeasurer, r: Float): List<Slice> {
    val n = options.size
    val fontSize = when { n <= 6 -> 20.sp; n <= 12 -> 16.sp; else -> 13.sp }
    return options.mapIndexed { i, text ->
        // Cycle Google's four colours; nudge the last slice if it would touch its twin across the seam.
        var c = i % 4
        if (i == n - 1 && c == 0 && n > 1) c = 2
        // Red is filled a shade darker and green takes dark text, so every label reaches 4.5:1 at the smaller sizes.
        val color = GoogleColors.all[c].let { if (it == GoogleColors.Red) GoogleColors.RedInk else it }
        val ink = if (color == GoogleColors.Yellow || color == GoogleColors.Green) DARK_TEXT else Color.White
        val label = measurer.measure(
            text,
            TextStyle(color = ink, fontSize = fontSize, fontWeight = FontWeight.Bold),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = (r * 0.66f).toInt().coerceAtLeast(0)),
        )
        Slice(color, label)
    }
}

private fun DrawScope.drawWheel(slices: List<Slice>, sweep: Float, rotation: Float) {
    val r = size.minDimension / 2 * 0.92f
    val topLeft = center - Offset(r, r)

    rotate(rotation) {
        slices.forEachIndexed { i, slice ->
            val start = -90f + sweep * i
            drawArc(slice.color, start, sweep, useCenter = true, topLeft = topLeft, size = Size(r * 2, r * 2))

            // Labels run along the slice's radius, flipped on the left half so they're never upside down.
            val mid = start + sweep / 2
            val absolute = (((mid + rotation) % 360f) + 360f) % 360f
            val flip = absolute > 90f && absolute < 270f
            val layout = slice.label
            rotate(if (flip) mid + 180f else mid, pivot = center) {
                val x = if (flip) center.x - r * 0.9f else center.x + r * 0.9f - layout.size.width
                drawText(layout, topLeft = Offset(x, center.y - layout.size.height / 2f))
            }
        }
    }
    drawCircle(Color.White, r * 0.1f, center)
    drawCircle(Color(0x33000000), r * 0.1f, center, style = Stroke(1.dp.toPx()))
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
