package com.toolbox.app.tools.dice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberShakeEnabled
import com.toolbox.app.tools.coin.CoinColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val DIE_TYPES = listOf(4, 6, 8, 10, 12, 20)

@Composable
fun DiceScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var sides by rememberSaveable { mutableIntStateOf(6) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var values by rememberSaveable { mutableStateOf(listOf(6)) }
    var rolling by remember { mutableStateOf(false) }
    val wobble = remember { Animatable(0f) }

    fun roll() {
        if (rolling) return
        rolling = true
        scope.launch {
            launch { wobble.snapTo(0f); wobble.animateTo(1f, tween(650)) }
            // Tumble through random faces, slowing down, then settle.
            listOf(40L, 50L, 60L, 80L, 100L, 130L, 170L).forEach {
                values = List(count) { Random.nextInt(1, sides + 1) }
                haptics.tick()
                delay(it)
            }
            values = List(count) { Random.nextInt(1, sides + 1) }
            haptics.heavy()
            rolling = false
        }
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { roll() } // roll() ignores shakes mid-roll

    ToolScaffold(
        title = "Roll a Die",
        onBack = onBack,
    ) { padding ->
        ToolBody(padding) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DIE_TYPES.forEach { s ->
                    FilterChip(
                        selected = sides == s,
                        onClick = { sides = s; values = List(count) { s } },
                        label = { Text("d$s") },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            val dieSize: Dp = if (count <= 2) 120.dp else 88.dp
            Column(
                Modifier.height(280.dp).clickable(enabled = !rolling) { roll() },
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                values.chunked(3).forEachIndexed { row, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        chunk.forEachIndexed { i, v ->
                            val idx = row * 3 + i
                            val spin = sin(wobble.value * PI * 3).toFloat() * 25f * (1f - wobble.value)
                            Die(
                                sides = sides,
                                value = v,
                                size = dieSize,
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = if (idx % 2 == 0) spin else -spin
                                    translationY = -40f * sin(wobble.value * PI).toFloat()
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (count > 1) "Total ${values.sum()}" else " ",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalIconButton(onClick = { if (count > 1) { count--; values = values.take(count) } }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Fewer dice")
                }
                Text("$count ${if (count == 1) "die" else "dice"}", style = MaterialTheme.typography.titleMedium)
                FilledTonalIconButton(onClick = { if (count < 6) { count++; values = values + sides } }) {
                    Icon(Icons.Filled.Add, contentDescription = "More dice")
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = ::roll, enabled = !rolling) { Text("Roll") }
        }
    }
}

@Composable
private fun Die(sides: Int, value: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            if (sides == 6) {
                // Coin colours: a darker rim round a lighter face, with ink pips.
                drawRoundRect(CoinColors.Rim, cornerRadius = CornerRadius(s * 0.18f))
                val inset = s * 0.06f
                drawRoundRect(
                    CoinColors.Face, topLeft = Offset(inset, inset), size = Size(s - 2 * inset, s - 2 * inset),
                    cornerRadius = CornerRadius(s * 0.13f),
                )
                drawPips(value)
            } else {
                drawPath(polygonFor(sides, s), CoinColors.Rim)
                scale(0.84f, Offset(s / 2, s / 2)) { drawPath(polygonFor(sides, s), CoinColors.Face) }
            }
        }
        if (sides != 6) {
            Text(
                "$value",
                color = CoinColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.3f).sp,
                modifier = if (sides == 4) Modifier.padding(top = size * 0.22f) else Modifier,
            )
        }
    }
}

private fun DrawScope.drawPips(value: Int) {
    val s = size.minDimension
    val r = s * 0.085f
    val a = s * 0.27f; val m = s * 0.5f; val b = s * 0.73f
    val pts = when (value) {
        1 -> listOf(m to m)
        2 -> listOf(a to a, b to b)
        3 -> listOf(a to a, m to m, b to b)
        4 -> listOf(a to a, b to a, a to b, b to b)
        5 -> listOf(a to a, b to a, m to m, a to b, b to b)
        else -> listOf(a to a, b to a, a to m, b to m, a to b, b to b)
    }
    pts.forEach { (x, y) -> drawCircle(CoinColors.Ink, r, Offset(x, y)) }
}

/** Flat silhouettes that read as the right die at a glance: triangle, diamond, pentagon, hexagon... */
private fun polygonFor(sides: Int, s: Float): Path {
    val corners = when (sides) { 4 -> 3; 8 -> 4; 10 -> 4; 12 -> 5; else -> 6 }
    val c = s / 2
    val r = s * 0.5f
    return Path().apply {
        for (i in 0 until corners) {
            val angle = -PI / 2 + 2 * PI * i / corners
            // d10 is a squashed kite so it differs from the d8 diamond.
            val rx = if (sides == 10) r * 0.72f else r
            val x = c + rx * cos(angle).toFloat()
            val y = c + r * sin(angle).toFloat() * (if (corners == 3) 1.1f else 1f) + (if (corners == 3) s * 0.08f else 0f)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
