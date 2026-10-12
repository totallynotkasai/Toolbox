package com.toolbox.app.games.breaker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.GameGuardDialog
import com.toolbox.app.ui.common.NewGameButton
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.accessibilityActions
import com.toolbox.app.ui.common.rememberBestScore
import com.toolbox.app.ui.common.rememberGameGuard
import com.toolbox.app.ui.common.rememberGameLoop
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.theme.GoogleColors
import com.toolbox.app.ui.theme.ink
import com.toolbox.app.ui.theme.isLight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// World units: width 1.0, height H. Rendering scales to pixels, so physics is resolution-independent.
private const val H = 1.4f
private const val COLS = 8
private const val BALL_R = 0.018f
private const val PADDLE_W = 0.22f
private const val PADDLE_H = 0.025f
private const val PADDLE_Y = H - 0.08f
private const val MAX_BALLS = 9
private const val DROP_CHANCE = 0.2f
private const val DROP_SPEED = 0.35f

private enum class Mode(val label: String) { Classic("Classic"), Action("Action") }

private enum class Kind { Normal, Tough, Steel, Explosive }

// Green, red and pink are darker than the Google tones, so the white letter on each reads clearly.
private enum class Power(val letter: String, val color: Color, val weight: Int) {
    Wide("W", GoogleColors.Blue, 5),
    Multi("M", GoogleColors.GreenInk, 5),
    Slow("S", Color(0xFF8E24AA), 4),
    Fire("F", Color(0xFFC5221F), 3),
    Life("+", Color(0xFFC2185B), 1),
    Shrink("−", Color(0xFF5F6368), 3), // the one to dodge
}

private class Brick(val col: Int, val row: Int, val rect: Rect, val kind: Kind, var hp: Int) {
    val maxHp = hp
    var alive = true
}
private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)
private class Drop(val power: Power, val x: Float, var y: Float)
private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color)

private val ROW_COLORS = listOf(GoogleColors.Red, GoogleColors.Red, GoogleColors.Yellow, GoogleColors.Yellow, GoogleColors.Green, GoogleColors.Green, GoogleColors.Blue)

private class Breaker(val mode: Mode, private val onHit: (strong: Boolean) -> Unit) {
    val action = mode == Mode.Action
    var bricks = buildBricks(1)
    val balls = mutableListOf<Ball>()
    val drops = mutableListOf<Drop>()
    val particles = mutableListOf<Particle>()
    var paddleX = 0.5f
    var stuck by mutableStateOf(true) // ball resting on the paddle, waiting for a tap
    var score by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var level by mutableIntStateOf(1)
    var over by mutableStateOf(false)

    // Power-up timers, seconds remaining.
    var wideT = 0f; var shrinkT = 0f; var slowT = 0f; var fireT = 0f

    val paddleW get() = PADDLE_W * (if (wideT > 0) 1.5f else 1f) * (if (shrinkT > 0) 0.6f else 1f)
    private val baseSpeed get() = 0.75f + level * 0.08f
    private val speedFactor get() = if (slowT > 0) 0.6f else 1f

    fun buildBricks(level: Int): List<Brick> {
        val rows = if (action) 7 else 6
        val gap = 0.01f
        val w = (1f - gap * (COLS + 1)) / COLS
        val h = 0.04f
        val rnd = Random(level * 7919) // deterministic layout per level
        val list = mutableListOf<Brick>()
        for (r in 0 until rows) for (c in 0 until COLS) {
            if (action && !layoutHas(level, r, c, rows, rnd)) continue
            val kind = if (!action) Kind.Normal else {
                val roll = rnd.nextFloat()
                when {
                    level >= 2 && r >= 2 && roll < 0.07f -> Kind.Steel
                    roll < 0.15f -> Kind.Explosive
                    roll < 0.35f + 0.03f * level -> Kind.Tough
                    else -> Kind.Normal
                }
            }
            val hp = when (kind) { Kind.Tough -> if (level >= 3 && rnd.nextBoolean()) 3 else 2; else -> 1 }
            val left = gap + c * (w + gap)
            val top = 0.1f + r * (h + gap)
            list += Brick(c, r, Rect(left, top, left + w, top + h), kind, hp)
        }
        return list
    }

    /** Action levels cycle through shapes so each wave looks different. */
    private fun layoutHas(level: Int, r: Int, c: Int, rows: Int, rnd: Random): Boolean = when (level % 4) {
        1 -> true                                            // full wall
        2 -> (r + c) % 2 == 0 || r == 0                      // checkerboard
        3 -> abs(c - 3.5f) <= (rows - r) * 0.7f              // pyramid
        else -> rnd.nextFloat() > 0.22f                      // scattered
    }

