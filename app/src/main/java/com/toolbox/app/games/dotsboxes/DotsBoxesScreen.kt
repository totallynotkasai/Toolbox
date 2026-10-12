package com.toolbox.app.games.dotsboxes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameMode
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Boxes along each side. */
private val SIZES = listOf(3, 4, 5)

private fun playerColor(p: Int) = if (p == 1) GoogleColors.Blue else GoogleColors.Red
private fun playerName(p: Int) = if (p == 1) "Blue" else "Red"

/** The scoreboard's name for player [p]: their colour between two players, else You or CPU. */
private fun scoreLabel(p: Int, cpu: Int) = if (cpu == 0) playerName(p) else if (p == cpu) "CPU" else "You"

/**
 * Where [line] is, as TalkBack says it: by the box it's a side of, "Row 2, column 3, top edge". A line between two
 * boxes is named as the top or left edge of the one below or to the right; only the outside lines along the bottom
 * and right are bottom and right edges.
 */
internal fun dotsLineName(game: DotsBoxes, line: Int): String {
    val (r, c) = game.ends(line).first
    return if (game.isHorizontal(line)) {
        if (r < game.rows) "Row ${r + 1}, column ${c + 1}, top edge" else "Row $r, column ${c + 1}, bottom edge"
    } else {
        if (c < game.cols) "Row ${r + 1}, column ${c + 1}, left edge" else "Row ${r + 1}, column $c, right edge"
    }
}

/**
 * What TalkBack says once [mover] has drawn the lines from [before] to [after]: where, when it was one line, or how
 * many, and the score when boxes were closed ("Computer drew 2 lines and closed a box. You 3, Computer 4"). [name]
 * names each player in the score.
 */
internal fun dotsTurnSummary(before: DotsBoxes, after: DotsBoxes, mover: String, name: (Int) -> String): String {
    val drawn = after.moves.drop(before.moves.size)
    val closed = after.score(1) + after.score(2) - before.score(1) - before.score(2)
    return buildString {
        append(mover).append(" drew ")
        append(if (drawn.size == 1) dotsLineName(after, drawn[0]).replaceFirstChar { it.lowercase() } else "${drawn.size} lines")
        if (closed > 0) {
            append(if (closed == 1) " and closed a box" else " and closed $closed boxes")
            append(". ${name(1)} ${after.score(1)}, ${name(2)} ${after.score(2)}")
        }
    }
}

