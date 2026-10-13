package com.toolbox.app.tools.dice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.toolbox.app.tools.coin.hintLines
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ResultHistorySheet
import com.toolbox.app.ui.common.ResultRow
import com.toolbox.app.ui.common.ResultStrip
import com.toolbox.app.ui.common.STRIP_MAX
import com.toolbox.app.ui.common.SteadyText
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.TossLayout
import com.toolbox.app.ui.common.TossMode
import com.toolbox.app.ui.common.TossModeToggle
import com.toolbox.app.ui.common.TossTable
import com.toolbox.app.ui.common.Vec3
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberResultLog
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.common.rememberTossMode
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.shakePower
import com.toolbox.app.ui.common.swipePower
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** The die type and how many, shared by both modes. They were Dice Toss's `dicetoss_` settings. */
const val DICE_COUNT_KEY = "dice_count"
const val DICE_SIDES_KEY = "dice_sides"

private const val MAX_DICE = 6

/** Quick looks nearly straight down on the dice; Throw looks across the table at an angle. */
private const val QUICK_ELEVATION = 80f
private const val THROW_ELEVATION = 58f

/** How long Quick's tumble takes. */
private const val QUICK_MS = 800

/** How close to a die (in die radii) a touch picks them up. */
private const val GRAB_REACH = 1.4f

/**
 * Dice, in two modes. Quick picks the numbers straight away and the dice tumble where they sit to land on them.
 * Throw is a physics table: swipe across it to throw the dice (harder goes further and spins more), or pick them up
 * and flick them, and the numbers come out of the simulation. The die type and count are shared, and the dice show
 * the same numbers in both modes. They use the same colours as the coin.
 */
