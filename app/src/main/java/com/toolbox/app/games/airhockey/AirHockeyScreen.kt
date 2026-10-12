package com.toolbox.app.games.airhockey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.Tally
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.common.rememberTally
import com.toolbox.app.ui.common.rememberToneSynth
import com.toolbox.app.ui.theme.GoogleColors

private enum class Mode(val label: String, val cpuSpeed: Float?, val cpuReaction: Float = 0f) {
    // "Easy" is kept as the name because saved settings and win counts use it. Without the reaction time the
    // computer read every shot perfectly and was close to unbeatable, however slow its mallet.
    Easy("Solo", 1.3f, cpuReaction = 0.18f), TwoPlayer("2 players", null)
}

private val BOTTOM = GoogleColors.Blue
private val TOP = GoogleColors.Red

@Composable
fun AirHockeyScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val synth = rememberToneSynth()
    var modeName by rememberPref("airhockey_mode", Mode.TwoPlayer.name)
    val mode = Mode.entries.firstOrNull { it.name == modeName } ?: Mode.TwoPlayer
    val (tally, setTally) = rememberTally("airhockey_tally_${mode.name}")
    var menu by remember { mutableStateOf(false) }

    ToolScaffold(
        title = "Air Hockey",
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Reset match tally") }, onClick = { setTally(Tally()); menu = false })
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(12.dp), contentAlignment = Alignment.Center) {
            // The table's shape follows the screen, within sensible limits, and the physics is built to match.
            val aspect = (maxHeight / maxWidth).coerceIn(1.35f, 2f)
            val game = remember(aspect) { AirHockey(height = aspect, seed = System.nanoTime()).apply { reset() } }
            var frame by remember { mutableLongStateOf(0L) }
            var playing by remember(game) { mutableStateOf(false) }
            var finished by remember(game) { mutableStateOf(false) }
            val cpu = mode.cpuSpeed != null
            game.malletSpeed[1] = mode.cpuSpeed ?: AirHockey.MALLET_MAX_SPEED
            game.aiReaction = mode.cpuReaction

            fun start() {
                game.reset()
                finished = false
                playing = true
                haptics.click()
            }

            LaunchedEffect(playing, game) {
                if (!playing) return@LaunchedEffect
                var last = withFrameNanos { it }
                while (playing) {
                    withFrameNanos { now ->
                        val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                        last = now
                        if (cpu) game.aiTarget().let { game.setTarget(1, it.x, it.y) }
                        for (e in game.step(dt)) when (e) {
                            is AirHockey.Event.Hit -> {
                                synth.knock(0.3f + 0.7f * e.intensity)
                                if (!cpu || e.player == 0) haptics.tick()
                            }
                            is AirHockey.Event.Wall -> if (e.intensity > 0.08f) synth.knock(0.15f + 0.4f * e.intensity)
                            is AirHockey.Event.Goal -> {
                                synth.play(if (e.scorer == 0 || !cpu) 784f else 392f, 220, 0.45f)
                                haptics.heavy()
                            }
                        }
                        game.winner?.let { w ->
                            playing = false
                            finished = true
                            setTally(tally.record(w + 1))
                        }
                        frame++
                    }
                }
            }

            val tableWidth = maxWidth.coerceAtMost(maxHeight / aspect)
            Box(Modifier.size(tableWidth, tableWidth * aspect)) {
                Table(game, frame, cpu, playing, Modifier.fillMaxSize())
                // Each player's score faces them: the top one is upside down.
                ScoreBadge(game.scores[1], TOP, Modifier.align(Alignment.CenterEnd).offset(x = (-12).dp, y = (-44).dp).rotate(180f))
                ScoreBadge(game.scores[0], BOTTOM, Modifier.align(Alignment.CenterEnd).offset(x = (-12).dp, y = 44.dp))
                if (!playing) {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!cpu) {
                            MessageCard(if (finished) resultText(game, cpu, top = true) else "First to ${AirHockey.WIN_SCORE}", Modifier.rotate(180f))
                            Spacer(Modifier.height(16.dp))
                        }
                        MessageCard(if (finished) resultText(game, cpu, top = false) else "First to ${AirHockey.WIN_SCORE}")
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (m in Mode.entries) {
                                FilterChip(selected = m == mode, onClick = { modeName = m.name }, label = { Text(m.label, maxLines = 1) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (cpu) "Matches: You ${tally.a} · CPU ${tally.b}" else "Matches: Blue ${tally.a} · Red ${tally.b}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = ::start) { Text(if (finished) "Play again" else "Start") }
                    }
                }
            }
        }
    }
}

