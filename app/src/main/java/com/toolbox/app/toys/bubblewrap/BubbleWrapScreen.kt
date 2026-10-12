package com.toolbox.app.toys.bubblewrap

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.Stat
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val BUBBLE_DP = 54
private const val HOLD_POP_MS = 150L         // a held bubble pops without waiting for the lift
private const val NEXT_SHEET_DELAY_MS = 700L // beat between the last pop and a fresh sheet
private const val SLIDE_SECONDS = 0.5f
private val EDGE_GUARD = 48.dp  // how far in from each side the back gesture is held off
private val GUARD_HEIGHT = 200.dp // the most of an edge Android lets an app keep for itself

/** A finger's claim on the bubble it first touched; [resolved] once that press has popped or squeaked it. */
private class Hold(val bubble: Int, val startMs: Long) {
    var resolved = false
}

/** What the frame loop, pointer handler and Canvas share. Plain fields: all three run on the main thread. */
private class WrapState {
    var grid: HexGrid? = null
    var sheet: BubbleSheet? = null
    var old: BubbleSheet? = null // the finished sheet sliding out
    var slide = 1f               // 0..1 progress of a fresh sheet rolling in; 1 = settled
    var nextSheetAt = 0L         // uptime ms to roll in a fresh sheet; 0 = none pending
    var sheets = 0
    val holds = HashMap<PointerId, Hold>()
}

