package com.toolbox.app.tools.teams

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ShakeToggleButton
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.max

/** Keeps the dealt teams across rotation; names can't contain these control characters. */
private val TeamsSaver = Saver<List<List<String>>, String>(
    save = { teams -> teams.joinToString("\u001E") { it.joinToString("\u001F") } },
    restore = { s -> if (s.isEmpty()) emptyList() else s.split('\u001E').map { it.split('\u001F') } },
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TeamSplitterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val shake = rememberShakeEnabled()
    // Prefs has no list type, so the names are stored newline-joined (names never contain a new line).
    var stored by rememberPref("teams_names", "")
    val names = remember(stored) { stored.split('\n').filter { it.isNotEmpty() } }
    var bySize by rememberPref("teams_by_size", false)
    var teamCount by rememberPref("teams_count", 2)
    var perTeam by rememberPref("teams_per_team", 3)
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var teams by rememberSaveable(stateSaver = TeamsSaver) { mutableStateOf(emptyList<List<String>>()) }
    var deal by rememberSaveable { mutableIntStateOf(0) } // bumped on every split so the cards re-animate

    val n = names.size
    // Clamp for display only, so the saved choice comes back when more people are added.
    val shownTeamCount = teamCount.coerceIn(2, max(2, n))
    val shownPerTeam = perTeam.coerceIn(1, max(1, n - 1))
    val splitInto = if (bySize) teamsForSize(n, shownPerTeam) else shownTeamCount

    fun setNames(list: List<String>) {
        stored = list.joinToString("\n")
        teams = emptyList() // Stale teams would still show removed people.
    }

    fun add() {
        val parsed = parseNames(input)
        if (parsed.isEmpty()) return
        val seen = names.mapTo(mutableSetOf()) { it.lowercase() }
        val fresh = parsed.filter { seen.add(it.lowercase()) }
        val room = MAX_PEOPLE - n
        error = when {
            fresh.isEmpty() -> if (parsed.size == 1) "${parsed[0]} is already on the list" else "Those names are already on the list"
            room <= 0 || fresh.size > room -> "The list holds up to $MAX_PEOPLE people"
            else -> null
        }
        if (fresh.isNotEmpty() && room > 0) {
            setNames(names + fresh.take(room))
            input = ""
        }
    }

    fun split() {
        if (n < 2) return
        teams = splitTeams(names, splitInto)
        deal++
        haptics.heavy()
    }

    OnShake(shake.value) { split() } // The detector's cooldown stops a shake re-dealing mid-animation.

    ToolScaffold(
        title = "Team Splitter",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, teamsAsText(teams))
                    context.startActivity(Intent.createChooser(send, "Share teams"))
                },
                enabled = teams.isNotEmpty(),
            ) { Icon(Icons.Filled.Share, contentDescription = "Share teams") }
            ShakeToggleButton(shake, "reshuffle")
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val column = Modifier.widthIn(max = 560.dp).fillMaxWidth()

            item("input") {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.replace('\u001E', ' ').replace('\u001F', ' '); error = null },
                    label = { Text("Add a name") },
                    placeholder = { Text("Or paste a list: Sam, Alex, Jo…") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Press Enter to add") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = {
                        IconButton(onClick = ::add, enabled = input.isNotBlank()) {
                            Icon(Icons.Filled.Add, contentDescription = "Add name")
                        }
                    },
                    modifier = column,
                )
            }

            item("people") {
                Column(column) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (n) { 0 -> "No one yet"; 1 -> "1 person"; else -> "$n people" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmClear = true }, enabled = n > 0) { Text("Clear all") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEach { name ->
                            InputChip(
                                selected = false,
                                onClick = { setNames(names - name) },
                                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $name", Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }

            item("mode") {
                Column(column, horizontalAlignment = Alignment.CenterHorizontally) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Number of teams", "People per team").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = bySize == (i == 1),
                                onClick = { bySize = i == 1 },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val value = if (bySize) shownPerTeam else shownTeamCount
                        val (lo, hi) = if (bySize) 1 to max(1, n - 1) else 2 to max(2, n)
                        FilledTonalIconButton(
                            onClick = { if (bySize) perTeam = value - 1 else teamCount = value - 1 },
                            enabled = value > lo,
                        ) { Icon(Icons.Filled.Remove, contentDescription = if (bySize) "Fewer per team" else "Fewer teams") }
                        Text(
                            if (bySize) "$value per team" else "$value teams",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.widthIn(min = 120.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        FilledTonalIconButton(
                            onClick = { if (bySize) perTeam = value + 1 else teamCount = value + 1 },
                            enabled = value < hi,
                        ) { Icon(Icons.Filled.Add, contentDescription = if (bySize) "More per team" else "More teams") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (n < 2) "Add at least two names" else sizeSummary(n, splitInto),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = ::split, enabled = n >= 2) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(if (teams.isEmpty()) "Split into teams" else "Reshuffle")
                    }
                    if (teams.isNotEmpty() && shake.value) {
                        Spacer(Modifier.height(4.dp))
                        Text("…or shake to reshuffle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item("teams") {
                AnimatedContent(
                    targetState = deal to teams,
                    transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.94f)) togetherWith fadeOut(tween(120)) },
                    contentKey = { it.first },
                    label = "teams",
                    modifier = column,
                ) { (_, dealt) ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        dealt.withIndex().chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { (i, team) -> TeamCard(i, team, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all names?") },
            text = { Text("This removes all $n people from the list.") },
            confirmButton = { TextButton(onClick = { setNames(emptyList()); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun sizeSummary(people: Int, teams: Int): String {
    val small = people / teams
    val big = (people + teams - 1) / teams
    val sizes = if (small == big) "$small" else "$small–$big"
    return "$teams teams of $sizes"
}

@Composable
private fun TeamCard(index: Int, members: List<String>, modifier: Modifier) {
    val accent = GoogleColors.all[index % GoogleColors.all.size]
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("Team ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${members.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            members.forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
