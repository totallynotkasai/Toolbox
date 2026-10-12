package com.toolbox.app.toys.switches

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.Stat
import com.toolbox.app.ui.common.StatRow
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.Vibes
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Positions on the notched slider. */
internal const val DETENTS = 10

private val SLIDER_PAD = 28.dp
private val CHROME = listOf(Color(0xFF9AA0A6), Color(0xFFF8F9FA), Color(0xFFBDC1C6), Color(0xFF80868B))
private const val PRESS_DELAY_MS = 100L // while the panel scrolls, how long a finger must stay put to count as a press

/** Nearest detent (0 until [DETENTS]) to a slider position 0..1. */
internal fun nearestDetent(pos: Float): Int = (pos.coerceIn(0f, 1f) * (DETENTS - 1)).roundToInt()

/** Where to draw the slider cap for finger position [pos]: pulled a little toward the nearest notch, like a magnet. */
internal fun magnetPos(pos: Float): Float {
    val p = pos.coerceIn(0f, 1f)
    return p + (nearestDetent(p) / (DETENTS - 1f) - p) * 0.35f
}

/** How a touch on a switch turned out: see [awaitPress]. */
private enum class Touch { Scroll, Tap, Hold }

/**
 * Waits for a finger and says what it was. On a panel that can't scroll ([scrolls] false) it's a [Touch.Hold] the
 * moment the finger lands, so a switch fires on press. On one that can, the finger has to stay put a moment first
 * ([PRESS_DELAY_MS], or move sideways): if the panel starts scrolling instead it's a [Touch.Scroll] and nothing
 * clicks, and if it lifts in that moment it's a [Touch.Tap], already over.
 */
private suspend fun AwaitPointerEventScope.awaitPress(scrolls: Boolean): Touch = pressAfter(awaitFirstDown(), scrolls)

/** [awaitPress] for a finger that has already landed at [down]. */
private suspend fun AwaitPointerEventScope.pressAfter(down: PointerInputChange, scrolls: Boolean): Touch {
    if (!scrolls) return Touch.Hold
    val slop = viewConfiguration.touchSlop
    return withTimeoutOrNull(PRESS_DELAY_MS) {
        var touch: Touch? = null
        while (touch == null) {
            // The Final pass comes after the panel's scrolling, so a scroll has consumed the move by now.
            val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
            touch = when {
                change == null || change.isConsumed -> Touch.Scroll
                !change.pressed -> Touch.Tap
                (change.position - down.position).getDistance() > slop -> Touch.Hold
                else -> null
            }
        }
        touch
    } ?: Touch.Hold
}

/** Waits until every finger is up, or the gesture is taken away. */
private suspend fun AwaitPointerEventScope.awaitRelease() {
    do {
        val event = awaitPointerEvent()
    } while (event.changes.any { it.pressed })
}

@Composable
fun SwitchesScreen(onBack: () -> Unit) {
    val audio = rememberPcmStream { SwitchAudio() }
    val vibes = rememberVibes()
    var total by rememberPref("switches_clicks", 0)
    var session by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val scrolls = { scroll.maxValue > 0 }

    fun counted() {
        session += 1
        total += 1
    }

    ToolScaffold(title = "Clicky Switches", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Like ToolBody: it only scrolls when the panel doesn't fit, and only then do presses wait.
                .verticalScroll(scroll, enabled = scroll.maxValue > 0)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatRow(gap = 40.dp) {
                Stat("Clicks", "$session")
                Stat("All time", "$total")
            }
            val rowModifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToyCard("Toggle", Modifier.weight(1f)) { ToggleSwitch(audio, vibes, scrolls) { counted() } }
                ToyCard("Rocker", Modifier.weight(1f)) { RockerSwitch(audio, vibes, scrolls) { counted() } }
            }
            Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToyCard("Arcade button", Modifier.weight(1f)) { ArcadeButton(audio, vibes, scrolls) { counted() } }
                ToyCard("Clicky pen", Modifier.weight(1f)) { ClickyPen(audio, vibes, scrolls) { counted() } }
            }
            ToyCard("Notched slider", rowModifier) { NotchedSlider(audio, vibes, scrolls) { counted() } }
        }
    }
}