@Composable
fun BubbleWrapScreen(onBack: () -> Unit) {
    val st = remember { WrapState() }
    var frame by remember { mutableLongStateOf(0L) } // bumped whenever the Canvas needs a redraw
    var sheetPops by remember { mutableIntStateOf(0) }
    var sheetSize by remember { mutableIntStateOf(0) }
    var total by rememberPref("bubblewrap_total", 0)
    val audio = rememberPcmStream { BubbleAudio() }
    val vibes = rememberVibes()
    val density = LocalDensity.current
    val pitchPx = with(density) { BUBBLE_DP.dp.toPx() }
    val marginPx = with(density) { 6.dp.toPx() }

    fun newSheet(rollIn: Boolean) {
        val grid = st.grid ?: return
        st.holds.clear()
        st.old = if (rollIn) st.sheet else null
        st.sheet = BubbleSheet(grid.count, tint = st.sheets % GoogleColors.all.size)
        st.sheets++
        st.slide = if (st.old != null) 0f else 1f
        st.nextSheetAt = 0L
        sheetPops = 0
        sheetSize = grid.count
        if (rollIn) {
            audio.rustle()
            vibes.pattern(longArrayOf(0, 8, 70, 8, 70, 8), floatArrayOf(0f, 0.3f, 0f, 0.22f, 0f, 0.15f))
        }
        frame++
    }

    fun resolve(sheet: BubbleSheet, i: Int) {
        when (sheet.press(i)) {
            Press.Pop -> {
                audio.pop(Random.nextFloat())
                vibes.click(0.5f + Random.nextFloat() * 0.3f)
                sheetPops = sheet.poppedCount
                total += 1
                if (sheet.allPopped) st.nextSheetAt = SystemClock.uptimeMillis() + NEXT_SHEET_DELAY_MS
            }
            Press.Squeak -> {
                audio.squeak()
                vibes.tick(0.35f)
            }
            Press.None -> Unit
        }
    }

    // Frame loop: hold-to-pop timing, squish easing, pop bursts and the sheet change. It goes idle once every
    // bubble has settled and no finger or new sheet is pending; a touch wakes it.
    val loop = rememberGameLoop { dt ->
        val now = SystemClock.uptimeMillis()
        var moving = false
        val sheet = st.sheet
        if (sheet != null) {
            for (h in st.holds.values) {
                if (!h.resolved && now - h.startMs >= HOLD_POP_MS) {
                    h.resolved = true
                    resolve(sheet, h.bubble)
                }
            }
            sheet.pressed.fill(false)
            for (h in st.holds.values) sheet.pressed[h.bubble] = true
            if (sheet.animate(dt)) moving = true
        }
        if (st.slide < 1f) {
            st.slide = (st.slide + dt / SLIDE_SECONDS).coerceAtMost(1f)
            if (st.slide >= 1f) st.old = null
            moving = true
        }
        if (st.nextSheetAt != 0L && now >= st.nextSheetAt) newSheet(rollIn = true)
        if (moving) frame++
        moving || st.holds.isNotEmpty() || st.nextSheetAt != 0L || st.slide < 1f
    }

    ToolScaffold(title = "Bubble Wrap", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Stat("This sheet", "$sheetPops / $sheetSize", widest = "000 / 000")
                Stat("Lifetime", "$total")
            }
            Spacer(Modifier.height(12.dp))
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    // Hold off the back gesture along the lower part of both edges, where a thumb popping the
                    // outer bubbles would otherwise leave the toy.
                    .systemGestureExclusion { with(density) { edgeGuard(it, left = true) } }
                    .systemGestureExclusion { with(density) { edgeGuard(it, left = false) } }
                    .onSizeChanged { sz ->
                        val g = st.grid
                        val w = sz.width.toFloat()
                        val h = sz.height.toFloat()
                        if (sz.width > 0 && sz.height > 0 && (g == null || g.width != w || g.height != h)) {
                            st.grid = HexGrid(w, h, pitchPx, marginPx)
                            newSheet(rollIn = false)
                        }
                    }
                    .pointerInput(Unit) {
                        // Each finger claims the bubble under it at touch-down; sliding onto others never pops them.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val now = SystemClock.uptimeMillis()
                                val sheet = st.sheet
                                val grid = st.grid
                                for (c in event.changes) {
                                    // A touch the system took away (a back swipe from the edge, say) comes through
                                    // as a lift that's already consumed: that one mustn't pop anything.
                                    val cancelled = c.isConsumed
                                    if (c.pressed && !c.previousPressed) {
                                        if (sheet != null && grid != null && st.slide >= 1f) {
                                            val i = grid.hit(c.position.x, c.position.y)
                                            if (i >= 0 && !sheet.popped[i] && st.holds.values.none { it.bubble == i }) {
                                                st.holds[c.id] = Hold(i, now)
                                                loop.wake()
                                            }
                                        }
                                    } else if (!c.pressed) {
                                        val h = st.holds.remove(c.id)
                                        if (h != null && !h.resolved && sheet != null && !cancelled) resolve(sheet, h.bubble)
                                        loop.wake()
                                    }
                                    c.consume()
                                }
                            }
                        }
                    },
            ) {
                if (frame < 0) return@Canvas
                val grid = st.grid ?: return@Canvas
                val ease = 1f - (1f - st.slide).pow(3)
                val w = size.width
                st.old?.let { old -> translate(left = -w * ease) { drawSheet(old, grid) } }
                st.sheet?.let { sheet -> translate(left = w * (1f - ease)) { drawSheet(sheet, grid) } }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { newSheet(rollIn = true); loop.wake() }) { Text("New sheet") }
        }
    }
}

/** A strip [EDGE_GUARD] wide up one side of the sheet, over its bottom [GUARD_HEIGHT]. */
private fun Density.edgeGuard(coords: LayoutCoordinates, left: Boolean): Rect {
    val w = coords.size.width.toFloat()
    val h = coords.size.height.toFloat()
    val strip = min(EDGE_GUARD.toPx(), w / 2f)
    val top = max(0f, h - GUARD_HEIGHT.toPx())
    return if (left) Rect(0f, top, strip, h) else Rect(w - strip, top, w, h)
}

