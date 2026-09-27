package com.toolbox.app.tools.coin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ShakeToggleButton
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.ui.common.rememberToneSynth
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** Nearly straight down, so the resting coin reads as a round face with just a sliver of edge. */
private val CAMERA = CoinCamera(80f)

/**
 * The classic flip: the result is picked up front and the animation lands on it.
 * Only the look is simulated (lighting, edge, shadow, landing settle); see CoinTossScreen for real physics.
 */
@Composable
fun CoinFlipScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val synth = rememberToneSynth()
    val art = remember { CoinArt() }
    var stats by rememberCoinStats("coin_stats")
    // Rest on the face last thrown, so coming back shows the previous result.
    val rotation = remember { Animatable(if (stats.streakHeads == false) 180f else 0f) }
    val lift = remember { Animatable(0f) }
    val settle = remember { Animatable(1f) }
    var flipping by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<Boolean?>(null) } // this visit's last result; true = heads

    fun flip() {
        if (flipping) return
        flipping = true
        haptics.click()
        val landHeads = Random.nextBoolean()
        // Each 180° is one face change. Pick an even/odd count of half-turns so we land on the chosen face.
        val currentHeads = floor((rotation.value + 90f) / 180f).toInt() % 2 == 0
        var halfTurns = 8 + Random.nextInt(3)
        if ((halfTurns % 2 == 0) != (currentHeads == landHeads)) halfTurns++
        val target = rotation.value + halfTurns * 180f
        scope.launch {
            launch {
                lift.animateTo(1f, tween(1100, easing = LinearEasing)) // drives a parabola below
                lift.snapTo(0f)
            }
            rotation.animateTo(target, tween(1100, easing = FastOutSlowInEasing))
            stats = stats.record(landHeads)
            result = landHeads
            synth.clink(0.75f)
            haptics.heavy()
            settle.snapTo(0f)
            settle.animateTo(1f, tween(300, easing = LinearEasing))
            flipping = false
        }
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { flip() } // flip() ignores shakes mid-flip

    ToolScaffold(
        title = "Flip a Coin",
        onBack = onBack,
        actions = {
            ShakeToggleButton(shake, "flip")
            IconButton(onClick = { stats = CoinStats(); result = null }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset counts")
            }
        },
    ) { padding ->
        val stage = @Composable { modifier: Modifier ->
            CoinStage(
                art = art,
                rotationDeg = rotation.value,
                lift = lift.value,
                settle = settle.value,
                modifier = modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Flip the coin",
                    role = Role.Button,
                ) { flip() },
            )
        }
        val controls = @Composable { scope: ColumnScope ->
            with(scope) {
                Text(
                    when (result) {
                        null -> if (shake.value) "Tap, shake or press Flip" else "Tap the coin or press Flip"
                        true -> "Heads"
                        false -> "Tails"
                    },
                    style = if (result == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = ::flip, enabled = !flipping) { Text("Flip") }
                Spacer(Modifier.height(24.dp))
                CoinStatsPanel(stats)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    stage(Modifier.weight(1f).fillMaxSize())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { controls(this) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    stage(Modifier.widthIn(max = 420.dp).fillMaxWidth().weight(1f, fill = false).heightIn(min = 240.dp, max = 440.dp))
                    Spacer(Modifier.height(16.dp))
                    controls(this)
                }
            }
        }
    }
}

/**
 * The coin rising and falling above its shadow. [lift] runs 0→1 over a flip and traces a parabola;
 * [settle] runs 0→1 after landing for a slight squash and then a small bob.
 */
@Composable
private fun CoinStage(art: CoinArt, rotationDeg: Float, lift: Float, settle: Float, modifier: Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val radius = minOf(size.width * 0.3f, size.height * 0.24f, 100.dp.toPx())
        val ground = Offset(size.width / 2, size.height - radius - 12.dp.toPx())
        val height = sin(lift * PI).toFloat()
        val rise = (size.height - 2 * radius - 24.dp.toPx()).coerceAtLeast(0f)

        drawCoinShadow(
            ground + Offset(radius * (0.05f + 0.25f * height), radius * (0.1f + 0.2f * height)),
            rx = radius * (1.02f - 0.3f * height),
            ry = radius * (0.99f - 0.3f * height),
            height = height,
        )

        // Landing: a quick squash on the bottom edge, then a small bob.
        val squash = if (settle < 0.4f) sin(settle / 0.4f * PI).toFloat() * 0.05f else 0f
        val bob = if (settle >= 0.4f) sin((settle - 0.4f) / 0.6f * PI).toFloat() * 0.05f * radius else 0f
        val scale = 1f + 0.25f * height
        val centre = ground - Offset(0f, rise * height + bob)
        withTransform({
            scale(1f + squash * 0.6f, 1f - squash, ground + Offset(0f, radius))
        }) {
            art.draw(this, centre, radius * scale, Rot.x(Math.toRadians(rotationDeg.toDouble()).toFloat()) * Rot.Rest, CAMERA)
        }
    }
}
