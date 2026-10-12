package com.toolbox.app.games.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.GameFooter
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.GameOver
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay

private const val COLS = 4
private const val ROWS = 4
private const val PAIRS = COLS * ROWS / 2

/** One symbol per pair, with the name TalkBack reads for it. */
private val SYMBOLS = listOf(
    Icons.Filled.Favorite to "heart", Icons.Filled.Star to "star", Icons.Filled.Pets to "paw",
    Icons.Filled.MusicNote to "music note", Icons.Filled.WbSunny to "sun", Icons.Filled.AcUnit to "snowflake",
    Icons.Filled.Cake to "cake", Icons.Filled.RocketLaunch to "rocket",
)

private data class Card(val symbol: Int, val matched: Boolean = false)

private fun deal(): List<Card> = (0 until PAIRS).flatMap { listOf(Card(it), Card(it)) }.shuffled()

/** Each card as one int, symbol × 2 plus 1 if matched, so the deal survives the screen being recreated. */
private val CardsSaver = Saver<List<Card>, IntArray>(
    save = { cards -> IntArray(cards.size) { cards[it].symbol * 2 + if (cards[it].matched) 1 else 0 } },
    restore = { saved -> saved.map { Card(it / 2, matched = it % 2 == 1) } },
)

private val OpenSaver = Saver<List<Int>, IntArray>(save = { it.toIntArray() }, restore = { it.toList() })

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val guard = rememberGameGuard()
    var cards by rememberSaveable(stateSaver = CardsSaver) { mutableStateOf(deal()) }
    var open by rememberSaveable(stateSaver = OpenSaver) { mutableStateOf(listOf<Int>()) }
    var moves by rememberSaveable { mutableIntStateOf(0) }
    // Counts deals, so each one gets new cards rather than turning the old ones face down over new symbols.
    var dealt by rememberSaveable { mutableIntStateOf(0) }
    var newBest by rememberSaveable { mutableStateOf(false) }
    // The key from when there was a choice of board sizes, kept so the saved best carries on.
    val (best, submit) = rememberBestScore("memory-Medium", lowerIsBetter = true)
    val won = cards.all { it.matched }

    fun newGame() { cards = deal(); open = emptyList(); moves = 0; newBest = false; dealt++ }

    fun tap(i: Int) {
        if (won || open.size == 2 || i in open || cards[i].matched) return
        haptics.click()
        open = open + i
        if (open.size == 2) {
            moves++
            val (a, b) = open
            if (cards[a].symbol == cards[b].symbol) {
                cards = cards.mapIndexed { k, c -> if (k == a || k == b) c.copy(matched = true) else c }
                open = emptyList()
                haptics.heavy()
                if (cards.all { it.matched }) {
                    newBest = best == 0 || moves < best
                    submit(moves)
                }
            }
        }
    }

    // Mismatch: leave both visible briefly, then flip them back.
    LaunchedEffect(open) {
        if (open.size == 2) { delay(800); open = emptyList() }
    }

    ToolScaffold(
        title = "Memory Game",
        onBack = onBack,
        actions = { NewGameButton(onClick = { guard.run(moves > 0 && !won) { newGame() } }, enabled = moves > 0 || open.isNotEmpty()) },
    ) { padding ->
        ToolBody(padding) {
            Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Moves $moves", style = MaterialTheme.typography.titleLarge)
                if (best > 0) Text("Best $best", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (r in 0 until ROWS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until COLS) {
                            val i = r * COLS + c
                            key(dealt, i) {
                                MemoryCard(
                                    card = cards[i],
                                    number = i + 1,
                                    faceUp = cards[i].matched || i in open,
                                    modifier = Modifier.weight(1f),
                                    onClick = { tap(i) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            GameFooter(
                over = if (!won) null else GameOver("Solved in $moves moves!", if (newBest) "New best!" else "Best $best"),
                turn = "${cards.count { it.matched } / 2} of $PAIRS pairs found",
                turnColor = null,
                onPlayAgain = ::newGame,
            )
        }
    }
    GameGuardDialog(guard)
}

@Composable
private fun MemoryCard(card: Card, number: Int, faceUp: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (faceUp) 180f else 0f, tween(300), label = "flip")
    val back = MaterialTheme.colorScheme.primary
    val front = if (card.matched) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val tint = GoogleColors.all[card.symbol % 4]
    val (icon, name) = SYMBOLS[card.symbol]
    Box(
        modifier
            .aspectRatio(0.8f)
            .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clip(MaterialTheme.shapes.medium)
            .background(if (rotation > 90f) front else back)
            .clickable(enabled = !faceUp, role = Role.Button, onClickLabel = "Turn over", onClick = onClick)
            .semantics {
                contentDescription = "Card $number, " + when {
                    card.matched -> "$name, matched"
                    faceUp -> name
                    else -> "face down"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (rotation > 90f) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }, contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
            }
        } else {
            Box(Modifier.size(18.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)))
        }
    }
}