@Composable
private fun ToyCard(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            Spacer(Modifier.height(6.dp))
            // Each switch names itself to TalkBack, so this caption isn't read twice.
            Text(
                label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

// ---- 1. Toggle switch ------------------------------------------------------------------------------------

/**
 * A chunky bat-handle toggle. It snaps over with a two-stage click as soon as you press it; keep going and you can
 * flick the lever back the other way.
 */
@Composable
private fun ToggleSwitch(audio: SwitchAudio, vibes: Vibes, scrolls: () -> Boolean, onClick: () -> Unit) {
    var on by rememberSaveable { mutableStateOf(false) }
    val lever = remember { Animatable(if (on) 1f else -1f) }
    val scope = rememberCoroutineScope()

    fun flip(to: Boolean) {
        if (to == on) return
        on = to
        onClick()
        audio.toggle(to)
        vibes.tick(0.2f) // leaving the detent...
        scope.launch {
            delay(TOGGLE_SNAP_MS)
            vibes.click(0.85f) // ...and the snap
        }
        scope.launch { lever.animateTo(if (to) 1f else -1f, spring(dampingRatio = 0.45f, stiffness = 2500f)) }
    }

    // Read here rather than in the semantics block, so a flip recomposes and TalkBack hears the new state.
    val state = ToggleableState(on)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Toggle switch"
                role = Role.Switch
                toggleableState = state
                onClick { flip(!on); true }
            }
            .pointerInput(Unit) {
                val threshold = 20.dp.toPx()
                awaitEachGesture {
                    val touch = awaitPress(scrolls())
                    if (touch == Touch.Scroll) return@awaitEachGesture
                    flip(!on)
                    if (touch == Touch.Tap) return@awaitEachGesture
                    // Still down: a flick the other way throws the lever back.
                    var travel = 0f
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        travel += change.positionChange().y
                        change.consume()
                        if (travel < -threshold) { flip(true); travel = 0f }
                        if (travel > threshold) { flip(false); travel = 0f }
                    }
                }
            },
    ) { drawToggle(lever.value) }
}

/** [p] is the lever position, -1 (down, off) to 1 (up, on); 0 points straight at the viewer. */
private fun DrawScope.drawToggle(p: Float) {
    val s = size.minDimension
    val c = center
    val plate = Size(s * 0.44f, s * 0.8f)
    val tl = Offset(c.x - plate.width / 2, c.y - plate.height / 2)
    val corner = CornerRadius(s * 0.06f)
    drawRoundRect(Color.Black.copy(alpha = 0.18f), tl + Offset(s * 0.015f, s * 0.025f), plate, corner)
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFFF1F3F4), Color(0xFFBDC1C6), Color(0xFF9AA0A6)), tl, tl + Offset(plate.width, plate.height)),
        tl, plate, corner,
    )
    drawRoundRect(Color(0xFF5F6368), tl, plate, corner, style = Stroke(s * 0.008f))
    drawScrew(Offset(c.x, tl.y + plate.height * 0.9f), s * 0.03f)

    // Status LED at the top of the plate.
    val lit = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
    val led = Offset(c.x, tl.y + plate.height * 0.1f)
    val ledR = s * 0.03f
    if (lit > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(GoogleColors.Green.copy(alpha = 0.55f * lit), Color.Transparent), center = led, radius = ledR * 3.2f),
            ledR * 3.2f, led,
        )
    }
    drawCircle(lerp(Color(0xFF3C4043), GoogleColors.Green, lit), ledR, led)
    drawCircle(Color.White.copy(alpha = 0.5f), ledR * 0.35f, led - Offset(ledR * 0.3f, ledR * 0.3f))

    // Hex nut and bushing the lever pivots in.
    val nutR = s * 0.1f
    val nut = hexagon(c, nutR)
    drawCircle(Color.Black.copy(alpha = 0.2f), nutR, c + Offset(s * 0.01f, s * 0.015f))
    drawPath(nut, Brush.linearGradient(listOf(Color(0xFFF8F9FA), Color(0xFF80868B)), c - Offset(nutR, nutR), c + Offset(nutR, nutR)))
    drawPath(nut, Color(0xFF5F6368), style = Stroke(s * 0.006f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF5F6368), Color(0xFF202124)), center = c, radius = nutR * 0.6f), nutR * 0.6f, c)

    // The lever, foreshortened: it points up, at the viewer (mid-throw) or down.
    val tip = Offset(c.x, c.y - s * 0.3f * p)
    drawLine(
        Color.Black.copy(alpha = 0.22f), c + Offset(s * 0.015f, s * 0.02f), tip + Offset(s * 0.03f, s * 0.05f),
        strokeWidth = s * 0.07f, cap = StrokeCap.Round,
    )
    drawLine(
        Brush.horizontalGradient(CHROME, startX = c.x - s * 0.035f, endX = c.x + s * 0.035f), c, tip,
        strokeWidth = s * 0.065f, cap = StrokeCap.Round,
    )
    val ballR = s * 0.075f * (1.2f - 0.2f * abs(p).coerceAtMost(1f)) // nearer the eye mid-throw, so bigger
    val ball = GoogleColors.Yellow
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(ball, Color.White, 0.55f), ball, ball.shade(0.7f)),
            center = tip - Offset(ballR * 0.35f, ballR * 0.4f), radius = ballR * 1.3f,
        ),
        ballR, tip,
    )
}

