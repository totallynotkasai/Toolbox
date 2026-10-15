package com.toolbox.app.games.charades

import android.hardware.Sensor
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.toolbox.app.ui.common.HideSystemBars
import com.toolbox.app.ui.common.KeepScreenOn
import com.toolbox.app.ui.common.LandscapeLock
import com.toolbox.app.ui.common.Prefs
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberSensorListener
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.common.toDisplayAxes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the game is: picking or editing a deck, upright, or one of a round's steps, which are played sideways. */
internal enum class Stage(val sideways: Boolean) {
    Setup(false),
    Editor(false),
    Ready(true),
    CountIn(true),
    Playing(true),
    Paused(true),
    Results(true),
}

private const val DECKS_KEY = "charades_decks"
private const val DECK_KEY = "charades_deck"
private const val SECONDS_KEY = "charades_seconds"

/** The cards a deck has played since it last ran out, kept between visits so the next game starts with fresh ones. */
private fun playedKey(deckId: String) = "charades_played_$deckId"

/** What [DeckEditor] is open on when it's making a new deck. */
private const val NEW_DECK = ""

/** Held upright this long (ms), the round counts itself in. */
private const val UPRIGHT_MS = 800L

/** How long (ms) the screen flashes green or orange after a card. */
internal const val FLASH_MS = 700L

private val RoundSaver = Saver<CharadesRound, ArrayList<String>>(save = { it.save() }, restore = { CharadesRound.restore(it) })

/** Smooths an accelerometer, for phones without a gravity sensor, so a jolt doesn't read as a tilt. */
private class LowPass(private val alpha: Float) {
    private val value = FloatArray(3)
    private var primed = false

    fun filter(reading: FloatArray): FloatArray {
        if (!primed) reading.copyInto(value, endIndex = 3) else for (i in 0..2) value[i] += alpha * (reading[i] - value[i])
        primed = true
        return value
    }
}

