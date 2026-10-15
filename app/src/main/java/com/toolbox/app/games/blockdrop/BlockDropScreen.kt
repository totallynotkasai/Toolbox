package com.toolbox.app.games.blockdrop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.toolbox.app.games.blockdrop.BlockDropGame.Event
import com.toolbox.app.games.blockdrop.BlockDropGame.State
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.GameOverCard
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.accessibilityActions
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberScreenReaderOn
import com.toolbox.app.ui.common.rememberToneSynth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val GameSaver = Saver<BlockDropGame, IntArray>(save = { it.save() }, restore = { BlockDropGame.restore(it) })

/** What the panel and the controls show, copied out of the game after every change so Compose sees it change. */
private data class Shown(val state: State, val score: Int, val level: Int, val lines: Int, val next: Kind) {
    constructor(game: BlockDropGame) : this(game.state, game.score, game.level, game.lines, game.next)
}

/** A downward flick at least this fast (dp a second) drops the piece to the bottom. */
private const val FLICK_DP_PER_S = 1500f

@Composable
fun BlockDropScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val synth = rememberToneSynth()
    val say = rememberAnnouncer()
    val scope = rememberCoroutineScope()
    val screenReader = rememberScreenReaderOn()
    val guard = rememberGameGuard()
    var game by rememberSaveable(stateSaver = GameSaver) { mutableStateOf(BlockDropGame()) }
    val (best, submit) = rememberBestScore("blockdrop")
    // A drop can end the game from the long-lived pointer handler, so it reads the best through this.
    val currentBest by rememberUpdatedState(best)
    // Whether the game just finished beat the best from before it, decided when it ends.
    var newBest by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(Shown(game)) }
    var cellPx by remember { mutableFloatStateOf(0f) }

    fun sync() {
        revision = game.revision
        shown = Shown(game)
    }

    /** Sounds, buzzes and TalkBack news for what a step or a drop did. */
    fun react(events: List<Event>, hard: Boolean = false) {
        for (e in events) when (e) {
            is Event.Locked -> {
                when (e.lines) {
                    0 -> synth.knock(if (hard) 0.7f else 0.45f)
                    4 -> for (f in TETRIS_CHORD) synth.play(f, 420, 0.16f)
                    else -> for (f in LINE_CHORD) synth.play(f, 180, 0.22f)
                }
                when {
                    e.lines == 4 -> haptics.heavy()
                    e.lines > 0 || hard -> haptics.click()
                    else -> haptics.tick()
                }
                if (e.levelUp) scope.launch {
                    delay(if (e.lines == 4) 300 else 160)
                    synth.play(1047f, 120, 0.25f)
                    delay(110)
                    synth.play(1568f, 200, 0.25f)
                }
                if (e.lines > 0) {
                    val cleared = if (e.lines == 1) "1 line" else "${e.lines} lines"
                    say("$cleared. Score ${game.score}" + if (e.levelUp) ". Level ${game.level}" else "")
                }
            }
            Event.Over -> {
                synth.buzz(400)
                haptics.heavy()
                newBest = game.score > currentBest
                submit(game.score)
            }
        }
    }

    fun act(block: () -> Boolean, sound: () -> Unit) {
        if (block()) {
            sound()
            sync()
        }
    }

    fun moveBy(dx: Int) = act({ game.move(dx) }) { synth.tick(); haptics.tick() }
    fun rotate() = act({ game.rotate() }) { synth.play(880f, 20, 0.18f); haptics.tick() }
    fun softDrop() = act({ game.softDrop() }) { synth.tick() }
    fun hardDrop() {
        react(game.hardDrop(), hard = true)
        sync()
    }

    fun play() {
        if (game.state == State.Paused) game.resume() else {
            game = BlockDropGame()
            game.start()
            newBest = false
        }
        sync()
    }

    fun pause() {
        game.pause()
        sync()
    }

    // The loop only runs while playing and the app is in view; leaving the app pauses the game too, so it waits
    // for Resume rather than carrying on the moment you're back.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { pause() }
    rememberGameLoop(enabled = shown.state == State.Playing && !guard.isAsking) { dt ->
        react(game.step(dt))
        sync()
        game.state == State.Playing
    }

    val underWay = shown.state == State.Playing || shown.state == State.Paused

    ToolScaffold(
        title = "Block Drop",
        onBack = onBack,
        actions = {
            if (underWay) {
                val playing = shown.state == State.Playing
                IconButton(onClick = { if (playing) pause() else play() }) {
                    if (playing) Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    else Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                }
            }
            NewGameButton(
                onClick = {
                    pause()
                    guard.run(underWay && game.score > 0) {
                        game = BlockDropGame()
                        newBest = false
                        sync()
                    }
                },
                enabled = shown.state != State.Ready,
            )
        },
    ) { padding ->
        // Drags and taps are read across the whole screen, not just the well, so a thumb anywhere can play.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    pieceGestures(
                        cellPx = { cellPx },
                        active = { game.state == State.Playing },
                        onTap = ::rotate,
                        onMove = ::moveBy,
                        onSoftDrop = ::softDrop,
                        onHardDrop = ::hardDrop,
                    )
                },
        ) {
            // Not a ToolBody: a drag plays, so this mustn't scroll, and the well shrinks instead when large text
            // leaves less room.
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    val panel = 96.dp
                    val gap = 12.dp
                    val wellHeight = min(maxHeight, (maxWidth - panel - gap) * (ROWS / COLS.toFloat())).coerceAtLeast(0.dp)
                    // Read on every change, since the piece's column isn't in [shown].
                    val wellState = if (revision >= 0) blockDropState(game) else ""
                    Row(Modifier.align(Alignment.Center)) {
                        Box(
                            Modifier
                                .size(wellHeight * (COLS / ROWS.toFloat()), wellHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .onSizeChanged { cellPx = it.width / COLS.toFloat() }
                                .semantics {
                                    contentDescription = "Well"
                                    stateDescription = wellState
                                    // Drags can't be made with TalkBack on, so the moves are in its actions menu.
                                    if (shown.state == State.Playing) {
                                        customActions = accessibilityActions(
                                            "Move left" to { moveBy(-1) },
                                            "Move right" to { moveBy(1) },
                                            "Rotate" to { rotate() },
                                            "Soft drop" to { softDrop() },
                                            "Hard drop" to { hardDrop() },
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                if (revision < 0) return@Canvas
                                drawWell(game)
                            }
                            when (shown.state) {
                                State.Ready -> Button(onClick = ::play) { Text("Play") }
                                State.Paused -> Overlay {
                                    GameOverCard(GameOver("Paused", "Score ${shown.score} · Level ${shown.level}"), onAction = ::play, action = "Resume")
                                }
                                State.Over -> Overlay {
                                    val line = if (newBest) "Score ${shown.score} · New best!" else "Score ${shown.score} · Best $best"
                                    GameOverCard(GameOver("Game over", line), onAction = ::play)
                                }
                                State.Playing -> Unit
                            }
                        }
                        Spacer(Modifier.width(gap))
                        SidePanel(shown, best, Modifier.width(panel).height(wellHeight))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (screenReader) "Use the actions menu to move, rotate and drop"
                    else "Drag sideways to move, tap to rotate.\nDrag down to speed up, or flick down to drop.",
                    // Balanced, so large text doesn't leave one word on a line of its own.
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    GameGuardDialog(guard)
}

/** Dims the well behind a card. */
@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SidePanel(shown: Shown, best: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) { }) {
            Label("Next")
            Spacer(Modifier.height(4.dp))
            // Empty until a game starts, since the first pieces are only dealt then.
            val next = shown.next.takeIf { shown.state != State.Ready }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .clearAndSetSemantics { contentDescription = next?.let { "${it.name} piece" } ?: "None yet" },
            ) { drawPreview(next) }
        }
        Stat("Score", shown.score)
        Stat("Best", maxOf(best, shown.score))
        Stat("Level", shown.level)
        Stat("Lines", shown.lines)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(Modifier.semantics(mergeDescendants = true) { }) {
        Label(label)
        val style = MaterialTheme.typography.titleLarge
        Text(
            "$value",
            style = style,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
        )
    }
}

/** The well as TalkBack reads it, after "Well": "T piece in columns 4 to 6. Score 120". */
internal fun blockDropState(game: BlockDropGame): String = when (game.state) {
    State.Ready -> "Not started"
    State.Paused -> "Paused. Score ${game.score}"
    State.Over -> "Game over. Score ${game.score}"
    State.Playing -> {
        val piece = game.piece
        if (piece == null) "Score ${game.score}" else {
            val from = piece.cells.minOf { it.first } + 1
            val to = piece.cells.maxOf { it.first } + 1
            val where = if (from == to) "column $from" else "columns $from to $to"
            "${piece.kind.name} piece in $where. Score ${game.score}"
        }
    }
}

/**
 * Taps and drags for the piece, anywhere on the screen while [active]. Each drag goes one way: sideways moves a
 * column for every cell width the finger travels, and down drops a row for every cell height. A drag down that ends
 * in a fast flick drops the piece to the bottom. A touch that hardly moves is a tap, which turns it.
 */
private suspend fun PointerInputScope.pieceGestures(
    cellPx: () -> Float,
    active: () -> Boolean,
    onTap: () -> Unit,
    onMove: (Int) -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    val flick = FLICK_DP_PER_S.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        if (!active()) return@awaitEachGesture
        val velocity = VelocityTracker()
        velocity.addPosition(down.uptimeMillis, down.position)
        var way = Way.Undecided
        var travel = Offset.Zero
        var carry = 0f // travel along the drag's way not yet turned into moves
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                if (!active()) break
                when (way) {
                    Way.Undecided -> onTap()
                    Way.Down -> if (velocity.calculateVelocity().y > flick) onHardDrop()
                    else -> Unit
                }
                break
            }
            velocity.addPosition(change.uptimeMillis, change.position)
            val delta = change.positionChange()
            change.consume()
            travel += delta
            if (way == Way.Undecided) {
                if (travel.getDistance() < slop) continue
                way = when {
                    abs(travel.x) >= abs(travel.y) -> Way.Sideways
                    travel.y > 0 -> Way.Down
                    else -> Way.Up
                }
                carry = if (way == Way.Sideways) travel.x else travel.y
            } else {
                carry += if (way == Way.Sideways) delta.x else delta.y
            }
            val cell = cellPx()
            if (cell <= 0f || !active()) continue
            when (way) {
                Way.Sideways -> while (abs(carry) >= cell) {
                    val dx = if (carry > 0) 1 else -1
                    onMove(dx)
                    carry -= dx * cell
                }
                Way.Down -> while (carry >= cell) {
                    onSoftDrop()
                    carry -= cell
                }
                else -> Unit
            }
        }
    }
}

