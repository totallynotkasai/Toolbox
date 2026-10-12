package com.toolbox.app.tools.coin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.Rot
import com.toolbox.app.ui.common.SteadyText
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.TossLayout
import com.toolbox.app.ui.common.TossMode
import com.toolbox.app.ui.common.TossModeToggle
import com.toolbox.app.ui.common.TossTable
import com.toolbox.app.ui.common.Vec3
import com.toolbox.app.ui.common.drawTossShadow
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.common.rememberTossMode
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.shakePower
import com.toolbox.app.ui.common.swipePower
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Where the counts are kept: Flip a Coin's old key, which Coin Toss's counts were merged into. */
const val COIN_STATS_KEY = "coin_stats"

/** Quick: nearly straight down, so the resting coin reads as a round face with just a sliver of edge. */
private val QUICK_CAMERA = CoinCamera(80f)

/** Throw: looking down at the table at an angle, so height, tilt and the edge all read. */
private const val ELEVATION = 62f
private val THROW_CAMERA = CoinCamera(ELEVATION)
private val SIN_E = sin(Math.toRadians(ELEVATION.toDouble())).toFloat()
private val COS_E = cos(Math.toRadians(ELEVATION.toDouble())).toFloat()

/** How close to the coin (in coin radii) a touch picks it up. */
private const val GRAB_REACH = 1.6f

/**
 * The coin, in two modes. Quick picks heads or tails up front and flips the coin to land on it. Throw is a physics
 * coin on a table: swipe up to flip it high, sideways or down to send it skimming across the table (where it may land
 * on its edge and roll), or pick it up and flick it, and the result comes out of the simulation. A harder swipe or
 * shake throws harder. Both modes keep one set of counts, and the coin shows the same face in each.
 */