// ---- 2. Rocker switch ------------------------------------------------------------------------------------

/** A light-switch rocker with a neon lamp; press it to rock it over with a deep thock. */
@Composable
private fun RockerSwitch(audio: SwitchAudio, vibes: Vibes, scrolls: () -> Boolean, onClick: () -> Unit) {
    var on by rememberSaveable { mutableStateOf(false) }
    val tilt = remember { Animatable(if (on) 1f else 0f) }
    val scope = rememberCoroutineScope()

    fun rock() {
        on = !on
        onClick()
        audio.rocker(on)
        vibes.thud(0.6f)
        scope.launch { tilt.animateTo(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 2500f)) }
    }

    val state = ToggleableState(on) // read in composition, as for the toggle
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Rocker switch"
                role = Role.Switch
                toggleableState = state
                onClick { rock(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val touch = awaitPress(scrolls())
                    if (touch == Touch.Scroll) return@awaitEachGesture
                    rock()
                    if (touch == Touch.Hold) awaitRelease()
                }
            },
    ) { drawRocker(tilt.value.coerceIn(0f, 1f), GoogleColors.Green) }
}

/** [q] 0..1: 0 = bottom half pressed in (off), 1 = top half pressed in (on). */
private fun DrawScope.drawRocker(q: Float, color: Color) {
    val s = size.minDimension
    val c = center
    val plate = Size(s * 0.56f, s * 0.82f)
    val ptl = Offset(c.x - plate.width / 2, c.y - plate.height / 2)
    val corner = CornerRadius(s * 0.05f)
    drawRoundRect(Color.Black.copy(alpha = 0.16f), ptl + Offset(s * 0.012f, s * 0.022f), plate, corner)
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE8EAED), Color(0xFFCFD2D6)), ptl, ptl + Offset(plate.width, plate.height)),
        ptl, plate, corner,
    )
    drawRoundRect(Color(0xFFBDC1C6), ptl, plate, corner, style = Stroke(s * 0.006f))
    drawScrew(Offset(c.x, c.y + s * 0.365f), s * 0.024f)

    // Neon lamp window.
    val lamp = GoogleColors.Red
    val lampSize = Size(s * 0.16f, s * 0.045f)
    val lampTl = Offset(c.x - lampSize.width / 2, ptl.y + plate.height * 0.07f)
    if (q > 0.01f) {
        val glow = s * 0.03f
        drawRoundRect(
            lamp.copy(alpha = 0.3f * q), lampTl - Offset(glow, glow),
            Size(lampSize.width + 2 * glow, lampSize.height + 2 * glow), CornerRadius(glow * 1.5f),
        )
    }
    drawRoundRect(lerp(Color(0xFF5F2B28), lamp, q), lampTl, lampSize, CornerRadius(lampSize.height / 2))

    // The recess and the rocker itself: each half is lit when raised and in shadow when pressed in.
    val rocker = Size(s * 0.36f, s * 0.54f)
    val rc = c + Offset(0f, s * 0.04f)
    val rtl = Offset(rc.x - rocker.width / 2, rc.y - rocker.height / 2)
    val gap = s * 0.018f
    drawRoundRect(
        Color(0xFF80868B), rtl - Offset(gap, gap), Size(rocker.width + 2 * gap, rocker.height + 2 * gap), CornerRadius(s * 0.04f),
    )
    val light = lerp(color, Color.White, 0.3f)
    val mid = color.shade(0.8f)
    val dark = color.shade(0.55f)
    val half = Size(rocker.width, rocker.height / 2)
    val bottom = rtl.y + rocker.height
    val clip = Path().apply {
        addRoundRect(RoundRect(rtl.x, rtl.y, rtl.x + rocker.width, bottom, CornerRadius(s * 0.03f)))
    }
    clipPath(clip) {
        drawRect(
            Brush.verticalGradient(listOf(lerp(light, dark, q), lerp(color, mid, q)), startY = rtl.y, endY = rc.y),
            rtl, half,
        )
        drawRect(
            Brush.verticalGradient(listOf(lerp(mid, color, q), lerp(dark, color.shade(0.7f), q)), startY = rc.y, endY = bottom),
            Offset(rtl.x, rc.y), half,
        )
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(rtl.x, rc.y), Offset(rtl.x + rocker.width, rc.y), strokeWidth = s * 0.008f)
        // I on top, O below.
        val mark = Color.White.copy(alpha = 0.85f)
        drawLine(
            mark, Offset(rc.x, rtl.y + rocker.height * 0.15f), Offset(rc.x, rtl.y + rocker.height * 0.32f),
            strokeWidth = s * 0.018f, cap = StrokeCap.Round,
        )
        drawCircle(mark, s * 0.04f, Offset(rc.x, rtl.y + rocker.height * 0.76f), style = Stroke(s * 0.016f))
    }
}

