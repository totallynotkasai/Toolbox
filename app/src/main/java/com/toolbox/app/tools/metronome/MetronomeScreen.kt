package com.toolbox.app.tools.metronome

import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import kotlinx.coroutines.launch

private const val MIN_BPM = 30
private const val MAX_BPM = 250

private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 168 -> "Allegro"
    else -> "Presto"
}

@Composable
fun MetronomeScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bpm by rememberSaveable { mutableIntStateOf(100) }
    var beats by rememberSaveable { mutableIntStateOf(4) }
    var accent by rememberSaveable { mutableStateOf(true) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var currentBeat by remember { mutableIntStateOf(-1) }
    val pulse = remember { Animatable(1f) }
    val taps = remember { ArrayDeque<Long>() }

    val engine = remember {
        MetronomeEngine { i ->
            currentBeat = i
            scope.launch { pulse.snapTo(1.12f); pulse.animateTo(1f, tween(180)) }
        }
    }
    engine.bpm = bpm
    engine.beatsPerBar = beats
    engine.accentFirstBeat = accent
    // Like every other sound in the app, it stops when you leave; it shows as stopped when you come back. A theme or
    // font change (or a large screen turning) recreates the screen without leaving, so it carries on through that.
    val activity = LocalActivity.current
    LifecycleStartEffect(engine) {
        if (playing) engine.start()
        onStopOrDispose {
            engine.stop()
            currentBeat = -1
            if (activity?.isChangingConfigurations != true) playing = false
        }
    }

    val say = rememberAnnouncer()
    fun setBpm(v: Int) { bpm = v.coerceIn(MIN_BPM, MAX_BPM) }
    // The buttons say the new tempo; the slider doesn't, as TalkBack reads its value as it moves.
    fun nudge(by: Int) { setBpm(bpm + by); say("$bpm BPM") }
    fun tap() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.addLast(now)
        if (taps.size > 5) taps.removeFirst()
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
            setBpm((60_000 / avg).toInt())
            say("$bpm BPM")
        }
    }

    ToolScaffold(title = "Metronome", onBack = onBack) { padding ->
        ToolBody(padding) {
            Box(
                Modifier
                    .size(220.dp)
                    .scale(pulse.value)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                // Inset so the number shrinks to stay inside the circle at large font sizes.
                Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val big = MaterialTheme.typography.displayLarge
                    val small = MaterialTheme.typography.labelLarge
                    Text(
                        "$bpm",
                        style = big,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = big.fontSize),
                    )
                    Text(
                        "BPM · ${tempoName(bpm)}",
                        style = small,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = small.fontSize),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(beats) { i -> BeatDot(active = playing && i == currentBeat, accent = accent && i == 0) }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 420.dp)) {
                FilledTonalIconButton(onClick = { nudge(-1) }) { Icon(Icons.Filled.Remove, "Slower") }
                Slider(
                    value = bpm.toFloat(),
                    onValueChange = { setBpm(it.toInt()) },
                    valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "Tempo"
                        stateDescription = "$bpm BPM"
                    },
                )
                FilledTonalIconButton(onClick = { nudge(1) }) { Icon(Icons.Filled.Add, "Faster") }
            }
            Spacer(Modifier.height(16.dp))
            // Wraps at large font sizes. TalkBack hears "4 beats per bar" rather than a fraction.
            FlowRow(
                Modifier.selectableGroup().semantics { contentDescription = "Time signature" },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                listOf(2, 3, 4, 6).forEach { b ->
                    FilterChip(
                        selected = beats == b,
                        onClick = { beats = b },
                        label = { Text("$b/4") },
                        modifier = Modifier.semantics { contentDescription = "$b beats per bar" },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // The whole row is the switch, so TalkBack reads the label with it and a tap anywhere on it toggles.
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(value = accent, role = Role.Switch) { accent = it }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Accent first beat")
                Switch(checked = accent, onCheckedChange = null)
            }
            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            ) {
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = ::tap) { Text("Tap tempo") }
                FilledIconButton(
                    onClick = {
                        if (playing) engine.stop() else engine.start()
                        playing = !playing
                        currentBeat = -1
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Stop" else "Start", Modifier.size(36.dp))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BeatDot(active: Boolean, accent: Boolean) {
    val color by animateColorAsState(
        when {
            active && accent -> MaterialTheme.colorScheme.tertiary
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(60),
        label = "beat",
    )
    Box(Modifier.size(if (accent) 22.dp else 18.dp).clip(CircleShape).background(color))
}
