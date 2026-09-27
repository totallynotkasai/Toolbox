package com.toolbox.app.tools.eightball

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ShakeToggleButton
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberShakeEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The 20 classic answers: 10 yes, 5 non-committal, 5 no. */
internal val EIGHT_BALL_ANSWERS = listOf(
    "It is certain", "It is decidedly so", "Without a doubt", "Yes, definitely", "You may rely on it",
    "As I see it, yes", "Most likely", "Outlook good", "Yes", "Signs point to yes",
    "Reply hazy, try again", "Ask again later", "Better not tell you now", "Cannot predict now", "Concentrate and ask again",
    "Don't count on it", "My reply is no", "My sources say no", "Outlook not so good", "Very doubtful",
)

private val TRIANGLE_BLUE = Color(0xFF2336A8)
private val ANSWER_TEXT = Color(0xFFDCE4FF)

@Composable
fun EightBallScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val shake = rememberShakeEnabled()
    var answer by rememberSaveable { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    // The ball jiggles on a loose spring; the answer die fades out and floats back up through the "liquid".
    val jiggleX = remember { Animatable(0f) }
    val jiggleTilt = remember { Animatable(0f) }
    val dieAlpha = remember { Animatable(if (answer == null) 0f else 1f) }
    val dieRise = remember { Animatable(0f) }
    val dieTurn = remember { Animatable(0f) }

    /** [strength] is the shake's peak in m/s² (a tap counts as a medium shake). */
    fun ask(strength: Float) {
        if (busy) return
        busy = true
        haptics.heavy()
        val push = (strength / 20f).coerceIn(0.6f, 1.6f)
        val dir = if (Random.nextBoolean()) 1f else -1f
        scope.launch {
            launch {
                jiggleX.snapTo(28f * push * dir)
                jiggleX.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = Spring.StiffnessMediumLow))
            }
            launch {
                jiggleTilt.snapTo(-7f * push * dir)
                jiggleTilt.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow))
            }
            dieAlpha.animateTo(0f, tween(180))
            delay(250)
            // Never repeat the previous answer, so every ask visibly changes something.
            answer = (EIGHT_BALL_ANSWERS.indices - setOfNotNull(answer)).random()
            dieTurn.snapTo(Random.nextFloat() * 50f - 25f)
            dieRise.snapTo(1f)
            launch { dieRise.animateTo(0f, tween(1100, easing = LinearOutSlowInEasing)) }
            launch { dieTurn.animateTo(Random.nextFloat() * 8f - 4f, tween(1300, easing = LinearOutSlowInEasing)) }
            dieAlpha.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
            haptics.tick()
            busy = false
        }
    }

    OnShake(shake.value) { ask(it) } // ask() ignores shakes while the answer is still surfacing

    ToolScaffold(
        title = "Magic 8-Ball",
        onBack = onBack,
        actions = { ShakeToggleButton(shake, "ask") },
    ) { padding ->
        ToolBody(padding) {
            Text(
                if (shake.value) "Ask a yes-or-no question, then shake or tap the ball"
                else "Ask a yes-or-no question, then tap the ball",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            BoxWithConstraints(
                Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        translationX = jiggleX.value * density
                        rotationZ = jiggleTilt.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Ask the ball",
                    ) { ask(20f) },
                contentAlignment = Alignment.Center,
            ) {
                val ballR = maxWidth / 2
                val windowR = ballR * 0.52f
                val triR = windowR * 0.9f // circumradius of the answer triangle
                Canvas(Modifier.fillMaxSize()) { drawBall() }
                val dens = LocalDensity.current
                val a = answer
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = dieAlpha.value
                            rotationZ = dieTurn.value
                            translationY = dieRise.value * with(dens) { windowR.toPx() } * 0.7f
                            val s = 0.85f + 0.15f * (1f - dieRise.value)
                            scaleX = s; scaleY = s
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = triR.toPx()
                        val c = center + Offset(0f, -r * 0.08f)
                        val path = Path().apply {
                            for (i in 0 until 3) {
                                val ang = -PI / 2 + i * 2 * PI / 3
                                val p = c + Offset(r * cos(ang).toFloat(), r * sin(ang).toFloat())
                                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                            }
                            close()
                        }
                        drawPath(path, TRIANGLE_BLUE)
                        drawPath(path, Color.White.copy(alpha = 0.12f), style = Stroke(r * 0.03f))
                    }
                    if (a != null) {
                        val fontPx = with(dens) { (triR * 0.155f).toSp() }
                        Text(
                            EIGHT_BALL_ANSWERS[a].uppercase(),
                            color = ANSWER_TEXT,
                            fontSize = fontPx,
                            lineHeight = 1.1.em,
                            letterSpacing = 0.04.em,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(triR * 1.12f)
                                .graphicsLayer { translationY = with(dens) { (triR * 0.12f).toPx() } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            // Spoken for screen readers, since the answer inside the ball is decorative text on a canvas.
            Text(
                answer?.let { EIGHT_BALL_ANSWERS[it] } ?: " ",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .graphicsLayer { alpha = dieAlpha.value },
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { ask(20f) }, enabled = !busy) { Text("Ask") }
        }
    }
}

private val BALL = Color(0xFF202124)
private val WINDOW_RING = Color(0xFF3C4043)
private val WINDOW = Color(0xFF0B1030)

/** Flat black ball with a dark window where the answer die floats. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBall() {
    val r = size.minDimension / 2
    drawCircle(BALL, r * 0.97f)
    val w = r * 0.52f
    drawCircle(WINDOW_RING, w * 1.1f)
    drawCircle(WINDOW, w)
}
