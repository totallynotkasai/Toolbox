package com.toolbox.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel

/**
 * Turns frame timestamps into a step length in seconds. The first frame after [reset] is a zero step, and every
 * step is capped at [maxDt], so a dropped frame or a pause slows the game down instead of teleporting it.
 */
class FrameClock(private val maxDt: Float) {
    private var last = NONE

    fun reset() { last = NONE }

    fun step(frameNanos: Long): Float {
        val dt = if (last == NONE) 0f else ((frameNanos - last) / 1e9f).coerceIn(0f, maxDt)
        last = frameNanos
        return dt
    }

    private companion object { const val NONE = Long.MIN_VALUE }
}

/** Handle for a loop from [rememberGameLoop]. */
class GameLoop internal constructor(maxDt: Float) {
    internal val clock = FrameClock(maxDt)
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    /** Restarts an idle loop, e.g. when a touch or a new game sets something moving. Safe to call any time. */
    fun wake() { wakeUp.trySend(Unit) }

    internal suspend fun awaitWake() = wakeUp.receive()
}

/**
 * Calls [onFrame] once per display frame with the step in seconds (see [FrameClock]) while [enabled] and the app
 * is started, so a game pauses by itself when you leave. [onFrame] returns whether anything is still moving:
 * once it returns false the loop stops asking for frames, sparing the battery, until [GameLoop.wake].
 * The first step after a pause or an idle spell is zero.
 */
@Composable
fun rememberGameLoop(enabled: Boolean = true, maxDt: Float = 0.05f, onFrame: (dt: Float) -> Boolean): GameLoop {
    val loop = remember(maxDt) { GameLoop(maxDt) }
    val frame by rememberUpdatedState(onFrame)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(loop, lifecycle, enabled) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loop.clock.reset()
            while (true) {
                val moving = withFrameNanos { frame(loop.clock.step(it)) }
                if (!moving) {
                    loop.awaitWake()
                    loop.clock.reset()
                }
            }
        }
    }
    return loop
}
