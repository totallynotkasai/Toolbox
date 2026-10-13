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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.ui.common.OnShake
import com.toolbox.app.ui.common.ResultHistorySheet
import com.toolbox.app.ui.common.ResultRow
import com.toolbox.app.ui.common.ResultStrip
import com.toolbox.app.ui.common.STRIP_MAX
import com.toolbox.app.ui.common.ToolBody
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberResultLog
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
    var result by rememberSaveable { mutableStateOf<Long?>(null) }
    // What TalkBack reads out: the final number only, with "again" when it repeats so the change is still announced.
    var spoken by rememberSaveable { mutableStateOf("") }
    // Not saved: the shuffle that clears them doesn't survive a recreation.
    var busy by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val history = rememberResultLog("random_history", snackbar)

    val min = minText.toLongOrNull()
    val max = maxText.toLongOrNull()
    val problem = rangeProblem(minText, maxText)

    fun generate() {
        if (problem != null || busy || min == null || max == null) return
        focus.clearFocus()
        val lo = min; val hi = max
        busy = true
        scope.launch {
            // A quick shuffle through values before revealing the real one.
            repeat(8) { shuffle = Random.nextLong(lo, hi + 1); haptics.tick(); delay(45L + it * 12) }
            val v = Random.nextLong(lo, hi + 1)
            spoken = if (v == result && spoken == "$v") "$v again" else "$v"
            result = v
            history.add(Draw(v, lo, hi).fields())
            busy = false
            haptics.heavy()
        }
    }

    val shake = rememberShakeEnabled()
    OnShake(shake.value) { generate() } // generate() ignores shakes mid-shuffle or with an invalid range

    ToolScaffold(
        title = "Random Number",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ToolBody(padding) {
            val big = MaterialTheme.typography.displayLarge.fontSize * 1.6f
            AnimatedContent(
                targetState = if (busy) shuffle else result,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                contentAlignment = Alignment.Center,
                label = "number",
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = spoken.ifEmpty { "No number yet" }
                    liveRegion = LiveRegionMode.Polite
                },
            ) { v ->
                // Long numbers shrink to stay on one line.
                Text(
                    v?.toString() ?: "?",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = big, lineHeight = big * 1.1f),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = big),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            val recent = history.entries.mapNotNull(Draw::from).take(STRIP_MAX).reversed()
            ResultStrip(recent.map { "${it.value}" }, onOpen = { history.open = true })
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField("Min", "minimum", minText, isError = problem?.min == true, modifier = Modifier.weight(1f)) { minText = it }
                Text("to")
                NumberField("Max", "maximum", maxText, isError = problem?.max == true, modifier = Modifier.weight(1f)) { maxText = it }
            }
            if (problem != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    problem.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = ::generate, enabled = problem == null && !busy) { Text("Generate") }
        }
    }

    if (history.open) {
        ResultHistorySheet(
            rows = history.entries.mapNotNull(Draw::from).map { ResultRow(it.text, it.spoken) },
            onClear = history::clear,
            onDismiss = { history.open = false },
        )
    }
}

/** Why the range can't be used, with which fields are at fault, or null when it's fine. */
internal data class RangeProblem(val message: String, val min: Boolean, val max: Boolean)

internal fun rangeProblem(minText: String, maxText: String): RangeProblem? {
    val min = minText.toLongOrNull()
    val max = maxText.toLongOrNull()
    return when {
        min == null && max == null -> RangeProblem("Enter a minimum and a maximum", min = true, max = true)
        min == null -> RangeProblem("Enter a minimum", min = true, max = false)
        max == null -> RangeProblem("Enter a maximum", min = false, max = true)
        min > max -> RangeProblem("The minimum is bigger than the maximum", min = true, max = true)
        else -> null
    }
}

/** A whole-number field with its own ± key, because some number keyboards have no minus. */
@Composable
private fun NumberField(
    label: String,
    spokenName: String,
    text: String,
    isError: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    fun accept(s: String) { if (s.length <= 12 && s.matches(Regex("-?\\d*"))) onChange(s) }
    OutlinedTextField(
        value = text,
        onValueChange = ::accept,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            IconButton(
                onClick = { accept(if (text.startsWith("-")) text.drop(1) else "-$text") },
                modifier = Modifier.semantics { contentDescription = "Change the sign of the $spokenName" },
            ) { Text("±", style = MaterialTheme.typography.titleLarge) }
        },
        modifier = modifier,
    )
}
