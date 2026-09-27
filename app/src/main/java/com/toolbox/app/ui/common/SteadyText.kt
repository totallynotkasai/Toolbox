package com.toolbox.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
    val height = with(LocalDensity.current) { (reserve.lineHeight * lines).toDp() }
    Box(modifier.height(height), contentAlignment = Alignment.Center) {
        Text(text, style = style, color = color, maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
