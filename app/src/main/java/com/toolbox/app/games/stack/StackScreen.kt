package com.toolbox.app.games.stack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberToneSynth
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/** Block height and the depth of its top face, as fractions of the play area's width. */
private const val BLOCK_H = 0.075f
private const val DEPTH = 0.05f

/** A sliced-off piece tumbling away; [y] is how far it has fallen, in play-area widths. */
private class Falling(val cut: StackGame.Offcut, var y: Float = 0f, var vy: Float = 0f, var angle: Float = 0f, val spin: Float)

/** How far the view rises to keep the top of [game]'s tower in sight. */
private fun cameraFor(game: StackGame): Float = max(0f, game.tower.size * BLOCK_H - 0.9f)

private fun blockColor(level: Int): Color = Color.hsv((205f + level * 9f) % 360f, 0.5f, 0.93f)

@Composable
fun StackScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val synth = rememberToneSynth()
    // Saved, so a recreated screen carries on with the same tower.
    val game = rememberSaveable(saver = Saver(save = { it.save() }, restore = { StackGame().apply { restore(it) } })) { StackGame() }
    val guard = rememberGameGuard()
    val (best, submitBest) = rememberBestScore("stack")
    // tap() runs from a long-lived pointer handler, so it reads the best through this.
    val currentBest by rememberUpdatedState(best)
    var frame by remember { mutableLongStateOf(0L) }
    var state by remember { mutableStateOf(game.state) }
    var score by remember { mutableIntStateOf(game.score) }
    var perfectLabel by remember { mutableStateOf<String?>(null) }
    var perfectAge by remember { mutableFloatStateOf(1f) } // seconds since the last perfect, for the ring and label
    val falling = remember { mutableListOf<Falling>() }
    var camera by remember { mutableFloatStateOf(cameraFor(game)) } // how far the view has risen, in play-area widths
    var newBest by rememberSaveable { mutableStateOf(false) }

    // One loop for the sliding block, falling offcuts and the rising camera; it idles when nothing is moving.
    val loop = rememberGameLoop { dt ->
        val targetCamera = cameraFor(game)
        game.step(dt)
        camera += (targetCamera - camera) * (1 - exp(-7f * dt))
        perfectAge = (perfectAge + dt).coerceAtMost(1f)
        val it = falling.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.vy += 3.2f * dt
            f.y += f.vy * dt
            f.angle += f.spin * dt
            if (f.y > 3f) it.remove()
        }
        frame++
        game.state == StackGame.State.Playing || falling.isNotEmpty() || abs(targetCamera - camera) > 0.0005f || perfectAge < 1f
    }

    fun newGame() {
        game.reset()
        falling.clear()
        perfectLabel = null
        newBest = false
        state = game.state
        score = game.score
        loop.wake() // the camera drops back to the base
    }

    fun tap() {
        when (game.state) {
            StackGame.State.Ready, StackGame.State.Over -> {
                if (game.state == StackGame.State.Over && falling.isNotEmpty()) return // let the last block land
                game.start()
                camera = 0f
                perfectLabel = null
                newBest = false
                haptics.click()
            }
            StackGame.State.Playing -> when (val d = game.drop()) {
                is StackGame.Drop.Perfect -> {
                    // A rising scale for a run of perfect drops.
                    val step = listOf(0, 2, 4, 7, 9, 12, 14, 16)[(d.streak - 1).coerceAtMost(7)]
                    synth.play(523f * 2f.pow(step / 12f), 120, 0.4f)
                    haptics.click()
                    perfectLabel = if (d.streak > 1) "Perfect ×${d.streak}" else "Perfect"
                    perfectAge = 0f
                }
                is StackGame.Drop.Cut -> {
                    falling += Falling(d.offcut, spin = if (d.offcut.x < game.top.x) -140f else 140f)
                    synth.knock(0.5f)
                    haptics.tick()
                }
                is StackGame.Drop.Miss -> {
                    falling += Falling(d.offcut, spin = 90f)
                    synth.buzz(250)
                    haptics.heavy()
                    // Decided before the best is saved, so equalling it isn't a new best.
                    newBest = game.score > currentBest
                    submitBest(game.score)
                }
                null -> Unit
            }
        }
        state = game.state
        score = game.score
        loop.wake()
    }

    ToolScaffold(
        title = "Stack Tower",
        onBack = onBack,
        actions = {
            NewGameButton(
                onClick = { guard.run(state == StackGame.State.Playing && score > 0) { newGame() } },
                enabled = state != StackGame.State.Ready,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.TopCenter) {
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.3f
            val skyTop = if (dark) Color(0xFF1B1F3A) else Color(0xFFDCE8FF)
            val skyBottom = if (dark) Color(0xFF2E2442) else Color(0xFFFCEFF6)
            Canvas(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Tower $score blocks high"
                        onClick(label = if (state == StackGame.State.Playing) "Drop the block" else "Start") { tap(); true }
                    }
                    .pointerInput(Unit) { detectTapGestures(onPress = { tap() }) },
            ) {
                if (frame < 0) return@Canvas
                // The sky shifts hue slowly as the tower climbs.
                drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))
                drawTower(game, falling, camera, perfectAge)
            }
            Column(Modifier.padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$score",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (best > 0) {
                    Text("Best $best", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                val label = when {
                    state == StackGame.State.Ready -> "Tap to drop each block on the one below.\nLine them up exactly for a perfect."
                    state == StackGame.State.Over -> (if (newBest) "New best!\n" else "") + "Tap to play again"
                    perfectAge < 0.9f -> perfectLabel
                    else -> null
                }
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (state == StackGame.State.Playing) (1f - perfectAge).coerceIn(0f, 1f) else 1f,
                        ),
                    )
                }
            }
        }
    }
    GameGuardDialog(guard)
}

