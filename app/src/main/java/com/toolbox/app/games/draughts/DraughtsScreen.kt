package com.toolbox.app.games.draughts

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.toolbox.app.ui.common.BoardTarget
import com.toolbox.app.ui.common.BoardTargets
import com.toolbox.app.ui.common.GameFooter
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameMode
import com.toolbox.app.ui.common.GameModeChips
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.Score
import com.toolbox.app.ui.common.ScoreRow
import com.toolbox.app.ui.common.ScoresMenu
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.TurnStatus
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameMode
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.theme.GoogleColors
import com.toolbox.app.ui.theme.isLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The game in progress, in Prefs so it's kept between visits and backed up, and whether the computer starts the next
// game against it.
private const val GAME_KEY = "draughts_game"
private const val CPU_FIRST_KEY = "draughts_cpu_first"

/** How long a piece takes to cross one square, or to jump one piece. */
private const val STEP_MS = 190

private fun sideColor(side: Int) = if (side == RED) GoogleColors.Red else GoogleColors.Yellow
private fun sideName(side: Int) = if (side == RED) "Red" else "Yellow"

/** A game saved as text: who moved first, then the moves. */
internal fun encodeGame(first: Int, moves: List<Move>): String = "$first;" + moves.joinToString(",") { it.encode() }

/** The game saved by [encodeGame], or null if the text isn't one or holds a move that isn't legal. */
internal fun decodeGame(text: String): Draughts? = runCatching {
    val parts = text.split(';', limit = 2)
    val first = parts[0].toInt()
    require(first == RED || first == YELLOW)
    Draughts.replay(first, if (parts[1].isEmpty()) emptyList() else parts[1].split(',').map(Move::decode))
}.getOrNull()

/** What a square holds, as TalkBack reads it. */
internal fun pieceName(piece: Int): String = when (piece) {
    RED_MAN -> "red man"
    YELLOW_MAN -> "yellow man"
    RED_KING -> "red king"
    YELLOW_KING -> "yellow king"
    else -> "empty"
}

/** A move as TalkBack offers it: "Move to D4", or "Jump to E5, then C7". */
internal fun moveLabel(move: Move): String =
    if (move.isCapture) "Jump to " + move.path.drop(1).joinToString(", then ") { squareName(it) } else "Move to ${squareName(move.to)}"

/** A move made: [piece] going along [path] from step [from] (the steps before it were shown as they were made), taking [taken] (square to piece). */
private class Motion(val piece: Int, val path: List<Int>, val from: Int, val taken: List<Pair<Int, Int>>)

/** A piece drawn off its square, centred at column [x] and row [y] (fractions for in between). */
private class Floating(val piece: Int, val x: Float, val y: Float)

/** Everything the board shows. */
private class Look(
    val pieces: IntArray,
    val fade: Map<Int, Float>,
    val floating: Floating?,
    val picked: Int?,
    val dots: Set<Int>,
    val rings: Set<Int>,
    val marks: Set<Int>,
    /** Yellow kings' crowns face the top of the board, for the player sitting there. */
    val yellowCrownsTurned: Boolean,
)