private fun DrawScope.drawSheet(sheet: BubbleSheet, grid: HexGrid) {
    val tint = GoogleColors.all[sheet.tint % GoogleColors.all.size]
    drawRect(tint.copy(alpha = 0.07f)) // the flat film between the bubbles
    val r = grid.pitch * 0.43f
    for (i in 0 until sheet.count) {
        val c = Offset(grid.x(i), grid.y(i))
        if (sheet.popped[i]) drawPopped(c, r, tint, sheet, i) else drawBubble(c, r, tint, sheet.squish[i], sheet.softened[i])
    }
}

/** A glossy translucent dome; [squish] 0..1 flattens it (wider, dimmer highlight, a dimple in the middle). */
private fun DrawScope.drawBubble(c: Offset, r: Float, tint: Color, squish: Float, softened: Boolean) {
    val s = squish.coerceIn(0f, 1f)
    val rr = r * (1f + 0.08f * s)
    drawCircle(Color.Black.copy(alpha = 0.10f * (1f - 0.6f * s)), rr, c + Offset(r * 0.07f, r * 0.12f * (1f - 0.5f * s)))
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.45f * (1f - 0.5f * s)),
            0.6f to tint.copy(alpha = 0.18f + 0.1f * s),
            1f to tint.copy(alpha = 0.42f),
            center = c - Offset(r * 0.3f, r * 0.3f) * (1f - s),
            radius = rr * 1.3f,
        ),
        rr, c,
    )
    drawCircle(tint.copy(alpha = 0.55f), rr, c, style = Stroke(r * 0.07f))
    val hl = r * (0.34f - 0.18f * s)
    drawOval(Color.White.copy(alpha = 0.75f * (1f - 0.6f * s)), topLeft = c + Offset(-r * 0.55f, -r * 0.6f), size = Size(hl * 1.3f, hl))
    if (s > 0.01f) drawCircle(Color.Black.copy(alpha = 0.10f * s), rr * 0.55f, c + Offset(r * 0.05f, r * 0.08f))
    if (softened) {
        // A stubborn bubble that has been squashed once keeps a crease.
        drawArc(
            tint.copy(alpha = 0.6f), 200f, 110f, false,
            topLeft = c - Offset(r * 0.45f, r * 0.2f), size = Size(r * 0.9f, r * 0.6f),
            style = Stroke(r * 0.06f, cap = StrokeCap.Round),
        )
    }
}

/** A popped bubble: flat, crinkled film, plus a quick burst ring just after the pop. */
private fun DrawScope.drawPopped(c: Offset, r: Float, tint: Color, sheet: BubbleSheet, i: Int) {
    val age = sheet.popAge[i]
    if (age < POP_FLASH) {
        val k = (age / POP_FLASH).coerceIn(0f, 1f)
        drawCircle(Color.White.copy(alpha = 0.55f * (1f - k)), r * (1f + 0.35f * k), c, style = Stroke(r * 0.12f * (1f - k) + 0.5f))
    }
    val base = i * CRINKLE
    val path = Path()
    for (k in 0 until CRINKLE) {
        val a = 2 * PI * k / CRINKLE + sheet.crinkle[base + k] * 0.4
        val rad = r * sheet.crinkle[base + k]
        val x = c.x + rad * cos(a).toFloat()
        val y = c.y + rad * sin(a).toFloat()
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, tint.copy(alpha = 0.12f))
    drawPath(path, tint.copy(alpha = 0.4f), style = Stroke(r * 0.045f, join = StrokeJoin.Round))
    // A couple of creases across the collapsed film.
    for (k in intArrayOf(1, 5)) {
        val a = 2 * PI * k / CRINKLE + sheet.crinkle[base + k] * 0.4
        val rad = r * sheet.crinkle[base + k] * 0.85f
        val from = c - Offset(r * 0.15f * cos(a).toFloat(), r * 0.15f * sin(a).toFloat())
        drawLine(
            tint.copy(alpha = 0.35f), from, Offset(c.x + rad * cos(a).toFloat(), c.y + rad * sin(a).toFloat()),
            strokeWidth = r * 0.035f, cap = StrokeCap.Round,
        )
    }
}
