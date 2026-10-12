package com.toolbox.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * Text that always takes the same height, [lines] lines of [reserve] (default: its own [style]), whatever it says,
 * or if it's blank. For status lines above a stage that fills the rest of the screen, so the stage doesn't jump
 * about as the text changes.
 *
 * Given [widest], it also keeps at least the width of that text, so a number that grows a digit doesn't push its
 * neighbours along. Digits are drawn at one width, so "111" and "888" take the same room.
 */
@Composable
fun SteadyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    reserve: TextStyle = style,
    lines: Int = 1,
    color: Color = Color.Unspecified,
    widest: String? = null,
) {
    val shown = if (widest != null) style.copy(fontFeatureSettings = "tnum") else style
    // Measured rather than lineHeight × lines: the real height of the lines can be a pixel or two more, and then the
    // text is cut down to fewer lines than were reserved.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val height = remember(reserve, lines, density) {
        with(density) { measurer.measure("A" + "\nA".repeat(lines - 1), reserve).size.height.toDp() }
    }
    val width = remember(widest, shown, density) {
        if (widest == null) Dp.Unspecified else with(density) { measurer.measure(widest, shown).size.width.toDp() }
    }
    Box(modifier.height(height).widthIn(min = width), contentAlignment = Alignment.Center) {
        Text(text, style = shown, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/**
 * A number with its label underneath, as the toys show their counts. [widest] is the widest the value gets (e.g.
 * "-00.00"), so a row of stats stays put as the numbers change; leave it out for counts that only grow.
 */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, widest: String? = null) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        SteadyText(value, MaterialTheme.typography.headlineMedium, widest = widest ?: "")
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