// ---- 3. Arcade button ------------------------------------------------------------------------------------

/** A big domed arcade button: solid click on press, lighter click on release. */
@Composable
private fun ArcadeButton(audio: SwitchAudio, vibes: Vibes, scrolls: () -> Boolean, onClick: () -> Unit) {
    val depth = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun press() {
        onClick()
        audio.arcadeDown()
        vibes.click(0.8f)
        scope.launch { depth.animateTo(1f, tween(35)) }
    }

    fun release() {
        audio.arcadeUp()
        vibes.tick(0.4f)
        scope.launch { depth.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 1500f)) }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Arcade button"
                role = Role.Button
                onClick { press(); release(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val touch = awaitPress(scrolls())
                    if (touch == Touch.Scroll) return@awaitEachGesture
                    press()
                    if (touch == Touch.Hold) awaitRelease()
                    release()
                }
            },
    ) { drawArcade(depth.value.coerceIn(0f, 1f), GoogleColors.Red) }
}

/** [d] 0..1: how far the cap is pushed into its housing. */
private fun DrawScope.drawArcade(d: Float, color: Color) {
    val s = size.minDimension
    val c = center
    val bezelR = s * 0.42f
    drawCircle(Color.Black.copy(alpha = 0.25f), bezelR, c + Offset(0f, s * 0.025f))
    drawCircle(
        Brush.linearGradient(listOf(Color(0xFF5F6368), Color(0xFF202124)), c - Offset(bezelR, bezelR), c + Offset(bezelR, bezelR)),
        bezelR, c,
    )
    drawCircle(Color(0xFF3C4043), bezelR * 0.82f, c)
    drawCircle(Color(0xFF101114), bezelR * 0.78f, c)

    val r = s * 0.3f
    val base = c + Offset(0f, s * 0.012f)
    val lift = s * 0.055f * (1f - d) + s * 0.008f
    val cap = base - Offset(0f, lift)
    // Side wall of the plunger, visible below the cap until it is pushed flush.
    drawCircle(Color.Black.copy(alpha = 0.4f), r * 1.02f, base + Offset(0f, s * 0.01f))
    drawRect(color.shade(0.55f), topLeft = Offset(c.x - r, cap.y), size = Size(2 * r, lift))
    drawCircle(color.shade(0.5f), r, base)
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(color, Color.White, 0.45f), color, color.shade(0.7f)),
            center = cap - Offset(r * 0.3f, r * 0.35f), radius = r * 1.35f,
        ),
        r, cap,
    )
    drawCircle(color.shade(0.6f), r, cap, style = Stroke(s * 0.008f))
    drawOval(
        Color.White.copy(alpha = 0.35f * (1f - 0.3f * d)),
        topLeft = cap + Offset(-r * 0.55f, -r * 0.75f), size = Size(r * 0.8f, r * 0.45f),
    )
}