@Composable
fun DraughtsScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val synth = rememberToneSynth()
    val say = rememberAnnouncer()
    val scope = rememberCoroutineScope()
    val guard = rememberGameGuard()
    var mode by rememberGameMode("draughts_mode")
    // Per mode, as "a,b,draws": you/CPU against the computer, red/yellow for two players.
    val tally = rememberTally("draughts_tally_${mode.key}")
    var cpuFirst by rememberPref(CPU_FIRST_KEY, false)
    var record by rememberPref(GAME_KEY, "")
    // You're always red, at the bottom; against the computer, who starts alternates from game to game.
    val cpuSide = if (mode.vsComputer) YELLOW else 0
    val game = remember(record) { decodeGame(record) ?: Draughts.start(if (mode.vsComputer && cpuFirst) YELLOW else RED) }
    val result = remember(game) { game.result() }
    val over = result != null
    val legal = remember(game) { if (over) emptyList() else game.legalMoves() }
    val cpuTurn = !over && game.toMove == cpuSide
    // You have moved: any move in two-player games, any but the computer's opening one otherwise.
    val yourMoves = if (mode.vsComputer && game.first == cpuSide) game.plies - 1 else game.plies
    val underWay = !over && yourMoves > 0

    // The squares of the piece being moved so far: its own, then each it has jumped to.
    var partial by remember(record) { mutableStateOf(listOf<Int>()) }
    var thinking by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Motion?>(null) }
    val progress = remember { Animatable(0f) }
    var hopFrom by remember { mutableIntStateOf(-1) }
    val hop = remember { Animatable(1f) }
    var drag by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var cellPx by remember { mutableFloatStateOf(1f) }
    val jobs = remember { arrayOfNulls<Job>(1) }

    val yourTurn = !over && !cpuTurn && motion == null
    val options = legal.filter { it.path.size > partial.size && it.path.subList(0, partial.size) == partial }
    val nextSquares = if (partial.isEmpty()) emptySet() else options.map { it.path[partial.size] }.toSet()
    val movable = legal.map { it.from }.toSet()
    val mustCapture = legal.firstOrNull()?.isCapture == true

    fun cancelMotion() {
        jobs[0]?.cancel()
        motion = null
        drag = null
        hopFrom = -1
    }

    fun ending(res: Result) {
        haptics.heavy()
        val happy = res is Result.Win && (!mode.vsComputer || res.winner == RED)
        scope.launch {
            delay(150)
            when {
                happy -> for (f in floatArrayOf(523.25f, 659.26f, 783.99f, 1046.5f)) {
                    synth.play(f, 220, 0.18f)
                    delay(90)
                }
                res is Result.Win -> {
                    synth.play(392f, 220, 0.18f)
                    delay(160)
                    synth.play(261.63f, 380, 0.18f)
                }
                else -> synth.play(523.25f, 400, 0.15f)
            }
        }
    }

    /** Plays [move], already shown up to step [fromStep], and slides the piece the rest of the way. */
    fun commit(move: Move, fromStep: Int) {
        val piece = game[move.from]
        val taken = move.captured.map { it to game[it] }
        val crowned = game.crowns(move)
        val after = game.copy().apply { play(move) }
        // The result is counted straight away, so leaving during the last slide can't lose it.
        val res = after.result()
        if (res != null) tally.record(if (res is Result.Win) (if (res.winner == RED) 1 else 2) else 0)
        cancelMotion()
        record = encodeGame(game.first, game.moves + move)
        partial = emptyList()
        hint = null
        motion = Motion(piece, move.path, fromStep, taken)
        jobs[0] = scope.launch {
            progress.snapTo(fromStep.toFloat())
            // A piece dropped by a drag is already there, so it only clacks down.
            if (fromStep >= move.path.lastIndex) {
                synth.knock(if (move.isCapture) 0.7f else 0.45f)
                if (move.isCapture) haptics.click() else haptics.tick()
            }
            for (s in fromStep until move.path.lastIndex) {
                progress.animateTo(s + 1f, tween(STEP_MS, easing = FastOutSlowInEasing))
                synth.knock(if (move.isCapture) 0.7f else 0.45f)
                if (move.isCapture) haptics.click() else haptics.tick()
            }
            motion = null
            if (crowned) {
                synth.play(1046.5f, 150, 0.2f)
                delay(110)
                synth.play(1568f, 260, 0.2f)
            }
            if (res != null) ending(res)
        }
    }

    /** Says what the other side did, for TalkBack: the computer, or whoever just went in a two-player game. */
    fun announce(move: Move, who: String) {
        val what = if (move.isCapture) "jumped" else "moved"
        val taking = when (move.captured.size) {
            0 -> ""
            1 -> ", taking 1"
            else -> ", taking ${move.captured.size}"
        }
        val crown = if (game.crowns(move)) ", and is crowned" else ""
        say("$who $what " + move.path.joinToString(" to ") { squareName(it) } + taking + crown)
    }

    fun nope(why: String) {
        hint = why
        haptics.tick()
        synth.knock(0.15f)
        say(why)
    }

    /** Moves the picked-up piece on to [square], one of [nextSquares]: the whole move, or the next jump of one. */
    fun stepTo(square: Int, slide: Boolean) {
        val path = partial + square
        val done = legal.firstOrNull { it.path == path }
        if (done != null) {
            if (!mode.vsComputer) announce(done, sideName(game.toMove))
            commit(done, if (slide) partial.size - 1 else path.size - 1)
            return
        }
        val from = partial.last()
        partial = path
        hint = null
        synth.knock(0.65f)
        haptics.click()
        if (slide) {
            hopFrom = from
            jobs[0]?.cancel()
            jobs[0] = scope.launch {
                hop.snapTo(0f)
                hop.animateTo(1f, tween(STEP_MS, easing = FastOutSlowInEasing))
                hopFrom = -1
            }
        }
    }

    fun tap(square: Int) {
        if (!yourTurn) return
        when {
            square in nextSquares -> stepTo(square, slide = true)
            partial.size > 1 -> nope("Keep jumping with the same piece")
            square in movable -> {
                partial = if (partial == listOf(square)) emptyList() else listOf(square)
                hint = null
                haptics.tick()
            }
            // The line above the hint already says a capture is due, so this says why not this piece.
            owner(game[square]) == game.toMove -> nope(if (mustCapture) "That piece can't capture" else "That piece can't move")
            else -> partial = emptyList()
        }
    }

    fun canDrag(square: Int) = yourTurn && if (partial.size > 1) square == partial.last() else square in movable

    fun undo() {
        if (over || cpuTurn || yourMoves == 0) return
        var moves = game.moves.dropLast(1)
        // Against the computer, go back to your own turn.
        fun toMoveAfter(n: Int) = if (n % 2 == 0) game.first else 3 - game.first
        if (mode.vsComputer) while (moves.isNotEmpty() && toMoveAfter(moves.size) == cpuSide) moves = moves.dropLast(1)
        cancelMotion()
        record = encodeGame(game.first, moves)
        hint = null
        synth.knock(0.3f)
        haptics.tick()
        say("Undone")
    }

    fun newGame(nextMode: GameMode = mode) {
        val nextCpuFirst = when {
            nextMode != mode -> false
            nextMode.vsComputer && game.plies > 0 -> !cpuFirst
            else -> cpuFirst
        }
        cpuFirst = nextCpuFirst
        mode = nextMode
        cancelMotion()
        record = encodeGame(if (nextMode.vsComputer && nextCpuFirst) YELLOW else RED, emptyList())
        hint = null
    }

    // The computer's move, chosen off the main thread once the last piece has landed.
    LaunchedEffect(record, mode) {
        if (!cpuTurn) return@LaunchedEffect
        snapshotFlow { motion }.first { it == null }
        thinking = true
        val move = try {
            val started = SystemClock.uptimeMillis()
            val best = withContext(Dispatchers.Default) { aiMove(game.copy()) }
            delay((500 - (SystemClock.uptimeMillis() - started)).coerceAtLeast(0))
            best
        } finally {
            thinking = false // also when Undo, New game or a mode change cancels it
        }
        announce(move, "Computer")
        commit(move, 0)
    }
    // A hint fades after a few seconds.
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(3000)
            hint = null
        }
    }

    val look = run {
        val pieces = IntArray(64) { game[it] }
        val fade = HashMap<Int, Float>()
        var floating: Floating? = null
        val m = motion
        if (m != null) {
            val p = progress.value
            pieces[m.path.last()] = EMPTY
            val s = p.toInt().coerceIn(0, m.path.size - 2)
            val t = (p - s).coerceIn(0f, 1f)
            val a = m.path[s]
            val b = m.path[s + 1]
            floating = Floating(m.piece, col(a) + (col(b) - col(a)) * t, row(a) + (row(b) - row(a)) * t)
            m.taken.forEachIndexed { k, (square, piece) ->
                // Each piece taken fades during the second half of the jump over it; ones jumped before are gone.
                val alpha = if (k < m.from) 0f else (2f - (p - k) * 2f).coerceIn(0f, 1f)
                if (alpha > 0f) {
                    pieces[square] = piece
                    fade[square] = alpha
                }
            }
        } else if (partial.size > 1) {
            val piece = game[partial[0]]
            pieces[partial[0]] = EMPTY
            partial.zipWithNext { a, b -> fade[(a + b) / 2] = 0.35f }
            val at = partial.last()
            if (hopFrom >= 0) {
                val t = hop.value
                floating = Floating(piece, col(hopFrom) + (col(at) - col(hopFrom)) * t, row(hopFrom) + (row(at) - row(hopFrom)) * t)
            } else {
                pieces[at] = piece
            }
        }
        drag?.let { (square, finger) ->
            val piece = pieces[square]
            if (piece != EMPTY) {
                pieces[square] = EMPTY
                floating = Floating(piece, finger.x / cellPx - 0.5f, finger.y / cellPx - 0.5f)
            }
        }
        val idle = yourTurn && motion == null
        Look(
            pieces = pieces,
            fade = fade,
            floating = floating,
            picked = partial.lastOrNull().takeIf { idle && drag == null },
            dots = if (idle) nextSquares else emptySet(),
            rings = if (idle && partial.isEmpty() && mustCapture) movable else emptySet(),
            marks = if (motion == null) game.lastMove?.let { setOf(it.from, it.to) }.orEmpty() else emptySet(),
            yellowCrownsTurned = !mode.vsComputer,
        )
    }

    fun targets(): List<BoardTarget> = (0 until 64).filter { isDark(it) }.map { s ->
        val rect = Rect(col(s) * cellPx, row(s) * cellPx, (col(s) + 1) * cellPx, (row(s) + 1) * cellPx)
        val description = "${squareName(s)}, ${pieceName(look.pieces[s])}"
        val state = when {
            s == look.picked -> "Picked up"
            s in look.rings -> "Must capture"
            else -> null
        }
        when {
            yourTurn && s in nextSquares -> BoardTarget(s, rect, description, state, clickLabel = "Move here", onClick = { tap(s) })
            yourTurn && partial.size <= 1 && s in movable -> BoardTarget(
                s, rect, description, state, clickLabel = if (s == look.picked) "Put down" else "Pick up", onClick = { tap(s) },
                actions = legal.filter { it.from == s }.map { m ->
                    CustomAccessibilityAction(moveLabel(m)) {
                        if (!mode.vsComputer) announce(m, sideName(game.toMove))
                        commit(m, 0)
                        true
                    }
                },
            )
            else -> BoardTarget(s, rect, description, state, button = false)
        }
    }

    val canUndo = !over && motion == null && !cpuTurn && yourMoves > 0
    ToolScaffold(
        title = "Draughts",
        onBack = onBack,
        actions = {
            IconButton(onClick = { undo() }, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            NewGameButton(onClick = { guard.run(underWay) { newGame() } }, enabled = game.plies > 0 && motion == null)
            ScoresMenu(onReset = tally::reset)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameModeChips(mode, onSelect = { m -> guard.run(underWay) { newGame(m) } })
            Spacer(Modifier.height(12.dp))
            val t = tally.value
            ScoreRow(
                if (mode.vsComputer) listOf(Score("You", t.a, sideColor(RED)), Score("Draws", t.draws), Score("CPU", t.b, sideColor(YELLOW)))
                else listOf(Score("Red", t.a, sideColor(RED)), Score("Draws", t.draws), Score("Yellow", t.b, sideColor(YELLOW))),
            )
            Spacer(Modifier.height(8.dp))

            // The result shows once the last piece has landed.
            val ended = result.takeIf { motion == null }
            val title = when (ended) {
                null -> null
                is Result.Draw -> "It's a draw"
                is Result.Win -> when {
                    !mode.vsComputer -> "${sideName(ended.winner)} wins!"
                    ended.winner == cpuSide -> "The computer wins"
                    else -> "You win!"
                }
            }
            val why = when ((ended as? Result.Draw)?.reason) {
                DrawReason.FortyMoves -> "40 moves each without a capture"
                DrawReason.Repetition -> "The same position three times"
                null -> null
            }
            val nextStart = if (mode.vsComputer) (if (cpuFirst) "You start the next game" else "The computer starts the next game") else null
            val turnText = when {
                motion != null || over -> " "
                thinking || cpuTurn -> "Thinking…"
                partial.size > 1 -> "Keep jumping"
                !mode.vsComputer -> sideName(game.toMove) + if (mustCapture) " must capture" else "'s turn"
                mustCapture -> "You must capture"
                game.plies == 0 -> "You go first"
                else -> "Your turn"
            }
            val turnColor = if (motion != null || over) null else sideColor(game.toMove)

            // With two players across the table, the player at the top has their own line, turned to face them,
            // and only the line of the player to move is lit.
            if (!mode.vsComputer) {
                val lit = over || game.toMove == YELLOW
                Box(
                    Modifier
                        .fillMaxWidth()
                        .rotate(180f)
                        .graphicsLayer { alpha = if (lit) 1f else 0.35f }
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    if (title != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            if (why != null) Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        TurnStatus(turnText, turnColor, hint = hint.takeIf { game.toMove == YELLOW })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = min(min(maxWidth, maxHeight), 520.dp)
                // Lambdas rather than references to the local functions: references to a local function compare
                // equal from one composition to the next, so the board would keep calling the first one, with the
                // moves of the position it was made in.
                BoardView(
                    look = look,
                    enabled = yourTurn,
                    onTap = { tap(it) },
                    canDrag = { canDrag(it) },
                    onDragStart = { s ->
                        if (partial.size <= 1) partial = listOf(s)
                        hint = null
                        haptics.tick()
                    },
                    onDrag = { s, at -> drag = s to at },
                    onDrop = { target ->
                        drag = null
                        if (target != null && target in nextSquares) stepTo(target, slide = false)
                    },
                    targets = { targets() },
                    onCell = { cellPx = it },
                    modifier = Modifier.size(side),
                )
            }
            Spacer(Modifier.height(12.dp))
            val bottomLit = mode.vsComputer || over || game.toMove == RED
            GameFooter(
                over = title?.let { GameOver(it, why ?: nextStart) },
                turn = turnText,
                turnColor = turnColor,
                hint = hint.takeIf { mode.vsComputer || game.toMove == RED },
                onPlayAgain = { newGame() },
                modifier = Modifier.widthIn(max = 400.dp).graphicsLayer { alpha = if (bottomLit) 1f else 0.35f },
            )
        }
    }
    GameGuardDialog(guard)
}

// Two calm tones that keep both pieces clear: cream and slate, a little darker in dark mode.
private val LIGHT_SQUARE = Color(0xFFECE5D5)
private val DARK_SQUARE = Color(0xFF54647A)
private val LIGHT_SQUARE_DARK = Color(0xFFB9B2A2)
private val DARK_SQUARE_DARK = Color(0xFF3B4757)

@Composable
private fun BoardView(
    look: Look,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    canDrag: (Int) -> Boolean,
    onDragStart: (Int) -> Unit,
    onDrag: (Int, Offset) -> Unit,
    onDrop: (Int?) -> Unit,
    targets: () -> List<BoardTarget>,
    onCell: (Float) -> Unit,
    modifier: Modifier,
) {
    val dark = !MaterialTheme.colorScheme.isLight
    val pulse by rememberInfiniteTransition(label = "rings").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    val currentEnabled by rememberUpdatedState(enabled)
    val currentTap by rememberUpdatedState(onTap)
    val currentCanDrag by rememberUpdatedState(canDrag)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDrop by rememberUpdatedState(onDrop)

    Box(modifier.clip(RoundedCornerShape(8.dp)).onSizeChanged { onCell(it.width / 8f) }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Tap a piece and then a square, or drag the piece there.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!currentEnabled) return@awaitEachGesture
                        val cell = size.width / 8f
                        fun squareAt(p: Offset): Int? {
                            val c = (p.x / cell).toInt()
                            val r = (p.y / cell).toInt()
                            return if (p.x >= 0 && p.y >= 0 && c in 0..7 && r in 0..7) r * 8 + c else null
                        }
                        val start = squareAt(down.position) ?: return@awaitEachGesture
                        val draggable = currentCanDrag(start)
                        var travel = Offset.Zero
                        var dragging = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                if (dragging) currentDrop(squareAt(change.position)) else currentTap(start)
                                return@awaitEachGesture
                            }
                            travel += change.positionChange()
                            if (!dragging && draggable && travel.getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                currentDragStart(start)
                            }
                            if (dragging) {
                                change.consume()
                                currentDrag(start, change.position)
                            }
                        }
                        if (dragging) currentDrop(null)
                    }
                },
        ) {
            val cell = size.width / 8
            val r = cell * 0.4f
            fun centre(x: Float, y: Float) = Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
            for (s in 0 until 64) {
                val light = !isDark(s)
                drawRect(
                    if (light) (if (dark) LIGHT_SQUARE_DARK else LIGHT_SQUARE) else (if (dark) DARK_SQUARE_DARK else DARK_SQUARE),
                    Offset(col(s) * cell, row(s) * cell),
                    Size(cell, cell),
                )
                if (s in look.marks) drawRect(Color.White.copy(alpha = 0.14f), Offset(col(s) * cell, row(s) * cell), Size(cell, cell))
            }
            for (s in look.rings) drawCircle(Color.White.copy(alpha = pulse), r * 1.12f, centre(col(s).toFloat(), row(s).toFloat()), style = Stroke(cell * 0.05f))
            for (s in 0 until 64) {
                val piece = look.pieces[s]
                if (piece == EMPTY) continue
                val c = centre(col(s).toFloat(), row(s).toFloat())
                drawPiece(c, r, piece, look.fade[s] ?: 1f, turned = look.yellowCrownsTurned && owner(piece) == YELLOW, lifted = s == look.picked)
                if (s == look.picked) drawCircle(Color.White, r * 1.1f, c, style = Stroke(cell * 0.06f))
            }
            for (s in look.dots) drawCircle(Color.White.copy(alpha = 0.6f), r * 0.32f, centre(col(s).toFloat(), row(s).toFloat()))
            look.floating?.let { f ->
                drawPiece(centre(f.x, f.y), r, f.piece, 1f, turned = look.yellowCrownsTurned && owner(f.piece) == YELLOW, lifted = true)
            }
        }
        BoardTargets(targets(), Modifier.matchParentSize())
    }
}

