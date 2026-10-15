package com.toolbox.app.games.charades

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.LocalToolRoute
import com.toolbox.app.ui.common.ToolScaffold

/**
 * Makes a new deck ([deck] null) or changes one: its name, and its cards typed one per line. Going back keeps the
 * changes, as the Spinner's options do; [onDone] gets the name and text as typed, to be cleaned when saved.
 */
@Composable
internal fun DeckEditor(deck: Deck?, onDone: (name: String, text: String) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(deck?.name ?: "") }
    var text by rememberSaveable { mutableStateOf(deck?.cards?.joinToString("\n") ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val done = { onDone(name, text) }
    BackHandler(onBack = done)

    // The editor isn't a tool of its own, so it has no favourite star.
    CompositionLocalProvider(LocalToolRoute provides null) {
        ToolScaffold(
            title = if (deck == null) "New deck" else "Edit deck",
            onBack = done,
            actions = {
                if (deck != null) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete deck") }
                }
                TextButton(onClick = done) { Text("Done") }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.replace('\n', ' ').take(MAX_NAME_LENGTH) },
                        label = { Text("Name") },
                        placeholder = { Text("My deck") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Cards, one per line") },
                        supportingText = { Text(cardsNote(text)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }

    if (confirmDelete && deck != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${deck.name}?") },
            text = { Text("Its cards will be gone for good.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Under the cards: how many there are, and what saving will leave out or cut. */
internal fun cardsNote(text: String): String {
    val n = parseCards(text).size
    val repeats = countRepeats(text)
    val long = text.lineSequence().any { cleanLine(it).length > MAX_CARD_LENGTH }
    return buildList {
        add(if (n >= MAX_CARDS) "$MAX_CARDS cards, the most a deck holds" else cardCount(n))
        if (repeats > 0) add(if (repeats == 1) "1 repeat left out" else "$repeats repeats left out")
        if (long) add("cards cut to $MAX_CARD_LENGTH characters")
    }.joinToString(" · ")
}