@Composable
fun CharadesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    val prefs = remember(context) { Prefs(context.applicationContext) }
    val haptics = rememberHaptics()
    val synth = rememberToneSynth()
    val say = rememberAnnouncer()
    val scope = rememberCoroutineScope()

    val builtIn = remember(resources) { BuiltInDeck.entries.map { it.load(resources) } }
    var storedDecks by rememberPref(DECKS_KEY, "")
    val custom = remember(storedDecks) { CustomDecks.decode(storedDecks) }
    var selectedId by rememberPref(DECK_KEY, BuiltInDeck.Animals.id)
    val selected = (builtIn + custom).firstOrNull { it.id == selectedId } ?: builtIn.first()
    var savedSeconds by rememberPref(SECONDS_KEY, 60)
    val seconds = savedSeconds.takeIf { it in CharadesRound.LENGTHS } ?: 60

    var stage by rememberSaveable { mutableStateOf(Stage.Setup) }
    var round by rememberSaveable(stateSaver = RoundSaver) { mutableStateOf(CharadesRound(selected.id, seconds)) }
    var editing by rememberSaveable { mutableStateOf(NEW_DECK) }
    var count by rememberSaveable { mutableIntStateOf(3) }
    // Got it (true) or passed, while the screen flashes after a card.
    var flash by remember { mutableStateOf<Boolean?>(null) }
    val roundDeck = (builtIn + custom).firstOrNull { it.id == round.deckId } ?: selected

    /** The round deck's next card, kept clear of the cards played lately; the played ones are saved as it goes. */
    fun deal(): String? {
        val key = playedKey(roundDeck.id)
        val (card, played) = dealCard(roundDeck.cards, CustomDecks.decodePlayed(prefs.get(key, "")), round.shown) ?: return null
        prefs.put(key, CustomDecks.encodePlayed(played))
        return card
    }

    fun newRound(deck: Deck, length: Int) {
        round = CharadesRound(deck.id, length)
        flash = null
        stage = Stage.Ready
    }

    fun endRound() {
        round = round.end()
        flash = null
        stage = Stage.Results
    }

    fun pause() {
        flash = null
        stage = if (round.started) Stage.Paused else Stage.Ready
    }

    fun answer(got: Boolean) {
        if (stage != Stage.Playing || flash != null || round.current == null) return
        round = round.answer(got, deal())
        flash = got
        if (got) {
            synth.play(784f, 90, 0.3f)
            scope.launch { delay(90); synth.play(1175f, 200, 0.3f) }
            haptics.heavy()
        } else {
            synth.play(392f, 80, 0.25f)
            scope.launch { delay(90); synth.play(294f, 180, 0.25f) }
            haptics.click()
        }
        say(if (got) "Got it" else "Passed")
        if (round.current == null) endRound()
    }

    // The tilt is read from gravity, or a smoothed accelerometer on phones without one; taps always work as well.
    val tilt = remember { TiltDetector() }
    val smoothing = remember { LowPass(0.15f) }
    var upright by remember { mutableStateOf(false) }
    val sensing = stage == Stage.Ready || stage == Stage.CountIn || stage == Stage.Playing
    val hasSensor = rememberSensorListener(Sensor.TYPE_GRAVITY, fallbackType = Sensor.TYPE_ACCELEROMETER, enabled = sensing) { e ->
        val v = if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) smoothing.filter(e.values) else e.values
        val (x, y) = toDisplayAxes(v[0], v[1], view.display?.rotation ?: Surface.ROTATION_0)
        upright = TiltDetector.heldUpright(x, y, v[2])
        // While the screen flashes the next tilt waits, so it's only read once the phone has come back level after.
        if (flash != null) return@rememberSensorListener
        val tipped = tilt.update(v[0], v[1], v[2])
        if (tipped != null && stage == Stage.Playing) answer(tipped == Tilt.Down)
    }

    // Held upright and still for a moment, the round counts itself in.
    LaunchedEffect(stage, upright) {
        if (stage != Stage.Ready || !upright) return@LaunchedEffect
        delay(UPRIGHT_MS)
        stage = Stage.CountIn
    }
    LaunchedEffect(stage) {
        if (stage != Stage.CountIn) return@LaunchedEffect
        for (n in 3 downTo 1) {
            count = n
            synth.play(660f, 120, 0.3f)
            haptics.tick()
            delay(1000)
        }
        synth.play(1320f, 260, 0.3f)
        haptics.click()
        if (round.current == null) round = round.copy(current = deal())
        if (round.current == null) endRound() else stage = Stage.Playing
    }
    LaunchedEffect(flash) {
        if (flash == null) return@LaunchedEffect
        delay(FLASH_MS)
        flash = null
    }

    // The clock only runs while the round is on screen; leaving the app pauses it, to carry on after a count-in.
    val carry = remember { floatArrayOf(0f) } // fractions of a millisecond the frames haven't handed on yet
    rememberGameLoop(enabled = stage == Stage.Playing, maxDt = 0.25f) { dt ->
        carry[0] += dt * 1000f
        val ms = carry[0].toLong()
        carry[0] -= ms
        val (next, events) = round.advance(ms)
        round = next
        for (event in events) when (event) {
            is ClockEvent.Tick -> {
                synth.tick()
                haptics.tick()
            }
            ClockEvent.TimeUp -> {
                synth.buzz(600)
                haptics.heavy()
                flash = null
                stage = Stage.Results
            }
        }
        stage == Stage.Playing
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (stage == Stage.Playing || stage == Stage.CountIn) pause()
    }

    BackHandler(enabled = stage.sideways) {
        when (stage) {
            Stage.Ready, Stage.CountIn -> if (round.started) stage = Stage.Paused else stage = Stage.Setup
            Stage.Playing -> pause()
            Stage.Paused -> endRound()
            else -> stage = Stage.Setup
        }
    }

    when {
        stage.sideways -> {
            LandscapeLock()
            HideSystemBars()
            KeepScreenOn()
            RoundStage(
                stage = stage,
                round = round,
                deckName = roundDeck.name,
                count = count,
                flash = flash,
                tiltWorks = hasSensor,
                onStart = { if (stage == Stage.Ready) stage = Stage.CountIn },
                onAnswer = ::answer,
                onPause = ::pause,
                onClose = { if (round.started) pause() else stage = Stage.Setup },
                onResume = { stage = Stage.Ready },
                onEnd = ::endRound,
                onPlayAgain = { newRound(roundDeck, round.seconds) },
                onChangeDeck = { stage = Stage.Setup },
            )
        }
        stage == Stage.Editor -> {
            val deck = custom.firstOrNull { it.id == editing }
            DeckEditor(
                deck = deck,
                onDone = { name, text ->
                    val cards = parseCards(text)
                    // A new deck left empty isn't kept.
                    if (deck != null || cleanLine(name).isNotEmpty() || cards.isNotEmpty()) {
                        val others = (builtIn + custom).filter { it.id != deck?.id }.map { it.name }
                        val saved = Deck(deck?.id ?: CustomDecks.newId(custom), CustomDecks.uniqueName(name, others), cards)
                        storedDecks = CustomDecks.encode(if (deck == null) custom + saved else custom.map { if (it.id == saved.id) saved else it })
                        if (deck == null) selectedId = saved.id
                    }
                    stage = Stage.Setup
                },
                onDelete = {
                    if (deck != null) {
                        storedDecks = CustomDecks.encode(custom.filter { it.id != deck.id })
                        prefs.remove(playedKey(deck.id))
                        if (selectedId == deck.id) selectedId = builtIn.first().id
                    }
                    stage = Stage.Setup
                },
            )
        }
        else -> SetupScreen(
            builtIn = builtIn,
            custom = custom,
            selected = selected,
            seconds = seconds,
            tiltWorks = hasSensor,
            onSelect = { selectedId = it.id },
            onSeconds = { savedSeconds = it },
            onEdit = { editing = it.id; stage = Stage.Editor },
            onNew = { editing = NEW_DECK; stage = Stage.Editor },
            onStart = { newRound(selected, seconds) },
            onBack = onBack,
        )
    }
}

