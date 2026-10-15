package com.toolbox.app.ui.common

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Provided by MainActivity: turns the app sideways for the screen at a route while it's asked for (true), and back
 * upright once it isn't. The app is otherwise portrait.
 */
val LocalLandscapeLock = staticCompositionLocalOf<(route: String, on: Boolean) -> Unit> { { _, _ -> } }

/**
 * Turns the app sideways while this is on screen, either way round as the phone is held, such as for a round of
 * Charades. It only holds for the screen that asked: leaving it turns the app upright again straight away.
 * API 36 ignores the request on screens 600dp and wider, so whatever this shows still has to work upright.
 */
@Composable
fun LandscapeLock() {
    val lock = LocalLandscapeLock.current
    val route = LocalToolRoute.current ?: return
    DisposableEffect(lock, route) {
        lock(route, true)
        onDispose { lock(route, false) }
    }
}

/** Hides the status and navigation bars while this is on screen; a swipe in from the edge shows them for a moment. */
@Composable
fun HideSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