private enum class Way { Undecided, Sideways, Down, Up }

// C major for a line or three, and spread over two octaves for four at once.
private val LINE_CHORD = floatArrayOf(523.25f, 659.26f, 783.99f)
private val TETRIS_CHORD = floatArrayOf(261.63f, 392f, 523.25f, 659.26f, 783.99f, 1046.5f)

// The classic colours, a little softened. The well is dark in both themes, so they glow against it.
private val WELL = Color(0xFF1B2130)
private val GRID = Color.White.copy(alpha = 0.05f)

private val Kind.color: Color
    get() = when (this) {
        Kind.I -> Color(0xFF34C6E5)
        Kind.O -> Color(0xFFF5C83A)
        Kind.T -> Color(0xFFA35ED9)
        Kind.S -> Color(0xFF4FC165)
        Kind.Z -> Color(0xFFE5564E)
        Kind.J -> Color(0xFF3F7EE6)
        Kind.L -> Color(0xFFF28D36)
    }

private fun DrawScope.drawWell(game: BlockDropGame) {
    val s = size.width / COLS
    drawRect(WELL)
    for (x in 1 until COLS) drawLine(GRID, Offset(x * s, 0f), Offset(x * s, size.height), 1f)
    for (y in 1 until ROWS) drawLine(GRID, Offset(0f, y * s), Offset(size.width, y * s), 1f)
    // Full rows flash white and fade before they go.
    val fade = 1f - game.clearProgress
    for (y in 0 until ROWS) for (x in 0 until COLS) {
        val kind = game.cell(x, y) ?: continue
        drawBlock(x * s, y * s, s, kind.color, alpha = if (y in game.clearing) fade else 1f)
    }
    for (y in game.clearing) drawRect(Color.White.copy(alpha = 0.8f * fade), Offset(0f, y * s), Size(size.width, s))
    if (game.state == State.Over || game.state == State.Ready) return
    val piece = game.piece ?: return
    game.ghost()?.takeIf { it != piece }?.let { ghost ->
        for ((x, y) in ghost.cells) if (y >= 0) drawGhost(x * s, y * s, s, piece.kind.color)
    }
    for ((x, y) in piece.cells) if (y >= 0) drawBlock(x * s, y * s, s, piece.kind.color)
}

