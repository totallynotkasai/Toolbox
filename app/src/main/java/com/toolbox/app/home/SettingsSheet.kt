package com.toolbox.app.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberVibes
import com.toolbox.app.ui.common.rememberVibrationEnabled

/** App-wide switches that used to sit in each tool's top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val vibration = rememberVibrationEnabled()
    val shake = rememberShakeEnabled()
    val vibes = rememberVibes()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp).semantics { heading() },
        )
        SettingSwitch(
            icon = Icons.Filled.Vibration,
            title = "Vibration",
            summary = "Taps in tools and games, and the feel of the toys",
            state = vibration,
            onChanged = { on ->
                // A buzz to confirm it's on. The switch only reaches vibes on the next recomposition, so arm it here.
                if (on) { vibes.enabled = true; vibes.click() }
            },
        )
        SettingSwitch(
            icon = Icons.Filled.Sensors,
            title = "Shake to decide",
            summary = "Shake the phone to flip, roll, spin, ask, pick a number or reshuffle teams",
            state = shake,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** A full-width row that toggles [state], read out by TalkBack as one switch. */
@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    summary: String,
    state: MutableState<Boolean>,
    onChanged: (Boolean) -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = state.value, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .toggleable(value = state.value, role = Role.Switch) { state.value = it; onChanged(it) }
            .padding(horizontal = 8.dp),
    )
}
