package com.toolbox.app.tools.compass

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.rememberPermission
import com.toolbox.app.ui.common.rememberPref
import java.time.LocalDate

private const val PREF_TRUE_NORTH = "compass_true_north"
/** The declination last worked out from the phone's location ("" for none), and the day it was worked out on. */
private const val PREF_DECLINATION = "compass_declination"
private const val PREF_DECLINATION_DAY = "compass_declination_day"
/** The declination typed in, used while location isn't ("" for none). */
private const val PREF_MANUAL_DECLINATION = "compass_manual_declination"
/** Whether turning True north on has asked for location yet. It asks only the first time. */
private const val PREF_LOCATION_ASKED = "compass_location_asked"

enum class LocationLookup { Idle, Finding, LocationOff, NoFix }

/** The True north switch and where its declination comes from. Made by [rememberTrueNorth]. */
class TrueNorthState(
    val on: Boolean,
    /** The declination in use, or null while True north is off or none is known (the heading stays magnetic). */
    val declination: Float?,
    val source: DeclinationSource?,
    val manual: Float?,
    val lookup: LocationLookup,
    val locationAllowed: Boolean,
    val locationBlocked: Boolean,
    val editing: Boolean,
    private val actions: Actions,
) {
    class Actions(
        val setOn: (Boolean) -> Unit,
        val useLocation: () -> Unit,
        val retry: () -> Unit,
        val setEditing: (Boolean) -> Unit,
        val saveManual: (Float) -> Unit,
    )

    fun setOn(value: Boolean) = actions.setOn(value)
    /** Asks for location again, or opens the app's settings once Android won't ask any more. */
    fun useLocation() = actions.useLocation()
    fun retry() = actions.retry()
    fun startEditing() = actions.setEditing(true)
    fun stopEditing() = actions.setEditing(false)
    fun saveManual(value: Float) = actions.saveManual(value)
}

/**
 * True north for the Compass. Turning it on asks once for approximate location. While location is allowed, the
 * declination is worked out from it when the screen opens, at most once a day, and only the declination and the day
 * are saved. Without location, a typed-in declination is used instead.
 */
@Composable
fun rememberTrueNorth(): TrueNorthState {
    val context = LocalContext.current
    var on by rememberPref(PREF_TRUE_NORTH, false)
    var fromLocation by rememberPref(PREF_DECLINATION, "")
    var day by rememberPref(PREF_DECLINATION_DAY, 0L)
    var manualText by rememberPref(PREF_MANUAL_DECLINATION, "")
    var asked by rememberPref(PREF_LOCATION_ASKED, false)
    var editing by rememberSaveable { mutableStateOf(false) }
    var lookup by remember { mutableStateOf(LocationLookup.Idle) }
    var retries by remember { mutableIntStateOf(0) }
    val permission = rememberPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    val saved = fromLocation.toFloatOrNull()
    val manual = manualText.toFloatOrNull()

    LaunchedEffect(on, permission.granted, retries) {
        if (!on || !permission.granted) {
            lookup = LocationLookup.Idle
            return@LaunchedEffect
        }
        val today = LocalDate.now().toEpochDay()
        if (retries == 0 && !declinationNeedsRefresh(saved?.let { day }, today)) return@LaunchedEffect
        lookup = LocationLookup.Finding
        lookup = when (val result = lookUpDeclination(context)) {
            is DeclinationLookup.Found -> {
                fromLocation = result.declination.toString()
                day = today
                LocationLookup.Idle
            }
            DeclinationLookup.LocationOff -> LocationLookup.LocationOff
            DeclinationLookup.NoFix -> LocationLookup.NoFix
        }
    }

    val chosen = if (on) chooseDeclination(permission.granted, saved, manual) else null
    return TrueNorthState(
        on = on,
        declination = chosen?.first,
        source = chosen?.second,
        manual = manual,
        lookup = lookup,
        locationAllowed = permission.granted,
        locationBlocked = permission.blocked,
        editing = editing,
        actions = TrueNorthState.Actions(
            setOn = { value ->
                on = value
                if (value && !permission.granted && !permission.blocked && !asked) {
                    asked = true
                    permission.request()
                }
            },
            useLocation = permission::request,
            retry = { retries++ },
            setEditing = { editing = it },
            saveManual = { manualText = it.toString(); editing = false },
        ),
    )
}

/** The True north switch, the declination in use, and what to do when location can't give one. */
@Composable
fun TrueNorthControls(state: TrueNorthState, modifier: Modifier = Modifier) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        // The whole row toggles, so TalkBack reads the label and the declination with the switch.
        Row(
            Modifier.fillMaxWidth().toggleable(value = state.on, role = Role.Switch, onValueChange = state::setOn)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("True north", style = MaterialTheme.typography.titleMedium)
                Text(
                    statusLine(state, ::formatDeclination),
                    style = MaterialTheme.typography.bodyMedium,
                    color = variant,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = statusLine(state, ::spokenDeclination)
                    },
                )
            }
            Switch(checked = state.on, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
        }
        val problem = locationProblem(state) ?: return@Column
        Text(
            problem + if (state.manual == null) " Type in the declination where you are to show true north." else "",
            style = MaterialTheme.typography.bodyMedium,
            color = variant,
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(onClick = state::startEditing) {
                Text(if (state.manual == null) "Enter declination" else "Change declination")
            }
            when {
                !state.locationAllowed -> TextButton(onClick = state::useLocation) {
                    Text(if (state.locationBlocked) "Open settings" else "Use location")
                }
                else -> TextButton(onClick = state::retry) { Text("Try again") }
            }
        }
    }
}

/** The line under the switch, with the declination written by [format] (for the screen or for TalkBack). */
private fun statusLine(state: TrueNorthState, format: (Float) -> String): String {
    val declination = state.declination
    return when {
        !state.on -> "Uses your approximate location, which isn't kept"
        declination != null && state.source == DeclinationSource.Manual -> "Declination ${format(declination)}, set by hand"
        declination != null -> "Declination ${format(declination)}"
        state.lookup == LocationLookup.Finding -> "Finding your approximate location…"
        else -> "Showing magnetic north for now"
    }
}

/** Why the declination isn't coming from location, or null when it is (or will be shortly). */
private fun locationProblem(state: TrueNorthState): String? = when {
    !state.on || state.source == DeclinationSource.Location -> null
    !state.locationAllowed -> "Location isn't allowed."
    state.lookup == LocationLookup.LocationOff -> "Location is off on this phone."
    state.lookup == LocationLookup.NoFix -> "Couldn't find your approximate location."
    else -> null
}

/** Takes a declination in degrees with an east/west choice. */
@Composable
fun DeclinationDialog(initial: Float?, onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    val (startText, startEast) = initial?.let(::declinationFieldText) ?: ("" to true)
    var text by rememberSaveable { mutableStateOf(startText) }
    var east by rememberSaveable { mutableStateOf(startEast) }
    val value = parseDeclination(text, east)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Declination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "How far magnetic north is from true north where you are. Most walking maps print it, and online " +
                        "magnetic declination calculators give it for any place. It changes slowly, so it holds for " +
                        "a few years.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    label = { Text("Degrees") },
                    singleLine = true,
                    isError = text.isNotEmpty() && value == null,
                    supportingText = { Text("0 to ${MAX_DECLINATION.toInt()}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(true, false).forEachIndexed { i, isEast ->
                        SegmentedButton(
                            selected = east == isEast,
                            onClick = { east = isEast },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(if (isEast) "East" else "West") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
