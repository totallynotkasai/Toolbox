package com.toolbox.app.toys.zip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.Feel
import com.toolbox.app.ui.common.Stat
import com.toolbox.app.ui.common.StatRow
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.accessibilityActions
import com.toolbox.app.ui.common.fixed
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.turnDelta
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

private enum class ZipMode(val label: String) { Zip("Zip"), Ratchet("Ratchet") }

internal const val ZIP_TEETH = 44          // teeth per side on a phone-sized zip
private const val TOOTH_PITCH_DP = 11f     // so a zip gets a tooth for about every 11 dp of travel
private const val MIN_TEETH = 16
private const val MAX_TEETH = 64
internal const val RATCHET_STEP = 15f      // degrees per gear tooth
internal const val TRACK_TOP = 0.05f       // slider travel, as fractions of the zip's height
internal const val TRACK_BOTTOM = 0.8f
private const val FAST_TEETH = 30f         // teeth/s above which ticks would smear; buzz instead
private const val HEAD = 0.34f             // ratchet head radius, fraction of the half-size
private const val ZIP_PULL_MS = 1200f      // a TalkBack pull from one end to the other, a brisk tug

/**
 * Teeth per side for a zip whose slider travels [trackDp]: about [ZIP_TEETH] on a phone, fewer on a short zip so
 * they don't shrink to mush, more on a tall one so they don't turn into planks.
 */
internal fun zipTeeth(trackDp: Float): Int = (trackDp / TOOTH_PITCH_DP).roundToInt().coerceIn(MIN_TEETH, MAX_TEETH)

/**
 * Teeth the slider has passed at [pos] (0 = closed, slider at the top; 1 = fully open) on a zip with [teeth] a side.
 * The two sides' teeth alternate, so it passes twice as many as there are on each side.
 */
internal fun teethPassed(pos: Float, teeth: Int = ZIP_TEETH): Int = floor(pos.coerceIn(0f, 1f) * teeth * 2).toInt()

/** How far each half of the zip has pulled away from the centre line, [d] px above the slider. */
internal fun zipSpread(d: Float, maxOpen: Float): Float = if (d <= 0f) 0f else maxOpen * tanh(d * 0.2f / maxOpen)

/**
 * How far the bolt turns when the handle moves [delta] degrees (clockwise +): in the clicking direction the
 * pawl slips over the gear and the bolt stays put; the other way the pawl locks and drives it.
 */
internal fun boltDelta(delta: Float, clicksClockwise: Boolean): Float =
    if (delta == 0f || (delta > 0f) == clicksClockwise) 0f else delta

/** Gear tooth the pawl sits behind; each change is one click. */
internal fun pawlNotch(handle: Float, bolt: Float): Int = floor((handle - bolt) / RATCHET_STEP).toInt()

/** How open the zip is at [pos], for TalkBack: in broad steps, so it isn't re-read at every tooth. */
internal fun zipStateText(pos: Float): String = when {
    pos <= 0f -> "Zipped up"
    pos >= 1f -> "Wide open"
    pos < 0.5f -> "Partly open"
    else -> "Mostly open"
}

/** The frame loop's speed estimates, which never need a recomposition. */
private class Rates {
    var prevPos = 0f
    var prevBolt = 0f
    var teeth = 0f // teeth/s
    var bolt = 0f  // deg/s
}

