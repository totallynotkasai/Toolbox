package com.toolbox.app.tools.meditate

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberVibes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class Kind(val label: String, val target: Float) { In("Breathe in", 1f), HoldFull("Hold", 1f), Out("Breathe out", 0.6f), HoldEmpty("Hold", 0.6f) }
private data class Phase(val kind: Kind, val seconds: Int)

private enum class Pattern(val label: String, val phases: List<Phase>) {
    Calm("Calm", listOf(Phase(Kind.In, 4), Phase(Kind.Out, 6))),
    Box("Box", listOf(Phase(Kind.In, 4), Phase(Kind.HoldFull, 4), Phase(Kind.Out, 4), Phase(Kind.HoldEmpty, 4))),
    Relax("4-7-8", listOf(Phase(Kind.In, 4), Phase(Kind.HoldFull, 7), Phase(Kind.Out, 8))),
}

@Composable
fun MeditateScreen(onBack: () -> Unit) {
    val vibes = rememberVibes()
    var pattern by rememberSaveable { mutableStateOf(Pattern.Calm) }
    var minutes by rememberSaveable { mutableIntStateOf(1) }
    var running by remember { mutableStateOf(false) }
    var phaseLabel by remember { mutableStateOf("Ready") }
    var remainingMs by remember { mutableLongStateOf(0L) }
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(running) {
        if (!running) {
            vibes.stop()
            scale.animateTo(0.6f, tween(800))
            return@LaunchedEffect
        }
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        launch {
            while (isActive) { remainingMs = (end - SystemClock.elapsedRealtime()).coerceAtLeast(0); delay(250) }
        }
        outer@ while (true) {
            for (p in pattern.phases) {
                if (SystemClock.elapsedRealtime() >= end) break@outer
                phaseLabel = p.kind.label
                // A steady, gentle buzz for the whole in-breath; still for the out-breath and holds.
                if (p.kind == Kind.In) vibes.pattern(longArrayOf(0, p.seconds * 1000L), floatArrayOf(0f, 0.3f)) else vibes.stop()
                if (p.kind.target != scale.value) scale.animateTo(p.kind.target, tween(p.seconds * 1000, easing = FastOutSlowInEasing))
                else delay(p.seconds * 1000L)
            }
        }
        phaseLabel = "Well done"
        vibes.thud(0.8f)
        running = false
    }

    ToolScaffold(title = "Meditate", onBack = onBack) { padding ->
        ToolBody(padding) {
            val primary = MaterialTheme.colorScheme.primary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val container = MaterialTheme.colorScheme.primaryContainer
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(300.dp)) {
                    val r = size.minDimension / 2
                    // Soft halo + core: the halo lags behind, which reads as a gentle bloom.
                    drawCircle(container.copy(alpha = 0.5f), r * (scale.value * 0.95f + 0.05f))
                    drawCircle(
                        Brush.linearGradient(listOf(primary, tertiary), start = center.copy(y = 0f), end = center.copy(y = size.height)),
                        r * scale.value * 0.8f,
                    )
                }
                AnimatedContent(phaseLabel, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) }, label = "phase") {
                    Text(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (running) "%d:%02d".format(remainingMs / 60_000, (remainingMs / 1000) % 60) else " ",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pattern.entries.forEach { p ->
                    FilterChip(selected = pattern == p, onClick = { pattern = p }, label = { Text(p.label) }, enabled = !running)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5, 10).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }, enabled = !running)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (running) phaseLabel = "Ready"
                running = !running
            }) { Text(if (running) "Stop" else "Start") }
        }
    }
}
