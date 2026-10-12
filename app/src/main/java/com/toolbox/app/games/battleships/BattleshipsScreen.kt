package com.toolbox.app.games.battleships

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.BoardTarget
import com.toolbox.app.ui.common.BoardTargets
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameModeChips
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.GameOverCard
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ScoresMenu
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameMode
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.theme.GoogleColors
import com.toolbox.app.ui.theme.ink
import kotlinx.coroutines.delay

/**
 * Place: [player] arranges their fleet. Handoff: the screen is hidden until the next player is holding the phone.
 * Aim: [player] fires. Result: two players only, showing the shot before passing the phone. CpuTurn: the computer
 * is about to fire. Over: [player] has won.
 */
private enum class Phase { Place, Handoff, Aim, Result, CpuTurn, Over }

private val HIT = GoogleColors.Red

/** [ship] is "the Destroyer" or "your Destroyer". */
private fun resultText(r: ShotResult?, ship: String?) = when (r) {
    ShotResult.Miss -> "Miss"
    ShotResult.Hit -> "Hit!"
    ShotResult.Sunk -> "Sunk $ship!"
    null -> ""
}

/** A square's name as on a paper board: a letter for the column and a number for the row, "B7". */
internal fun cellName(cell: Int) = "${'A' + cell % GRID}${cell / GRID + 1}"

/**
 * What TalkBack says about [cell] after its name. In the enemy's waters ([showShips] false) that's only what's been
 * found there: "not fired", "miss", "hit" or "sunk Destroyer". A fleet on show also names the ship or the water, such
 * as "your Destroyer, hit" or "water, miss", with [whose] ("your ") before the ship.
 */
internal fun cellState(board: Board, cell: Int, showShips: Boolean, whose: String = ""): String {
    val ship = board.shipAt(cell)
    val shot = board.isShot(cell)
    if (!showShips) return when {
        !shot -> "not fired"
        ship < 0 -> "miss"
        board.isSunk(ship) -> "sunk ${Fleet.NAMES[ship]}"
        else -> "hit"
    }
    val found = when {
        !shot -> null
        ship < 0 -> "miss"
        board.isSunk(ship) -> "sunk"
        else -> "hit"
    }
    return listOfNotNull(if (ship < 0) "water" else "$whose${Fleet.NAMES[ship]}", found).joinToString(", ")
}

/** White or near-black, whichever stands out more against [fill]: for a hit marked on a ship. */
private fun markOn(fill: Color): Color {
    val l = fill.luminance()
    return if (1.05f / (l + 0.05f) >= (l + 0.05f) / 0.0637f) Color.White else Color(0xFF1F1F1F)
}

