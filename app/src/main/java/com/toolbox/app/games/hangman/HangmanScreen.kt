package com.toolbox.app.games.hangman

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.toolbox.app.R
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameMode
import com.toolbox.app.ui.common.GameModeChips
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.GameOverCard
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ScoresMenu
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.theme.GoogleColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val KEY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

private val CARROT = Color(0xFFF57C00)
private val COAL = Color(0xFF263238)
private val STICK = Color(0xFF6D4C41)
private val SCARF = Color(0xFFD93025)
private val HAT = Color(0xFF212121)
private val WATER = Color(0xFF7FC4EE)
private val SUN = Color(0xFFFFD54F)

@Composable
fun HangmanScreen(onBack: () -> Unit) {
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()
    val guard = rememberGameGuard()
    val words = remember(resources) {
        HangmanState.parseWords(resources.openRawResource(R.raw.words).bufferedReader().use { it.readText() })
    }
    var twoPlayer by rememberPref("hangman_two_player", false)
    val mode = if (twoPlayer) GameMode.TwoPlayer else GameMode.Solo
    // The solo word and its guesses are saved, so leaving and coming back brings the same word back. Otherwise a
    // hard word could be swapped for a fresh one, streak intact, just by leaving the screen.
    var savedWord by rememberPref("hangman_word", "")
    var soloGuessed by rememberPref("hangman_guessed", "")
    val firstWord = remember { HangmanState.pick(words) }
    val soloWord = savedWord.takeIf(HangmanState::isWord) ?: firstWord
    LaunchedEffect(soloWord) { if (savedWord != soloWord) { savedWord = soloWord; soloGuessed = "" } }
    // Two players: null while the setter is choosing a word.
    var duoWord by rememberSaveable { mutableStateOf<String?>(null) }
    var duoGuessed by rememberSaveable { mutableStateOf("") }
    var streak by rememberPref("hangman_streak", 0)
    val (best, submitBest) = rememberBestScore("hangman-streak")
    // Rounds won by whoever was guessing (side a) and whoever set the word (side b).
    val duoTally = rememberTally("hangman_tally_TwoPlayer")

    /** The round as it stands now, read from the saved state rather than the last composition. */
    fun current(): HangmanState? = if (twoPlayer) duoWord?.let { HangmanState(it, duoGuessed.toSet()) } else HangmanState(soloWord, soloGuessed.toSet())
    val state = current()

    fun guess(c: Char) {
        val now = current() ?: return
        val next = now.guess(c)
        if (next === now) return
        val letters = next.guessed.joinToString("")
        if (twoPlayer) duoGuessed = letters else soloGuessed = letters
        say(snowmanGuessText(next, c))
        when {
            next.won -> { haptics.heavy(); if (twoPlayer) duoTally.record(1) else { streak += 1; submitBest(streak) } }
            next.lost -> { haptics.heavy(); if (twoPlayer) duoTally.record(2) else streak = 0 }
            c.lowercaseChar() in next.word -> haptics.tick()
            else -> haptics.click()
        }
    }

    val started = state != null && !state.over && state.guessed.isNotEmpty()

    /** Skipping a solo word you've started counts as a loss, or the streak could be farmed by skipping hard words. */
    fun abandonSolo() {
        if (!twoPlayer && started) streak = 0
    }

    fun newSoloWord() {
        savedWord = HangmanState.pick(words, previous = soloWord)
        soloGuessed = ""
    }

    fun newWord() {
        abandonSolo()
        if (twoPlayer) { duoWord = null; duoGuessed = "" } else newSoloWord()
    }

    /** The solo word is saved, so it waits for you while you play two-player rounds. */
    fun setMode(m: GameMode) {
        twoPlayer = m == GameMode.TwoPlayer
        duoWord = null
        duoGuessed = ""
    }

    /** Asks first when leaving the round would cost something: a streak, or a two-player word in progress. */
    fun guarded(action: () -> Unit) = when {
        twoPlayer -> guard.run(started, title = "Start a new word?", text = "The word in progress will be lost.", confirm = "New word", action = action)
        else -> guard.run(
            started && streak > 0,
            title = "Skip this word?",
            text = "Skipping counts as a miss, so your streak of $streak will end.",
            confirm = "Skip",
            action = action,
        )
    }

    val focus = remember { FocusRequester() }
    val word = state?.word
    LaunchedEffect(word) { if (word != null) focus.requestFocus() }

    ToolScaffold(
        title = "Snowman",
        onBack = onBack,
        actions = {
            if (state != null) NewGameButton(onClick = { guarded(::newWord) }, description = "New word")
            if (twoPlayer) ScoresMenu(onReset = duoTally::reset)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e ->
                    if (state == null || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val ch = e.utf16CodePoint.toChar().lowercaseChar()
                    when {
                        ch in 'a'..'z' -> { guess(ch); true }
                        state.over && (e.key == Key.Enter || e.key == Key.Spacebar) -> { newWord(); true }
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GameModeChips(mode, onSelect = { m -> if (twoPlayer) guarded { setMode(m) } else setMode(m) })
                Spacer(Modifier.height(12.dp))
                if (state == null) {
                    WordEntry(onStart = { duoWord = it; duoGuessed = ""; haptics.click() })
                    return@Column
                }
                Text(
                    if (twoPlayer) duoTally.value.let { "Guesser ${it.a}  ·  Setter ${it.b}" }
                    else "Streak $streak" + if (best > 0) "  ·  Best $best" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SnowmanScene(
                    wrong = state.wrong,
                    won = state.won,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.35f),
                )
                Spacer(Modifier.height(8.dp))
                LivesRow(state.wrongLeft, state.maxWrong)
                Spacer(Modifier.height(16.dp))
                WordRow(state)
                Spacer(Modifier.height(12.dp))
                if (state.over) {
                    val swap = "Swap roles and pass the phone"
                    GameOverCard(
                        when {
                            state.won -> GameOver(
                                if (twoPlayer) "The guesser saved the snowman!" else "You saved the snowman!",
                                if (twoPlayer) swap else null,
                            )
                            else -> GameOver(
                                "Melted!",
                                "The word was ${state.word.uppercase()}" + if (twoPlayer) ". $swap" else "",
                            )
                        },
                        onAction = ::newWord,
                        action = if (twoPlayer) "Next word" else "New word",
                    )
                } else {
                    Text(
                        if (state.guessed.isEmpty()) "Guess a letter before the sun gets too high" else " ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    // The keys' touch areas reach 4dp past the keys, so this looks 12dp.
                    Spacer(Modifier.height(8.dp))
                    Keyboard(state, onKey = ::guess)
                }
            }
        }
    }
    GameGuardDialog(guard)
}

/** Two-player setup: the setter types a secret word (hidden by default) while the guesser looks away. */
@Composable
private fun WordEntry(onStart: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    val ready = HangmanState.customWord(text)
    val entryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { entryFocus.requestFocus() }

    Icon(
        Icons.Filled.AcUnit, contentDescription = null, tint = GoogleColors.Blue,
        modifier = Modifier.padding(top = 16.dp).size(48.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text("Choose a secret word", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "Make sure the guesser can't see, then hand them the phone",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = HangmanState.filterCustomInput(it) },
        label = { Text("Secret word") },
        supportingText = {
            val range = HangmanState.CUSTOM_LENGTH
            Text("Letters only, ${range.first} to ${range.last} long")
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide word" else "Show word",
                )
            }
        },
        // Password keyboards don't learn or suggest the word, so it won't pop up for the guesser later.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { ready?.let(onStart) }),
        modifier = Modifier.fillMaxWidth().focusRequester(entryFocus),
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { ready?.let(onStart) }, enabled = ready != null, modifier = Modifier.height(52.dp)) {
        Text("Start guessing")
    }
}

@Composable
private fun LivesRow(left: Int, max: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$left wrong guesses left" },
    ) {
        repeat(max) { i ->
            Icon(
                Icons.Filled.AcUnit,
                contentDescription = null,
                tint = if (i < left) GoogleColors.Blue else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp).padding(horizontal = 1.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (left == 1) "1 guess left" else "$left guesses left",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The word as TalkBack reads it: "C, blank, T". */
internal fun spokenWord(state: HangmanState): String =
    state.word.mapIndexed { i, c -> if (state.isRevealed(i)) c.uppercase() else "blank" }.joinToString(", ")

/**
 * What TalkBack says after [letter] is guessed, given the round as it now stands: "C is in the word. C, blank, T.
 * 5 guesses left", or "Q is not in the word. 4 guesses left". Once the round is over the result card says the rest.
 */
internal fun snowmanGuessText(state: HangmanState, letter: Char): String {
    val hit = letter.lowercaseChar() in state.word
    val result = "${letter.uppercaseChar()} is ${if (hit) "" else "not "}in the word"
    if (state.over) return result
    val left = if (state.wrongLeft == 1) "1 guess left" else "${state.wrongLeft} guesses left"
    return if (hit) "$result. ${spokenWord(state)}. $left" else "$result. $left"
}

@Composable
private fun WordRow(state: HangmanState) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = 6.dp
        val n = state.word.length
        val cell = min(40.dp, (maxWidth - gap * (n - 1)) / n)
        // Sized to the cell, which doesn't grow with the font size, so the letters mustn't either.
        val letterSize = with(LocalDensity.current) { (cell * 0.7f).toSp() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Word: " + spokenWord(state) },
        ) {
            state.word.forEachIndexed { i, c ->
                val revealed = state.isRevealed(i)
                val show = revealed || state.lost
                Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (show) c.uppercase() else " ",
                        fontSize = letterSize,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            !revealed -> MaterialTheme.colorScheme.error
                            state.won -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                    )
                }
            }
        }
    }
}