private fun DrawScope.drawTower(game: StackGame, falling: List<Falling>, camera: Float, perfectAge: Float) {
    val w = size.width
    val bh = BLOCK_H * w
    val depth = DEPTH * w
    // Row [level]'s bottom edge on screen: the base sits near the bottom, and everything shifts down as the camera rises.
    fun bottomOf(level: Int) = size.height - w * 0.12f - (level - camera / BLOCK_H) * bh

    game.tower.forEachIndexed { level, b ->
        val y = bottomOf(level)
        if (y < -bh * 2 || y - bh > size.height + bh) return@forEachIndexed
        drawBlock(b.x * w, b.width * w, y, bh, depth, blockColor(level))
    }
    // The base goes all the way down, like a plinth.
    val base = game.tower.first()
    val baseTop = bottomOf(0)
    if (baseTop < size.height) {
        drawRect(blockColor(0).darken(0.12f), Offset(base.x * w, baseTop), Size(base.width * w, size.height - baseTop))
        drawSide(base.x * w + base.width * w, baseTop, size.height - baseTop + depth, depth, blockColor(0).darken(0.3f))
    }
    if (game.state == StackGame.State.Playing) {
        val m = game.moving
        drawBlock(m.x * w, m.width * w, bottomOf(game.tower.size), bh, depth, blockColor(game.tower.size))
    }
    if (perfectAge < 1f) {
        // A ring spreading out from the block that was just placed perfectly.
        val t = game.top
        val grow = perfectAge * w * 0.08f
        drawRoundRect(
            Color.White.copy(alpha = (1f - perfectAge) * 0.8f),
            topLeft = Offset(t.x * w - grow, bottomOf(game.tower.size - 1) - bh - grow),
            size = Size(t.width * w + grow * 2, bh + grow * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(grow),
            style = Stroke(3.dp.toPx()),
        )
    }
    for (f in falling) {
        val x = f.cut.x * w
        val bw = f.cut.width * w
        val y = bottomOf(f.cut.level) + f.y * w
        rotate(f.angle, Offset(x + bw / 2, y - bh / 2)) {
            drawBlock(x, bw, y, bh, depth, blockColor(f.cut.level), alpha = (1f - f.y / 3f).coerceIn(0f, 1f))
        }
    }
}

/** A block as a front face with a lighter top and a darker right side, so the tower reads as 3D. */
private fun DrawScope.drawBlock(x: Float, width: Float, bottom: Float, h: Float, depth: Float, color: Color, alpha: Float = 1f) {
    val top = bottom - h
    val lift = depth * 0.6f
    val topFace = Path().apply {
        moveTo(x, top); lineTo(x + depth, top - lift); lineTo(x + width + depth, top - lift); lineTo(x + width, top); close()
    }
    drawPath(topFace, color.lighten(0.25f), alpha = alpha)
    drawSide(x + width, top, h, depth, color.darken(0.25f), alpha)
    drawRect(color, Offset(x, top), Size(width, h), alpha = alpha)
}

private fun DrawScope.drawSide(x: Float, top: Float, h: Float, depth: Float, color: Color, alpha: Float = 1f) {
    val lift = depth * 0.6f
    val side = Path().apply {
        moveTo(x, top); lineTo(x + depth, top - lift); lineTo(x + depth, top + h - lift); lineTo(x, top + h); close()
    }
    drawPath(side, color, alpha = alpha)
}

private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
