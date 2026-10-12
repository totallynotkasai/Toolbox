package com.toolbox.app.tools.cointoss

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.toolbox.app.tools.coin.CoinArt
import com.toolbox.app.tools.coin.CoinCamera
import com.toolbox.app.tools.coin.CoinStats
import com.toolbox.app.tools.coin.CoinStatsPanel
import com.toolbox.app.tools.coin.Vec3
import com.toolbox.app.tools.coin.drawCoinShadow
import com.toolbox.app.tools.coin.rememberCoinStats
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.SteadyText
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.common.rememberVibes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Looking down at the table at an angle, so height, tilt and the edge all read. */
private const val ELEVATION = 62f
private val CAMERA = CoinCamera(ELEVATION)
private val SIN_E = sin(Math.toRadians(ELEVATION.toDouble())).toFloat()
private val COS_E = cos(Math.toRadians(ELEVATION.toDouble())).toFloat()

/** Swipe speeds (dp/s) mapped onto throw power 0–1. Slower swipes don't throw. */
private const val SWIPE_MIN = 350f
private const val SWIPE_MAX = 4000f

/** Shake strengths (m/s², see OnShake) mapped onto throw power 0–1. */
private const val SHAKE_MIN = 12f
private const val SHAKE_MAX = 40f

/** How close to the coin (in coin radii) a touch picks it up, and how quick a touch on it must be to count as a tap. */
private const val GRAB_REACH = 1.6f
private const val TAP_MS = 220L

/**
 * The physics coin, on a table with depth: swipe up to flip it high, sideways or down to send it skimming across
 * the table (where it may land on its edge and roll), or pick it up and flick it. A harder swipe or shake throws
 * harder, and the result comes from the simulation.
 */
@Composable
fun CoinTossScreen(onBack: () -> Unit) {
    val vibes = rememberVibes()
    val synth = rememberToneSynth()
    val art = remember { CoinArt() }
    var stats by rememberCoinStats("cointoss_stats")
    val physics = remember { CoinPhysics(seed = System.nanoTime(), startHeads = stats.streakHeads != false) }
    var frame by remember { mutableLongStateOf(0L) } // bumped every simulated frame to redraw
    var flying by remember { mutableStateOf(false) } // off the table: held, in the air or rolling
    var held by remember { mutableStateOf(false) }
    var thrown by remember { mutableStateOf(false) } // this time off the table was a throw, not just picking it up
    var result by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var lastFlips by rememberSaveable { mutableIntStateOf(0) }

    fun toss(power: Float, alongX: Float, alongZ: Float) {
        if (!physics.toss(power, alongX, alongZ)) return // already moving
        result = null
        held = false
        thrown = true
        vibes.click(0.55f)
        flying = true
    }

    /** A throw in a random direction, mostly upwards: for the button, a tap and a shake. */
    fun tossAnyway(power: Float, along: Float) {
        val a = Random.nextFloat() * 2 * PI.toFloat()
        toss(power, cos(a) * along, sin(a) * along)
    }

    fun grab(): Boolean {
        if (!physics.grab()) return false
        result = null
        held = true
        thrown = false
        vibes.tick(0.6f)
        flying = true
        return true
    }

    fun drop() {
        if (physics.drop()) held = false
    }

    LaunchedEffect(flying) {
        if (!flying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (physics.phase != CoinPhysics.Phase.Resting) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f) // after a pause, don't jump
                last = now
                for (e in physics.step(dt)) when (e) {
                    is CoinPhysics.Event.Bounce -> {
                        synth.clink(e.intensity)
                        vibes.thud(0.25f + 0.75f * e.intensity)
                    }
                    is CoinPhysics.Event.Rattle -> {
                        synth.rattle(e.intensity)
                        vibes.tick(0.2f + 0.5f * e.intensity)
                    }
                    is CoinPhysics.Event.Settled -> vibes.click(0.4f)
                }
                frame++
            }
        }
        if (thrown) {
            stats = stats.record(physics.heads)
            result = physics.heads
            lastFlips = physics.flips
        }
        flying = false
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { strength ->
        tossAnyway((strength - SHAKE_MIN) / (SHAKE_MAX - SHAKE_MIN), 0.1f + Random.nextFloat() * 0.4f)
    }

    ToolScaffold(
        title = "Coin Toss",
        onBack = onBack,
        actions = {
            IconButton(onClick = { stats = CoinStats(); result = null }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset counts")
            }
        },
    ) { padding ->
        val stage = @Composable { modifier: Modifier ->
            TossStage(
                physics = physics,
                art = art,
                frame = frame,
                onTap = { tossAnyway(0.08f + Random.nextFloat() * 0.12f, 0.05f) },
                onSwipe = ::toss,
                onGrab = ::grab,
                onDrop = ::drop,
                modifier = modifier,
            )
        }
        val controls = @Composable { scope: ColumnScope ->
            with(scope) {
                // Fixed heights, so the table above doesn't change size as these come and go.
                SteadyText(
                    when {
                        held -> "Flick it to toss"
                        flying -> ""
                        result == true -> "Heads"
                        result == false -> "Tails"
                        else -> "Swipe to toss"
                    },
                    style = if (result != null && !held) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    reserve = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                SteadyText(
                    when {
                        held -> "Drag to move it. Let go gently to put it down"
                        flying -> ""
                        result != null -> "Turned over $lastFlips ${if (lastFlips == 1) "time" else "times"}"
                        else -> "Up flips it high, sideways sends it rolling. Or pick the coin up and flick it"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    lines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { tossAnyway(0.45f + Random.nextFloat() * 0.35f, 0.1f + Random.nextFloat() * 0.2f) },
                    enabled = !flying,
                ) { Text("Toss") }
                Spacer(Modifier.height(20.dp))
                CoinStatsPanel(stats)
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
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    stage(Modifier.widthIn(max = 480.dp).fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(16.dp))
                    controls(this)
                }
            }
        }
    }
}