@Composable
fun BattleshipsScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()
    val synth = rememberToneSynth()
    val guard = rememberGameGuard()
    var mode by rememberGameMode("battleships_mode")
    val tally = rememberTally("battleships_tally_${mode.key}")

    // Everything is kept as text so a rotation (or the app being reclaimed) doesn't lose the game.
    var phaseName by rememberSaveable { mutableStateOf(Phase.Place.name) }
    val phase = Phase.valueOf(phaseName)
    var player by rememberSaveable { mutableIntStateOf(0) }
    var afterHandoff by rememberSaveable { mutableStateOf(Phase.Place.name) }
    var draft by rememberSaveable { mutableStateOf(Board(Fleet.random()).encode()) }
    var board0 by rememberSaveable { mutableStateOf("") }
    var board1 by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var lastShot by rememberSaveable { mutableIntStateOf(-1) }
    var lastResult by rememberSaveable { mutableStateOf<String?>(null) }
    // The other side's latest shot at the player on screen, shown on their own fleet.
    var incomingShot by rememberSaveable { mutableIntStateOf(-1) }
    var incomingResult by rememberSaveable { mutableStateOf<String?>(null) }

    // A short note shown in place of the shot results, such as after tapping a square already fired at.
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) { if (notice != null) { delay(1800); notice = null } }

    val boards = listOf(Board.decode(board0), Board.decode(board1))
    fun setBoard(p: Int, b: Board) { if (p == 0) board0 = b.encode() else board1 = b.encode() }
    val draftBoard = Board.decode(draft) ?: Board(Fleet.random())
    val vsCpu = mode.vsComputer
    fun name(p: Int) = if (vsCpu) (if (p == 0) "You" else "The computer") else "Player ${p + 1}"
    // Once the first fleet is ready, a new game would throw away the placing (and any shots).
    val underWay = board0.isNotEmpty() && phase != Phase.Over

    fun newGame() {
        phaseName = Phase.Place.name
        player = 0
        draft = Board(Fleet.random()).encode()
        board0 = ""; board1 = ""
        selected = -1; lastShot = -1; lastResult = null; incomingShot = -1; incomingResult = null; notice = null
    }

    fun handoffTo(next: Phase, p: Int) {
        player = p
        afterHandoff = next.name
        phaseName = Phase.Handoff.name
    }

    fun ready() {
        setBoard(player, draftBoard)
        selected = -1
        when {
            // The computer's ships may touch, as yours can, so sinking one doesn't give away the squares around it.
            vsCpu -> { setBoard(1, Board(Fleet.random(spaced = false))); player = 0; phaseName = Phase.Aim.name }
            player == 0 -> { draft = Board(Fleet.random()).encode(); handoffTo(Phase.Place, 1) }
            else -> handoffTo(Phase.Aim, 0)
        }
        haptics.click()
    }

    fun feedback(r: ShotResult) {
        when (r) {
            ShotResult.Miss -> { synth.play(330f, 90, 0.25f); haptics.tick() }
            ShotResult.Hit -> { synth.knock(0.9f); haptics.click() }
            ShotResult.Sunk -> { synth.buzz(260); haptics.heavy() }
        }
    }

    fun fire(cell: Int) {
        if (phase != Phase.Aim) return
        val target = boards[1 - player] ?: return
        if (target.isShot(cell)) {
            notice = "Already fired there. Pick another square"
            haptics.heavy()
            return
        }
        val (next, r) = target.fire(cell) ?: return
        notice = null
        setBoard(1 - player, next)
        lastShot = cell
        lastResult = resultText(r, next.shipAt(cell).takeIf { it >= 0 }?.let { "the ${Fleet.NAMES[it]}" })
        incomingResult = null
        incomingShot = -1
        feedback(r)
        when {
            next.allSunk -> {
                phaseName = Phase.Over.name
                tally.record(if (vsCpu) 1 else player + 1)
            }
            vsCpu -> phaseName = Phase.CpuTurn.name
            else -> phaseName = Phase.Result.name
        }
    }

    LaunchedEffect(phaseName) {
        if (phase != Phase.CpuTurn) return@LaunchedEffect
        delay(750)
        val mine = boards[0] ?: return@LaunchedEffect
        val cell = aiShot(mine)
        val (next, r) = mine.fire(cell) ?: return@LaunchedEffect
        setBoard(0, next)
        incomingShot = cell
        incomingResult = resultText(r, next.shipAt(cell).takeIf { it >= 0 }?.let { "your ${Fleet.NAMES[it]}" })
        feedback(r)
        if (next.allSunk) {
            player = 1
            phaseName = Phase.Over.name
            tally.record(2)
        } else {
            phaseName = Phase.Aim.name
        }
    }

    // Placing by taps, as the hint says: a ship, then where its front goes, or the same ship again to turn it.
    fun place(cell: Int) {
        val hit = draftBoard.shipAt(cell)
        val ships = draftBoard.ships
        when {
            hit >= 0 && hit == selected -> {
                val turned = ships.toMutableList().also { it[hit] = ships[hit].rotated() }
                if (Fleet.isValid(turned)) {
                    draft = Board(turned).encode(); haptics.tick()
                    say("${Fleet.NAMES[hit]} turned ${if (turned[hit].horizontal) "across" else "down"}")
                } else {
                    haptics.heavy(); say("No room to turn it")
                }
            }
            hit >= 0 -> { selected = hit; haptics.tick(); say("${Fleet.NAMES[hit]} selected") }
            selected >= 0 -> {
                val moved = ships.toMutableList().also { it[selected] = ships[selected].movedTo(cell % GRID, cell / GRID) }
                if (Fleet.isValid(moved)) {
                    draft = Board(moved).encode(); haptics.tick()
                    say("${Fleet.NAMES[selected]} moved to ${cellName(cell)}")
                } else {
                    haptics.heavy(); say("It doesn't fit there")
                }
            }
        }
    }

    // What a double tap on a square of your fleet does while placing, as TalkBack offers it.
    fun placeLabel(cell: Int): String? {
        val hit = draftBoard.shipAt(cell)
        return when {
            hit >= 0 && hit == selected -> "Turn ${Fleet.NAMES[hit]}"
            hit >= 0 -> "Select ${Fleet.NAMES[hit]}"
            selected >= 0 -> "Move ${Fleet.NAMES[selected]} here"
            else -> null
        }
    }

    ToolScaffold(
        title = "Battleships",
        onBack = onBack,
        actions = {
            NewGameButton(onClick = { guard.run(underWay) { newGame() } }, enabled = board0.isNotEmpty())
            ScoresMenu(onReset = tally::reset)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val column = Modifier.widthIn(max = 460.dp).fillMaxWidth()
            val choosingMode = phase == Phase.Over || (phase == Phase.Place && player == 0)
            if (choosingMode) {
                GameModeChips(mode, onSelect = { m -> guard.run(underWay) { mode = m; newGame() } })
                val t = tally.value
                Text(
                    if (vsCpu) t.summary("Games", "You", "Computer", withDraws = false) else t.summary("Games", "Player 1", "Player 2", withDraws = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            when (phase) {
                Phase.Place -> {
                    Text(if (vsCpu) "Place your fleet" else "${name(player)}: place your fleet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tap a ship, then tap where its front should go. Tap it again to turn it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Grid(
                        board = draftBoard,
                        showShips = true,
                        label = "Your fleet",
                        whose = "your ",
                        selected = selected,
                        clickLabel = ::placeLabel,
                        onTap = ::place,
                        modifier = column,
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { draft = Board(Fleet.random()).encode(); selected = -1 }) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Shuffle")
                        }
                        Button(onClick = ::ready) { Text("Ready") }
                    }
                }

                Phase.Handoff -> {
                    Spacer(Modifier.height(48.dp))
                    Icon(Icons.Filled.Sailing, contentDescription = null, tint = GoogleColors.Blue, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Pass the phone to ${name(player)}", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${name(1 - player)}, no peeking!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        phaseName = afterHandoff
                        incomingShot = lastShot
                        incomingResult = lastResult
                        lastShot = -1
                        lastResult = null
                    }) { Text("I'm ${name(player)}") }
                }

                Phase.Aim, Phase.Result, Phase.CpuTurn, Phase.Over -> {
                    // During a turn the shooter sees the enemy waters; at the end, both fleets are revealed.
                    val shooter = if (phase == Phase.Over && vsCpu) 0 else player
                    val enemy = boards[1 - shooter] ?: return@Column
                    val own = boards[shooter] ?: return@Column
                    if (phase == Phase.Over) {
                        // The winner's shots are the ones at the loser's fleet.
                        val shots = boards[1 - player]?.shots?.size ?: 0
                        GameOverCard(
                            GameOver(
                                if (vsCpu) (if (player == 0) "You win!" else "The computer wins") else "${name(player)} wins!",
                                when {
                                    !vsCpu -> "Sank the whole fleet in $shots shots"
                                    player == 0 -> "You sank the whole fleet in $shots shots"
                                    else -> "It sank your fleet in $shots shots"
                                },
                            ),
                            onAction = ::newGame,
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text(
                            if (phase == Phase.CpuTurn) "Computer's turn…" else if (vsCpu) "Your turn: fire!" else "${name(player)}: fire!",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    FleetStatus(enemy, column)
                    Spacer(Modifier.height(8.dp))
                    Grid(
                        board = enemy,
                        showShips = phase == Phase.Over,
                        label = "Enemy waters",
                        lastShot = lastShot,
                        clickLabel = { if (enemy.isShot(it)) null else "Fire" },
                        onTap = if (phase == Phase.Aim) ::fire else null,
                        modifier = column,
                    )
                    Spacer(Modifier.height(8.dp))
                    // TalkBack also hears where each shot landed, which the board shows but the line doesn't say.
                    val spoken = listOfNotNull(
                        lastResult?.takeIf { it.isNotEmpty() && lastShot >= 0 }?.let {
                            "${if (vsCpu) "You" else name(player)} fired at ${cellName(lastShot)}: $it"
                        },
                        incomingResult?.takeIf { it.isNotEmpty() && incomingShot >= 0 }?.let {
                            "${if (vsCpu) "The computer" else name(1 - player)} fired at ${cellName(incomingShot)}: $it"
                        },
                    ).joinToString(". ")
                    Text(
                        notice ?: listOfNotNull(
                            lastResult?.takeIf { it.isNotEmpty() }?.let { if (vsCpu) "You: $it" else it },
                            incomingResult?.takeIf { it.isNotEmpty() }?.let { if (vsCpu) "Computer: $it" else "${name(1 - player)}: $it" },
                        ).joinToString("   ·   ").ifEmpty { if (phase == Phase.Aim) "Tap a square to fire" else " " },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            if (notice == null && spoken.isNotEmpty()) contentDescription = spoken
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    when (phase) {
                        Phase.Result -> Button(onClick = { handoffTo(Phase.Aim, 1 - player) }) { Text("Pass to ${name(1 - player)}") }
                        else -> Unit
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (vsCpu) "Your fleet" else "${name(shooter)}'s fleet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Grid(
                        board = own, showShips = true, label = "Your fleet", whose = "your ",
                        lastShot = if (phase == Phase.Over) -1 else incomingShot, onTap = null,
                        modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth(0.6f),
                    )
                }
            }
        }
    }
    GameGuardDialog(guard)
}

/** The enemy ships, struck through once sunk. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetStatus(board: Board, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        board.ships.forEachIndexed { i, ship ->
            val sunk = board.isSunk(i)
            Text(
                "${Fleet.NAMES[i]} (${ship.length})",
                style = MaterialTheme.typography.labelLarge,
                color = if (sunk) HIT.ink() else MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (sunk) TextDecoration.LineThrough else null,
                // The strike-through isn't read out, so it's said in words.
                modifier = Modifier.semantics {
                    contentDescription = if (sunk) "${Fleet.NAMES[i]}, sunk" else "${Fleet.NAMES[i]}, ${ship.length} squares"
                },
            )
        }
    }
}

/**
 * One player's grid. [label] names it for TalkBack ("Enemy waters"), which finds each square as its own item; a square
 * can be chosen when [onTap] is set and [clickLabel] gives it an action ("Fire"). [whose] goes before a ship's name.
 */
@Composable
private fun Grid(
    board: Board,
    showShips: Boolean,
    label: String,
    modifier: Modifier,
    selected: Int = -1,
    lastShot: Int = -1,
    whose: String = "",
    clickLabel: (Int) -> String? = { null },
    onTap: ((Int) -> Unit)?,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.3f
    val water = if (dark) Color(0xFF16324A) else Color(0xFFD6EAF8)
    val gridLine = if (dark) Color(0xFF28506F) else Color(0xFFB4D3EA)
    val shipColor = if (dark) Color(0xFF90A4AE) else Color(0xFF607D8B)
    val miss = if (dark) Color(0xFFB0C4D4) else Color(0xFF5B7A93)
    val accent = GoogleColors.Blue

    var side by remember { mutableIntStateOf(0) }
    val targets = if (side == 0) emptyList() else List(GRID * GRID) { cell ->
        val c = side / GRID.toFloat()
        val action = if (onTap != null) clickLabel(cell) else null
        val ship = board.shipAt(cell)
        BoardTarget(
            key = cell,
            rect = Rect(Offset(cell % GRID * c, cell / GRID * c), Size(c, c)),
            description = cellName(cell),
            state = cellState(board, cell, showShips, whose) + if (ship >= 0 && ship == selected) ", selected" else "",
            clickLabel = action,
            onClick = if (action != null && onTap != null) ({ onTap(cell) }) else null,
            // Your own fleet during play is only there to be read; while placing, its squares can be chosen.
            button = onTap != null,
        )
    }

    Box(
        modifier
            .aspectRatio(1f)
            .onSizeChanged { side = it.width }
            .semantics {
                contentDescription = "$label: ${board.shots.count { board.isHit(it) }} hits, ${board.shots.count { !board.isHit(it) }} misses"
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (onTap == null) Modifier else Modifier.pointerInput(board) {
                        detectTapGestures { p ->
                            val cell = size.width / GRID.toFloat()
                            val x = (p.x / cell).toInt().coerceIn(0, GRID - 1)
                            val y = (p.y / cell).toInt().coerceIn(0, GRID - 1)
                            onTap(cellOf(x, y))
                        }
                    },
                ),
        ) {
            val c = size.width / GRID
            drawRoundRect(water, cornerRadius = CornerRadius(c * 0.4f))
            for (i in 1 until GRID) {
                drawLine(gridLine, Offset(i * c, 0f), Offset(i * c, size.height), strokeWidth = 1.dp.toPx())
                drawLine(gridLine, Offset(0f, i * c), Offset(size.width, i * c), strokeWidth = 1.dp.toPx())
            }
            // The colour each ship is drawn in over the water, or null when it's hidden.
            val fills = arrayOfNulls<Color>(board.ships.size)
            board.ships.forEachIndexed { i, ship ->
                val sunk = board.isSunk(i)
                if (!showShips && !sunk) return@forEachIndexed
                val w = if (ship.horizontal) ship.length * c else c
                val h = if (ship.horizontal) c else ship.length * c
                val inset = c * 0.14f
                val tl = Offset(ship.x * c + inset, ship.y * c + inset)
                val sz = Size(w - 2 * inset, h - 2 * inset)
                val color = when {
                    i == selected -> accent
                    sunk && !showShips -> HIT.copy(alpha = 0.35f)
                    else -> shipColor
                }
                fills[i] = color.compositeOver(water)
                drawRoundRect(color, tl, sz, CornerRadius(c * 0.36f))
                if (sunk) drawRoundRect(HIT, tl, sz, CornerRadius(c * 0.36f), style = Stroke(2.dp.toPx()))
            }
            for (cell in board.shots) {
                val centre = Offset((cell % GRID + 0.5f) * c, (cell / GRID + 0.5f) * c)
                if (board.isHit(cell)) {
                    // Red on the water, but red barely shows on a ship, so there it's white or near-black.
                    val mark = fills[board.shipAt(cell)]?.let(::markOn) ?: HIT
                    val r = c * 0.26f
                    drawLine(mark, centre - Offset(r, r), centre + Offset(r, r), strokeWidth = c * 0.12f, cap = StrokeCap.Round)
                    drawLine(mark, centre + Offset(-r, r), centre + Offset(r, -r), strokeWidth = c * 0.12f, cap = StrokeCap.Round)
                } else {
                    drawCircle(miss, c * 0.12f, centre)
                }
                if (cell == lastShot) drawCircle(accent, c * 0.42f, centre, style = Stroke(2.dp.toPx()))
            }
        }
        BoardTargets(targets, Modifier.matchParentSize())
    }
}