@Composable
private fun Keyboard(state: HangmanState, onKey: (Char) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Each key's touch area is a tenth of the width, so neighbours meet with no dead space between them; the gaps
        // are drawn inside the keys instead.
        val slot = maxWidth / 10
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            KEY_ROWS.forEach { row ->
                Row {
                    row.forEach { c -> Key(c, state.status(c), slot, onKey) }
                }
            }
        }
    }
}

@Composable
private fun Key(c: Char, status: LetterStatus, width: Dp, onKey: (Char) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        LetterStatus.Unused -> scheme.surfaceContainerHighest to scheme.onSurface
        // White is too faint on Google green in either theme, so a hit key is the darker green.
        LetterStatus.Hit -> GoogleColors.GreenInk to Color.White
        LetterStatus.Miss -> scheme.surfaceContainerHighest.copy(alpha = 0.45f) to scheme.onSurface.copy(alpha = 0.35f)
    }
    val interaction = remember { MutableInteractionSource() }
    val style = MaterialTheme.typography.titleMedium
    Box(
        Modifier
            .width(width)
            .height(60.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = status == LetterStatus.Unused, role = Role.Button) { onKey(c) }
            .semantics {
                contentDescription = "Letter ${c.uppercase()}"
                stateDescription = when (status) {
                    LetterStatus.Unused -> "Not guessed"
                    LetterStatus.Hit -> "In the word"
                    LetterStatus.Miss -> "Not in the word"
                }
            }
            // The key as drawn: 52dp tall with 5dp between neighbours, as before the touch areas grew.
            .padding(horizontal = 2.5.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .indication(interaction, ripple()),
        contentAlignment = Alignment.Center,
    ) {
        // Shrinks at large font sizes rather than being cut off by the narrow key.
        Text(
            c.uppercase(),
            color = fg,
            style = style,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
        )
    }
}