/**
 * How the table fits a stage of [size]: the back of the table [far] px down (the space above it is air, where a
 * high throw flies), the table running to the bottom edge, [r] px per coin radius.
 */
private class TableView(size: IntSize, density: Density) {
    val r = with(density) { minOf(size.width / 9f, size.height / 10f, 48.dp.toPx()) }
    val far = size.height * 0.3f
    private val cx = size.width / 2f
    val halfWidth = cx / r
    val halfDepth = max(1.5f, (size.height - r * 0.2f - far) / (SIN_E * r) / 2)
    private val cy = far + halfDepth * SIN_E * r

    /** How high a throw can go and still stay in view, even at the back of the table. */
    val ceiling = max(3f, (far + SIN_E * r - 1.1f * r) / (COS_E * r))

    fun point(p: Vec3) = Offset(cx + p.x * r, cy + (p.z * SIN_E - p.y * COS_E) * r)
}

@Composable
private fun TossStage(
    physics: CoinPhysics,
    art: CoinArt,
    frame: Long,
    onTap: () -> Unit,
    onSwipe: (power: Float, alongX: Float, alongZ: Float) -> Unit,
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
                val view = TableView(it, density)
                physics.resize(view.halfWidth, view.halfDepth, view.ceiling)
            }
            .semantics {
                role = Role.Button
                contentDescription = "Coin. Swipe to toss, or pick it up and flick it"
                onClick(label = "Toss the coin") { onTap(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // Pressing on the resting coin picks it up; anywhere else starts a swipe.
                    val view = TableView(size, density)
                    val start = physics.position
                    val onCoin = (down.position - view.point(start)).getDistance() < view.r * GRAB_REACH
                    val holding = onCoin && currentGrab()
                    var travelled = 0f
                    var upTime = down.uptimeMillis
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        travelled = max(travelled, (change.position - down.position).getDistance())
                        change.consume()
                        upTime = change.uptimeMillis
                        if (holding) {
                            // It follows the fingers over the table: up the screen is away, across the table.
                            val moved = change.position - down.position
                            physics.hold(start.x + moved.x / view.r, start.z + moved.y / (view.r * SIN_E))
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
                    val vUp = -v.y / density.density
                    val speed = hypot(vx, vUp)
                    if (speed < SWIPE_MIN) {
                        if (holding) currentDrop()
                        return@awaitEachGesture
                    }
                    // The coin goes the way the finger went. Straight up is mostly a flip into the air with a little
                    // drift; the flatter the swipe, the more it's sent skimming across the table instead.
                    val along = 0.12f + 0.88f * (1f - vUp / speed) / 2f
                    currentSwipe(
                        (speed - SWIPE_MIN) / (SWIPE_MAX - SWIPE_MIN),
                        vx / speed * along,
                        -vUp / speed * along,
                    )
                }
            },
    ) {
        if (frame < 0) return@Canvas // reading frame makes every simulated step redraw
        val view = TableView(IntSize(size.width.toInt(), size.height.toInt()), this)
        val r = view.r
        drawRect(Brush.verticalGradient(listOf(tableFar, table))) // one surface, as on the dice table

        // The shadow is the coin's disc squashed onto the floor, so it thins to a line when the coin stands on edge.
        val pose = physics.pose()
        val lift = physics.y - CoinPhysics.REST_Y
        val height = (lift / physics.ceiling).coerceIn(0f, 1f)
        val u = pose.column(0); val w = pose.column(1)
        val spread = 1f - 0.3f * height
        val su = Offset(u.x, u.z * SIN_E) * (r * spread)
        val sw = Offset(w.x, w.z * SIN_E) * (r * spread)
        val c = view.point(Vec3(physics.x, 0f, physics.z)) + Offset(r * (0.06f + 0.5f * height), r * (0.08f + 0.2f * height))
        withTransform({
            transform(Matrix(floatArrayOf(su.x, su.y, 0f, 0f, sw.x, sw.y, 0f, 0f, 0f, 0f, 1f, 0f, c.x, c.y, 0f, 1f)))
        }) {
            drawCoinShadow(Offset.Zero, rx = 1.04f, ry = 1.04f, height = height)
        }
        art.draw(this, view.point(physics.position), r * (1f + 0.02f * min(lift, 8f)), pose, CAMERA)
    }
}
