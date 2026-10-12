package com.toolbox.app.tools.counter

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.toolbox.app.ui.common.Prefs
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import kotlinx.coroutines.launch

private const val COUNT_KEY = "counter_count"

/**
 * Moves a count saved by older versions, in a "counter" prefs file of its own, into the shared [Prefs].
 * It runs once: the old file is emptied afterwards. Returns the key the count now lives under.
 */
private fun migrateOldCount(context: Context): String {
    val old = context.getSharedPreferences("counter", Context.MODE_PRIVATE)
    if (!old.contains("count")) return COUNT_KEY
    Prefs(context).put(COUNT_KEY, old.getInt("count", 0))
    old.edit { clear() }
    return COUNT_KEY
}

/** Tally counter. Persisted so a count survives leaving the screen (e.g. counting laps or stock). */
@Composable
fun CounterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val key = remember { migrateOldCount(context.applicationContext) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var count by rememberPref(key, 0)

    fun change(by: Int) {
        snackbar.currentSnackbarData?.dismiss() // counting again settles a reset
        count += by
        haptics.click()
    }

    fun reset() {
        val before = count
        count = 0
        haptics.heavy()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val choice = snackbar.showSnackbar("Reset from $before", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (choice == SnackbarResult.ActionPerformed) count = before
        }
    }

    ToolScaffold(
        title = "Tally Counter",
        onBack = onBack,
        actions = {
            IconButton(onClick = ::reset, enabled = count != 0) { Icon(Icons.Filled.RestartAlt, contentDescription = "Reset") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ToolBody(padding) {
            Box(
                Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClickLabel = "Count one") { change(1) },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                            (slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "count",
                ) {
                    Text(
                        "$it",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Tap the circle to count", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            // Big enough to find without looking, but well apart from the circle.
            FilledTonalIconButton(onClick = { change(-1) }, modifier = Modifier.size(80.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(36.dp))
            }
        }
    }
}