// ---- The melting snowman ----

/**
 * [wrong] guesses melt the snowman in stages: hat, left arm, right arm, scarf, buttons, head, middle, bottom, leaving a puddle
 * with the carrot and coal. The sun climbs as it melts. [won] refreezes it and adds sparkles.
 */
@Composable
private fun SnowmanScene(wrong: Int, won: Boolean, modifier: Modifier) {
    val melt by animateFloatAsState(
        targetValue = if (won) 0f else wrong.toFloat(),
        animationSpec = tween(if (won) 1400 else 900, easing = FastOutSlowInEasing),
        label = "melt",
    )
    // A fresh set of drips falls every time a wrong guess lands.
    val drip = remember { Animatable(1f) }
    LaunchedEffect(wrong) {
        if (wrong > 0 && !won) {
            drip.snapTo(0f)
            drip.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    val sparkle by animateFloatAsState(if (won) 1f else 0f, tween(600), label = "sparkle")
    val twinkle by rememberInfiniteTransition(label = "twinkle")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "t")

    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.3f
    val skyTop = if (dark) Color(0xFF10223A) else Color(0xFF9ED3F7)
    val skyBottom = if (dark) Color(0xFF2B4466) else Color(0xFFE6F4FE)
    val warm = if (dark) Color(0xFF5B3A4A) else Color(0xFFFFE0B2)
    val snow = if (dark) Color(0xFFDCE6F2) else Color(0xFFFFFFFF)
    val snowShade = if (dark) Color(0xFFAFC0D4) else Color(0xFFD5E6F5)

    Canvas(
        modifier.semantics {
            contentDescription = when {
                won -> "The snowman is frozen solid again"
                wrong >= HangmanState.MAX_WRONG -> "The snowman has melted into a puddle"
                wrong == 0 -> "A complete snowman"
                else -> "The snowman is melting: $wrong of ${HangmanState.MAX_WRONG} pieces gone"
            }
        },
    ) {
        val w = size.width
        val h = size.height
        val m = melt / HangmanState.MAX_WRONG // 0..1
        val corner = 24.dp.toPx()

        // Sky warms up as the sun climbs.
        drawRoundRect(
            Brush.verticalGradient(listOf(skyTop, lerp(skyBottom, warm, m * 0.8f))),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
        )

        // Sun.
        val sunC = Offset(w * 0.83f, lerpF(h * 0.62f, h * 0.16f, m))
        val sunR = h * (0.07f + 0.035f * m)
        drawCircle(Brush.radialGradient(listOf(SUN.copy(alpha = 0.55f), Color.Transparent), sunC, sunR * 2.4f), sunR * 2.4f, sunC)
        rotate(twinkle * 45f, sunC) {
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4
                drawLine(
                    SUN, sunC + polar(a, sunR * 1.25f), sunC + polar(a, sunR * 1.6f),
                    strokeWidth = sunR * 0.14f, cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(SUN, sunR, sunC)

        // Snowy ground with a gentle hump.
        val groundY = h * 0.8f
        val ground = Path().apply {
            moveTo(0f, groundY + h * 0.03f)
            quadraticTo(w * 0.45f, groundY - h * 0.04f, w, groundY + h * 0.02f)
            lineTo(w, h - corner); quadraticTo(w, h, w - corner, h)
            lineTo(corner, h); quadraticTo(0f, h, 0f, h - corner)
            close()
        }
        drawPath(ground, snow)

        val cx = w * 0.42f
        val baseY = groundY - h * 0.01f

        // Puddle grows as the snow turns to water.
        val puddleW = w * (0.06f + 0.36f * m)
        val puddleH = puddleW * 0.2f
        if (m > 0f) {
            drawOval(WATER.copy(alpha = 0.85f), Offset(cx - puddleW / 2, baseY - puddleH / 2), Size(puddleW, puddleH))
            drawOval(Color.White.copy(alpha = 0.45f), Offset(cx - puddleW * 0.3f, baseY - puddleH * 0.3f), Size(puddleW * 0.28f, puddleH * 0.25f))
        }

        // Each piece fades out over its own stage: hat 0→1, left arm 1→2, right arm 2→3, scarf 3→4, buttons 4→5,
        // head 5→6, middle 6→7, bottom 7→8.
        fun stage(k: Int) = (k - melt).coerceIn(0f, 1f)
        val fHat = stage(1); val fArmL = stage(2); val fArmR = stage(3); val fScarf = stage(4)
        val fButtons = stage(5); val fHead = stage(6); val fMid = stage(7); val fBottom = stage(8)
        val sag = 1f - 0.3f * m

        val rb = h * 0.155f * sag * (0.2f + 0.8f * fBottom)
        val rm = h * 0.112f * sag * (0.2f + 0.8f * fMid)
        val rh = h * 0.082f * sag * (0.2f + 0.8f * fHead)
        val squash = 1f - 0.18f * m // the balls slump a little as they melt
        val yb = baseY - rb * 0.85f * squash
        val ym = yb - rb * 0.9f - rm * 0.75f
        val yh = ym - rm * 0.85f - rh * 0.8f

        // [f] is the piece's stage progress: it shrinks while staying fairly opaque, then fades at the end.
        fun snowball(c: Offset, r: Float, f: Float) {
            if (f <= 0f) return
            val alpha = 0.3f + 0.7f * f
            drawOval(
                Brush.radialGradient(listOf(snow, snow, snowShade), c - Offset(r * 0.35f, r * 0.35f), r * 1.6f),
                topLeft = Offset(c.x - r, c.y - r * squash), size = Size(r * 2, r * 2 * squash), alpha = alpha,
            )
        }

        // Arms go in behind the middle ball.
        if (fMid > 0f) {
            for (side in listOf(-1f, 1f)) {
                val fArms = if (side < 0) fArmL else fArmR
                if (fArms <= 0f) continue
                val droop = (1f - fArms) * 0.9f
                val a = (if (side < 0) PI.toFloat() + 0.45f - droop else -0.45f + droop)
                val start = Offset(cx + side * rm * 0.7f, ym - rm * 0.1f)
                val end = start + polar(a, rm * 1.35f)
                drawLine(STICK.copy(alpha = fArms), start, end, strokeWidth = rm * 0.09f, cap = StrokeCap.Round)
                val mid = start + (end - start) * 0.65f
                drawLine(STICK.copy(alpha = fArms), mid, mid + polar(a - side * 0.7f, rm * 0.3f), strokeWidth = rm * 0.07f, cap = StrokeCap.Round)
            }
        }

        snowball(Offset(cx, yb), rb, fBottom)
        snowball(Offset(cx, ym), rm, fMid)

        // Coal buttons drop out before the middle melts.
        if (fButtons > 0f && fMid > 0f) {
            for (i in -1..1) drawCircle(COAL.copy(alpha = fButtons),rm * 0.08f, Offset(cx, ym + i * rm * 0.35f))
        }

        snowball(Offset(cx, yh), rh, fHead)

        // Scarf: a band where head meets body, with a tail on one side.
        if (fScarf > 0f && fHead > 0f) {
            val sw = rm * 1.35f
            val sy = ym - rm * 0.82f
            drawRoundRect(
                SCARF.copy(alpha = fScarf), Offset(cx - sw / 2, sy - rh * 0.16f), Size(sw, rh * 0.32f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rh * 0.16f),
            )
            drawRoundRect(
                SCARF.copy(alpha = fScarf), Offset(cx + sw * 0.18f, sy), Size(rh * 0.28f, rh * 0.85f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rh * 0.1f),
            )
        }

        // Face. As the head melts, the eyes and carrot slide down into the puddle.
        val fall = 1f - fHead
        val eyeRest = listOf(Offset(cx - puddleW * 0.25f, baseY), Offset(cx + puddleW * 0.3f, baseY + puddleH * 0.1f))
        listOf(-1f, 1f).forEachIndexed { i, side ->
            val onHead = Offset(cx + side * rh * 0.35f, yh - rh * 0.2f)
            drawCircle(COAL, h * 0.012f, lerpO(onHead, eyeRest[i], fall))
        }
        val noseHead = Offset(cx + rh * 0.05f, yh + rh * 0.05f)
        val noseRest = Offset(cx - puddleW * 0.05f, baseY - h * 0.005f)
        val nose = lerpO(noseHead, noseRest, fall)
        val noseLen = h * 0.075f
        rotate(fall * 12f, nose) {
            val p = Path().apply {
                moveTo(nose.x, nose.y - noseLen * 0.14f)
                lineTo(nose.x + noseLen, nose.y + noseLen * 0.06f)
                lineTo(nose.x, nose.y + noseLen * 0.14f)
                close()
            }
            drawPath(p, CARROT)
        }

        // Hat slides off and fades.
        if (fHat > 0f && fHead > 0f) {
            val off = 1f - fHat
            val top = yh - rh * 0.72f
            translate(off * rh * 1.2f, off * rh * 0.8f) {
                rotate(off * 35f, Offset(cx, top)) {
                    val brimW = rh * 1.9f
                    drawRoundRect(HAT.copy(alpha = fHat), Offset(cx - brimW / 2, top - rh * 0.1f), Size(brimW, rh * 0.2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rh * 0.1f))
                    val crownW = rh * 1.2f
                    drawRect(HAT.copy(alpha = fHat), Offset(cx - crownW / 2, top - rh * 1.15f), Size(crownW, rh * 1.1f))
                    drawRect(SCARF.copy(alpha = fHat), Offset(cx - crownW / 2, top - rh * 0.38f), Size(crownW, rh * 0.2f))
                }
            }
        }

        // Drips run off the lowest standing snowball.
        if (drip.value < 1f && melt > 0.2f) {
            val t = drip.value
            val src = when {
                fHead > 0f -> Offset(cx, yh) to rh
                fMid > 0f -> Offset(cx, ym) to rm
                else -> Offset(cx, yb) to rb
            }
            listOf(-0.8f, 0.15f, 0.7f).forEachIndexed { i, dx ->
                val delay = i * 0.18f
                val tt = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
                if (tt in 0.001f..0.999f) {
                    val sx = src.first.x + dx * src.second
                    val sy = src.first.y + src.second * 0.5f
                    val y = lerpF(sy, baseY, tt * tt)
                    drawDrop(Offset(sx, y), h * 0.013f, WATER.copy(alpha = 1f - tt * 0.6f))
                }
            }
        }

        // Frosty sparkles on a win.
        if (sparkle > 0f) {
            val spots = listOf(-1.4f to -2.9f, 1.3f to -2.3f, -1.7f to -0.9f, 1.8f to -1.3f, -0.9f to 0.2f, 1.2f to 0.35f)
            spots.forEachIndexed { i, (dx, dy) ->
                val phase = (twinkle + i / spots.size.toFloat()) % 1f
                val s = sin(phase * PI.toFloat()).coerceAtLeast(0f)
                drawStar(Offset(cx + dx * rm, yb + dy * rm), h * 0.035f * s * sparkle, Color.White.copy(alpha = sparkle))
            }
        }
    }
}

private fun polar(angle: Float, r: Float) = Offset(cos(angle) * r, sin(angle) * r)
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerpO(a: Offset, b: Offset, t: Float) = Offset(lerpF(a.x, b.x, t), lerpF(a.y, b.y, t))

private fun DrawScope.drawDrop(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r * 2.2f)
        quadraticTo(c.x + r * 1.1f, c.y - r * 0.4f, c.x + r, c.y)
        quadraticTo(c.x, c.y + r * 1.4f, c.x - r, c.y)
        quadraticTo(c.x - r * 1.1f, c.y - r * 0.4f, c.x, c.y - r * 2.2f)
        close()
    }
    drawPath(p, color)
}

/** Four-pointed twinkle. */
private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    if (r <= 0f) return
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
    drawCircle(color, r * 0.15f, c)
}