// ---- 4. Clicky pen ---------------------------------------------------------------------------------------

/** A retractable pen: each press on it clicks the nib out (high, sharp) or back in (lower). */
@Composable
private fun ClickyPen(audio: SwitchAudio, vibes: Vibes, scrolls: () -> Boolean, onClick: () -> Unit) {
    var extended by rememberSaveable { mutableStateOf(false) }
    val nib = remember { Animatable(if (extended) 1f else 0f) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun click() {
        extended = !extended
        onClick()
        if (extended) {
            audio.penIn()
            vibes.click(0.55f)
        } else {
            audio.penOut()
            vibes.tick(0.6f)
        }
        scope.launch { press.animateTo(1f, tween(30)) }
        scope.launch { nib.animateTo(if (extended) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 2000f)) }
    }

    fun letGo() {
        scope.launch { press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 1500f)) }
    }

    val out = extended // read in composition, as for the toggle
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Clicky pen"
                role = Role.Switch
                stateDescription = if (out) "Nib out" else "Nib in"
                toggleableState = ToggleableState(out)
                onClick { click(); letGo(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val touch = awaitPress(scrolls())
                    if (touch == Touch.Scroll) return@awaitEachGesture
                    click()
                    if (touch == Touch.Hold) awaitRelease()
                    letGo()
                }
            },
    ) { drawPen(nib.value.coerceIn(0f, 1.2f), press.value.coerceIn(0f, 1f), GoogleColors.Blue) }
}

