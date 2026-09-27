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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics

/** Tally counter. Persisted so a count survives leaving the screen (e.g. counting laps or stock). */
@Composable
fun CounterScreen(onBack: () -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("counter", Context.MODE_PRIVATE)
    val haptics = rememberHaptics()
    var count by remember { mutableIntStateOf(prefs.getInt("count", 0)) }
    fun set(v: Int) { count = v; prefs.edit().putInt("count", v).apply() }

    ToolScaffold(
        title = "Tally Counter",
        onBack = onBack,
        actions = {
            IconButton(onClick = { set(0); haptics.heavy() }) { Icon(Icons.Filled.RestartAlt, contentDescription = "Reset") }
        },
    ) { padding ->
        ToolBody(padding) {
            Box(
                Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { set(count + 1); haptics.click() },
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalIconButton(onClick = { set(count - 1); haptics.click() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
            }
        }
    }
}
