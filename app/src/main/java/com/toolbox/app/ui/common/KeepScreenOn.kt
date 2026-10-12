package com.toolbox.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Keeps the display awake while [on] and this composable is on screen, for things that are watched rather than touched. */
@Composable
fun KeepScreenOn(on: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}
