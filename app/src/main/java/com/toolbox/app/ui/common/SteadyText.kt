package com.toolbox.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text that always takes the same height, [lines] lines of [reserve] (default: its own [style]), whatever it says,
 * or if it's blank. For status lines above a stage that fills the rest of the screen, so the stage doesn't jump
 * about as the text changes.
 */
@Composable
fun SteadyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    reserve: TextStyle = style,
    lines: Int = 1,
    color: Color = Color.Unspecified,
) {
    // Measured rather than lineHeight × lines: the real height of the lines can be a pixel or two more, and then the
    // text is cut down to fewer lines than were reserved.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val height = remember(reserve, lines, density) {
        with(density) { measurer.measure("A" + "\nA".repeat(lines - 1), reserve).size.height.toDp() }
    }
    Box(modifier.height(height), contentAlignment = Alignment.Center) {
        Text(text, style = style, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
