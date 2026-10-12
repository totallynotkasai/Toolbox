package com.toolbox.app.tools.random

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberShakeEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun RandomNumberScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    var minText by rememberSaveable { mutableStateOf("1") }
    var maxText by rememberSaveable { mutableStateOf("100") }
    var value by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    val min = minText.toLongOrNull()
    val max = maxText.toLongOrNull()
    val valid = min != null && max != null && min <= max

    fun generate() {
        if (!valid || busy) return
        focus.clearFocus()
        val lo = min; val hi = max
        busy = true
        scope.launch {
            // A quick shuffle through values before revealing the real one.
            repeat(8) { value = Random.nextLong(lo, hi + 1); haptics.tick(); delay(45L + it * 12) }
            value = Random.nextLong(lo, hi + 1)
            haptics.heavy()
            busy = false
        }
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { generate() } // generate() ignores shakes mid-shuffle or with an invalid range

    ToolScaffold(
        title = "Random Number",
        onBack = onBack,
    ) { padding ->
        ToolBody(padding) {
            AnimatedContent(
                targetState = value,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "number",
            ) { v ->
                Text(v?.toString() ?: "?", style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.6f))
            }
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Min", minText) { minText = it }
                Text("to")
                NumberField("Max", maxText) { maxText = it }
            }
            if (!valid) {
                Spacer(Modifier.height(8.dp))
                Text("Min must be less than or equal to max", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = ::generate, enabled = valid && !busy) { Text("Generate") }
        }
    }
}

@Composable
private fun NumberField(label: String, text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = { s -> if (s.length <= 12 && s.matches(Regex("-?\\d*"))) onChange(s) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(120.dp),
    )
}