/** Vertical pen, plunger on top. [nib] 0 = retracted, 1 = out; [press] 0..1 = plunger pushed down by a finger. */
private fun DrawScope.drawPen(nib: Float, press: Float, color: Color) {
    val s = size.minDimension
    val oy = (size.height - s) / 2
    val cx = center.x
    val w = s * 0.15f
    val bodyTop = oy + s * 0.3f
    val bodyBot = oy + s * 0.78f
    val coneBot = oy + s * 0.9f

    // Shadow of the whole pen.
    drawRoundRect(
        Color.Black.copy(alpha = 0.16f), Offset(cx - w / 2 + s * 0.02f, bodyTop + s * 0.02f),
        Size(w, coneBot - bodyTop), CornerRadius(w * 0.4f),
    )

    // Plunger: sits lower once the nib is out, and lower still under a finger.
    val plungerW = w * 0.55f
    val plungerH = s * 0.14f - nib.coerceAtMost(1f) * s * 0.04f - press * s * 0.035f
    drawRoundRect(
        Brush.horizontalGradient(CHROME, startX = cx - plungerW / 2, endX = cx + plungerW / 2),
        Offset(cx - plungerW / 2, bodyTop - plungerH), Size(plungerW, plungerH + s * 0.02f), CornerRadius(plungerW * 0.3f),
    )

    // Nib (drawn first so the cone covers its root).
    val nibLen = s * 0.06f * nib
    if (nibLen > 0.5f) {
        drawLine(Color(0xFF3C4043), Offset(cx, coneBot - s * 0.01f), Offset(cx, coneBot + nibLen), strokeWidth = w * 0.14f, cap = StrokeCap.Round)
    }
    val cone = Path().apply {
        moveTo(cx - w / 2, bodyBot)
        lineTo(cx + w / 2, bodyBot)
        lineTo(cx + w * 0.12f, coneBot)
        lineTo(cx - w * 0.12f, coneBot)
        close()
    }
    drawPath(cone, Brush.horizontalGradient(CHROME, startX = cx - w / 2, endX = cx + w / 2))

    // Barrel with a rubber grip and a chrome band.
    drawRoundRect(
        Brush.horizontalGradient(
            listOf(color.shade(0.6f), lerp(color, Color.White, 0.35f), color, color.shade(0.55f)),
            startX = cx - w / 2, endX = cx + w / 2,
        ),
        Offset(cx - w / 2, bodyTop), Size(w, bodyBot - bodyTop), CornerRadius(w * 0.25f),
    )
    for (k in 0 until 7) {
        val y = oy + s * (0.6f + k * 0.022f)
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(cx - w * 0.42f, y), Offset(cx + w * 0.42f, y), strokeWidth = s * 0.008f)
    }
    drawRect(Brush.horizontalGradient(CHROME, startX = cx - w / 2, endX = cx + w / 2), Offset(cx - w / 2, bodyTop), Size(w, s * 0.03f))

    // Pocket clip.
    val clipX = cx + w * 0.35f
    val clipTop = bodyTop + s * 0.02f
    drawRoundRect(
        Brush.horizontalGradient(CHROME, startX = clipX, endX = clipX + w * 0.22f),
        Offset(clipX, clipTop), Size(w * 0.22f, s * 0.25f), CornerRadius(w * 0.1f),
    )
    drawCircle(Color(0xFFDADCE0), w * 0.14f, Offset(clipX + w * 0.11f, clipTop + s * 0.25f))
}

// ---- 5. Notched slider -----------------------------------------------------------------------------------

/** A fader with [DETENTS] notches: jumps to your finger, ticks as it passes each one and snaps to the nearest on release. */
@Composable
private fun NotchedSlider(audio: SwitchAudio, vibes: Vibes, scrolls: () -> Boolean, onClick: () -> Unit) {
    var pos by remember { mutableFloatStateOf(0f) }
    var detent by remember { mutableIntStateOf(0) }
    val snapJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marks = MaterialTheme.colorScheme.outline
    val fill = GoogleColors.Blue

    fun follow(to: Float) {
        pos = to.coerceIn(0f, 1f)
        val d = nearestDetent(pos)
        if (d != detent) {
            detent = d
            onClick()
            audio.detent(d)
            vibes.tick(0.45f)
        }
    }

    fun snap() {
        snapJob.value?.cancel()
        val target = detent / (DETENTS - 1f)
        snapJob.value = scope.launch {
            animate(pos, target, animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f)) { v, _ -> pos = v }
        }
    }

    val notch = detent // read in composition, as for the toggle
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .semantics {
                contentDescription = "Notched slider"
                // Said instead of the percentage TalkBack would make of the range.
                stateDescription = "Notch ${notch + 1} of $DETENTS"
                progressBarRangeInfo = ProgressBarRangeInfo(notch.toFloat(), 0f..(DETENTS - 1f), steps = DETENTS - 2)
                setProgress { value ->
                    val d = value.roundToInt().coerceIn(0, DETENTS - 1)
                    if (d != detent) {
                        snapJob.value?.cancel()
                        follow(d / (DETENTS - 1f))
                        snap()
                    }
                    true
                }
            }
            .pointerInput(Unit) {
                fun at(x: Float): Float {
                    val pad = SLIDER_PAD.toPx()
                    return (x - pad) / (size.width - 2 * pad)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Waits only while the panel scrolls, to tell a press from a scroll that started here.
                    val touch = pressAfter(down, scrolls())
                    if (touch == Touch.Scroll) return@awaitEachGesture
                    snapJob.value?.cancel()
                    follow(at(down.position.x))
                    if (touch == Touch.Hold) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            follow(at(change.position.x))
                        }
                    }
                    snap()
                }
            },
    ) { drawSlider(magnetPos(pos), detent, track, marks, fill) }
}