@Composable
fun ZipScreen(onBack: () -> Unit) {
    var modeName by rememberPref("zip_mode", ZipMode.Zip.name)
    val mode = ZipMode.entries.firstOrNull { it.name == modeName } ?: ZipMode.Zip
    val currentMode by rememberUpdatedState(mode)
    var clicksClockwise by rememberPref("ratchet_clicks_cw", true)

    var pos by remember { mutableFloatStateOf(0f) }         // slider: 0 = closed at the top, 1 = fully open
    var held by remember { mutableStateOf(false) }
    var handle by remember { mutableFloatStateOf(0f) }      // wrench handle angle, degrees
    var bolt by remember { mutableFloatStateOf(0f) }        // bolt angle, degrees, clockwise = tighter
    var clicks by rememberSaveable { mutableIntStateOf(0) }
    var teeth by remember { mutableIntStateOf(ZIP_TEETH) } // a side, set from the zip's height
    val feel = remember { Feel() }
    val rates = remember { Rates() }

    val audio = rememberPcmStream { ZipAudio() }
    val vibes = rememberVibes()
    val scope = rememberCoroutineScope()
    val slideJob = remember { mutableStateOf<Job?>(null) }

    fun flip() {
        clicksClockwise = !clicksClockwise
        audio.flip()
        vibes.click(0.6f)
    }

    // Frame loop: tooth rate and bolt speed, which drive the zip sound, the creak and the buzz. It goes idle once
    // both have died away; a drag wakes it.
    val loop = rememberGameLoop { dt ->
        if (dt > 0f) {
            val k = min(1f, dt * 12f)
            rates.teeth += (abs(pos - rates.prevPos) / dt * teeth * 2 - rates.teeth) * k
            rates.bolt += (abs(bolt - rates.prevBolt) / dt - rates.bolt) * k
        }
        rates.prevPos = pos
        rates.prevBolt = bolt
        if (rates.teeth < 0.05f) rates.teeth = 0f
        if (rates.bolt < 0.05f) rates.bolt = 0f
        val m = currentMode
        feel.fast = rates.teeth > FAST_TEETH
        audio.zipRate = if (m == ZipMode.Zip) rates.teeth else 0f
        audio.creak = if (m == ZipMode.Ratchet) rates.bolt else 0f
        when {
            m == ZipMode.Zip && feel.fast -> vibes.buzz(0.25f + 0.6f * min(1f, (rates.teeth - FAST_TEETH) / 300f))
            m == ZipMode.Ratchet && rates.bolt > 5f -> vibes.buzz(min(1f, rates.bolt / 540f) * 0.3f)
            else -> vibes.stopBuzz()
        }
        held || rates.teeth > 0f || rates.bolt > 0f
    }

    fun grabZip() {
        slideJob.value?.cancel()
        held = true
        audio.grab()
        vibes.tick(0.25f)
        loop.wake()
    }

    fun moveZipTo(to: Float) {
        val before = pos
        val after = to.coerceIn(0f, 1f)
        if (after != before) {
            pos = after
            val opening = after > before
            val passed = abs(teethPassed(after, teeth) - teethPassed(before, teeth))
            if (passed > 0) {
                audio.opening = opening
                repeat(min(passed, 3)) { audio.tooth(opening, it) }
                if (!feel.fast) feel.space { vibes.tick(if (opening) 0.35f else 0.3f) }
            }
            if ((after == 1f || after == 0f) && before != after) {
                audio.endStop()
                vibes.thud(0.45f)
                feel.mark()
            }
        }
    }

    // TalkBack's Unzip, Zip up and slider swipes: the tab is taken and run to [target] at a steady pull, so the teeth
    // whirr and click past just as they do under a finger.
    fun slideZip(target: Float) {
        if (target == pos) return
        grabZip()
        val from = pos
        val ms = (abs(target - from) * ZIP_PULL_MS).roundToInt().coerceAtLeast(150)
        slideJob.value = scope.launch {
            animate(from, target, animationSpec = tween(ms, easing = LinearEasing)) { v, _ -> moveZipTo(v) }
            held = false
        }
    }

    ToolScaffold(title = "Zip & Ratchet", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                ZipMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = m == mode,
                        onClick = {
                            if (m != mode) {
                                modeName = m.name
                                audio.grab()
                                vibes.tick(0.4f)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, ZipMode.entries.size),
                    ) { Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (mode) {
                ZipMode.Zip -> ZipPane(
                    pos = { pos },
                    held = held,
                    teeth = teeth,
                    onTrack = { trackDp -> teeth = zipTeeth(trackDp) },
                    onGrab = { grabZip() },
                    onRelease = { held = false },
                    onMove = { moveZipTo(pos + it) },
                    onSlide = { slideZip(it) },
                )
                ZipMode.Ratchet -> RatchetPane(
                    handle = handle,
                    bolt = bolt,
                    clicks = clicks,
                    clicksClockwise = clicksClockwise,
                    onTurn = { d ->
                        loop.wake()
                        val before = pawlNotch(handle, bolt)
                        handle += d
                        bolt += boltDelta(d, clicksClockwise)
                        val passed = abs(pawlNotch(handle, bolt) - before)
                        if (passed > 0) {
                            clicks += passed
                            repeat(min(passed, 3)) { audio.ratchet(it) }
                            feel.space { vibes.tick(0.6f) }
                        }
                    },
                    onFlip = { flip() },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ZipPane(
    pos: () -> Float,
    held: Boolean,
    teeth: Int,
    onTrack: (Float) -> Unit,
    onGrab: () -> Unit,
    onRelease: () -> Unit,
    onMove: (Float) -> Unit,
    onSlide: (Float) -> Unit,
) {
    val grab by rememberUpdatedState(onGrab)
    val release by rememberUpdatedState(onRelease)
    val move by rememberUpdatedState(onMove)
    val density = LocalDensity.current
    // Only these coarse readings recompose the pane as it moves: TalkBack's state, and the position in tenths, which
    // is also the step a swipe up or down moves it.
    val said by remember { derivedStateOf { zipStateText(pos()) } }
    val tenths by remember { derivedStateOf { (pos() * 10f).roundToInt() / 10f } }
    val actions = buildList {
        if (said != "Wide open") addAll(accessibilityActions("Unzip" to { onSlide(1f) }))
        if (said != "Zipped up") addAll(accessibilityActions("Zip up" to { onSlide(0f) }))
    }
    ZipStatus(pos, teeth)
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .weight(1f)
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .onSizeChanged { onTrack(with(density) { it.height.toDp().value } * (TRACK_BOTTOM - TRACK_TOP)) }
            .semantics {
                contentDescription = "Zip"
                stateDescription = said
                progressBarRangeInfo = ProgressBarRangeInfo(tenths, 0f..1f, steps = 9)
                setProgress { v -> onSlide(((v * 10f).roundToInt() / 10f).coerceIn(0f, 1f)); true }
                customActions = actions
            }
            .pointerInput(Unit) {
                // Drag anywhere: the slider follows the finger's vertical movement, not its position,
                // so it never jumps when grabbed.
                detectDragGestures(
                    onDragStart = { grab() },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                    onDrag = { change, d ->
                        change.consume()
                        move(d.y / (size.height * (TRACK_BOTTOM - TRACK_TOP)))
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawZip(pos(), held, teeth) }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Drag the pull tab down to unzip, up to close",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** How far open the zip is, in its own scope so a drag recomposes just this line, not the whole pane. */
@Composable
private fun ZipStatus(pos: () -> Float, teeth: Int) {
    val p = pos()
    Text(
        when {
            p <= 0f -> "Zipped up"
            p >= 1f -> "Wide open"
            else -> "${teethPassed(p, teeth) / 2} of $teeth teeth open"
        },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColumnScope.RatchetPane(
    handle: Float,
    bolt: Float,
    clicks: Int,
    clicksClockwise: Boolean,
    onTurn: (Float) -> Unit,
    onFlip: () -> Unit,
) {
    val boltTurns = fixed(bolt / 360f, 2)
    StatRow {
        Stat("Bolt turns", boltTurns, widest = "-00.00")
        Stat("Clicks", "$clicks")
    }
    val turn by rememberUpdatedState(onTurn)
    val flip by rememberUpdatedState(onFlip)
    val plate = MaterialTheme.colorScheme.surfaceVariant
    val marks = MaterialTheme.colorScheme.onSurfaceVariant
    val (free, drive) = if (clicksClockwise) "clockwise" to "anticlockwise" else "anticlockwise" to "clockwise"
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics {
                    contentDescription = "Ratchet wrench"
                    stateDescription = "Clicks $free, drives the bolt $drive. Bolt at $boltTurns turns"
                    onClick(label = "Swap direction") { flip(); true }
                    // One gear tooth of handle movement, as a drag would give.
                    customActions = accessibilityActions(
                        "Turn clockwise" to { turn(RATCHET_STEP) },
                        "Turn anticlockwise" to { turn(-RATCHET_STEP) },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        if ((p - c).getDistance() < size.width / 2f * HEAD * 1.15f) flip()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val c = Offset(size.width / 2f, size.height / 2f)
                        // Near the pivot the angle swings wildly for tiny moves, and a flick through the middle
                        // would read as half a turn, so turnDelta ignores that zone and caps the step.
                        val d = turnDelta(change.previousPosition, change.position, c, size.width * 0.08f)
                        if (d != 0f) turn(d)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawRatchet(handle, bolt, clicksClockwise, plate, marks) }
        }
    }
    Text(
        "Turn the handle: it clicks freely $free and drives the bolt $drive. Tap the head to swap.",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A socket wrench seen from above, on a steel plate with a degree scale. The chrome handle and head turn
 * with [handle]; the gear ring, socket and hex bolt turn with [bolt]. The pawl and the little reverse lever
 * on the head both lean toward the direction that clicks.
 */
private fun DrawScope.drawRatchet(handle: Float, bolt: Float, clicksClockwise: Boolean, plate: Color, marks: Color) {
    val r = size.minDimension / 2f
    val c = center

    // Plate and scale.
    drawCircle(plate, r * 0.98f, c)
    for (i in 0 until 24) {
        val a = Math.toRadians(i * 15.0)
        val major = i % 6 == 0
        val inner = r * if (major) 0.86f else 0.9f
        val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
        drawLine(marks.copy(alpha = if (major) 0.7f else 0.35f), c + dir * inner, c + dir * (r * 0.95f), strokeWidth = r * 0.012f, cap = StrokeCap.Round)
    }

    val head = r * HEAD
    val chrome = listOf(SteelDark, SteelLight, SteelMid, SteelDark)
    rotate(handle, pivot = c) {
        // Shadow, then the handle: a chrome bar with a red rubber grip.
        val hw = r * 0.15f
        val tip = c.y - r * 0.93f
        drawRoundRect(
            Color.Black.copy(alpha = 0.25f),
            topLeft = Offset(c.x - hw / 2f + r * 0.02f, tip + r * 0.03f),
            size = Size(hw, c.y - tip),
            cornerRadius = CornerRadius(hw / 2f),
        )
        drawRoundRect(
            Brush.horizontalGradient(chrome, startX = c.x - hw / 2f, endX = c.x + hw / 2f),
            topLeft = Offset(c.x - hw / 2f, tip),
            size = Size(hw, c.y - tip),
            cornerRadius = CornerRadius(hw / 2f),
        )
        val gw = hw * 1.15f
        val gripTop = tip
        val gripLen = r * 0.4f
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color(0xFF7A1F18), GoogleColors.Red, Color(0xFFB3261E), Color(0xFF7A1F18)),
                startX = c.x - gw / 2f, endX = c.x + gw / 2f,
            ),
            topLeft = Offset(c.x - gw / 2f, gripTop),
            size = Size(gw, gripLen),
            cornerRadius = CornerRadius(gw / 2f),
        )
        for (k in 1..7) {
            val y = gripTop + gripLen * k / 8f
            drawLine(Color.Black.copy(alpha = 0.25f), Offset(c.x - gw * 0.42f, y), Offset(c.x + gw * 0.42f, y), strokeWidth = r * 0.01f)
        }

        // Head.
        drawCircle(Color.Black.copy(alpha = 0.25f), head, c + Offset(r * 0.02f, r * 0.03f))
        drawCircle(Brush.linearGradient(listOf(SteelLight, SteelDark), start = c - Offset(head, head), end = c + Offset(head, head)), head, c)
        drawCircle(SteelDark, head, c, style = Stroke(r * 0.012f))

        // Pawl, leaning toward the clicking direction, sitting against the gear on the handle side.
        val lean = if (clicksClockwise) 1f else -1f
        val pawl = Path().apply {
            moveTo(c.x - lean * r * 0.05f, c.y - r * 0.28f)
            lineTo(c.x + lean * r * 0.06f, c.y - r * 0.27f)
            lineTo(c.x + lean * r * 0.015f, c.y - r * 0.215f)
            close()
        }
        drawPath(pawl, SteelDark)

        // Reverse lever on the far side of the head.
        rotate(lean * 28f, pivot = Offset(c.x, c.y + head * 0.78f)) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF3C4043), Color(0xFF80868B), Color(0xFF3C4043)), startX = c.x - r * 0.07f, endX = c.x + r * 0.07f),
                topLeft = Offset(c.x - r * 0.07f, c.y + head * 0.78f - r * 0.025f),
                size = Size(r * 0.14f, r * 0.05f),
                cornerRadius = CornerRadius(r * 0.025f),
            )
        }
    }

    rotate(bolt, pivot = c) {
        // Gear ring glimpsed through the head, its teeth 15° apart.
        drawCircle(Color(0xFF3C4043), r * 0.235f, c)
        for (i in 0 until 24) {
            val a = Math.toRadians(i * RATCHET_STEP.toDouble())
            val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
            drawLine(SteelMid, c + dir * (r * 0.19f), c + dir * (r * 0.232f), strokeWidth = r * 0.028f)
        }
        // Socket and the hex bolt head inside it.
        drawCircle(Brush.linearGradient(listOf(SteelMid, Color(0xFF3C4043)), start = c - Offset(r * 0.18f, r * 0.18f), end = c + Offset(r * 0.18f, r * 0.18f)), r * 0.18f, c)
        val hex = Path().apply {
            for (k in 0 until 6) {
                val a = Math.toRadians(30.0 + 60.0 * k)
                val p = c + Offset(sin(a).toFloat(), -cos(a).toFloat()) * (r * 0.13f)
                if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(hex, Brush.linearGradient(listOf(BrassLight, BrassDark), start = c - Offset(r * 0.13f, r * 0.13f), end = c + Offset(r * 0.13f, r * 0.13f)))
        drawPath(hex, Color(0xFF5F4A14), style = Stroke(r * 0.01f))
        // A paint mark on the bolt so you can watch it turn.
        drawLine(GoogleColors.Blue, c, c + Offset(0f, -r * 0.1f), strokeWidth = r * 0.03f, cap = StrokeCap.Round)
    }
}