    /** Whether anything is moving: once the ball rests on the paddle and the last sparks fade, nothing needs drawing. */
    val moving: Boolean get() = (!stuck && !over) || particles.isNotEmpty()

    /** Something to lose: a ball in play or points scored, and the game not over. */
    val underWay: Boolean get() = !over && (!stuck || score > 0)

    fun launch() {
        if (over) { restart(); return }
        if (!stuck) return
        stuck = false
        val a = Math.toRadians(-90.0 + (Math.random() * 50 - 25))
        balls.clear()
        balls += Ball(paddleX, PADDLE_Y - BALL_R * 1.5f, (cos(a) * baseSpeed).toFloat(), (sin(a) * baseSpeed).toFloat())
    }

    fun restart() {
        bricks = buildBricks(1); score = 0; lives = 3; level = 1; over = false
        resetRound()
    }

    /**
     * The game as ints for saved state: level, score, lives, game over, then each brick's hits left (0 once broken).
     * A level's layout is rebuilt from its seed, so the bricks themselves needn't be stored.
     */
    fun save(): IntArray = intArrayOf(level, score, lives, if (over) 1 else 0) + bricks.map { if (it.alive) it.hp else 0 }

    /** Restores [save]d state with the ball back on the paddle, so nothing moves until the next tap. */
    fun restore(saved: IntArray) {
        level = saved[0]; score = saved[1]; lives = saved[2]; over = saved[3] == 1
        bricks = buildBricks(level).onEachIndexed { i, b ->
            val hp = saved.getOrElse(4 + i) { b.hp }
            if (hp <= 0) b.alive = false else b.hp = hp
        }
        resetRound()
    }

    private fun resetRound() {
        stuck = true; balls.clear(); drops.clear()
        wideT = 0f; shrinkT = 0f; slowT = 0f; fireT = 0f
    }

    fun update(dt: Float) {
        particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.vy += 1.2f * dt; it.life -= dt }
        particles.removeAll { it.life <= 0f }
        if (over) return
        paddleX = paddleX.coerceIn(paddleW / 2, 1 - paddleW / 2)
        if (stuck) return

        wideT = (wideT - dt).coerceAtLeast(0f); shrinkT = (shrinkT - dt).coerceAtLeast(0f)
        slowT = (slowT - dt).coerceAtLeast(0f); fireT = (fireT - dt).coerceAtLeast(0f)

        // Falling power-ups: caught by the paddle or lost off the bottom.
        drops.forEach { it.y += DROP_SPEED * dt }
        val caught = drops.filter { d ->
            d.y >= PADDLE_Y - 0.01f && d.y <= PADDLE_Y + PADDLE_H + 0.02f && abs(d.x - paddleX) <= paddleW / 2 + 0.03f
        }
        caught.forEach { apply(it.power) }
        drops.removeAll { it in caught || it.y > H + 0.05f }

        // Sub-step so a fast ball can't tunnel through a brick in one frame.
        repeat(4) { substep(dt / 4) }