/**
 * A piece seen from above: a rim, a face with a groove round it, and a crown for a king. A [lifted] piece is drawn a
 * little bigger with a deeper shadow; a [turned] crown faces the top of the board.
 */
private fun DrawScope.drawPiece(c: Offset, radius: Float, piece: Int, alpha: Float, turned: Boolean, lifted: Boolean) {
    val r = if (lifted) radius * 1.08f else radius
    val color = sideColor(owner(piece))
    drawCircle(Color.Black.copy(alpha = if (lifted) 0.35f else 0.25f), r, c + Offset(0f, r * (if (lifted) 0.2f else 0.1f)), alpha = alpha)
    drawCircle(if (owner(piece) == YELLOW) GoogleColors.YellowInk else lerp(color, Color.Black, 0.25f), r, c, alpha = alpha)
    drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color), c - Offset(r * 0.3f, r * 0.3f), r), r * 0.86f, c, alpha = alpha)
    drawCircle(lerp(color, Color.Black, 0.18f), r * 0.6f, c, alpha = alpha, style = Stroke(r * 0.07f))
    if (isKing(piece)) {
        val ink = if (owner(piece) == YELLOW) GoogleColors.YellowInk else Color.White
        val w = r * 0.95f
        rotate(if (turned) 180f else 0f, pivot = c) {
            val left = c.x - w / 2
            val top = c.y - w * 0.4f
            val crown = Path().apply {
                moveTo(left, top + w * 0.7f)
                lineTo(left, top + w * 0.12f)
                lineTo(left + w * 0.27f, top + w * 0.42f)
                lineTo(left + w * 0.5f, top)
                lineTo(left + w * 0.73f, top + w * 0.42f)
                lineTo(left + w, top + w * 0.12f)
                lineTo(left + w, top + w * 0.7f)
                close()
            }
            drawPath(crown, ink, alpha = alpha)
            drawRect(ink, Offset(left, top + w * 0.76f), Size(w, w * 0.08f), alpha = alpha)
            for (x in floatArrayOf(0f, 0.5f, 1f)) drawCircle(ink, w * 0.07f, Offset(left + w * x, top + (if (x == 0.5f) 0f else w * 0.12f)), alpha = alpha)
        }
    }
}
