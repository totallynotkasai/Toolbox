package com.toolbox.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A tool's last [CAP] results, newest first, saved as one string in Prefs. Each result is a list of text fields,
 * which the tool reads back into its own entry type. Pure, for the unit tests.
 *
 * Results are one per line and fields are split by commas. A backslash escapes a backslash, a comma or a line break
 * ("\n"), so any text round-trips, such as a Spinner option with a comma in it. A lone result of one empty field
 * would save as "", which reads back as no results, so no tool saves one.
 */
object ResultHistory {
    const val CAP = 20

    /** [entries] with [entry] in front, cut to [CAP]. A repeat is kept: rolling the same total twice is two results. */
    fun add(entries: List<List<String>>, entry: List<String>): List<List<String>> = (listOf(entry) + entries).take(CAP)

    /** Undoing a clear: the results from before it go back under any added since, cut to [CAP]. */
    fun restore(current: List<List<String>>, cleared: List<List<String>>): List<List<String>> = (current + cleared).take(CAP)

    fun encode(entries: List<List<String>>): String =
        entries.joinToString("\n") { fields -> fields.joinToString(",", transform = ::escape) }

    fun decode(text: String): List<List<String>> {
        if (text.isEmpty()) return emptyList()
        val entries = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var i = 0
        while (i < text.length) {
            when (val c = text[i]) {
                '\\' -> {
                    i++
                    // A lone backslash at the very end has nothing to escape, and is dropped.
                    if (i < text.length) field.append(if (text[i] == 'n') '\n' else text[i])
                }
                ',' -> { fields += field.toString(); field.clear() }
                '\n' -> { fields += field.toString(); field.clear(); entries += fields; fields = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        fields += field.toString()
        entries += fields
        return entries.take(CAP)
    }

    private fun escape(s: String) = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            ',' -> append("\\,")
            '\n' -> append("\\n")
            else -> append(c)
        }
    }
}

/** What TalkBack says for a strip of [shown] results, oldest first. */
fun stripDescription(shown: List<String>): String =
    if (shown.isEmpty()) "No results yet" else "Last ${shown.size}, oldest first: ${shown.joinToString(", ")}"

/** How many results a strip holds. Fewer show when they don't fit across. */
const val STRIP_MAX = 10

/**
 * A tool's saved results under [key] in Prefs, with whether its full list is open, and Clear with an Undo on
 * [snackbar]. [entries] are newest first, each as the fields the tool saved.
 */
@Stable
class ResultLog internal constructor(
    private val stored: MutableState<String>,
    private val opened: MutableState<Boolean>,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
) {
    val entries: List<List<String>> get() = ResultHistory.decode(stored.value)

    var open: Boolean
        get() = opened.value
        set(v) { opened.value = v }

    fun add(entry: List<String>) {
        stored.value = ResultHistory.encode(ResultHistory.add(entries, entry))
    }

    fun clear() {
        val before = entries
        stored.value = ""
        open = false
        scope.offerUndo(snackbar, "History cleared") { stored.value = ResultHistory.encode(ResultHistory.restore(entries, before)) }
    }
}

@Composable
fun rememberResultLog(key: String, snackbar: SnackbarHostState): ResultLog {
    val stored = rememberPref(key, "")
    val opened = rememberSaveable(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return remember(stored, opened, scope, snackbar) { ResultLog(stored, opened, scope, snackbar) }
}

/** Shows [message] with an Undo button on [snackbar], replacing any snackbar already showing, and runs [onUndo] if it's pressed. */
fun CoroutineScope.offerUndo(snackbar: SnackbarHostState, message: String, onUndo: () -> Unit) {
    launch {
        snackbar.currentSnackbarData?.dismiss()
        if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) onUndo()
    }
}

/**
 * The frame every result strip shares: a rounded bar with a history icon, which opens the full list. TalkBack reads
 * the whole strip as [description]. While [enabled] is false (nothing saved yet) it can't be opened.
 */
@Composable
fun StripFrame(
    description: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onOpen,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            if (enabled) onClick(label = "Show all results") { onOpen(); true }
        },
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(start = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            content()
        }
    }
}

/**
 * A strip of recent results: [shown] is oldest first, so the newest is on the right, a little bigger, like the Coin's.
 * Only as many as fit across are drawn, dropping the oldest. TalkBack reads [spoken], which can give in full what
 * the strip shortens. Tapping it calls [onOpen].
 */
@Composable
fun ResultStrip(shown: List<String>, onOpen: () -> Unit, modifier: Modifier = Modifier, spoken: List<String> = shown) {
    StripFrame(stripDescription(spoken), enabled = shown.isNotEmpty(), onOpen = onOpen, modifier = modifier.widthIn(max = 360.dp)) {
        if (shown.isEmpty()) {
            Text("History appears here", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            return@StripFrame
        }
        FitFromEnd(Modifier.weight(1f, fill = false)) {
            shown.forEachIndexed { i, text ->
                val newest = i == shown.lastIndex
                Text(
                    text,
                    style = if (newest) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (newest) FontWeight.Bold else null,
                    color = if (newest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Lays its children out in a row, keeping as many of the last ones as fit whole and dropping the earlier ones. */
@Composable
private fun FitFromEnd(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = 14.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        var width = 0
        var first = placeables.size
        for (i in placeables.indices.reversed()) {
            val next = width + placeables[i].width + if (first < placeables.size) gap else 0
            if (next > constraints.maxWidth && first < placeables.size) break
            width = next
            first = i
        }
        val kept = placeables.drop(first)
        val height = kept.maxOfOrNull { it.height } ?: 0
        layout(width.coerceAtMost(constraints.maxWidth), height) {
            var x = 0
            kept.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2) // baselines differ by size, so centre them on the strip
                x += p.width + gap
            }
        }
    }
}

/** One entry in the full list: its [text], what TalkBack says for it, and an optional picture beside it. */
class ResultRow(val text: String, val spoken: String = text, val icon: (@Composable () -> Unit)? = null)

/** The full list of saved results, newest first, with Clear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultHistorySheet(rows: List<ResultRow>, onClear: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onClear, enabled = rows.isNotEmpty()) { Text("Clear") }
        }
        if (rows.isEmpty()) {
            Text(
                "Results show here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            )
            return@ModalBottomSheet
        }
        Text(
            "Newest first",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
            itemsIndexed(rows) { i, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clearAndSetSemantics { contentDescription = row.spoken }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.icon?.let { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { it() } }
                    Text(
                        row.text,
                        style = if (i == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