        balls.removeAll { it.y > H + BALL_R }
        if (balls.isEmpty() && !stuck) {
            lives--
            onHit(true)
            if (lives <= 0) over = true
            resetRound()
        }
        if (bricks.none { it.alive && it.kind != Kind.Steel }) {
            level++
            bricks = buildBricks(level)
            resetRound()
        }
    }

    private fun apply(p: Power) {
        onHit(true)
        when (p) {
            Power.Wide -> { wideT = 12f; shrinkT = 0f }
            Power.Shrink -> { shrinkT = 10f; wideT = 0f }
            Power.Slow -> slowT = 10f
            Power.Fire -> fireT = 8f
            Power.Life -> lives++
            Power.Multi -> {
                // Each ball splits into three, fanned ±20°.
                val extra = balls.flatMap { b ->
                    listOf(-20.0, 20.0).map { deg ->
                        val a = Math.toRadians(deg)
                        Ball(b.x, b.y, (b.vx * cos(a) - b.vy * sin(a)).toFloat(), (b.vx * sin(a) + b.vy * cos(a)).toFloat())
                    }
                }
                balls += extra.take((MAX_BALLS - balls.size).coerceAtLeast(0))
            }
        }
    }

    private fun substep(dt: Float) {
        val sf = speedFactor
        for (b in balls) {
            b.x += b.vx * sf * dt; b.y += b.vy * sf * dt
            if (b.x < BALL_R) { b.x = BALL_R; b.vx = abs(b.vx) }
            if (b.x > 1 - BALL_R) { b.x = 1 - BALL_R; b.vx = -abs(b.vx) }
            if (b.y < BALL_R) { b.y = BALL_R; b.vy = abs(b.vy) }

            // Paddle: bounce angle depends on where the ball lands, like the classic.
            val pw = paddleW
            if (b.vy > 0 && b.y + BALL_R >= PADDLE_Y && b.y < PADDLE_Y + PADDLE_H && abs(b.x - paddleX) <= pw / 2 + BALL_R) {
                val offset = ((b.x - paddleX) / (pw / 2)).coerceIn(-1f, 1f)
                val a = Math.toRadians(-90.0 + offset * 60)
                val s = hypot(b.vx, b.vy)
                b.vx = (cos(a) * s).toFloat(); b.vy = (sin(a) * s).toFloat()
                b.y = PADDLE_Y - BALL_R
                onHit(false)
                continue
            }

            val brick = bricks.firstOrNull { br ->
                br.alive && run {
                    val cx = b.x.coerceIn(br.rect.left, br.rect.right)
                    val cy = b.y.coerceIn(br.rect.top, br.rect.bottom)
                    hypot(b.x - cx, b.y - cy) < BALL_R
                }
            } ?: continue

            // Fireball smashes straight through anything but steel.
            if (fireT > 0 && brick.kind != Kind.Steel) { destroy(brick); continue }

            val r = brick.rect
            // Reflect along whichever axis has the shallower penetration.
            val overlapX = minOf(abs(b.x + BALL_R - r.left), abs(r.right - (b.x - BALL_R)))
            val overlapY = minOf(abs(b.y + BALL_R - r.top), abs(r.bottom - (b.y - BALL_R)))
            if (overlapX < overlapY) {
                b.vx = -b.vx; b.x += if (b.x < r.center.x) -overlapX else overlapX
            } else {
                b.vy = -b.vy; b.y += if (b.y < r.center.y) -overlapY else overlapY
            }
            hit(brick)
        }
    }

    private fun hit(b: Brick) {
        when (b.kind) {
            Kind.Steel -> { burst(b, Color(0xFFBDC1C6), 3); onHit(false) }
            Kind.Tough -> { b.hp--; if (b.hp <= 0) destroy(b) else { burst(b, colorOf(b), 3); onHit(false) } }
            else -> destroy(b)
        }
    }

    private fun destroy(b: Brick) {
        if (!b.alive || b.kind == Kind.Steel) return
        b.alive = false
        score += when (b.kind) { Kind.Tough -> 25; Kind.Explosive -> 30; else -> 10 } * (if (action) 1 else (7 - b.row))
        burst(b, colorOf(b), if (b.kind == Kind.Explosive) 16 else 8)
        if (action && Random.nextFloat() < DROP_CHANCE) {
            val total = Power.entries.sumOf { it.weight }
            var pick = Random.nextInt(total)
            val power = Power.entries.first { pick -= it.weight; pick < 0 }
            drops += Drop(power, b.rect.center.x, b.rect.center.y)
        }
        if (b.kind == Kind.Explosive) {
            onHit(true)
            // Chain reaction: clears the 8 neighbours, which may themselves be explosive.
            bricks.filter { it.alive && abs(it.col - b.col) <= 1 && abs(it.row - b.row) <= 1 }.forEach { destroy(it) }
        } else onHit(false)
    }

    private fun burst(b: Brick, color: Color, n: Int) {
        repeat(n) {
            val a = Random.nextDouble(0.0, 2 * Math.PI)
            val s = Random.nextFloat() * 0.5f + 0.2f
            particles += Particle(b.rect.center.x, b.rect.center.y, (cos(a) * s).toFloat(), (sin(a) * s).toFloat(), 0.5f + Random.nextFloat() * 0.3f, color)
        }
    }

    fun colorOf(b: Brick): Color = when (b.kind) {
        Kind.Steel -> Color(0xFF9AA0A6)
        Kind.Explosive -> Color(0xFFFF6D00)
        else -> ROW_COLORS[b.row % ROW_COLORS.size]
    }
}