@Composable
fun DotsBoxesScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()
    val guard = rememberGameGuard()
    var mode by rememberGameMode("dots_mode")
    var size by rememberPref("dots_size", 4)
    val tally = rememberTally("dots_tally_${mode.key}")
    var moves by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var cpuFirst by rememberSaveable { mutableStateOf(false) } // alternates each game against the computer
    val game = remember(moves, size) { DotsBoxes.fromMoves(size, size, moves) }
    val cpu = if (!mode.vsComputer) 0 else if (cpuFirst) 1 else 2
    val human = if (cpu == 0) 0 else 3 - cpu
    val cpuTurn = !game.isOver && game.toMove == cpu
    // You have drawn a line: any move in two-player games, any but the computer's opening one otherwise. (No box
    // can be closed on the first move, so the computer never opens with more than one.)
    val underWay = !game.isOver && moves.size > (if (cpu == 1) 1 else 0)

    // The newest line (and any boxes it closed) animates in. Only the board's drawing reads it.
    val appear = remember { Animatable(1f) }
    LaunchedEffect(moves.size) {
        if (moves.isEmpty()) { appear.snapTo(1f); return@LaunchedEffect }
        appear.snapTo(0f)
        appear.animateTo(1f, tween(220))
    }

    fun spokenName(p: Int) = if (cpu == 0) playerName(p) else if (p == human) "You" else "Computer"

    fun play(line: Int) {
        if (game.isOver || game.isDrawn(line)) return
        val mover = game.toMove
        val next = game.play(line)
        moves = next.moves
        // TalkBack hears every line in a two-player game, the computer's whole turn once it hands back (it carries on
        // while it closes boxes), and your own line only when it scores; otherwise the turn line says what changed.
        when {
            cpu == 0 -> say(dotsTurnSummary(game, next, playerName(mover), ::spokenName))
            mover == cpu -> if (next.isOver || next.toMove != cpu) {
                var from = next.moves.size - 1
                while (from > 0 && DotsBoxes.fromMoves(size, size, next.moves.take(from - 1)).toMove == cpu) from--
                say(dotsTurnSummary(DotsBoxes.fromMoves(size, size, next.moves.take(from)), next, "Computer", ::spokenName))
            }
            next.score(mover) > game.score(mover) -> say(dotsTurnSummary(game, next, "You", ::spokenName))
        }
        when {
            next.isOver -> {
                haptics.heavy()
                val w = next.winner
                // In tallies against the computer, side a is you.
                tally.record(if (cpu == 0 || w == 0) w else if (w == human) 1 else 2)
            }
            next.score(game.toMove) > game.score(game.toMove) -> haptics.click()
            else -> haptics.tick()
        }
    }

    fun newGame(nextMode: GameMode = mode, nextSize: Int = size) {
        cpuFirst = when {
            nextMode != mode -> false
            nextMode.vsComputer && moves.isNotEmpty() -> !cpuFirst
            else -> cpuFirst
        }
        mode = nextMode
        size = nextSize
        moves = emptyList()
    }

    fun undo() {
        if (game.isOver || moves.isEmpty()) return
        var m = moves.dropLast(1)
        // Against the computer, keep going back until it's your turn again.
        while (m.isNotEmpty() && cpu != 0 && DotsBoxes.fromMoves(size, size, m).toMove != human) m = m.dropLast(1)
        moves = m
    }

    LaunchedEffect(moves, mode, cpuFirst, size) {
        if (!cpuTurn) return@LaunchedEffect
        // Quicker when it's carrying on after closing a box.
        val carryingOn = moves.isNotEmpty() && DotsBoxes.fromMoves(size, size, moves.dropLast(1)).toMove == cpu
        delay(if (carryingOn) 380 else 550)
        play(dotsMove(game))
    }

    // Replaying every earlier position is the slow part, so it's only redone when a move is made.
    val canUndo = remember(game, cpu) {
        !game.isOver && moves.isNotEmpty() && !cpuTurn &&
            (cpu == 0 || (0 until moves.size).any { DotsBoxes.fromMoves(size, size, moves.take(it)).toMove == human })
    }
    ToolScaffold(
        title = "Dots and Boxes",
        onBack = onBack,
        actions = {
            IconButton(onClick = ::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
            NewGameButton(onClick = { guard.run(underWay) { newGame() } }, enabled = moves.isNotEmpty())
            ScoresMenu(onReset = tally::reset)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameModeChips(mode, onSelect = { m -> guard.run(underWay) { newGame(nextMode = m) } })
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                for (s in SIZES) {
                    FilterChip(
                        selected = size == s,
                        onClick = { if (size != s) guard.run(underWay) { newGame(nextSize = s) } },
                        label = { Text("$s × $s", Modifier.semantics { contentDescription = "$s by $s" }) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // This game's boxes, then the running tally underneath.
            ScoreRow(
                listOf(
                    Score(scoreLabel(1, cpu), game.score(1), playerColor(1)),
                    Score(scoreLabel(2, cpu), game.score(2), playerColor(2)),
                ),
            )
            val t = tally.value
            Text(
                if (cpu == 0) t.summary("Games", "Blue", "Red") else t.summary("Games", "You", "CPU"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Board(
                    game = game,
                    cpu = cpu,
                    appear = { appear.value },
                    enabled = !game.isOver && !cpuTurn,
                    onLine = ::play,
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            val result = when {
                !game.isOver -> null
                game.winner == 0 -> "It's a draw"
                cpu == 0 -> "${playerName(game.winner)} wins!"
                game.winner == human -> "You win!"
                else -> "The computer wins"
            }
            GameFooter(
                over = result?.let {
                    GameOver(it, if (cpu == 0) null else if (cpuFirst) "You start the next game" else "The computer starts the next game")
                },
                turn = when {
                    cpuTurn -> "Computer's turn"
                    cpu == 0 -> "${playerName(game.toMove)}'s turn"
                    else -> "Your turn"
                },
                turnColor = playerColor(game.toMove),
                hint = if (moves.isEmpty()) "Close a box to score and go again" else " ",
                onPlayAgain = { newGame() },
            )
        }
    }
    GameGuardDialog(guard)
}

/**
 * The board. [cpu] is the computer's player number, or 0 between two players, for naming who drew what. TalkBack finds
 * each line, and each claimed box, as its own item over the drawing.
 */
@Composable
private fun Board(game: DotsBoxes, cpu: Int, appear: () -> Float, enabled: Boolean, onLine: (Int) -> Unit, modifier: Modifier) {
    val guide = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val last = game.moves.lastOrNull()
    // A box is claimed by its last side, so any claimed box next to the newest line was just closed by it.
    val justClosed = last?.let { l -> game.boxesOf(l).filter { game.boxOwner(it / game.cols, it % game.cols) != 0 } }.orEmpty()
    fun who(p: Int) = if (cpu == 0) playerName(p) else if (p == cpu) "the computer" else "you"

    var side by remember { mutableIntStateOf(0) }
    val targets = buildList {
        if (side == 0) return@buildList
        val cell = side / (game.cols + 1f)
        // Half-cell squares on each line's middle and each box's centre, which meet without overlapping.
        val half = cell / 4
        for (line in 0 until game.lineCount) {
            val (a, b) = game.ends(line)
            val mid = Offset((a.second + b.second + 1) / 2f * cell, (a.first + b.first + 1) / 2f * cell)
            val owner = game.lineOwner(line)
            add(
                BoardTarget(
                    key = "line$line",
                    rect = Rect(mid, half),
                    description = dotsLineName(game, line),
                    state = if (owner == 0) "not drawn" else "drawn by ${who(owner)}",
                    clickLabel = "Draw line",
                    onClick = if (enabled && owner == 0) ({ onLine(line) }) else null,
                ),
            )
        }
        for (r in 0 until game.rows) for (c in 0 until game.cols) {
            val owner = game.boxOwner(r, c)
            if (owner == 0) continue
            val name = who(owner).let { if (it == "you") "yours" else "$it's" }
            add(BoardTarget("box$r,$c", Rect(Offset((c + 1) * cell, (r + 1) * cell), half), "Box row ${r + 1}, column ${c + 1}, $name", button = false))
        }
    }

    Box(
        modifier
            .onSizeChanged { side = it.width }
            .semantics { contentDescription = "Dots and boxes board, ${game.freeLines.size} lines left" },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(game, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { p ->
                        val cell = size.width / (game.cols + 1f)
                        val gx = p.x / cell - 0.5f
                        val gy = p.y / cell - 0.5f
                        // The nearest horizontal and vertical line to the tap; take whichever is closer, if close enough.
                        // Within 0.45 of a cell rather than half, so a tap in the very middle of a box still does nothing.
                        val hr = gy.roundToInt(); val hc = floor(gx).toInt()
                        val vc = gx.roundToInt(); val vr = floor(gy).toInt()
                        val candidates = buildList {
                            if (hr in 0..game.rows && hc in 0 until game.cols) add(game.horizontal(hr, hc) to abs(gy - hr))
                            if (vr in 0 until game.rows && vc in 0..game.cols) add(game.vertical(vr, vc) to abs(gx - vc))
                        }.filter { !game.isDrawn(it.first) && it.second < 0.45f }
                        candidates.minByOrNull { it.second }?.let { onLine(it.first) }
                    }
                },
        ) {
            val cell = size.width / (game.cols + 1f)
            fun dotAt(r: Int, c: Int) = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
            val stroke = cell * 0.09f
            // Each claimed box carries its owner's initial as well as their tint, so it doesn't rest on colour alone.
            val letterStyle = TextStyle(color = dot, fontSize = (cell * 0.34f).toSp(), fontWeight = FontWeight.Bold)

            for (r in 0 until game.rows) for (c in 0 until game.cols) {
                val owner = game.boxOwner(r, c)
                if (owner == 0) continue
                val grow = if (r * game.cols + c in justClosed) appear() else 1f
                val inset = cell * (0.12f + 0.3f * (1 - grow))
                val tl = dotAt(r, c) + Offset(inset, inset)
                drawRoundRect(
                    playerColor(owner).copy(alpha = 0.5f), tl, Size(cell - 2 * inset, cell - 2 * inset),
                    cornerRadius = CornerRadius(cell * 0.12f),
                )
                val letter = measurer.measure(scoreLabel(owner, cpu).take(1), letterStyle)
                val centre = dotAt(r, c) + Offset(cell / 2, cell / 2)
                drawText(letter, topLeft = centre - Offset(letter.size.width / 2f, letter.size.height / 2f), alpha = grow)
            }
            for (line in 0 until game.lineCount) {
                val (a, b) = game.ends(line)
                val p0 = dotAt(a.first, a.second)
                val p1 = dotAt(b.first, b.second)
                val owner = game.lineOwner(line)
                if (owner == 0) {
                    drawLine(guide, p0, p1, strokeWidth = stroke * 0.3f, cap = StrokeCap.Round)
                } else {
                    // The newest line draws itself out from the middle.
                    val t = if (line == last) appear() else 1f
                    val mid = (p0 + p1) / 2f
                    drawLine(
                        playerColor(owner), mid + (p0 - mid) * t, mid + (p1 - mid) * t,
                        strokeWidth = if (line == last) stroke * 1.25f else stroke, cap = StrokeCap.Round,
                    )
                }
            }
            for (r in 0..game.rows) for (c in 0..game.cols) drawCircle(dot, stroke * 0.95f, dotAt(r, c))
        }
        BoardTargets(targets, Modifier.matchParentSize())
    }
}
