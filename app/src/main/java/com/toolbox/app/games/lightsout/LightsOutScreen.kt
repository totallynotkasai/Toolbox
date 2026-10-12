package com.toolbox.app.games.lightsout

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.toolbox.app.ui.theme.isLight

private val LIGHT_CORE = Color(0xFFFFF4C2)

/** How far over par: "par" for none, otherwise "+2". */
private fun overParLabel(over: Int) = if (over == 0) "par" else "+$over"

@Composable
fun LightsOutScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val guard = rememberGameGuard()
    // The best is the fewest moves over par, since puzzles differ in length. It's saved plus one, because the store
    // reads 0 as "no best yet" and 0 over par is the best there is.
    val (bestPlusOne, submit) = rememberBestScore("lightsout-overpar", lowerIsBetter = true)
    val best = bestPlusOne - 1
    var start by rememberSaveable { mutableIntStateOf(LightsOutBoard.random().bits) }
    var bits by rememberSaveable { mutableIntStateOf(start) }
    var moves by rememberSaveable { mutableIntStateOf(0) }
    var hintCell by rememberSaveable { mutableIntStateOf(-1) }
    var usedHint by rememberSaveable { mutableStateOf(false) }
    var newBest by rememberSaveable { mutableStateOf(false) }
    val board = LightsOutBoard(bits)
    // Par is fixed for the puzzle: the fewest presses that solve it from the start.
    val par = remember(start) { LightsOutBoard(start).solution()?.size ?: 0 }
    val solved = board.isSolved

    fun press(cell: Int) {
        if (solved) return
        bits = board.press(cell).bits
        moves++
        hintCell = -1
        if (LightsOutBoard(bits).isSolved) {
            haptics.heavy()
            if (!usedHint) {
                newBest = best < 0 || moves - par < best
                submit(moves - par + 1)
            }
        } else {
            haptics.click()
        }
    }

    fun newPuzzle() {
        start = LightsOutBoard.random().bits
        bits = start; moves = 0; hintCell = -1; usedHint = false; newBest = false
    }

    ToolScaffold(
        title = "Lights Out",
        onBack = onBack,
        actions = {
            NewGameButton(
                onClick = {
                    guard.run(moves > 0 && !solved, title = "Start a new puzzle?", text = "Your moves on this one will be lost.", confirm = "New puzzle") { newPuzzle() }
                },
                description = "New puzzle",
            )
        },
    ) { padding ->
        ToolBody(padding) {
            Text("Moves $moves  ·  Par $par", style = MaterialTheme.typography.titleLarge)
            Text(
                if (best >= 0) "Best: ${overParLabel(best)}" else "Solve it in as few moves as you can",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (r in 0 until LightsOutBoard.SIZE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until LightsOutBoard.SIZE) {
                            val cell = r * LightsOutBoard.SIZE + c
                            Light(
                                on = board.isOn(cell),
                                hinted = cell == hintCell,
                                dimmed = solved,
                                label = "Row ${r + 1}, column ${c + 1}",
                                modifier = Modifier.weight(1f),
                                onClick = { press(cell) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            val over = moves - par
            GameFooter(
                over = if (!solved) null else GameOver(
                    if (over == 0) "Perfect!" else "Solved, $over over par",
                    when {
                        usedHint -> "$moves moves · hints used, so it doesn't count"
                        newBest -> "$moves moves · New best!"
                        else -> "$moves moves · Best: ${overParLabel(best)}"
                    },
                ),
                turn = "${board.lightsOn} lights on",
                turnColor = null,
                hint = "Tap a light to switch it and its neighbours. Turn them all off.",
                onPlayAgain = ::newPuzzle,
                action = "New puzzle",
            )
            Spacer(Modifier.height(16.dp))
            // Wraps onto two lines at large font sizes.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        hintCell = board.solution()?.firstOrNull() ?: -1
                        if (hintCell >= 0) usedHint = true
                    },
                    enabled = !solved && hintCell < 0,
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Hint")
                }
                // Once solved, replaying the same puzzle knowing the answer can't count, so Restart is off.
                OutlinedButton(onClick = { bits = start; moves = 0; hintCell = -1 }, enabled = moves > 0 && !solved) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Restart")
                }
            }
        }
    }
    GameGuardDialog(guard)
}

@Composable
private fun Light(on: Boolean, hinted: Boolean, dimmed: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    val glow by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "glow")
    val fade by animateFloatAsState(if (dimmed) 0.55f else 1f, tween(400), label = "fade")
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    val ring = MaterialTheme.colorScheme.primary
    // On a light surface the yellow is barely brighter than an unlit cell, so a lit one also gets a dark amber rim.
    val rim = if (MaterialTheme.colorScheme.isLight) GoogleColors.YellowInk else null
    val pulse = if (hinted) {
        rememberInfiniteTransition(label = "hint")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse").value
    } else 0f

    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer {
                val s = 1f + 0.06f * pulse
                scaleX = s; scaleY = s
                alpha = fade
            }
            .semantics {
                contentDescription = label
                // The hint is only shown by a ring, so it's said too.
                stateDescription = (if (on) "On" else "Off") + if (hinted) ", suggested" else ""
            }
            // A solved board is dimmed and can't be pressed any more.
            .clickable(enabled = !dimmed, onClickLabel = "Press", role = Role.Button, onClick = onClick)
            .drawBehind {
                val corner = CornerRadius(size.minDimension * 0.2f)
                drawRoundRect(off, cornerRadius = corner)
                if (glow > 0f) {
                    drawRoundRect(
                        Brush.radialGradient(listOf(LIGHT_CORE, GoogleColors.Yellow), center = center, radius = size.minDimension * 0.7f),
                        cornerRadius = corner,
                        alpha = glow,
                    )
                    if (rim != null) {
                        val w = 2.5.dp.toPx()
                        drawRoundRect(
                            rim,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = corner,
                            style = Stroke(w),
                            alpha = glow,
                        )
                    }
                }
                if (hinted) {
                    val w = 3.dp.toPx()
                    drawRoundRect(
                        ring.copy(alpha = 0.5f + 0.5f * pulse),
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = corner,
                        style = Stroke(w),
                    )
                }
            },
    )
}