@Composable
fun BlockBreakerScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    var mode by rememberSaveable { mutableStateOf(Mode.Classic) }
    val onHit = { strong: Boolean -> if (strong) haptics.heavy() else haptics.tick() }
    val game = rememberSaveable(mode, saver = Saver(save = { it.save() }, restore = { Breaker(mode, onHit).apply { restore(it) } })) {
        Breaker(mode, onHit)
    }
    var frame by remember { mutableLongStateOf(0L) }
    val (best, submit) = rememberBestScore(if (mode == Mode.Classic) "breaker" else "breaker-action")
    val guard = rememberGameGuard()
    val measurer = rememberTextMeasurer()
    // The power-up letters, measured once rather than on every frame.
    val letters = remember(measurer) {
        Power.entries.associateWith { measurer.measure(it.letter, TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
    }

    // Idles while the ball rests on the paddle or the game is over, once the last sparks have faded. It's held still
    // while a "Start a new game?" question is open, so the ball can't be lost behind it.
    val loop = rememberGameLoop(enabled = !guard.isAsking, maxDt = 1f / 30) { dt ->
        val wasOver = game.over
        game.update(dt)
        if (game.over && !wasOver) submit(game.score)
        frame++
        game.moving
    }

    /** Ends a run that's under way: its score still counts towards the best, then [then] runs. */
    fun endRun(title: String, confirm: String, then: () -> Unit) {
        guard.run(game.underWay, title = title, text = if (game.score > 0) "This run will end. Its score of ${game.score} still counts towards your best." else "This run will end.", confirm = confirm) {
            submit(game.score)
            then()
            loop.wake()
        }
    }

    fun nudgePaddle(by: Float) {
        game.paddleX = (game.paddleX + by).coerceIn(game.paddleW / 2, 1 - game.paddleW / 2)
        loop.wake()
    }

    val surface = MaterialTheme.colorScheme.surfaceContainer
    val paddleColor = MaterialTheme.colorScheme.onSurface
    val light = MaterialTheme.colorScheme.isLight

    ToolScaffold(
        title = "Block Breaker",
        onBack = onBack,
        actions = {
            NewGameButton(
                onClick = { endRun("Start a new game?", "New game") { game.restart() } },
                enabled = game.underWay || game.over,
            )
        },
    ) { padding ->
        ToolBody(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { if (m != mode) endRun("Switch to ${m.label}?", "Switch") { mode = m } },
                        label = { Text(m.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Wraps onto a second line at large font sizes.
            FlowRow(Modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Score ${game.score}", style = MaterialTheme.typography.titleMedium)
                Text("Level ${game.level}", style = MaterialTheme.typography.titleMedium)
                val lives = game.lives.coerceAtLeast(0)
                Text(
                    "♥ $lives",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoogleColors.Red.ink(),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = livesText(lives) },
                )
                Text("Best $best", style = MaterialTheme.typography.titleMedium)
            }
            if (game.action) ActiveEffects(game) { frame }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f / H)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface)
                    // A tap launches; a drag only moves the paddle, so you can line up before launching.
                    .pointerInput(game) { detectTapGestures { game.launch(); loop.wake() } }
                    .pointerInput(game) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            game.paddleX = (change.position.x / size.width).coerceIn(game.paddleW / 2, 1 - game.paddleW / 2)
                            loop.wake()
                        }
                    }
                    // With TalkBack a double tap does what a tap does, and the paddle moves from its actions menu.
                    .semantics {
                        contentDescription = "Play area, level ${game.level}, ${livesText(game.lives.coerceAtLeast(0))}"
                        onClick(label = if (game.over) "Play again" else "Launch ball") { game.launch(); loop.wake(); true }
                        if (!game.over) {
                            customActions = accessibilityActions(
                                "Move paddle left" to { nudgePaddle(-PADDLE_NUDGE) },
                                "Move paddle right" to { nudgePaddle(PADDLE_NUDGE) },
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame // read so the canvas redraws every frame
                    val s = size.width
                    game.bricks.forEach { if (it.alive) drawBrick(it, game.colorOf(it), s, light) }
                    game.particles.forEach { p ->
                        drawCircle(p.color.copy(alpha = (p.life / 0.8f).coerceIn(0f, 1f)), 0.006f * s, Offset(p.x * s, p.y * s))
                    }
                    game.drops.forEach { d ->
                        val w = 0.075f * s; val h = 0.034f * s
                        val tl = Offset(d.x * s - w / 2, d.y * s - h / 2)
                        drawRoundRect(d.power.color, tl, Size(w, h), CornerRadius(h / 2))
                        val label = letters.getValue(d.power)
                        drawText(label, topLeft = Offset(d.x * s - label.size.width / 2f, d.y * s - label.size.height / 2f))
                    }
                    val pw = game.paddleW
                    drawRoundRect(
                        if (game.shrinkT > 0) Color(0xFF9AA0A6) else paddleColor,
                        Offset((game.paddleX - pw / 2) * s, PADDLE_Y * s),
                        Size(pw * s, PADDLE_H * s),
                        CornerRadius(PADDLE_H * s / 2),
                    )
                    val ballColor = if (game.fireT > 0) Color(0xFFFF6D00) else GoogleColors.Blue
                    if (game.stuck && !game.over) {
                        drawCircle(ballColor, BALL_R * s, Offset(game.paddleX * s, (PADDLE_Y - BALL_R * 1.5f) * s))
                    }
                    game.balls.forEach { b ->
                        if (game.fireT > 0) drawCircle(Color(0x66FFAB00), BALL_R * 1.9f * s, Offset(b.x * s, b.y * s))
                        drawCircle(ballColor, BALL_R * s, Offset(b.x * s, b.y * s))
                    }
                }
                when {
                    game.over -> Text(
                        "Game over · tap to play again",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    game.stuck -> Text(
                        if (game.level > 1) "Level ${game.level} · tap to launch" else "Tap to launch · drag to move",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (game.action) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Catch: W wide · M multi-ball · S slow · F fireball · + life. Dodge − (shrink).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    GameGuardDialog(guard)
}

/** Timers for running power-ups. Reads the frame clock itself so only this row recomposes every frame. */
@Composable
private fun ActiveEffects(game: Breaker, frame: () -> Long) {
    frame()
    val active = listOfNotNull(
        (Power.Wide to game.wideT).takeIf { it.second > 0 },
        (Power.Shrink to game.shrinkT).takeIf { it.second > 0 },
        (Power.Slow to game.slowT).takeIf { it.second > 0 },
        (Power.Fire to game.fireT).takeIf { it.second > 0 },
    )
    Row(Modifier.heightIn(min = 28.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        active.forEach { (p, t) ->
            Box(Modifier.clip(CircleShape).background(p.color).padding(horizontal = 10.dp, vertical = 2.dp)) {
                Text("${p.letter} ${t.toInt() + 1}s", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** "3 lives", "1 life". */
private fun livesText(lives: Int) = if (lives == 1) "1 life" else "$lives lives"

/** How far a TalkBack "Move paddle" action moves it, as a share of the width. */
private const val PADDLE_NUDGE = 0.1f

private fun DrawScope.drawBrick(b: Brick, color: Color, s: Float, light: Boolean) {
    val tl = Offset(b.rect.left * s, b.rect.top * s)
    val sz = Size(b.rect.width * s, b.rect.height * s)
    val corner = CornerRadius(4.dp.toPx())
    // On a light background yellow and green bricks are faint, so they get a thin edge of the darker tone.
    fun edge(fill: Color) {
        if (!light || (fill != GoogleColors.Yellow && fill != GoogleColors.Green)) return
        val w = 1.dp.toPx()
        drawRoundRect(GoogleColors.ink(fill, light), tl + Offset(w / 2, w / 2), Size(sz.width - w, sz.height - w), corner, style = Stroke(w))
    }
    when (b.kind) {
        Kind.Normal -> { drawRoundRect(color, tl, sz, corner); edge(color) }
        Kind.Tough -> {
            // Darker, with a bevel; cracks appear as it takes damage.
            drawRoundRect(color.copy(red = color.red * 0.7f, green = color.green * 0.7f, blue = color.blue * 0.7f), tl, sz, corner)
            drawRoundRect(Color.White.copy(alpha = 0.35f), tl, sz, corner, style = Stroke(2.dp.toPx()))

            if (b.hp < b.maxHp) {
                val y = tl.y + sz.height / 2
                drawLine(Color.Black.copy(alpha = 0.45f), Offset(tl.x + sz.width * 0.2f, tl.y + 2), Offset(tl.x + sz.width * 0.45f, y), 1.5.dp.toPx())
                drawLine(Color.Black.copy(alpha = 0.45f), Offset(tl.x + sz.width * 0.45f, y), Offset(tl.x + sz.width * 0.35f, tl.y + sz.height - 2), 1.5.dp.toPx())
            }
        }
        Kind.Steel -> {
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFDADCE0), Color(0xFF80868B)), tl.y, tl.y + sz.height), tl, sz, corner)
            listOf(0.15f, 0.85f).forEach { fx -> drawCircle(Color(0xFF5F6368), 1.5.dp.toPx(), Offset(tl.x + sz.width * fx, tl.y + sz.height / 2)) }
        }
        Kind.Explosive -> {
            drawRoundRect(GoogleColors.Yellow, tl, sz, corner)
            edge(GoogleColors.Yellow)
            drawCircle(color, sz.height * 0.32f, tl + Offset(sz.width / 2, sz.height / 2))
            drawCircle(Color.White, sz.height * 0.12f, tl + Offset(sz.width / 2, sz.height / 2))
        }
    }
}
