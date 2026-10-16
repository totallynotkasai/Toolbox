package com.toolbox.app.tools.soundscapes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import kotlinx.coroutines.delay

/**
 * A mixer of seven synthesised sounds with a sleep timer. Playing is done by [SoundscapeService], so it carries on
 * after leaving this screen or the app, with the screen off, until Stop or the sleep timer.
 */
@Composable
fun SoundscapesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Soundscapes.load(context) // Reads the saved mix the first time; after that it does nothing.
    val mix by Soundscapes.mix.collectAsStateWithLifecycle()
    val playback by Soundscapes.playback.collectAsStateWithLifecycle()
    val status = playback.status
    val sleep = playback.sleep
    // While the sleep timer runs down, the time left ticks.
    val now by produceState(Soundscapes.now(), sleep) {
        value = Soundscapes.now()
        while (sleep.running) {
            delay(250)
            value = Soundscapes.now()
        }
    }

    // Each change starts from the latest mix, so quick changes on two rows can't undo each other.
    fun change(sound: Sound, channel: Channel) = Soundscapes.setMix(Soundscapes.mix.value.with(sound, channel))
    val save = { Soundscapes.save(context) }

    ToolScaffold(title = "Soundscapes", onBack = onBack) { padding ->
        ToolBody(padding) {
            val rows = Modifier.fillMaxWidth().widthIn(max = 480.dp)
            Text(
                mix.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                when {
                    status == Status.Playing -> "Playing"
                    status == Status.Paused -> "Paused"
                    mix.anyOn -> "Ready to play"
                    else -> "Turn on a sound to play"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            // Not a live region, or TalkBack would read it out every second.
            sleepLine(sleep, status, now)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (status == Status.Playing) {
                    Button(onClick = { SoundscapeService.pause(context) }) { Label(Icons.Filled.Pause, "Pause") }
                } else {
                    Button(onClick = { SoundscapeService.play(context) }, enabled = mix.anyOn || status == Status.Paused) {
                        Label(Icons.Filled.PlayArrow, "Play")
                    }
                }
                if (status != Status.Stopped) {
                    OutlinedButton(onClick = { SoundscapeService.stop(context) }) { Label(Icons.Filled.Stop, "Stop") }
                }
            }

            Spacer(Modifier.height(24.dp))
            Heading("Nature", rows)
            Sound.entries.filter { it.nature }.forEach { SoundRow(it, mix[it], rows, { c -> change(it, c) }, save) }
            Spacer(Modifier.height(8.dp))
            Heading("Noise", rows)
            Sound.entries.filter { !it.nature }.forEach { SoundRow(it, mix[it], rows, { c -> change(it, c) }, save) }
            Spacer(Modifier.height(8.dp))
            Heading("Volume", rows)
            Row(rows.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = mix.master,
                    onValueChange = { Soundscapes.setMix(Soundscapes.mix.value.copy(master = it)) },
                    onValueChangeFinished = save,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Volume" },
                )
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            Heading("Sleep timer", rows)
            FlowRow(
                rows.selectableGroup().semantics { contentDescription = "Sleep timer" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SleepTimer.CHOICES.forEach { m ->
                    FilterChip(
                        selected = sleep.minutes == m,
                        onClick = { Soundscapes.setSleep(context, m) },
                        label = { Text(if (m == 0) "Off" else "$m min") },
                        modifier = Modifier.semantics { contentDescription = if (m == 0) "Off" else "$m minutes" },
                    )
                }
            }
        }
    }
}

/** What the sleep timer will do, such as "Stops in 23:41", or null when it's off. */
internal fun sleepLine(sleep: SleepTimer, status: Status, now: Long): String? {
    val left = sleep.left(now) ?: return null
    val seconds = (left + 999) / 1000
    val clock = "%d:%02d".format(seconds / 60, seconds % 60)
    return when (status) {
        Status.Playing -> "Stops in $clock"
        Status.Paused -> "$clock left on the sleep timer"
        Status.Stopped -> "Stops after ${sleep.minutes} minutes of playing"
    }
}

@Composable
private fun Label(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(text)
}

@Composable
private fun Heading(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 4.dp).semantics { heading() },
    )
}

/**
 * A sound's name, its volume and its switch. Moving the slider of a sound that's off turns it on, so it can be heard
 * while it's set. TalkBack hears the switch as the sound's name, and the slider as its volume.
 */
@Composable
private fun SoundRow(sound: Sound, channel: Channel, modifier: Modifier, onChange: (Channel) -> Unit, onSettled: () -> Unit) {
    Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(sound.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 64.dp).clearAndSetSemantics {})
        Slider(
            value = channel.volume,
            onValueChange = { onChange(Channel(on = true, volume = it)) },
            onValueChangeFinished = onSettled,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .alpha(if (channel.on) 1f else 0.45f)
                .semantics { contentDescription = "${sound.title} volume" },
        )
        Switch(
            checked = channel.on,
            onCheckedChange = {
                onChange(channel.copy(on = it))
                onSettled()
            },
            modifier = Modifier.semantics { contentDescription = sound.title },
        )
    }
}