internal fun cardCount(n: Int) = when (n) {
    0 -> "No cards yet"
    1 -> "1 card"
    else -> "$n cards"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    builtIn: List<Deck>,
    custom: List<Deck>,
    selected: Deck,
    seconds: Int,
    tiltWorks: Boolean,
    onSelect: (Deck) -> Unit,
    onSeconds: (Int) -> Unit,
    onEdit: (Deck) -> Unit,
    onNew: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    ToolScaffold(title = "Charades", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            val column = Modifier.widthIn(max = 560.dp).fillMaxWidth()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().selectableGroup(),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item("decks") { Heading("Decks", column) }
                items(builtIn, key = { it.id }) { DeckRow(it, it.id == selected.id, column, onSelect, onEdit = null) }
                item("yours") { Heading("Your decks", column.padding(top = 8.dp)) }
                if (custom.isEmpty()) {
                    item("none") {
                        Text(
                            "Make your own, with one card per line.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = column.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                items(custom, key = { it.id }) { DeckRow(it, it.id == selected.id, column, onSelect, onEdit) }
                item("new") {
                    Row(column.padding(horizontal = 4.dp)) {
                        TextButton(onClick = onNew, enabled = custom.size < MAX_DECKS) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (custom.size < MAX_DECKS) "New deck" else "Up to $MAX_DECKS decks")
                        }
                    }
                }
            }
            HorizontalDivider()
            Column(column.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CharadesRound.LENGTHS.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = seconds == s,
                            onClick = { onSeconds(s) },
                            shape = SegmentedButtonDefaults.itemShape(i, CharadesRound.LENGTHS.size),
                        ) { Text("$s seconds", textAlign = TextAlign.Center) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onStart, enabled = selected.cards.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (selected.cards.isEmpty()) "Add cards to ${selected.name} first" else "Start")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "One player holds the phone to their forehead, screen out, and the others give clues. " +
                        (if (tiltWorks) "Tilt it down if you got it, or up to pass, or tap " else "Tap ") +
                        "the right of the screen for got it and the left to pass.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
    )
}

@Composable
private fun DeckRow(deck: Deck, selected: Boolean, modifier: Modifier, onSelect: (Deck) -> Unit, onEdit: ((Deck) -> Unit)?) {
    Row(
        modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(deck) })
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(deck.name, style = MaterialTheme.typography.bodyLarge)
            Text(cardCount(deck.cards.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onEdit != null) {
            IconButton(onClick = { onEdit(deck) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${deck.name}") }
        }
    }
}
