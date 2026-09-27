package com.toolbox.app.tools.dicetoss

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ShakeToggleButton
import com.toolbox.app.ui.common.SteadyText
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.VibesToggleButton
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.common.rememberVibes
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val ELEVATION = 58f
private const val MAX_DICE = 5

/** Swipe speeds (dp/s) mapped onto throw power 0–1. Slower swipes don't throw. */
private const val SWIPE_MIN = 350f
private const val SWIPE_MAX = 4000f

/** Shake strengths (m/s², see OnShake) mapped onto throw power 0–1. */
private const val SHAKE_MIN = 12f
private const val SHAKE_MAX = 40f

/** How close to a die (in die radii) a touch picks them up, and how quick a touch on one must be to count as a tap. */
private const val GRAB_REACH = 1.4f
private const val TAP_MS = 220L

/**
 * Physics dice: swipe across the table to throw them (harder goes further and spins more), and the numbers come
 * out of the simulation. Uses the same colours as the coin.
 */
@Composable
fun DiceTossScreen(onBack: () -> Unit) {
    val vibes = rememberVibes()
    val synth = rememberToneSynth()
    var count by rememberPref("dicetoss_count", 2)
    var sides by rememberPref("dicetoss_sides", 6)
    val physics = remember {
        DicePhysics(
            seed = System.nanoTime(),
            count = count.coerceIn(1, MAX_DICE),
            sides = sides.takeIf { it in DieShape.SIDES } ?: 6,
        )
    }
    var frame by remember { mutableLongStateOf(0L) } // bumped every simulated frame to redraw
    var rolling by remember { mutableStateOf(false) } // off the table: held or rolling
    var held by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<List<Int>?>(null) }

    fun toss(power: Float, dirX: Float, dirZ: Float) {
        if (!physics.toss(power, dirX, dirZ)) return // still rolling
        result = null
        held = false
        vibes.click(0.45f)
        rolling = true
    }

    fun grab(): Boolean {
        if (!physics.grab()) return false
        result = null
        held = true
        vibes.tick(0.6f)
        rolling = true
        return true
    }

    fun drop() {
        if (physics.drop()) held = false
    }

    fun changeCount(n: Int) {
        if (rolling || n !in 1..MAX_DICE) return
        count = n
        physics.setCount(n)
        result = null
        frame++
    }

    fun changeSides(n: Int) {
        if (rolling || n == physics.shape.sides) return
        sides = n
        physics.setSides(n)
        result = null
        vibes.tick(0.4f)
        frame++
    }

    LaunchedEffect(rolling) {
        if (!rolling) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (physics.phase != DicePhysics.Phase.Resting) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f) // after a pause, don't jump
                last = now
                for (e in physics.step(dt)) when (e) {
                    is DicePhysics.Event.Knock -> {
                        synth.knock(e.intensity)
                        // Hard landings thump; the little knocks as they tumble to a stop tick.
                        if (e.intensity > 0.3f) vibes.thud(0.3f + 0.7f * e.intensity) else vibes.tick(0.25f + e.intensity)
                    }
                    is DicePhysics.Event.Settled -> vibes.click(0.35f)
                }
                frame++
            }
        }
        result = physics.values
        rolling = false
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { strength ->
        toss((strength - SHAKE_MIN) / (SHAKE_MAX - SHAKE_MIN), Random.nextFloat() - 0.5f, -1f)
    }

    ToolScaffold(
        title = "Dice Toss",
        onBack = onBack,
        actions = {
            VibesToggleButton()
            ShakeToggleButton(shake, "throw")
        },
    ) { padding ->
        val stage = @Composable { modifier: Modifier ->
            DiceTable(
                physics = physics,
                frame = frame,
                onTap = { toss(0.25f + Random.nextFloat() * 0.15f, Random.nextFloat() * 0.4f - 0.2f, -1f) },
                onSwipe = ::toss,
                onGrab = ::grab,
                onDrop = ::drop,
                modifier = modifier,
            )
        }
        val controls = @Composable { scope: ColumnScope ->
            with(scope) {
                val values = result
                SteadyText(
                    when {
                        held -> "Flick to throw"
                        rolling -> ""
                        values == null -> "Swipe to throw"
                        values.size == 1 -> "${values[0]}"
                        else -> "Total ${values.sum()}"
                    },
                    style = if (values != null && !held) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    reserve = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                SteadyText(
                    when {
                        held -> "Drag to move them. Let go to drop them"
                        rolling -> ""
                        values != null && values.size > 1 -> values.joinToString("  +  ")
                        else -> "Harder goes further. Or pick them up and flick"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    lines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DieShape.SIDES.forEach { n ->
                        FilterChip(
                            selected = sides == n,
                            onClick = { changeSides(n) },
                            enabled = !rolling,
                            label = { Text("d$n") },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalIconButton(onClick = { changeCount(count - 1) }, enabled = !rolling && count > 1) {
                        Icon(Icons.Filled.Remove, contentDescription = "Fewer dice")
                    }
                    Text("$count ${if (count == 1) "die" else "dice"}", style = MaterialTheme.typography.titleMedium)
                    FilledTonalIconButton(onClick = { changeCount(count + 1) }, enabled = !rolling && count < MAX_DICE) {
                        Icon(Icons.Filled.Add, contentDescription = "More dice")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { toss(0.45f + Random.nextFloat() * 0.35f, Random.nextFloat() * 0.5f - 0.25f, -1f) },
                    enabled = !rolling,
                ) { Text("Throw") }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    stage(Modifier.weight(1f).fillMaxSize())
                    Spacer(Modifier.padding(8.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { controls(this) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    stage(Modifier.widthIn(max = 480.dp).fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(16.dp))
                    controls(this)
                }
            }
        }
    }
}

/** How the table fits a canvas of [size]: dice small enough that five fit across and the whole table shows. */
private fun tableView(size: IntSize, density: Density): DiceView = with(density) {
    val s = minOf(size.width / 6.5f, size.height / 9f, 64.dp.toPx())
    // Leave room above the far wall for a die in the air, and a little below the near wall.
    DiceView(ELEVATION, size.width / 2f, s * 1.6f + halfDepth(size, s) * SIN_ELEVATION * s, s)
}

private val SIN_ELEVATION = sin(Math.toRadians(ELEVATION.toDouble())).toFloat()

private fun halfDepth(size: IntSize, scale: Float) = max(1.5f, (size.height / scale - 2.4f) / (2 * SIN_ELEVATION))

@Composable
private fun DiceTable(
    physics: DicePhysics,
    frame: Long,
    onTap: () -> Unit,
    onSwipe: (power: Float, dirX: Float, dirZ: Float) -> Unit,
    onGrab: () -> Boolean,
    onDrop: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val table = MaterialTheme.colorScheme.surfaceContainerHigh
    val tableFar = MaterialTheme.colorScheme.surfaceContainer
    val currentTap by rememberUpdatedState(onTap)
    val currentSwipe by rememberUpdatedState(onSwipe)
    val currentGrab by rememberUpdatedState(onGrab)
    val currentDrop by rememberUpdatedState(onDrop)
    Canvas(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .onSizeChanged {
                val view = tableView(it, density)
                physics.resize(it.width / 2f / view.scale, halfDepth(it, view.scale))
            }
            .semantics {
                role = Role.Button
                contentDescription = "Dice showing ${physics.values.joinToString(", ")}. Swipe to throw"
                onClick(label = "Throw the dice") { onTap(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // Pressing on any resting die picks them all up; anywhere else starts a swipe.
                    val view = tableView(size, density)
                    val reach = view.scale * physics.shape.radius * GRAB_REACH
                    val onDie = physics.dice.any { (down.position - view.point(it.position)).getDistance() < reach }
                    val holding = onDie && currentGrab()
                    val start = physics.handPosition
                    var travelled = 0f
                    var upTime = down.uptimeMillis
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        travelled = max(travelled, (change.position - down.position).getDistance())
                        change.consume()
                        upTime = change.uptimeMillis
                        if (holding) {
                            // They follow the fingers over the table: up the screen is away, across the table.
                            val moved = change.position - down.position
                            physics.hold(start.x + moved.x / view.scale, start.z + moved.y / (view.scale * SIN_ELEVATION))
                        }
                        if (!change.pressed) break
                    }
                    val tap = travelled < viewConfiguration.touchSlop
                    if (tap && (!holding || upTime - down.uptimeMillis < TAP_MS)) {
                        currentTap()
                        return@awaitEachGesture
                    }
                    val v = tracker.calculateVelocity()
                    val vx = v.x / density.density
                    val vy = v.y / density.density
                    val speed = hypot(vx, vy)
                    // Any direction works: the dice go the way the finger went (screen up is away across the table).
                    when {
                        speed >= SWIPE_MIN -> currentSwipe((speed - SWIPE_MIN) / (SWIPE_MAX - SWIPE_MIN), vx / speed, vy / speed)
                        holding -> currentDrop()
                    }
                }
            },
    ) {
        if (frame < 0) return@Canvas // reading frame makes every simulated step redraw
        drawRect(Brush.verticalGradient(listOf(tableFar, table)))
        drawDice(physics.dice, tableView(IntSize(size.width.toInt(), size.height.toInt()), this))
    }
}
