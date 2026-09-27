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
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.theme.GoogleColors
import kotlinx.coroutines.delay

private val SYMBOLS = listOf(
    Icons.Filled.Favorite, Icons.Filled.Star, Icons.Filled.Pets, Icons.Filled.MusicNote,
    Icons.Filled.WbSunny, Icons.Filled.AcUnit, Icons.Filled.Cake, Icons.Filled.RocketLaunch,
    Icons.Filled.LocalFlorist, Icons.Filled.Anchor, Icons.Filled.FlashOn, Icons.AutoMirrored.Filled.DirectionsBike,
)

private enum class Difficulty(val label: String, val cols: Int, val rows: Int) {
    Medium("Medium", 4, 4), // single 4x4 board; kept as an enum so the saved best-score key is unchanged
}

private data class Card(val symbol: Int, val matched: Boolean = false)

private fun deal(d: Difficulty): List<Card> {
    val pairs = d.cols * d.rows / 2
    return (0 until pairs).flatMap { listOf(Card(it), Card(it)) }.shuffled()
}

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val difficulty = Difficulty.Medium
    var cards by remember(difficulty) { mutableStateOf(deal(difficulty)) }
    var open by remember(difficulty) { mutableStateOf(listOf<Int>()) }
    var moves by remember(difficulty) { mutableIntStateOf(0) }
    val (best, submit) = rememberBestScore("memory-${difficulty.name}", lowerIsBetter = true)
    val won = cards.all { it.matched }

    fun newGame() { cards = deal(difficulty); open = emptyList(); moves = 0 }

    fun tap(i: Int) {
        if (open.size == 2 || i in open || cards[i].matched) return
        haptics.click()
        open = open + i
        if (open.size == 2) {
            moves++
            val (a, b) = open
            if (cards[a].symbol == cards[b].symbol) {
                cards = cards.mapIndexed { k, c -> if (k == a || k == b) c.copy(matched = true) else c }
                open = emptyList()
                haptics.heavy()
                if (cards.all { it.matched }) submit(moves)
            }
        }
    }

    // Mismatch: leave both visible briefly, then flip them back.
    LaunchedEffect(open) {
        if (open.size == 2) { delay(800); open = emptyList() }
    }

    ToolScaffold(title = "Memory Game", onBack = onBack) { padding ->
        ToolBody(padding) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (won) "Solved in $moves moves!" else "Moves $moves" + if (best > 0) "  ·  Best $best" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (r in 0 until difficulty.rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in 0 until difficulty.cols) {
                            val i = r * difficulty.cols + c
                            MemoryCard(
                                card = cards[i],
                                faceUp = cards[i].matched || i in open,
                                modifier = Modifier.weight(1f),
                                onClick = { tap(i) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = ::newGame) { Text(if (won) "Play again" else "New game") }
        }
    }
}

@Composable
private fun MemoryCard(card: Card, faceUp: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (faceUp) 180f else 0f, tween(300), label = "flip")
    val back = MaterialTheme.colorScheme.primary
    val front = if (card.matched) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val tint = GoogleColors.all[card.symbol % 4]
    Box(
        modifier
            .aspectRatio(0.8f)
            .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clip(MaterialTheme.shapes.medium)
            .background(if (rotation > 90f) front else back)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (rotation > 90f) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }, contentAlignment = Alignment.Center) {
                Icon(SYMBOLS[card.symbol % SYMBOLS.size], contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
            }
        } else {
            Box(Modifier.size(18.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)))
        }
    }
}
