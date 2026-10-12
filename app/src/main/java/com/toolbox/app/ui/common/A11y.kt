package com.toolbox.app.ui.common

import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Whether TalkBack (or another screen reader that explores by touch) is on, kept up to date while shown. */
@Composable
fun rememberScreenReaderOn(): Boolean {
    val manager = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    var on by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}

/**
 * Something for TalkBack to say once, such as where the computer played, that isn't shown as text anywhere it would
 * be read. Saying the same words twice reads them twice, which a live region wouldn't. Nothing happens without a
 * screen reader.
 *
 * Android 16 deprecates announcements in favour of live regions and pane titles, which the app uses wherever there is
 * text on screen to hang them on; this is for the rest, and still works.
 */
@Composable
fun rememberAnnouncer(): (String) -> Unit {
    val view = LocalView.current
    return remember(view) { { text: String -> @Suppress("DEPRECATION") view.announceForAccessibility(text) } }
}

/**
 * Says [text] when [active] turns true and stays true for [settleMs], such as "Level" or "In tune". It isn't said
 * again within [quietMs] of the last time, so a reading flickering at the edge doesn't chatter.
 */
@Composable
fun AnnounceOnStart(active: Boolean, text: String, settleMs: Long = 600, quietMs: Long = 4000) {
    val say = rememberAnnouncer()
    val latest by rememberUpdatedState(text)
    var lastSaid by remember { mutableStateOf(Long.MIN_VALUE / 2) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(settleMs)
        val now = SystemClock.elapsedRealtime()
        if (now - lastSaid >= quietMs) {
            say(latest)
            lastSaid = now
        }
    }
}

/** TalkBack actions, listed in its actions menu, that each run their block. */
fun accessibilityActions(vararg actions: Pair<String, () -> Unit>): List<CustomAccessibilityAction> =
    actions.map { (label, run) -> CustomAccessibilityAction(label) { run(); true } }

/**
 * One part of a board drawn on a Canvas, as TalkBack sees it: [rect] is where it is, in pixels from the board's
 * top-left. [onClick] null means it can't be chosen now. [state] is read after the [description].
 */
class BoardTarget(
    val key: Any,
    val rect: Rect,
    val description: String,
    val state: String? = null,
    val clickLabel: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * Invisible TalkBack nodes over a board drawn on a Canvas, one per [BoardTarget], so each line or cell can be found by
 * touch and chosen with a double tap. Lay it over the Canvas at the same size (`Modifier.matchParentSize()` in a Box
 * with it). It takes no touches itself, so taps and drags still reach the board underneath.
 */
@Composable
fun BoardTargets(targets: List<BoardTarget>, modifier: Modifier = Modifier) {
    Layout(
        content = {
            for (t in targets) {
                Box(
                    Modifier.semantics {
                        contentDescription = t.description
                        t.state?.let { stateDescription = it }
                        role = Role.Button
                        val click = t.onClick
                        if (click != null) onClick(label = t.clickLabel) { click(); true } else disabled()
                    },
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { i, m ->
            val r = targets[i].rect
            val w = r.width.roundToInt().coerceAtLeast(1)
            val h = r.height.roundToInt().coerceAtLeast(1)
            m.measure(Constraints.fixed(w, h))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val r = targets[i].rect
                p.place(r.left.roundToInt(), r.top.roundToInt())
            }
        }
    }
}