@Composable
fun CoinScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val vibes = rememberVibes()
    val synth = rememberToneSynth()
    val art = remember { CoinArt() }
    var stats by rememberCoinStats(COIN_STATS_KEY)
    var mode by rememberTossMode("coin_mode")
    // This visit's last result (true = heads) and how many times the coin turned over; null while it's moving.
    var result by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var turns by rememberSaveable { mutableIntStateOf(0) }

    // Quick: an animation that lands on a face picked up front. Both modes start on the face last thrown.
    val rotation = remember { Animatable(if (stats.streakHeads == false) 180f else 0f) }
    val lift = remember { Animatable(0f) }
    val settle = remember { Animatable(1f) }
    var flipping by remember { mutableStateOf(false) }

    // Throw: the physics coin.
    val physics = remember { CoinPhysics(seed = System.nanoTime(), startHeads = stats.streakHeads != false) }
    var frame by remember { mutableLongStateOf(0L) } // bumped every simulated frame to redraw
    var flying by remember { mutableStateOf(false) } // off the table: held, in the air or rolling
    var held by remember { mutableStateOf(false) }
    var thrown by remember { mutableStateOf(false) } // this time off the table was a throw, not just picking it up

    val busy = flipping || flying

    fun land(heads: Boolean, flips: Int) {
        stats = stats.record(heads)
        result = heads
        turns = flips
    }

    val loop = rememberGameLoop { dt ->
        if (physics.phase == CoinPhysics.Phase.Resting) return@rememberGameLoop false
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
        if (physics.phase != CoinPhysics.Phase.Resting) return@rememberGameLoop true
        if (thrown) land(physics.heads, physics.flips)
        flying = false
        false
    }

    fun flip() {
        if (flipping) return
        flipping = true
        result = null // so a repeat result is still a change, and TalkBack reads it out
        vibes.click(0.55f)
        val landHeads = Random.nextBoolean()
        // Each 180° is one face change. Pick an even/odd count of half-turns so we land on the chosen face.
        var halfTurns = 8 + Random.nextInt(3)
        if ((halfTurns % 2 == 0) != (quickHeads(rotation.value) == landHeads)) halfTurns++
        val target = rotation.value + halfTurns * 180f
        scope.launch {
            launch {
                lift.animateTo(1f, tween(1100, easing = LinearEasing)) // drives a parabola in the drawing
                lift.snapTo(0f)
            }
            rotation.animateTo(target, tween(1100, easing = FastOutSlowInEasing))
            rotation.snapTo(target % 360f) // the same face, so the angle doesn't grow with every flip
            land(landHeads, halfTurns)
            synth.clink(0.75f)
            vibes.thud(0.8f)
            settle.snapTo(0f)
            settle.animateTo(1f, tween(300, easing = LinearEasing))
            flipping = false
        }
    }

    fun toss(power: Float, alongX: Float, alongZ: Float) {
        if (!physics.toss(power, alongX, alongZ)) return // already moving
        result = null
        held = false
        thrown = true
        vibes.click(0.55f)
        flying = true
        loop.wake()
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
        loop.wake()
        return true
    }

    fun drop() {
        if (physics.drop()) held = false
    }

    /** The coin shows the same face in both modes, so the result on screen always matches it. */
    fun switchTo(m: TossMode) {
        if (busy || m == mode) return
        if (m == TossMode.Quick) {
            val heads = physics.heads
            scope.launch { rotation.snapTo(if (heads) 0f else 180f) }
        } else {
            physics.lay(quickHeads(rotation.value))
            frame++
        }
        mode = m
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { strength ->
        if (mode == TossMode.Quick) flip() // flip() ignores shakes mid-flip
        else tossAnyway(shakePower(strength), 0.1f + Random.nextFloat() * 0.4f)
    }

    ToolScaffold(
        title = "Coin",
        onBack = onBack,
        actions = {
            IconButton(onClick = { stats = CoinStats(); result = null }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset counts")
            }
        },
    ) { padding ->
        TossLayout(
            padding = padding,
            toggle = { TossModeToggle(mode, ::switchTo, enabled = !busy) },
            stage = { modifier ->
                if (mode == TossMode.Quick) {
                    TossTable(
                        description = "Coin. Tap to flip it",
                        tapLabel = "Flip the coin",
                        onSize = {},
                        onTap = ::flip,
                        onFling = { _, _ -> flip() },
                        modifier = modifier,
                    ) { drawQuickCoin(art, rotation.value, lift.value, settle.value) }
                } else {
                    CoinTable(
                        physics = physics,
                        art = art,
                        frame = frame,
                        onTap = { tossAnyway(0.08f + Random.nextFloat() * 0.12f, 0.05f) },
                        onToss = ::toss,
                        onGrab = ::grab,
                        onDrop = ::drop,
                        modifier = modifier,
                    )
                }
            },
        ) {
            // Fixed heights, so the table above doesn't change size as these come and go.
            val headline = when {
                held -> "Flick it to toss"
                busy -> ""
                result == true -> "Heads"
                result == false -> "Tails"
                mode == TossMode.Quick -> "Tap the coin to flip"
                else -> "Swipe to toss"
            }
            SteadyText(
                headline,
                style = if (result != null && !busy) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                reserve = MaterialTheme.typography.headlineMedium,
                // On the box, not just the text inside, so TalkBack reads each new result out.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = headline
                    liveRegion = LiveRegionMode.Polite
                },
            )
            SteadyText(
                when {
                    held -> "Drag to move it. Let go gently to put it down"
                    busy -> ""
                    result != null -> "Turned over $turns ${if (turns == 1) "time" else "times"}"
                    mode == TossMode.Quick -> if (shake.value) "Or shake the phone, or press Flip" else "Or press Flip"
                    else -> "Up flips it high, sideways sends it rolling. Or pick the coin up and flick it"
                },
                style = MaterialTheme.typography.bodyMedium,
                lines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (mode == TossMode.Quick) flip()
                    else tossAnyway(0.45f + Random.nextFloat() * 0.35f, 0.1f + Random.nextFloat() * 0.2f)
                },
                enabled = !busy,
            ) { Text(if (mode == TossMode.Quick) "Flip" else "Toss") }
            Spacer(Modifier.height(20.dp))
            CoinStatsPanel(stats)
        }
    }
}

/** Whether Quick's coin, turned [deg] about its flip axis, shows heads. Each 180° is one face change. */
private fun quickHeads(deg: Float) = floor((deg + 90f) / 180f).toInt() % 2 == 0

/**
 * Quick's coin rising and falling above its shadow. [lift] runs 0→1 over a flip and traces a parabola;
 * [settle] runs 0→1 after landing for a slight squash and then a small bob.
 */
