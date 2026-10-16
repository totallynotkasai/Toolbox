package com.toolbox.app.games.solitaire

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.toolbox.app.ui.common.BoardTarget
import com.toolbox.app.ui.common.BoardTargets
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.GameOverCard
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.Prefs
import com.toolbox.app.ui.common.ScoreStore
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberScreenReaderOn
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.theme.isLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow

// The game in progress and the draw for the next deal, in Prefs (so they're backed up). Games won and played are
// there too, and the best time in seconds is in ScoreStore, all for each draw.
private const val GAME_KEY = "solitaire_game"
private const val DRAW_KEY = "solitaire_draw"
private fun playedKey(draw: Int) = "solitaire_played_$draw"
private fun wonKey(draw: Int) = "solitaire_won_$draw"
private fun bestKey(draw: Int) = "solitaire_best_$draw"

/** The gap between cards as the game finishes itself. */
private const val AUTO_STEP_MS = 90L

/** Cards being dragged: those from [index] up in [from], held [grab] from the first card's top-left corner. */
private class Drag(val from: Pile, val index: Int, val cards: List<Card>, val grab: Offset, var finger: Offset)

@Composable
fun SolitaireScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context.applicationContext) }
    val scores = remember(context) { ScoreStore(context.applicationContext) }
    val haptics = rememberHaptics()
    val synth = rememberToneSynth()
    val say = rememberAnnouncer()
    val scope = rememberCoroutineScope()
    val screenReader = rememberScreenReaderOn()
    val guard = rememberGameGuard()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val dark = !MaterialTheme.colorScheme.isLight
    var drawPref by rememberPref(DRAW_KEY, 1)
    val restored = remember { Klondike.restore(prefs.get(GAME_KEY, "")) }
    var game by remember { mutableStateOf(restored ?: Klondike.deal(drawPref)) }
    // A new game is dealt out on screen, which waits for the table to have a size when the screen has just opened.
    var dealPending by remember { mutableStateOf(restored == null) }
    val motion = remember { CardMotion() }
    var revision by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf((game.elapsedMs / 1000).toInt()) }
    var stuck by remember { mutableStateOf(false) }
    var showWin by remember { mutableStateOf(game.won) }
    var newBest by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TableLayout?>(null) }
    var drag by remember { mutableStateOf<Drag?>(null) }

    fun held(l: TableLayout): Map<Card, Offset> {
        val d = drag ?: return emptyMap()
        val first = d.finger - d.grab
        return d.cards.withIndex().associate { (j, c) -> c to first + Offset(0f, j * l.cardH * 0.3f) }
    }

    val loop = rememberGameLoop { dt ->
        val l = layout ?: return@rememberGameLoop false
        val moving = motion.step(l.place(game), dt, held(l))
        frame++
        moving || drag != null
    }

    fun save() = prefs.put(GAME_KEY, game.save())

    fun bump(key: String) = prefs.put(key, prefs.get(key, 0) + 1)

    fun changed() {
        revision++
        stuck = false
        save()
        loop.wake()
    }

    /** Sounds, buzzes and TalkBack news for what a step did. */
    fun react(step: Step, auto: Boolean) {
        when (step.kind) {
            Step.Kind.Draw -> {
                scope.launch {
                    repeat(step.count) {
                        synth.card(0.35f, flick = true)
                        delay(55)
                    }
                }
                haptics.tick()
                game.top(Pile.Waste)?.let { say("${it.name} turned up") }
            }
            Step.Kind.Recycle -> {
                scope.launch {
                    repeat(6) {
                        synth.card(0.22f, flick = true)
                        delay(30)
                    }
                }
                haptics.tick()
                say("Waste turned back over")
            }
            Step.Kind.Move -> {
                val pile = game.cards(step.to)
                val card = pile[pile.size - step.count]
                if (step.to.isFoundation) {
                    synth.card(0.5f)
                    synth.play(FOUNDATION_NOTES[card.rank - 1], 160, 0.12f)
                    if (!auto) haptics.click()
                } else {
                    synth.card(0.55f)
                    haptics.tick()
                }
                if (step.turned) {
                    scope.launch {
                        delay(150)
                        synth.card(0.25f, flick = true)
                    }
                }
                if (!auto) {
                    val under = pile.getOrNull(pile.size - step.count - 1)
                    val where = when {
                        step.to.isFoundation -> "to foundation"
                        under != null -> "onto ${under.name}"
                        else -> "to empty column ${step.to.number + 1}"
                    }
                    val turned = game.top(step.from)?.takeIf { step.turned }?.let { ". ${it.name} turned up" }.orEmpty()
                    say("${card.name} $where$turned")
                }
            }
        }
    }

    fun win() {
        val time = ((game.elapsedMs + 999) / 1000).toInt().coerceAtLeast(1)
        val key = bestKey(game.draw)
        val before = scores.best(key)
        newBest = before == 0 || time < before
        scores.submit(key, time, lowerIsBetter = true)
        bump(wonKey(game.draw))
        seconds = time
        motion.celebrate()
        haptics.heavy()
        val won = game
        scope.launch {
            delay(250)
            for (f in WIN_NOTES) {
                synth.play(f, 260, 0.16f)
                delay(95)
            }
            delay(450)
            if (game === won) showWin = true
        }
    }

    fun afterStep(step: Step, auto: Boolean = false) {
        if (!game.counted) {
            game.counted = true
            bump(playedKey(game.draw))
        }
        react(step, auto)
        if (game.won) win()
        changed()
    }

    /** While the game finishes itself, or once it's won, the cards can't be touched. */
    fun busy() = game.won || game.canAutoFinish

    fun turnStock() {
        if (!busy()) game.turnStock()?.let { afterStep(it) }
    }

    fun move(from: Pile, index: Int, to: Pile) {
        if (!busy()) game.move(from, index, to)?.let { afterStep(it) }
    }

    /** The cards tapped can't go anywhere: they shake. */
    fun nope(from: Pile, index: Int) {
        val cards = game.cards(from).drop(index)
        motion.shake(cards)
        haptics.tick()
        synth.knock(0.12f)
        loop.wake()
        say("${cards.first().name} can't go anywhere")
    }

    fun tap(spot: Spot) {
        if (busy()) return
        when {
            spot.pile == Pile.Stock -> turnStock()
            game.canLift(spot.pile, spot.index) -> {
                val to = game.bestDestination(spot.pile, spot.index)
                if (to != null) move(spot.pile, spot.index, to) else nope(spot.pile, spot.index)
            }
        }
    }

    fun undo() {
        if (busy()) return
        game.undo() ?: return
        synth.card(0.3f, flick = true)
        haptics.tick()
        say("Undone")
        changed()
    }

    fun newDeal(draw: Int = drawPref) {
        game = Klondike.deal(draw)
        showWin = false
        newBest = false
        seconds = 0
        drag = null
        motion.calm()
        val l = layout
        if (l != null) motion.deal(dealOrder(game), l.origin(Pile.Stock)) else dealPending = true
        scope.launch {
            repeat(8) {
                synth.card(0.3f, flick = true)
                delay(45)
            }
        }
        changed()
    }

    fun chooseDraw(draw: Int) {
        guard.run(game.moves > 0 && !game.won) {
            drawPref = draw
            newDeal(draw)
        }
    }

    fun startDrag(spot: Spot, at: Offset) {
        val cards = game.cards(spot.pile).drop(spot.index)
        val first = motion.position(cards.first()) ?: return
        drag = Drag(spot.pile, spot.index, cards, at - first, at)
        synth.card(0.18f, flick = true)
        loop.wake()
    }

    /** Lets go of the dragged cards: onto the place they cover most that takes them, or back where they came from. */
    fun drop(cancelled: Boolean = false) {
        val d = drag ?: return
        drag = null
        val l = layout
        val to = if (cancelled || l == null) null else l.dropTarget(game, d.from, d.index, Rect(d.finger - d.grab, l.cardSize))
        if (to != null) move(d.from, d.index, to) else synth.card(0.2f)
        loop.wake()
    }

    // The game remembers the draw it was dealt with; the chips follow it.
    LaunchedEffect(Unit) { if (drawPref != game.draw) drawPref = game.draw }

    // The clock runs from the first move until the game is won, and only while the screen is in front.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val ticking = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && revision >= 0 && game.moves > 0 && !game.won
    LaunchedEffect(ticking) {
        if (!ticking) return@LaunchedEffect
        var last = SystemClock.elapsedRealtime()
        while (true) {
            delay(250)
            val now = SystemClock.elapsedRealtime()
            game.elapsedMs += now - last
            last = now
            seconds = (game.elapsedMs / 1000).toInt()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { save() }
    DisposableEffect(Unit) { onDispose { save() } }

    // Once nothing is left to find, the cards go up one by one.
    LaunchedEffect(revision) {
        if (game.canAutoFinish) {
            delay(AUTO_STEP_MS)
            game.autoStep()?.let { afterStep(it, auto = true) }
        }
    }
    // After each move, a look in the background for whether anything useful is left to do.
    LaunchedEffect(revision) {
        if (game.won || game.canAutoFinish) return@LaunchedEffect
        delay(450)
        val position = game.copy()
        stuck = withContext(Dispatchers.Default) { position.isStuck() }
    }

    val painter = remember(layout, dark, density) { layout?.let { CardPainter(measurer, it.cardW, it.cardH, density, dark) } }
    val busyNow = revision >= 0 && busy()

    ToolScaffold(
        title = "Solitaire",
        onBack = onBack,
        actions = {
            IconButton(onClick = ::undo, enabled = game.canUndo && !busyNow) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            NewGameButton(onClick = { guard.run(game.moves > 0 && !game.won) { newDeal() } })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawChips(game.draw, ::chooseDraw)
                Spacer(Modifier.weight(1f))
                Text(
                    "${game.moves} ${if (game.moves == 1) "move" else "moves"} · ${clock(seconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .onSizeChanged { size ->
                        val l = TableLayout(size.width.toFloat(), size.height.toFloat(), with(density) { 5.dp.toPx() })
                        layout = l
                        if (dealPending) {
                            motion.deal(dealOrder(game), l.origin(Pile.Stock))
                            dealPending = false
                        }
                        loop.wake()
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val l = layout ?: return@awaitEachGesture
                            if (busy()) return@awaitEachGesture
                            val spot = l.hit(l.place(game), down.position) ?: return@awaitEachGesture
                            val canDrag = spot.pile != Pile.Stock && game.canLift(spot.pile, spot.index)
                            var travel = Offset.Zero
                            var dragging = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    if (dragging) drop() else tap(spot)
                                    return@awaitEachGesture
                                }
                                travel += change.positionChange()
                                if (!dragging && canDrag && travel.getDistance() > viewConfiguration.touchSlop) {
                                    dragging = true
                                    startDrag(spot, change.position)
                                }
                                if (dragging) {
                                    change.consume()
                                    drag?.finger = change.position
                                }
                            }
                            // The touch was taken away, say by a system gesture: the cards go back.
                            if (dragging) drop(cancelled = true)
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (frame < 0 || revision < 0) return@Canvas // read, so every frame and move redraws
                    drawFelt(dark)
                    val l = layout ?: return@Canvas
                    val p = painter ?: return@Canvas
                    drawTable(l, p, game, motion, drag?.cards.orEmpty())
                }
                if (screenReader) {
                    val l = layout
                    if (l != null && revision >= 0) {
                        BoardTargets(tableTargets(l, game, onTap = ::tap, onTurnStock = ::turnStock, onMove = ::move), Modifier.matchParentSize())
                    }
                }
                when {
                    showWin -> Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val best = scores.best(bestKey(game.draw))
                        val record = if (newBest) "New best!" else "Best ${clock(best)}"
                        GameOverCard(GameOver("You win!", "${clock(seconds)} · ${game.moves} moves · $record"), onAction = { newDeal() })
                    }
                    stuck -> StuckCard(game.canUndo, onUndo = ::undo, onNewDeal = { newDeal() }, Modifier.align(Alignment.BottomCenter).padding(12.dp))
                }
            }
            val note = if (game.moves == 0 && !screenReader) {
                "Tap a card to send it where it fits, or drag it."
            } else {
                val played = prefs.get(playedKey(game.draw), 0)
                val won = prefs.get(wonKey(game.draw), 0)
                val best = scores.best(bestKey(game.draw))
                "Draw ${game.draw}: won $won of $played" + if (best > 0) " · best ${clock(best)}" else ""
            }
            Text(
                if (revision >= 0) note else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
            )
        }
    }
    GameGuardDialog(guard)
}