@Composable
fun DiceScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val vibes = rememberVibes()
    val synth = rememberToneSynth()
    val say = rememberAnnouncer()
    var count by rememberPref(DICE_COUNT_KEY, 2)
    var sides by rememberPref(DICE_SIDES_KEY, 6)
    var mode by rememberTossMode("dice_mode")
    // The last roll, shared by both modes; null while the dice are moving, or after changing them. It's saved, and
    // the dice are laid out showing it again, so a recreated screen still matches what it says.
    var result by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val history = rememberResultLog("dice_history", snackbar)
    val startCount = count.coerceIn(1, MAX_DICE)
    val startSides = sides.takeIf { it in DieShape.SIDES } ?: 6

    // Quick: the numbers are picked, then the dice tumble onto them as progress runs 0 to 1.
    val quick = remember { QuickDice(System.nanoTime(), startCount, startSides).also { q -> result?.let(q::show) } }
    val progress = remember { Animatable(1f) }
    var tumbling by remember { mutableStateOf(false) }

    // Throw: the physics dice.
    val physics = remember { DicePhysics(System.nanoTime(), startCount, startSides).also { p -> result?.let(p::show) } }
    var frame by remember { mutableLongStateOf(0L) } // bumped every simulated frame, or change of dice, to redraw
    var rolling by remember { mutableStateOf(false) } // off the table: held or rolling
    var held by remember { mutableStateOf(false) }
    var thrown by remember { mutableStateOf(false) } // this time off the table was a throw, not just picking them up

    val busy = tumbling || rolling

    fun record(values: List<Int>) = history.add(DiceRoll(physics.shape.sides, values).fields())

    val loop = rememberGameLoop { dt ->
        if (physics.phase == DicePhysics.Phase.Resting) return@rememberGameLoop false
        for (e in physics.step(dt)) when (e) {
            is DicePhysics.Event.Knock -> {
                synth.knock(e.intensity)
                // Hard landings thump; the little knocks as they tumble to a stop tick.
                if (e.intensity > 0.3f) vibes.thud(0.3f + 0.7f * e.intensity) else vibes.tick(0.25f + e.intensity)
            }
            is DicePhysics.Event.Settled -> vibes.click(0.35f)
        }
        frame++
        if (physics.phase != DicePhysics.Phase.Resting) return@rememberGameLoop true
        result = physics.values
        // Putting the dice down shows their total, but only a throw is a roll worth keeping, as with the coin. Each
        // throw is kept once, even if something wakes the table again before the next.
        if (thrown) record(physics.values)
        thrown = false
        rolling = false
        false
    }

    fun roll() {
        if (tumbling) return
        tumbling = true
        result = null // so a repeat result is still a change, and TalkBack reads it out
        vibes.click(0.45f)
        val values = quick.roll()
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(QUICK_MS, easing = LinearEasing))
            result = values
            record(values)
            synth.knock(0.6f)
            vibes.thud(0.6f)
            tumbling = false
        }
    }

    fun toss(power: Float, dirX: Float, dirZ: Float) {
        if (!physics.toss(power, dirX, dirZ)) return // still rolling
        result = null
        held = false
        thrown = true
        vibes.click(0.45f)
        rolling = true
        loop.wake()
    }

    fun grab(): Boolean {
        if (!physics.grab()) return false
        result = null
        held = true
        thrown = false
        vibes.tick(0.6f)
        rolling = true
        loop.wake()
        return true
    }

    fun drop() {
        if (physics.drop()) held = false
    }

    fun changeCount(n: Int) {
        if (busy || n !in 1..MAX_DICE) return
        count = n
        physics.setCount(n)
        quick.setCount(n)
        result = null
        frame++
        say("$n ${if (n == 1) "die" else "dice"}")
    }

    fun changeSides(n: Int) {
        if (busy || n == physics.shape.sides) return
        sides = n
        physics.setSides(n)
        quick.setSides(n)
        result = null
        vibes.tick(0.4f)
        frame++
    }

    /** The dice show the same numbers in both modes, so the result on screen always matches them. */
    fun switchTo(m: TossMode) {
        if (busy || m == mode) return
        if (m == TossMode.Quick) quick.show(physics.values) else physics.show(quick.values)
        frame++
        mode = m
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { strength ->
        if (mode == TossMode.Quick) roll() // roll() ignores shakes mid-roll
        else toss(shakePower(strength), Random.nextFloat() - 0.5f, -1f)
    }

    ToolScaffold(
        title = "Dice",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val showing = when {
            busy -> null
            mode == TossMode.Quick -> quick.values
            else -> physics.values
        }
        val described = showing?.let { "Dice showing ${it.joinToString(", ")}" } ?: "Dice rolling"
        TossLayout(
            padding = padding,
            toggle = { TossModeToggle(mode, ::switchTo, enabled = !busy) },
            stage = { modifier ->
                if (mode == TossMode.Quick) {
                    TossTable(
                        description = described, // TalkBack adds "double tap to roll the dice"
                        tapLabel = "Roll the dice",
                        onSize = {},
                        onTap = ::roll,
                        onFling = { _, _ -> roll() },
                        modifier = modifier,
                    ) {
                        if (frame < 0) return@TossTable // reading frame redraws after changing the dice
                        val view = quickView(quick, size.width, size.height, this)
                        drawDice(quick.dice(progress.value), view)
                    }
                } else {
                    DiceTable(
                        physics = physics,
                        frame = frame,
                        description = "$described. Swipe to throw",
                        onTap = { toss(0.25f + Random.nextFloat() * 0.15f, Random.nextFloat() * 0.4f - 0.2f, -1f) },
                        onToss = ::toss,
                        onGrab = ::grab,
                        onDrop = ::drop,
                        modifier = modifier,
                    )
                }
            },
        ) {
            val values = result
            val headline = when {
                held -> "Flick to throw"
                busy -> ""
                values == null -> if (mode == TossMode.Quick) "Tap the dice to roll" else "Swipe to throw"
                values.size == 1 -> "${values[0]}"
                else -> "Total ${values.sum()}"
            }
            SteadyText(
                headline,
                style = if (values != null && !busy) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                reserve = MaterialTheme.typography.headlineMedium,
                // On the box, not just the text inside, so TalkBack reads each new result out, with every number.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = when {
                        values == null || busy -> headline
                        values.size == 1 -> "Rolled ${values[0]}"
                        else -> "Rolled ${values.joinToString(", ")}. Total ${values.sum()}"
                    }
                    liveRegion = LiveRegionMode.Polite
                },
            )
            SteadyText(
                when {
                    held -> "Drag to move them. Let go to drop them"
                    busy -> ""
                    values != null && values.size > 1 -> values.joinToString("  +  ")
                    values != null -> ""
                    mode == TossMode.Quick -> if (shake.value) "Or shake the phone, or press Roll" else "Or press Roll"
                    else -> "Harder goes further. Or pick them up and flick"
                },
                style = MaterialTheme.typography.bodyMedium,
                lines = hintLines(LocalDensity.current.fontScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val rolls = history.entries.mapNotNull(DiceRoll::from)
            ResultStrip(rolls.take(STRIP_MAX).reversed().map { it.short }, onOpen = { history.open = true })
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DieShape.SIDES.forEach { n ->
                    FilterChip(
                        selected = sides == n,
                        onClick = { changeSides(n) },
                        enabled = !busy,
                        label = { Text("d$n") },
                        modifier = Modifier.semantics { contentDescription = "$n-sided" },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalIconButton(onClick = { changeCount(count - 1) }, enabled = !busy && count > 1) {
                    Icon(Icons.Filled.Remove, contentDescription = "Fewer dice")
                }
                Text("$count ${if (count == 1) "die" else "dice"}", style = MaterialTheme.typography.titleMedium)
                FilledTonalIconButton(onClick = { changeCount(count + 1) }, enabled = !busy && count < MAX_DICE) {
                    Icon(Icons.Filled.Add, contentDescription = "More dice")
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (mode == TossMode.Quick) roll()
                    else toss(0.45f + Random.nextFloat() * 0.35f, Random.nextFloat() * 0.5f - 0.25f, -1f)
                },
                enabled = !busy,
            ) { Text(if (mode == TossMode.Quick) "Roll" else "Throw") }
        }
    }

    if (history.open) {
        ResultHistorySheet(
            rows = history.entries.mapNotNull(DiceRoll::from).map { ResultRow(it.text, it.spoken) },
            onClear = history::clear,
            onDismiss = { history.open = false },
        )
    }
}

/** Quick's view: the rows of dice filling the middle of the stage, but never bigger than a large die. */
private fun quickView(quick: QuickDice, width: Float, height: Float, density: Density): DiceView = with(density) {
    val s = minOf(width / (2 * quick.halfWidth + 1f), height / (2 * quick.halfDepth + 1.4f), 90.dp.toPx())
    DiceView(QUICK_ELEVATION, width / 2f, height / 2f, s)
}

/** How Throw's table fits a canvas of [size]: dice small enough that five fit across and the whole table shows. */
private fun tableView(size: IntSize, density: Density): DiceView = with(density) {
    val s = minOf(size.width / 6.5f, size.height / 9f, 64.dp.toPx())
    // Leave room above the far wall for a die in the air, and a little below the near wall.
    DiceView(THROW_ELEVATION, size.width / 2f, s * 1.6f + halfDepth(size, s) * SIN_ELEVATION * s, s)
}

private val SIN_ELEVATION = sin(Math.toRadians(THROW_ELEVATION.toDouble())).toFloat()

private fun halfDepth(size: IntSize, scale: Float) = max(1.5f, (size.height / scale - 2.4f) / (2 * SIN_ELEVATION))

/** Throw's table: the physics dice, which can be swiped, tapped, or picked up, dragged and flicked. */
@Composable
private fun DiceTable(
    physics: DicePhysics,
    frame: Long,
    description: String,
    onTap: () -> Unit,
    onToss: (power: Float, dirX: Float, dirZ: Float) -> Unit,
    onGrab: () -> Boolean,
    onDrop: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    var view by remember { mutableStateOf<DiceView?>(null) }
    var holdFrom by remember { mutableStateOf(Vec3.Zero) } // where the handful was when they were picked up
    TossTable(
        description = description,
        tapLabel = "Throw the dice",
        onSize = {
            val v = tableView(it, density)
            view = v
            physics.resize(it.width / 2f / v.scale, halfDepth(it, v.scale))
        },
        onTap = onTap,
        // Any direction works: the dice go the way the finger went (screen up is away across the table).
        onFling = { vx, vUp ->
            val speed = hypot(vx, vUp)
            onToss(swipePower(speed), vx / speed, -vUp / speed)
        },
        // Pressing on any resting die picks them all up; anywhere else starts a swipe.
        onPress = { at ->
            val v = view
            val reach = (v?.scale ?: 0f) * physics.shape.radius * GRAB_REACH
            val onDie = v != null && physics.dice.any { (at - v.point(it.position)).getDistance() < reach }
            onDie && onGrab().also { if (it) holdFrom = physics.handPosition }
        },
        // They follow the finger over the table: up the screen is away, across the table.
        onDrag = { moved ->
            view?.let { v -> physics.hold(holdFrom.x + moved.x / v.scale, holdFrom.z + moved.y / (v.scale * v.sinA)) }
        },
        onPutDown = onDrop,
        modifier = modifier,
    ) {
        if (frame < 0) return@TossTable // reading frame makes every simulated step redraw
        drawDice(physics.dice, tableView(IntSize(size.width.toInt(), size.height.toInt()), this))
    }
}