/** The end-of-match line for the player at the [top] or bottom, with the score from their side. */
private fun resultText(game: AirHockey, cpu: Boolean, top: Boolean): String {
    val w = game.winner ?: return ""
    val headline = when {
        cpu -> if (w == 0) "You win!" else "The computer wins"
        w == (if (top) 1 else 0) -> "You win!"
        else -> "You lose"
    }
    val score = if (top) "${game.scores[1]}–${game.scores[0]}" else "${game.scores[0]}–${game.scores[1]}"
    return "$headline  $score"
}

@Composable
private fun ScoreBadge(score: Int, color: Color, modifier: Modifier) {
    Text("$score", fontSize = 40.sp, fontWeight = FontWeight.Light, color = color.copy(alpha = 0.75f), modifier = modifier)
}

@Composable
private fun MessageCard(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 2.dp) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

@Composable
private fun Table(game: AirHockey, frame: Long, cpu: Boolean, playing: Boolean, modifier: Modifier) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val lines = MaterialTheme.colorScheme.outlineVariant
    val puck = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Air hockey table. Blue ${game.scores[0]}, Red ${game.scores[1]}" }
            .pointerInput(game, cpu, playing) {
                if (!playing) return@pointerInput
                val scale = size.width.toFloat()
                // Each finger drives the mallet on the half where it first touched, so both players can play at once.
                awaitPointerEventScope {
                    val owners = HashMap<PointerId, Int>()
                    while (true) {
                        val event = awaitPointerEvent()
                        for (c in event.changes) {
                            if (c.pressed && c.id !in owners) {
                                val player = if (c.position.y > size.height / 2f) 0 else 1
                                if (!(cpu && player == 1)) owners[c.id] = player
                            }
                            val player = owners[c.id]
                            if (c.pressed && player != null) {
                                game.setTarget(player, c.position.x / scale, c.position.y / scale)
                                c.consume()
                            } else if (!c.pressed) {
                                owners.remove(c.id)
                            }
                        }
                    }
                }
            },
    ) {
        if (frame < 0) return@Canvas
        val s = size.width
        drawRect(surface)
        drawMarkings(s, game.height, lines)
        drawPuck(Offset(game.puckX * s, game.puckY * s), AirHockey.PUCK_R * s, puck)
        drawMallet(Offset(game.mallet(0).x * s, game.mallet(0).y * s), AirHockey.MALLET_R * s, BOTTOM)
        drawMallet(Offset(game.mallet(1).x * s, game.mallet(1).y * s), AirHockey.MALLET_R * s, TOP)
    }
}

private fun DrawScope.drawMarkings(s: Float, height: Float, lines: Color) {
    val stroke = Stroke(2.dp.toPx())
    val mid = height * s / 2
    drawLine(lines, Offset(0f, mid), Offset(s, mid), strokeWidth = 3.dp.toPx())
    drawCircle(lines, s * 0.16f, Offset(s / 2, mid), style = stroke)
    drawCircle(lines, 5.dp.toPx(), Offset(s / 2, mid))
    // Goal creases and the goals themselves, in each player's colour.
    val gw = AirHockey.GOAL_WIDTH * s
    for ((y, color, up) in listOf(Triple(0f, TOP, false), Triple(height * s, BOTTOM, true))) {
        drawArc(
            color.copy(alpha = 0.35f), if (up) 180f else 0f, 180f, false,
            topLeft = Offset(s / 2 - gw * 0.6f, y - gw * 0.6f), size = Size(gw * 1.2f, gw * 1.2f), style = stroke,
        )
        val depth = 6.dp.toPx()
        drawRoundRect(
            color, Offset(s / 2 - gw / 2, y - depth), Size(gw, depth * 2),
            cornerRadius = CornerRadius(depth),
        )
    }
}

private fun DrawScope.drawMallet(c: Offset, r: Float, color: Color) {
    drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.04f, c + Offset(r * 0.08f, r * 0.12f))
    drawCircle(color, r, c)
    drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.62f, c)
    drawCircle(color, r * 0.5f, c)
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.22f, c - Offset(r * 0.12f, r * 0.12f))
}

private fun DrawScope.drawPuck(c: Offset, r: Float, color: Color) {
    drawCircle(Color.Black.copy(alpha = 0.2f), r * 1.05f, c + Offset(r * 0.1f, r * 0.14f))
    drawCircle(color, r, c)
    drawCircle(Color.White.copy(alpha = 0.18f), r * 0.7f, c, style = Stroke(r * 0.12f))
}
