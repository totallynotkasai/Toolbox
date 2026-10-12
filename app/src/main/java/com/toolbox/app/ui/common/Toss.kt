package com.toolbox.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/** Quick picks the answer straight away and animates it landing; Throw is the physics table. Coin and Dice have both. */
enum class TossMode(val label: String) { Quick("Quick"), Throw("Throw") }

/** The [TossMode] a tool last used, saved under [key]. Throw until one is chosen. */
@Composable
fun rememberTossMode(key: String): MutableState<TossMode> {
    val pref = rememberPref(key, TossMode.Throw.name)
    return remember(pref) {
        object : MutableState<TossMode> {
            override var value: TossMode
                get() = TossMode.entries.firstOrNull { it.name == pref.value } ?: TossMode.Throw
                set(v) { pref.value = v.name }
            override fun component1() = value
            override fun component2(): (TossMode) -> Unit = { value = it }
        }
    }
}

/** The Quick / Throw switch at the top of Coin and Dice. It's disabled while something is moving. */
@Composable
fun TossModeToggle(mode: TossMode, onChange: (TossMode) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.widthIn(max = 320.dp).fillMaxWidth()) {
        TossMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onChange(m) },
                shape = SegmentedButtonDefaults.itemShape(i, TossMode.entries.size),
                enabled = enabled,
                label = { Text(m.label) },
            )
        }
    }
}

/**
 * The frame Coin and Dice share: the mode switch at the top, the table filling the space in between, and the result
 * and controls below. In a wide window (a large screen turned sideways) the table goes on the left.
 */
@Composable
fun TossLayout(
    padding: PaddingValues,
    toggle: @Composable () -> Unit,
    stage: @Composable (Modifier) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                stage(Modifier.weight(1f).fillMaxSize())
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    toggle()
                    Spacer(Modifier.height(16.dp))
                    controls()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                toggle()
                Spacer(Modifier.height(12.dp))
                stage(Modifier.widthIn(max = 480.dp).fillMaxWidth().weight(1f))
                Spacer(Modifier.height(16.dp))
                controls()
            }
        }
    }
}

/** Swipe speeds (dp/s) mapped onto throw power 0–1. Slower swipes don't throw. */
private const val SWIPE_MIN = 350f
private const val SWIPE_MAX = 4000f

/** Shake strengths (m/s², see [OnShake]) mapped onto throw power 0–1. */
private const val SHAKE_MIN = 12f
private const val SHAKE_MAX = 40f

/** How quick a touch on something that can be picked up must be to count as a tap rather than a hold. */
private const val TAP_MS = 220L

/** Throw power 0–1 for a swipe of [speed] dp/s (see [TossTable]'s onFling). */
fun swipePower(speed: Float) = ((speed - SWIPE_MIN) / (SWIPE_MAX - SWIPE_MIN)).coerceIn(0f, 1f)

/** Throw power 0–1 for a shake of [strength] m/s². */
fun shakePower(strength: Float) = ((strength - SHAKE_MIN) / (SHAKE_MAX - SHAKE_MIN)).coerceIn(0f, 1f)

/**
 * The table the coin or dice sit on: a rounded panel with [draw] on it, and the touch handling the two share.
 *
 * - A quick touch is a tap ([onTap]), which TalkBack offers as [tapLabel].
 * - Pressing where [onPress] finds something to pick up holds it, and dragging moves it: [onDrag] gets how far the
 *   finger has gone, in px. A long press that doesn't move is a hold, not a tap.
 * - Letting go faster than a slow swipe throws ([onFling], with the finger's velocity in dp/s, y up the screen).
 * - Letting go of something held any slower puts it down ([onPutDown]).
 *
 * [onSize] isn't called for an empty size, such as a squashed split-screen window, which would make the physics'
 * table infinitely wide.
 */
@Composable
fun TossTable(
    description: String,
    tapLabel: String,
    onSize: (IntSize) -> Unit,
    onTap: () -> Unit,
    onFling: (vx: Float, vUp: Float) -> Unit,
    modifier: Modifier = Modifier,
    onPress: (Offset) -> Boolean = { false },
    onDrag: (Offset) -> Unit = {},
    onPutDown: () -> Unit = {},
    draw: DrawScope.() -> Unit,
) {
    val table = MaterialTheme.colorScheme.surfaceContainerHigh
    val tableFar = MaterialTheme.colorScheme.surfaceContainer
    val currentSize by rememberUpdatedState(onSize)
    val currentTap by rememberUpdatedState(onTap)
    val currentFling by rememberUpdatedState(onFling)
    val currentPress by rememberUpdatedState(onPress)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentPutDown by rememberUpdatedState(onPutDown)
    Canvas(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .onSizeChanged { if (it.width > 0 && it.height > 0) currentSize(it) }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = tapLabel) { currentTap(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val holding = currentPress(down.position)
                    var travelled = 0f
                    var upTime = down.uptimeMillis
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        travelled = max(travelled, (change.position - down.position).getDistance())
                        change.consume()
                        upTime = change.uptimeMillis
                        if (holding) currentDrag(change.position - down.position)
                        if (!change.pressed) break
                    }
                    val tap = travelled < viewConfiguration.touchSlop
                    if (tap && (!holding || upTime - down.uptimeMillis < TAP_MS)) {
                        currentTap()
                        return@awaitEachGesture
                    }
                    val v = tracker.calculateVelocity()
                    val vx = v.x / density
                    val vUp = -v.y / density
                    when {
                        hypot(vx, vUp) >= SWIPE_MIN -> currentFling(vx, vUp)
                        holding -> currentPutDown()
                    }
                }
            },
    ) {
        drawRect(Brush.verticalGradient(listOf(tableFar, table)))
        draw()
    }
}

/** Flat periwinkle palette shared by the coin and the dice, so they match. */
object TossColors {
    val Rim = Color(0xFF7D7FE3)
    val Face = Color(0xFF9A9CF4)
    val Dots = Color(0xFFE4E4FF)
    val Ink = Color(0xFF3C3A9E)
    val Shade = Color(0xFF2A2870)
    val EdgeHi = Color(0xFFA9ABFA)
    val EdgeLo = Color(0xFF4E50B8)
}

/** Light from the upper left, above the table, for shading the coin and the dice. */
val TOSS_LIGHT = Vec3(-0.45f, 0.8f, 0.45f).normalized()

/**
 * A soft shadow on the table under a coin or die: an ellipse [rx] by [ry] that fades and blurs as [height] (0 on the
 * table, 1 at the top of a big throw) rises, and is sharpest on landing.
 */
fun DrawScope.drawTossShadow(center: Offset, rx: Float, ry: Float, height: Float) {
    val h = height.coerceIn(0f, 1f)
    val alpha = 0.34f * (1f - 0.7f * h)
    val core = 0.72f - 0.6f * h
    withTransform({ scale(1f, ry / rx, center) }) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = alpha),
                core to Color.Black.copy(alpha = alpha * 0.75f),
                1f to Color.Transparent,
                center = center,
                radius = rx,
            ),
            rx,
            center,
        )
    }
}