private fun DrawScope.drawQuickCoin(art: CoinArt, rotationDeg: Float, lift: Float, settle: Float) {
    val radius = minOf(size.width * 0.3f, size.height * 0.24f, 100.dp.toPx())
    val ground = Offset(size.width / 2, size.height - radius - 24.dp.toPx())
    val height = sin(lift * PI).toFloat()
    val rise = (size.height - 2 * radius - 48.dp.toPx()).coerceAtLeast(0f)

    drawTossShadow(
        ground + Offset(radius * (0.05f + 0.25f * height), radius * (0.1f + 0.2f * height)),
        rx = radius * (1.02f - 0.3f * height),
        ry = radius * (0.99f - 0.3f * height),
        height = height,
    )

    // Landing: a quick squash on the bottom edge, then a small bob.
    val squash = if (settle < 0.4f) sin(settle / 0.4f * PI).toFloat() * 0.05f else 0f
    val bob = if (settle >= 0.4f) sin((settle - 0.4f) / 0.6f * PI).toFloat() * 0.05f * radius else 0f
    val scale = 1f + 0.25f * height
    val centre = ground - Offset(0f, rise * height + bob)
    withTransform({
        scale(1f + squash * 0.6f, 1f - squash, ground + Offset(0f, radius))
    }) {
        art.draw(this, centre, radius * scale, Rot.x(Math.toRadians(rotationDeg.toDouble()).toFloat()) * Rot.Rest, QUICK_CAMERA)
    }
}

/**
 * How Throw's table fits a stage of [size]: the back of the table [far] px down (the space above it is air, where a
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

/** Throw's table: the physics coin, which can be swiped, tapped, or picked up, dragged and flicked. */
@Composable
private fun CoinTable(
    physics: CoinPhysics,
    art: CoinArt,
    frame: Long,
    onTap: () -> Unit,
    onToss: (power: Float, alongX: Float, alongZ: Float) -> Unit,
    onGrab: () -> Boolean,
    onDrop: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    var view by remember { mutableStateOf<TableView?>(null) }
    var holdFrom by remember { mutableStateOf(Vec3.Zero) } // where the coin was when it was picked up
    TossTable(
        description = "Coin. Swipe to toss, or pick it up and flick it",
        tapLabel = "Toss the coin",
        onSize = {
            val v = TableView(it, density)
            view = v
            physics.resize(v.halfWidth, v.halfDepth, v.ceiling)
        },
        onTap = onTap,
        onFling = { vx, vUp ->
            // The coin goes the way the finger went. Straight up is mostly a flip into the air with a little drift;
            // the flatter the swipe, the more it's sent skimming across the table instead.
            val speed = hypot(vx, vUp)
            val along = 0.12f + 0.88f * (1f - vUp / speed) / 2f
            onToss(swipePower(speed), vx / speed * along, -vUp / speed * along)
        },
        // Pressing on the resting coin picks it up; anywhere else starts a swipe.
        onPress = { at ->
            val v = view
            holdFrom = physics.position
            v != null && (at - v.point(holdFrom)).getDistance() < v.r * GRAB_REACH && onGrab()
        },
        // It follows the finger over the table: up the screen is away, across the table.
        onDrag = { moved ->
            view?.let { v -> physics.hold(holdFrom.x + moved.x / v.r, holdFrom.z + moved.y / (v.r * SIN_E)) }
        },
        onPutDown = onDrop,
        modifier = modifier,
    ) {
        if (frame < 0) return@TossTable // reading frame makes every simulated step redraw
        val v = TableView(IntSize(size.width.toInt(), size.height.toInt()), this)
        val r = v.r

        // The shadow is the coin's disc squashed onto the floor, so it thins to a line when the coin stands on edge.
        val pose = physics.pose()
        val lift = physics.y - CoinPhysics.REST_Y
        val height = (lift / physics.ceiling).coerceIn(0f, 1f)
        val u = pose.column(0); val w = pose.column(1)
        val spread = 1f - 0.3f * height
        val su = Offset(u.x, u.z * SIN_E) * (r * spread)
        val sw = Offset(w.x, w.z * SIN_E) * (r * spread)
        val c = v.point(Vec3(physics.x, 0f, physics.z)) + Offset(r * (0.06f + 0.5f * height), r * (0.08f + 0.2f * height))
        withTransform({
            transform(Matrix(floatArrayOf(su.x, su.y, 0f, 0f, sw.x, sw.y, 0f, 0f, 0f, 0f, 1f, 0f, c.x, c.y, 0f, 1f)))
        }) {
            drawTossShadow(Offset.Zero, rx = 1.04f, ry = 1.04f, height = height)
        }
        art.draw(this, v.point(physics.position), r * (1f + 0.02f * min(lift, 8f)), pose, THROW_CAMERA)
    }
}
