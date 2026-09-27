package com.toolbox.app.games.dotsboxes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ScoreColumn
import com.toolbox.app.ui.common.Tally
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private enum class Mode(val label: String, val vsComputer: Boolean) {
    // "Easy" is kept as the name because saved settings and win counts use it.
    Easy("Solo", true), TwoPlayer("2 players", false)
}

/** Boxes along each side. */
private val SIZES = listOf(3, 4, 5)

private fun playerColor(p: Int) = if (p == 1) GoogleColors.Blue else GoogleColors.Red
private fun playerName(p: Int) = if (p == 1) "Blue" else "Red"

@Composable
fun DotsBoxesScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    var modeName by rememberPref("dots_mode", Mode.Easy.name)
    val mode = Mode.entries.firstOrNull { it.name == modeName } ?: Mode.Easy
    var size by rememberPref("dots_size", 4)
    val (tally, setTally) = rememberTally("dots_tally_${mode.name}")
    var moves by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var cpuFirst by rememberSaveable { mutableStateOf(false) } // alternates each game against the computer
    var menu by remember { mutableStateOf(false) }
    val game = remember(moves, size) { DotsBoxes.fromMoves(size, size, moves) }
    val cpu = if (!mode.vsComputer) 0 else if (cpuFirst) 1 else 2
    val human = if (cpu == 0) 0 else 3 - cpu
    val cpuTurn = !game.isOver && game.toMove == cpu

    // The newest line (and any boxes it closed) animates in.
    val appear = remember { Animatable(1f) }
    LaunchedEffect(moves.size) {
        if (moves.isEmpty()) { appear.snapTo(1f); return@LaunchedEffect }
        appear.snapTo(0f)
        appear.animateTo(1f, tween(220))
    }

    fun play(line: Int) {
        if (game.isOver || game.isDrawn(line)) return
        val next = game.play(line)
        moves = next.moves
        when {
            next.isOver -> {
                haptics.heavy()
                val w = next.winner
                // In tallies against the computer, side a is you.
                setTally(tally.record(if (cpu == 0 || w == 0) w else if (w == human) 1 else 2))
            }
            next.score(game.toMove) > game.score(game.toMove) -> haptics.click()
            else -> haptics.tick()
        }
    }

    fun newGame() {
        if (cpu != 0 && moves.isNotEmpty()) cpuFirst = !cpuFirst
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

    val canUndo = !game.isOver && moves.isNotEmpty() && !cpuTurn &&
        (cpu == 0 || (0 until moves.size).any { DotsBoxes.fromMoves(size, size, moves.take(it)).toMove == human })
    ToolScaffold(
        title = "Dots and Boxes",
        onBack = onBack,
        actions = {
            IconButton(onClick = ::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Reset scores") }, onClick = { setTally(Tally()); menu = false })
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in Mode.entries) {
                    FilterChip(
                        selected = mode == m,
                        onClick = { if (mode != m) { modeName = m.name; moves = emptyList(); cpuFirst = false } },
                        label = { Text(m.label) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in SIZES) {
                    FilterChip(
                        selected = size == s,
                        onClick = { if (size != s) { size = s; moves = emptyList() } },
                        label = { Text("$s × $s") },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // This game's boxes, then the running tally underneath.
            Row(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ScoreColumn(if (cpu == 0) "Blue" else if (human == 1) "You" else "CPU", game.score(1), playerColor(1))
                ScoreColumn(if (cpu == 0) "Red" else if (human == 2) "You" else "CPU", game.score(2), playerColor(2))
            }
            Text(
                if (cpu == 0) "Games: Blue ${tally.a} · Red ${tally.b} · Drawn ${tally.draws}"
                else "Games: You ${tally.a} · CPU ${tally.b} · Drawn ${tally.draws}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Board(
                    game = game,
                    appear = appear.value,
                    enabled = !game.isOver && !cpuTurn,
                    onLine = ::play,
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            val status = when {
                game.isOver -> when {
                    game.winner == 0 -> "It's a draw"
                    cpu == 0 -> "${playerName(game.winner)} wins!"
                    game.winner == human -> "You win!"
                    else -> "The computer wins"
                }
                cpuTurn -> "Computer's turn"
                cpu == 0 -> "${playerName(game.toMove)}'s turn"
                else -> "Your turn"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(32.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                if (!game.isOver) {
                    Box(Modifier.size(16.dp).background(playerColor(game.toMove), CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                Text(status, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (moves.isEmpty()) "Close a box to score and go again" else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::newGame) { Text(if (game.isOver) "Play again" else "New game") }
        }
    }
}

@Composable
private fun Board(game: DotsBoxes, appear: Float, enabled: Boolean, onLine: (Int) -> Unit, modifier: Modifier) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.onSurface
    val last = game.moves.lastOrNull()
    // A box is claimed by its last side, so any claimed box next to the newest line was just closed by it.
    val justClosed = last?.let { l -> game.boxesOf(l).filter { game.boxOwner(it / game.cols, it % game.cols) != 0 } }.orEmpty()
    Canvas(
        modifier
            .semantics { contentDescription = "Dots and boxes board, ${game.freeLines.size} lines left" }
            .pointerInput(game, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { p ->
                    val cell = size.width / (game.cols + 1f)
                    val gx = p.x / cell - 0.5f
                    val gy = p.y / cell - 0.5f
                    // The nearest horizontal and vertical line to the tap; take whichever is closer, if close enough.
                    val hr = gy.roundToInt(); val hc = floor(gx).toInt()
                    val vc = gx.roundToInt(); val vr = floor(gy).toInt()
                    val candidates = buildList {
                        if (hr in 0..game.rows && hc in 0 until game.cols) add(game.horizontal(hr, hc) to abs(gy - hr))
                        if (vr in 0 until game.rows && vc in 0..game.cols) add(game.vertical(vr, vc) to abs(gx - vc))
                    }.filter { !game.isDrawn(it.first) && it.second < 0.4f }
                    candidates.minByOrNull { it.second }?.let { onLine(it.first) }
                }
            },
    ) {
        val cell = size.width / (game.cols + 1f)
        fun dotAt(r: Int, c: Int) = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
        val stroke = cell * 0.09f

        for (r in 0 until game.rows) for (c in 0 until game.cols) {
            val owner = game.boxOwner(r, c)
            if (owner == 0) continue
            val grow = if (r * game.cols + c in justClosed) appear else 1f
            val inset = cell * (0.12f + 0.3f * (1 - grow))
            val tl = dotAt(r, c) + Offset(inset, inset)
            drawRoundRect(
                playerColor(owner).copy(alpha = 0.28f), tl, Size(cell - 2 * inset, cell - 2 * inset),
                cornerRadius = CornerRadius(cell * 0.12f),
            )
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
                val t = if (line == last) appear else 1f
                val mid = (p0 + p1) / 2f
                drawLine(
                    playerColor(owner), mid + (p0 - mid) * t, mid + (p1 - mid) * t,
                    strokeWidth = if (line == last) stroke * 1.25f else stroke, cap = StrokeCap.Round,
                )
            }
        }
        for (r in 0..game.rows) for (c in 0..game.cols) drawCircle(dot, stroke * 0.95f, dotAt(r, c))
    }
}
