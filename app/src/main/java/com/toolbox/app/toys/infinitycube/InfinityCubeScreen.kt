package com.toolbox.app.toys.infinitycube

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.Feel
import com.toolbox.app.ui.common.Quat
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.Vec3
import com.toolbox.app.ui.common.accessibilityActions
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberPcmStream
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.wrapDelta
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

private const val SNAP_RATE = 12f       // 1/s: how quickly a let-go cube eases into the nearest position
private const val SNAP_MIN_SPEED = 1.6f // steps/s it moves at least, so the last of a fold snaps rather than creeps
private const val FAST_FOLD = 2.5f      // steps/s above which a flat position passed gives a light tick, not a clack
private const val GRAB_SLOP_DP = 28f    // a touch this close to the cube still takes hold of it
private const val IDLE_FRAME_GAP_MS = 25L // only floating, about 30 frames a second
private const val TURNED_DEGREES = 2f   // turned further than this from the usual view, Reset view shows
private const val RECENTRE_RATE = 5f    // 1/s: how quickly a folded toy drifts back to the middle

/**
 * The toy's state between frames, gestures and TalkBack. The fold and the clock are Compose state, so the drawing
 * follows them; everything else is read on the next frame.
 */
private class CubeToy(step: Int = 0, view: Quat = Quat.Identity) {
    /** Where it is in the cycle (see [InfinityCube.pose]). */
    var fold by mutableFloatStateOf(step.toFloat())

    /** Seconds on screen, for floating; also what makes the drawing redraw every frame. */
    var time by mutableFloatStateOf(0f)

    /** How the user has turned it, in the viewer's frame, on top of [CubeCamera.Usual]. */
    var turn = view
    var resetting = false

    /** The whole step a let-go cube is easing to, or null when it's still or held. */
    var target: Int? = null

    /** Where the toy has been put while folding: a fold keeps the part held still, and after it it drifts back to the middle. */
    var placement = Placement.Home
    var hold: Hold? = null

    var grab: Grab? = null
    var finger = Offset.Zero
    var grabOffset = Offset.Zero // finger minus the grabbed point, so taking hold never makes it jump
    var turning = false
    var floating = 1f            // 0 while held, so it doesn't drift under the finger
    var speed = 0f               // smoothed steps/s
    var box = IntSize.Zero
    val clicks = FoldClicks(fold)
    private var lastFold = fold

    val held get() = grab != null || turning

    /** Settled on a position: the one it shows, else null. */
    val settled: Int?
        get() = if (target == null && !held && fold == snapStep(fold).toFloat()) snapStep(fold) else null

    /** Every cube at [fold], where the toy is now. */
    fun poses(fold: Float = this.fold): List<CubePose> = placement.place(InfinityCube.pose(fold))

    /**
     * Ends a hold once the fold has settled. The way the held fold left the toy turned becomes part of the view, so the
     * toy stays as it was left, and only where it sits drifts back to the middle.
     */
    fun settle() {
        val r = placement.rot
        turn = (turn * (CubeCamera.Usual * r * CubeCamera.Usual.transposed()).toQuat()).normalized()
        placement = Placement(Placement.Home.rot, r.transposed() * placement.shift)
        hold = null
    }

    /** How it's seen this frame, bobbing and swaying gently while it floats. */
    fun camera(): CubeCamera {
        val w = box.width.toFloat()
        val h = box.height.toFloat()
        val unit = min(w / 5f, h / 5.4f)
        // A slow bob and sway, at unrelated periods so it never looks like a loop.
        val f = floating
        val lift = 0.07f * f * sin(2f * PI.toFloat() * time / 4.2f)
        val yaw = Math.toRadians(3.0).toFloat() * f * sin(2f * PI.toFloat() * time / 9.1f)
        val pitch = Math.toRadians(1.5).toFloat() * f * sin(2f * PI.toFloat() * time / 6.3f)
        val view = Quat.axisAngle(Vec3(0f, 1f, 0f), yaw).toRot() * Quat.axisAngle(Vec3(1f, 0f, 0f), pitch).toRot() *
            turn.toRot() * CubeCamera.Usual
        return CubeCamera(view, lift, Offset(w / 2f, h * 0.42f), unit)
    }

    /** Degrees it's turned from the usual view. */
    fun turnedDegrees(): Float = Math.toDegrees(2.0 * acos(min(1f, abs(turn.w)).toDouble())).toFloat()