private fun DrawScope.drawSlider(shown: Float, detent: Int, track: Color, marks: Color, fill: Color) {
    val pad = SLIDER_PAD.toPx()
    val y = size.height / 2
    val x0 = pad
    val x1 = size.width - pad
    val th = 8.dp.toPx()
    drawRoundRect(track, Offset(x0, y - th / 2), Size(x1 - x0, th), CornerRadius(th / 2))

    // Notches above and below the track; the ones already passed take the fill colour, and are bolder so they don't
    // differ by colour alone.
    val gap = th / 2 + 6.dp.toPx()
    val len = 8.dp.toPx()
    for (i in 0 until DETENTS) {
        val x = x0 + (x1 - x0) * i / (DETENTS - 1f)
        val passed = i <= detent
        val col = if (passed) fill else marks
        val w = (if (passed) 3.dp else 2.dp).toPx()
        drawLine(col, Offset(x, y - gap), Offset(x, y - gap - len), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(col, Offset(x, y + gap), Offset(x, y + gap + len), strokeWidth = w, cap = StrokeCap.Round)
    }
    val tx = x0 + (x1 - x0) * shown
    drawRoundRect(fill, Offset(x0, y - th / 2), Size(tx - x0 + th / 2, th), CornerRadius(th / 2))

    // Fader cap: brushed grey with grip ridges and a coloured index line.
    val cw = 26.dp.toPx()
    val ch = 44.dp.toPx()
    val ctl = Offset(tx - cw / 2, y - ch / 2)
    val corner = CornerRadius(6.dp.toPx())
    drawRoundRect(Color.Black.copy(alpha = 0.22f), ctl + Offset(2.dp.toPx(), 3.dp.toPx()), Size(cw, ch), corner)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFFF8F9FA), Color(0xFFBDC1C6), Color(0xFF9AA0A6)), startY = ctl.y, endY = ctl.y + ch),
        ctl, Size(cw, ch), corner,
    )
    drawRoundRect(Color(0xFF80868B), ctl, Size(cw, ch), corner, style = Stroke(1.dp.toPx()))
    for (k in listOf(-1, 1)) {
        for (j in 1..3) {
            val ry = y + k * (ch * 0.1f + j * ch * 0.09f)
            drawLine(Color.Black.copy(alpha = 0.2f), Offset(ctl.x + cw * 0.2f, ry), Offset(ctl.x + cw * 0.8f, ry), strokeWidth = 1.dp.toPx())
        }
    }
    drawLine(fill, Offset(ctl.x + cw * 0.15f, y), Offset(ctl.x + cw * 0.85f, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
}

// ---- Shared drawing bits ---------------------------------------------------------------------------------

private fun Color.shade(f: Float) = Color(red * f, green * f, blue * f, alpha)

private fun hexagon(c: Offset, r: Float) = Path().apply {
    for (k in 0 until 6) {
        val a = Math.toRadians(60.0 * k + 30.0)
        val x = c.x + r * kotlin.math.cos(a).toFloat()
        val y = c.y + r * kotlin.math.sin(a).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawScrew(c: Offset, r: Float) {
    drawCircle(Brush.linearGradient(listOf(Color(0xFFF1F3F4), Color(0xFF80868B)), c - Offset(r, r), c + Offset(r, r)), r, c)
    drawCircle(Color(0xFF5F6368), r, c, style = Stroke(r * 0.15f))
    drawLine(Color(0xFF5F6368), c + Offset(-r * 0.55f, r * 0.25f), c + Offset(r * 0.55f, -r * 0.25f), strokeWidth = r * 0.25f)
}