@Composable
private fun DrawChips(draw: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in listOf(1, 3)) {
            FilterChip(selected = draw == n, onClick = { if (draw != n) onSelect(n) }, label = { Text("Draw $n", maxLines = 1) })
        }
    }
}

/** Shown when nothing useful is left to do, with a way back (if there's a move to take back) and a way on. */
@Composable
private fun StuckCard(canUndo: Boolean, onUndo: () -> Unit, onNewDeal: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.widthIn(max = 400.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No moves left", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    "Undo to try another way, or deal again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
                Button(onClick = onNewDeal) { Text("New deal") }
            }
        }
    }
}

/** "4:05", or "1:02:03" past an hour. */
internal fun clock(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val s = seconds % 60
    fun two(n: Int) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

/** The columns' cards in the order they're dealt: across the columns a row at a time, as at a real table. */
private fun dealOrder(game: Klondike): List<Card> = buildList {
    for (row in 0 until 7) for (c in row until 7) add(game.cards(Pile.column(c))[row])
}

/**
 * The table: the empty places, then the cards in their piles, then any flying to a new pile over the top, then the
 * ones under the finger. Cards stacked straight up (the stock, the foundations, all but the waste's top few) are only
 * drawn while they could show.
 */
private fun DrawScope.drawTable(l: TableLayout, painter: CardPainter, game: Klondike, motion: CardMotion, held: List<Card>) {
    with(painter) {
        slot(l.origin(Pile.Stock), if (game.cards(Pile.Waste).isNotEmpty()) SlotMark.TurnOver else SlotMark.None)
        for (f in Pile.foundations) slot(l.origin(f), SlotMark.Ace)
        for (c in Pile.columns) slot(l.origin(c), SlotMark.None)
    }
    val placed = l.place(game)
    fun covered(p: Placed): Boolean {
        val size = game.cards(p.pile).size
        return when {
            p.pile == Pile.Stock || p.pile.isFoundation -> p.index < size - 2
            p.pile == Pile.Waste -> p.index < size - 4
            else -> false
        }
    }
    fun lift(p: Placed) = if (p.pile.isFoundation && p.index == game.cards(p.pile).lastIndex) motion.hop(p.pile.number) * l.cardH else 0f
    for (p in placed) if (!motion.isFlying(p.card) && p.card !in held && !covered(p)) drawCard(l, painter, motion, p, lift(p))
    for (p in placed) if (motion.isFlying(p.card) && p.card !in held) drawCard(l, painter, motion, p, lift(p))
    for (c in held) placed.firstOrNull { it.card == c }?.let { drawCard(l, painter, motion, it, 0f) }
}

/** One card where it's got to, squeezed sideways as it turns over and showing the back until it's halfway. */
private fun DrawScope.drawCard(l: TableLayout, painter: CardPainter, motion: CardMotion, p: Placed, lift: Float) {
    val at = (motion.position(p.card) ?: p.pos) + Offset(motion.shake(p.card) * l.cardW, -lift)
    val turned = motion.face(p.card)
    val squeeze = abs(1f - 2f * turned)
    if (squeeze < 0.02f) return
    withTransform({ scale(squeeze, 1f, pivot = at + Offset(l.cardW / 2, l.cardH / 2)) }) {
        with(painter) { if (turned >= 0.5f) face(p.card, at) else back(at) }
    }
}

/**
 * What TalkBack finds on the table: the stock, the waste's top card, the foundations, and every card in the columns
 * (face-down ones only to be read). A face-up card's double tap does what a tap does, and its actions menu lists every
 * place it can go.
 */
private fun tableTargets(
    l: TableLayout,
    game: Klondike,
    onTap: (Spot) -> Unit,
    onTurnStock: () -> Unit,
    onMove: (Pile, Int, Pile) -> Unit,
): List<BoardTarget> = buildList {
    fun cardRect(at: Offset) = Rect(at, l.cardSize)
    fun actions(from: Pile, index: Int) = game.destinations(from, index).map { to ->
        val top = game.top(to)
        val label = when {
            to.isFoundation -> "Move to foundation"
            top == null -> "Move to column ${to.number + 1}, empty"
            else -> "Move to column ${to.number + 1}, onto ${top.name}"
        }
        CustomAccessibilityAction(label) { onMove(from, index, to); true }
    }
    fun movable(key: Any, rect: Rect, description: String, state: String?, pile: Pile, index: Int) =
        BoardTarget(key, rect, description, state, clickLabel = "Move", onClick = { onTap(Spot(pile, index)) }, actions = actions(pile, index))

    val placed = l.place(game)
    val stock = game.cards(Pile.Stock)
    val waste = game.cards(Pile.Waste)
    add(
        BoardTarget(
            "stock", cardRect(l.origin(Pile.Stock)), "Stock", if (stock.isEmpty()) "Empty" else "${stock.size} cards",
            clickLabel = if (stock.isNotEmpty()) "Turn over" else "Turn the waste back over",
            onClick = if (stock.isNotEmpty() || waste.isNotEmpty()) onTurnStock else null,
        ),
    )
    val wasteTop = placed.lastOrNull { it.pile == Pile.Waste }
    if (wasteTop == null) {
        add(BoardTarget("waste", cardRect(l.origin(Pile.Waste)), "Waste", "Empty", button = false))
    } else {
        add(movable("waste", cardRect(wasteTop.pos), "Waste, ${wasteTop.card.name}", null, Pile.Waste, wasteTop.index))
    }
    for (f in Pile.foundations) {
        val top = game.top(f)
        val name = "Foundation ${f.number + 1}"
        if (top == null) add(BoardTarget(f, cardRect(l.origin(f)), name, "Empty", button = false))
        else add(movable(f, cardRect(l.origin(f)), "$name, ${top.name}", null, f, game.cards(f).lastIndex))
    }
    for (column in Pile.columns) {
        val name = "Column ${column.number + 1}"
        val cards = placed.filter { it.pile == column }
        if (cards.isEmpty()) add(BoardTarget(column, cardRect(l.origin(column)), name, "Empty", button = false))
        cards.forEachIndexed { i, p ->
            // Each card's target is the strip of it that shows, and the whole of the last one.
            val bottom = cards.getOrNull(i + 1)?.pos?.y ?: (p.pos.y + l.cardH)
            val rect = Rect(p.pos.x, p.pos.y, p.pos.x + l.cardW, bottom)
            if (p.faceUp) add(movable("${column.index}-$i", rect, p.card.name, name, column, i))
            else add(BoardTarget("${column.index}-$i", rect, "Face-down card", name, button = false))
        }
    }
}

// A C major scale from C5, one note a rank, so a foundation climbs as it builds; and an arpeggio for a win.
private val SCALE_STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17, 19, 21)
private val FOUNDATION_NOTES = FloatArray(13) { 523.25f * 2f.pow(SCALE_STEPS[it] / 12f) }
private val WIN_NOTES = floatArrayOf(523.25f, 659.26f, 783.99f, 1046.5f, 1318.51f, 1567.98f)
