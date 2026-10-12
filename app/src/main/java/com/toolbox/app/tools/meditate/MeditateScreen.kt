package com.toolbox.app.tools.meditate

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.toolbox.app.ui.common.KeepScreenOn
import com.toolbox.app.ui.common.SteadyText
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberVibes

private const val HALO_LAG_MS = 350L // the soft halo trails the core by this much, which reads as a gentle bloom

/** Bookkeeping for the frame loop that never needs a recomposition. */
private class Guide {
    var phase = -1     // the phase whose vibration was last sent; -1 sends the current one again
    var size = EMPTY   // the circle as last drawn, for easing back to rest after Stop
}

@Composable
fun MeditateScreen(onBack: () -> Unit) {
    val vibes = rememberVibes()
    var pattern by rememberSaveable { mutableStateOf(Pattern.Calm) }
    var minutes by rememberSaveable { mutableIntStateOf(1) }
    var running by rememberSaveable { mutableStateOf(false) }   // a session has started and not ended
    var paused by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }  // the last session ran to the end
    // Time spent breathing before the latest resume, and when that resume was (elapsedRealtime, 0 while paused).
    var banked by rememberSaveable { mutableLongStateOf(0L) }
    var resumedAt by rememberSaveable { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(banked) }
    val session = remember(pattern, minutes) { Session(pattern, minutes) }
    val guide = remember { Guide() }
    val rest = remember { Animatable(EMPTY) }

    fun now() = if (resumedAt == 0L) banked else banked + SystemClock.elapsedRealtime() - resumedAt

    fun start() {
        banked = 0L
        elapsed = 0L
        resumedAt = SystemClock.elapsedRealtime()
        guide.phase = -1
        finished = false
        paused = false
        running = true
    }

    fun pause() {
        banked = now()
        elapsed = banked
        resumedAt = 0L
        paused = true
        vibes.stop()
    }

    fun resume() {
        resumedAt = SystemClock.elapsedRealtime()
        guide.phase = -1 // pick the breath back up, vibration and all
        paused = false
    }

    fun stop() {
        running = false
        paused = false
        resumedAt = 0L
        vibes.stop()
    }

    // Leaving the app (or locking the phone) pauses the session where it is.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (running && !paused) pause() }
    // The screen stays awake while it's guiding you; you may well have your eyes shut.
    KeepScreenOn(running && !paused)

    rememberGameLoop(enabled = running && !paused) {
        elapsed = now()
        val m = session.at(elapsed)
        if (m.index != guide.phase) {
            guide.phase = m.index
            // A steady, gentle buzz for the whole in-breath; still for the out-breath and holds.
            if (m.kind == Kind.In) vibes.pattern(longArrayOf(0, m.phaseLeftMs), floatArrayOf(0f, 0.3f)) else vibes.stop()
        }
        if (elapsed >= session.totalMs) {
            // Ended on a breath out, so the motor is already still and nothing cuts this short.
            running = false
            finished = true
            resumedAt = 0L
            vibes.thud(0.8f)
            false
        } else {
            true
        }
    }

    // After a session the circle eases back to its resting size from wherever it was.
    LaunchedEffect(running) {
        if (!running) {
            rest.snapTo(guide.size)
            rest.animateTo(EMPTY, tween(800))
        }
    }

    val label by remember(session) {
        derivedStateOf {
            when {
                paused -> "Paused"
                running -> session.at(elapsed).kind.label
                finished -> "Well done"
                else -> "Ready"
            }
        }
    }
    val secondsLeft by remember(session) { derivedStateOf { ((session.at(elapsed).remainingMs + 999) / 1000).toInt() } }

    ToolScaffold(title = "Meditate", onBack = onBack) { padding ->
        ToolBody(padding) {
            val primary = MaterialTheme.colorScheme.primary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val container = MaterialTheme.colorScheme.primaryContainer
            Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    val core: Float
                    val halo: Float
                    if (running) {
                        core = session.sizeAt(elapsed)
                        halo = session.sizeAt(elapsed - HALO_LAG_MS)
                        guide.size = core
                    } else {
                        core = rest.value
                        halo = rest.value
                    }
                    drawCircle(container.copy(alpha = 0.5f), r * (halo * 0.95f + 0.05f))
                    drawCircle(
                        Brush.linearGradient(listOf(primary, tertiary), start = center.copy(y = 0f), end = center.copy(y = size.height)),
                        r * core * 0.8f,
                    )
                }
                // The label sits on the core at its smallest, so it shrinks to fit there at large font sizes.
                AnimatedContent(
                    label,
                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
                    label = "phase",
                    modifier = Modifier.fillMaxWidth(0.4f).semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    BasicText(
                        it,
                        style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SteadyText(
                if (running) "%d:%02d".format(secondsLeft / 60, secondsLeft % 60) else "",
                style = MaterialTheme.typography.headlineMedium,
                widest = "00:00",
            )
            Spacer(Modifier.height(24.dp))
            // The chips wrap onto another line at large font sizes rather than squeezing.
            val chips = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            FlowRow(horizontalArrangement = chips) {
                Pattern.entries.forEach { p ->
                    FilterChip(selected = pattern == p, onClick = { pattern = p }, label = { Text(p.label) }, enabled = !running)
                }
            }
            FlowRow(horizontalArrangement = chips) {
                listOf(1, 2, 5, 10).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }, enabled = !running)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !running -> Button(onClick = { start() }) { Text("Start") }
                    paused -> {
                        Button(onClick = { resume() }) { Text("Resume") }
                        OutlinedButton(onClick = { stop() }) { Text("Stop") }
                    }
                    else -> {
                        OutlinedButton(onClick = { pause() }) { Text("Pause") }
                        Button(onClick = { stop() }) { Text("Stop") }
                    }
                }
            }
        }
    }
}