/** The next piece, centred in its box. */
private fun DrawScope.drawPreview(kind: Kind?) {
    drawRect(WELL)
    if (kind == null) return
    val cells = kind.turns[0]
    val minX = cells.minOf { it.first }
    val minY = cells.minOf { it.second }
    val w = cells.maxOf { it.first } - minX + 1
    val h = cells.maxOf { it.second } - minY + 1
    val s = minOf(size.width / 4.6f, size.height / 2.8f)
    val left = (size.width - w * s) / 2
    val top = (size.height - h * s) / 2
    for ((x, y) in cells) drawBlock(left + (x - minX) * s, top + (y - minY) * s, s, kind.color)
}

/** One square of a piece: shaded from light to dark, with a highlight along the top like a bevel. */
private fun DrawScope.drawBlock(x: Float, y: Float, s: Float, color: Color, alpha: Float = 1f) {
    val inset = s * 0.05f
    drawRoundRect(
        Brush.verticalGradient(listOf(color.lighten(0.18f), color.darken(0.14f)), startY = y, endY = y + s),
        Offset(x + inset, y + inset),
        Size(s - 2 * inset, s - 2 * inset),
        CornerRadius(s * 0.16f),
        alpha = alpha,
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.32f),
        Offset(x + s * 0.18f, y + s * 0.14f),
        Size(s * 0.64f, s * 0.11f),
        CornerRadius(s * 0.055f),
        alpha = alpha,
    )
}

/** Where the piece will land: an outline with a faint fill. */
private fun DrawScope.drawGhost(x: Float, y: Float, s: Float, color: Color) {
    val inset = s * 0.1f
    val topLeft = Offset(x + inset, y + inset)
    val box = Size(s - 2 * inset, s - 2 * inset)
    drawRoundRect(color.copy(alpha = 0.16f), topLeft, box, CornerRadius(s * 0.14f))
    drawRoundRect(color.copy(alpha = 0.6f), topLeft, box, CornerRadius(s * 0.14f), style = Stroke(s * 0.07f))
}

private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