    /**
     * The colour on top of the first closed cube, as the view stands (without the sway), for TalkBack. The cube's sides
     * face the toy's axes when closed, so this is whichever axis now points most nearly up.
     */
    fun topColour(): String {
        val view = turn.toRot() * CubeCamera.Usual
        val sides = InfinityCube.sideNormals
        return CubeColours.names[sides.indices.maxBy { (view * sides[it]).y }]
    }

    /** Updates the fold's smoothed speed after a frame step of [dt] seconds. */
    fun updateSpeed(dt: Float) {
        if (dt > 0f) speed += ((fold - lastFold) / dt - speed) * min(1f, dt * 12f)
        lastFold = fold
    }

    fun save(): FloatArray = floatArrayOf((target ?: snapStep(fold)).toFloat(), turn.w, turn.x, turn.y, turn.z)

    companion object {
        val saver = Saver<CubeToy, FloatArray>(
            save = { it.save() },
            restore = { CubeToy(it[0].toInt(), Quat(it[1], it[2], it[3], it[4]).normalized()) },
        )
    }
}

@Composable
fun InfinityCubeScreen(onBack: () -> Unit) {
    val toy = rememberSaveable(saver = CubeToy.saver) { CubeToy() }
    var stateText by remember { mutableStateOf(cubeStateText(toy.settled ?: 0, toy.topColour())) }
    var turned by remember { mutableStateOf(toy.turnedDegrees() > TURNED_DEGREES) }
    val resetAlpha by animateFloatAsState(if (turned) 1f else 0f, label = "reset")
    val audio = rememberPcmStream { CubeAudio() }
    val vibes = rememberVibes()
    val feel = remember { Feel(minGapMs = 40L) }

    // Each position reached: closing snaps with a click and a buzz, opening out flat clacks, and flat positions
    // passed in a fast fold give a light tick.
    fun reached(step: Int) {
        when {
            InfinityCube.isClosed(step) -> {
                audio.snap()
                feel.mark()
                vibes.click(0.8f)
            }
            abs(toy.speed) > FAST_FOLD -> {
                audio.tick()
                feel.space { vibes.tick(0.3f) }
            }
            else -> {
                audio.clack()
                feel.space { vibes.tick(0.5f) }
            }
        }
    }

    val loop = rememberGameLoop { dt ->
        toy.time += dt
        toy.floating = if (toy.held) max(0f, toy.floating - dt * 3f) else min(1f, toy.floating + dt * 0.8f)
        if (toy.resetting) {
            toy.turn = toy.turn.slerp(Quat.Identity, 1f - exp(-9f * dt))
            if (toy.turnedDegrees() < 0.2f) {
                toy.turn = Quat.Identity
                toy.resetting = false
            }
        }
        val grab = toy.grab
        val hold = toy.hold
        if (grab != null && hold != null && toy.box != IntSize.Zero) {
            // The fold follows the finger: whatever puts the grabbed point back under it, with the rest held still.
            val cam = toy.camera()
            toy.fold = followFold(toy.fold, toy.finger - toy.grabOffset) { f ->
                cam.project(cam.world(hold.placementAt(f).place(InfinityCube.pose(f)[grab.cube].place(grab.point))))
            }
            toy.placement = hold.placementAt(toy.fold)
            toy.hold = Hold(grab.cube, toy.fold, toy.placement)
        } else {
            toy.target?.let { t ->
                val d = t - toy.fold
                val step = max(SNAP_MIN_SPEED, abs(d) * SNAP_RATE) * dt
                if (abs(d) <= step) {
                    toy.fold = t.toFloat()
                    toy.target = null
                } else {
                    toy.fold += sign(d) * step
                }
                // Let go, it settles with the same part still held.
                hold?.let { toy.placement = it.placementAt(toy.fold) }
            }
            if (toy.target == null && toy.hold != null && grab == null) toy.settle()
            val shift = toy.placement.shift
            if (toy.hold == null && shift != Vec3.Zero) {
                val k = exp(-RECENTRE_RATE * dt)
                toy.placement = Placement(toy.placement.rot, if (shift.length() < 0.002f) Vec3.Zero else shift * k)
            }
        }
        toy.updateSpeed(dt)
        toy.clicks.move(toy.fold) { reached(it) }

        val settled = toy.settled
        // Set every frame, but an unchanged value doesn't recompose: only a new position (or the start of a fold) does.
        stateText = if (settled != null) cubeStateText(settled, toy.topColour()) else "Folding"
        turned = toy.turnedDegrees() > TURNED_DEGREES && !toy.resetting
        val busy = toy.held || toy.target != null || toy.resetting || toy.floating < 1f || abs(toy.speed) > 0.01f ||
            toy.placement.shift != Vec3.Zero
        frameGapMs = if (busy) 0L else IDLE_FRAME_GAP_MS
        true
    }

    fun letGo() {
        if (toy.grab != null) toy.target = snapStep(toy.fold)
        toy.grab = null
        toy.turning = false
        loop.wake()
    }

    // TalkBack's Fold and Fold back: on to the next position that way, as a let-go cube eases.
    fun foldBy(direction: Int) {
        toy.target = stepFrom(toy.target?.toFloat() ?: toy.fold, direction)
        loop.wake()
    }

    val slop = with(LocalDensity.current) { GRAB_SLOP_DP.dp.toPx() }
    val edge = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)

    ToolScaffold(title = "Infinity Cube", onBack = onBack) { padding ->
        ToolBody(padding) {
            val said = stateText // read here, not in the semantics block, so a new position reaches TalkBack
            Box(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .aspectRatio(0.92f)
                    .onSizeChanged { toy.box = it }
                    .semantics {
                        contentDescription = "Infinity cube"
                        stateDescription = said
                        customActions = accessibilityActions("Fold" to { foldBy(1) }, "Fold back" to { foldBy(-1) })
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            val cam = toy.camera()
                            val poses = toy.poses()
                            toy.grab = cubeAt(first.position, poses, cam, slop)?.also { g ->
                                // Take hold where it is, part-way through easing or not.
                                toy.target = null
                                toy.hold = Hold(g.cube, toy.fold, toy.placement)
                                toy.finger = first.position
                                toy.grabOffset = first.position - cam.screenOf(poses, g.cube, g.point)
                            }
                            loop.wake()
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.filter { it.pressed }
                                if (down.isEmpty()) break
                                if (down.size >= 2) {
                                    // Two fingers turn it instead; a fold under way settles.
                                    if (!toy.turning) {
                                        letGo()
                                        toy.turning = true
                                        toy.resetting = false
                                    }
                                    val a = down[0]
                                    val b = down[1]
                                    turnBy(toy, a.previousPosition, a.position, b.previousPosition, b.position)
                                } else if (!toy.turning && toy.grab != null && down[0].id == first.id) {
                                    toy.finger = down[0].position
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            letGo()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cam = toy.camera() // reads the clock, so it redraws every frame as it floats
                    drawCubeShadow(cam, cam.lift)
                    drawInfinityCube(toy.poses(), cam, edge)
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    toy.resetting = true
                    loop.wake()
                },
                enabled = turned,
                // Hidden but keeping its place, so the hint below doesn't jump when it shows.
                modifier = Modifier.alpha(resetAlpha).then(if (turned) Modifier else Modifier.clearAndSetSemantics {}),
            ) { Text("Reset view") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Drag a cube to fold it, and turn it with two fingers",
                // Balanced, so at large text sizes no word is left on a line of its own.
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Two fingers moving from [a0] and [b0] to [a1] and [b1] turn the cube: moving together rolls it that way, half a turn
 * across the box, and twisting turns it about the line of sight.
 */
private fun turnBy(toy: CubeToy, a0: Offset, a1: Offset, b0: Offset, b1: Offset) {
    val w = toy.box.width.toFloat()
    if (w <= 0f) return
    val move = (a1 + b1) / 2f - (a0 + b0) / 2f
    val k = PI.toFloat() / w
    val twist = Math.toRadians(
        wrapDelta(
            Math.toDegrees(atan2((b1 - a1).y, (b1 - a1).x).toDouble()).toFloat() -
                Math.toDegrees(atan2((b0 - a0).y, (b0 - a0).x).toDouble()).toFloat(),
        ).toDouble(),
    ).toFloat()
    // Screen y runs down: dragging right turns the front to the right (about y), down tips it towards you (about x),
    // and a clockwise twist on screen is a turn the other way about z, which points out of the screen.
    val d = Quat.axisAngle(Vec3(0f, 0f, 1f), -twist) *
        Quat.axisAngle(Vec3(1f, 0f, 0f), move.y * k) *
        Quat.axisAngle(Vec3(0f, 1f, 0f), move.x * k)
    toy.turn = (d * toy.turn).normalized()
}

