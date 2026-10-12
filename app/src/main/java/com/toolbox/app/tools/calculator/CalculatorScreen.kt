package com.toolbox.app.tools.calculator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.toolbox.app.tools.calculator.CalculatorEngine.DIVIDE
import com.toolbox.app.tools.calculator.CalculatorEngine.MINUS
import com.toolbox.app.tools.calculator.CalculatorEngine.PLUS
import com.toolbox.app.tools.calculator.CalculatorEngine.TIMES
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import kotlinx.coroutines.launch
import java.math.BigDecimal

private enum class KeyKind { Digit, Operator, Function, Equals }

private data class CalcKey(val label: String, val kind: KeyKind, val spoken: String = label)

private val KEYS = listOf(
    listOf(CalcKey("C", KeyKind.Function, "Clear"), CalcKey("( )", KeyKind.Function, "Brackets"),
        CalcKey("%", KeyKind.Function, "Percent"), CalcKey("$DIVIDE", KeyKind.Operator, "Divide")),
    listOf(CalcKey("7", KeyKind.Digit), CalcKey("8", KeyKind.Digit), CalcKey("9", KeyKind.Digit), CalcKey("$TIMES", KeyKind.Operator, "Multiply")),
    listOf(CalcKey("4", KeyKind.Digit), CalcKey("5", KeyKind.Digit), CalcKey("6", KeyKind.Digit), CalcKey("$MINUS", KeyKind.Operator, "Minus")),
    listOf(CalcKey("1", KeyKind.Digit), CalcKey("2", KeyKind.Digit), CalcKey("3", KeyKind.Digit), CalcKey("$PLUS", KeyKind.Operator, "Plus")),
    listOf(CalcKey("±", KeyKind.Digit, "Change sign"), CalcKey("0", KeyKind.Digit), CalcKey(".", KeyKind.Digit, "Point"),
        CalcKey("=", KeyKind.Equals, "Equals")),
)

private enum class MemoryKey(val label: String, val spoken: String) {
    Clear("MC", "Memory clear"),
    Recall("MR", "Memory recall"),
    Add("M+", "Memory add"),
    Subtract("M$MINUS", "Memory subtract"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val say = rememberAnnouncer()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Memory as an exact decimal ("" for none), and the finished sums, newest first. Both are kept between visits.
    var memoryText by rememberPref("calculator_memory", "")
    val memory = memoryText.toBigDecimalOrNull() ?: BigDecimal.ZERO
    var historyText by rememberPref("calculator_history", "")
    val history = remember(historyText) { CalculatorHistory.decode(historyText) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var expr by rememberSaveable { mutableStateOf("") }
    // After "=", a digit starts a new sum but an operator carries on from the answer.
    var showingResult by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    // The last answer in full ("" for none). The display shows it to 12 digits, and carrying on from it uses every digit.
    var answerText by rememberSaveable { mutableStateOf("") }
    val answer = answerText.takeIf { it.isNotEmpty() }?.let(::BigDecimal)

    fun showAnswer(value: BigDecimal) {
        answerText = value.toString()
        expr = CalculatorEngine.format(value)
        showingResult = true
    }

    fun press(key: CalcKey) {
        haptics.tick()
        val fresh = showingResult && (key.kind == KeyKind.Digit && key.label != "±" || key.label == "( )")
        if (fresh || key.label == "C") answerText = ""
        val base = if (fresh) "" else expr
        error = false
        if (key.label == "=") {
            if (CalculatorEngine.isUnfinished(expr)) return // "5+" waits for the next number
            val value = CalculatorEngine.evaluate(expr, answer) ?: run { error = true; return }
            // "=" on a lone number, such as the answer already showing, isn't a sum worth keeping.
            if (CalculatorEngine.isSum(expr)) {
                val base = answer?.takeIf { expr.startsWith(CalculatorEngine.format(it)) }
                historyText = CalculatorHistory.encode(CalculatorHistory.add(history, HistoryEntry(expr, value, base)))
            }
            showAnswer(value)
            return
        }
        if (key.label == "±" && showingResult && answer != null) return showAnswer(answer.negate())
        showingResult = false
        expr = when (key.label) {
            "C" -> ""
            "( )" -> CalculatorEngine.bracket(base)
            "%" -> CalculatorEngine.percent(base)
            "±" -> CalculatorEngine.negate(base)
            "." -> CalculatorEngine.point(base)
            else -> if (key.kind == KeyKind.Operator) CalculatorEngine.operator(base, key.label[0])
            else CalculatorEngine.digit(base, key.label[0])
        }
    }

    fun backspace() {
        haptics.tick()
        error = false
        showingResult = false
        expr = CalculatorEngine.backspace(expr)
        // Once the answer's own digits are edited, the edited digits are what count.
        if (answer != null && !expr.startsWith(CalculatorEngine.format(answer))) answerText = ""
    }

    fun pressMemory(key: MemoryKey) {
        haptics.tick()
        val operand = { CalculatorEngine.memoryOperand(expr, answer, showingResult).takeUnless { error } }
        val updated = when (key) {
            MemoryKey.Clear -> CalculatorEngine.memoryClear()
            MemoryKey.Add -> CalculatorEngine.memoryAdd(memory, operand())
            MemoryKey.Subtract -> CalculatorEngine.memorySubtract(memory, operand())
            MemoryKey.Recall -> {
                val fresh = showingResult || error
                val recalled = CalculatorEngine.memoryRecall(if (fresh) "" else expr, memory)
                // On its own, the recalled value carries on with every digit, like an answer.
                if (recalled == CalculatorEngine.memoryRecall("", memory)) answerText = memory.toString()
                else if (fresh || answer != null && !recalled.startsWith(CalculatorEngine.format(answer))) answerText = ""
                expr = recalled
                showingResult = false
                error = false
                return
            }
        }
        memoryText = if (updated.signum() == 0) "" else updated.toString()
        say(if (updated.signum() == 0) "Memory clear" else "Memory ${CalculatorEngine.spoken(CalculatorEngine.format(updated))}")
    }

    fun clearHistory() {
        val before = historyText
        historyText = ""
        showHistory = false
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val choice = snackbar.showSnackbar("History cleared", actionLabel = "Undo", duration = SnackbarDuration.Long)
            // Sums finished since the clear stay on top.
            if (choice == SnackbarResult.ActionPerformed) {
                historyText = CalculatorHistory.encode(
                    (CalculatorHistory.decode(historyText) + CalculatorHistory.decode(before)).take(CalculatorHistory.CAP),
                )
            }
        }
    }

    ToolScaffold(
        title = "Calculator",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showHistory = true }) { Icon(Icons.Filled.History, contentDescription = "History") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val landscape = maxWidth > maxHeight
            val display = @Composable { modifier: Modifier ->
                Display(
                    expr = expr,
                    preview = if (error) "Can't work that out" else if (showingResult) null else CalculatorEngine.preview(expr, answer)?.let(CalculatorEngine::format),
                    isError = error,
                    isAnswer = showingResult,
                    memory = memory.takeIf { it.signum() != 0 },
                    onBackspace = ::backspace,
                    modifier = modifier,
                )
            }
            val memoryRow = @Composable { modifier: Modifier -> MemoryRow(memory.signum() != 0, ::pressMemory, modifier) }
            if (landscape) {
                // The memory row sits under the display here, as above the keypad it would squeeze the keys below 46 dp.
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        display(Modifier.fillMaxWidth().weight(1f))
                        memoryRow(Modifier.fillMaxWidth())
                    }
                    Keypad(::press, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(16.dp)) {
                    display(Modifier.fillMaxWidth().weight(1f))
                    memoryRow(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Keypad(::press, Modifier.fillMaxWidth().weight(1.6f))
                }
            }
        }
    }

    if (showHistory) {
        HistorySheet(
            entries = history,
            onUse = { entry ->
                haptics.tick()
                error = false
                showAnswer(entry.answer)
                showHistory = false
            },
            onEdit = { entry ->
                haptics.tick()
                error = false
                showingResult = false
                expr = entry.sum
                answerText = entry.base?.toString().orEmpty()
                showHistory = false
            },
            onClear = ::clearHistory,
            onDismiss = { showHistory = false },
        )
    }
}

@Composable
private fun MemoryRow(hasMemory: Boolean, onKey: (MemoryKey) -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MemoryKey.entries.forEach { key ->
            // MC and MR have nothing to work on while memory is empty.
            val enabled = hasMemory || key == MemoryKey.Add || key == MemoryKey.Subtract
            TextButton(
                onClick = { onKey(key) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = key.spoken },
            ) {
                Text(key.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.titleMedium.fontSize))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    entries: List<HistoryEntry>,
    onUse: (HistoryEntry) -> Unit,
    onEdit: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("Clear") }
        }
        if (entries.isEmpty()) {
            Text(
                "Sums you finish with = show here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            )
            return@ModalBottomSheet
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(entries) { entry ->
                val shown = CalculatorEngine.format(entry.answer)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Use answer") { onUse(entry) }
                        .semantics { contentDescription = "${CalculatorEngine.spoken(entry.sum)}, equals ${CalculatorEngine.spoken(shown)}" }
                        .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(entry.sum, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("= $shown", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.End,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onEdit(entry) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit sum") }
                }
            }
        }
    }
}

@Composable
private fun Display(
    expr: String,
    preview: String?,
    isError: Boolean,
    isAnswer: Boolean,
    memory: BigDecimal?,
    onBackspace: () -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
            if (memory != null) {
                Text(
                    "M",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start).semantics {
                        contentDescription = "Memory holds ${CalculatorEngine.spoken(CalculatorEngine.format(memory))}"
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            // Shrinks as the sum gets longer, so most sums fit on one line, and further still at large font sizes.
            val size = when {
                expr.length <= 9 -> 52.sp
                expr.length <= 14 -> 40.sp
                expr.length <= 20 -> 30.sp
                else -> 24.sp
            }
            // Short sums stay on one line rather than breaking a number in two; long ones wrap as they always have.
            val lines = if (expr.length <= 14) 1 else 3
            Text(
                expr.ifEmpty { "0" },
                fontSize = size,
                lineHeight = 1.15.em,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                color = if (expr.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = lines,
                softWrap = lines > 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = size),
                // The answer after "=" is read out; the sum being typed isn't, as each key already says what it is.
                modifier = Modifier.fillMaxWidth().semantics { if (isAnswer) liveRegion = LiveRegionMode.Polite },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val previewStyle = MaterialTheme.typography.headlineSmall
                Text(
                    preview?.let { if (isError) it else "= $it" } ?: " ",
                    style = previewStyle,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = previewStyle.fontSize),
                    // Only the error is read out: the running answer would otherwise be said after every key.
                    modifier = Modifier.weight(1f).semantics { if (isError) liveRegion = LiveRegionMode.Polite },
                )
                IconButton(onClick = onBackspace, enabled = expr.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun Keypad(onKey: (CalcKey) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KEYS.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key -> Key(key, onKey) }
            }
        }
    }
}

@Composable
private fun RowScope.Key(key: CalcKey, onKey: (CalcKey) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (bg: Color, fg: Color) = when (key.kind) {
        KeyKind.Digit -> scheme.surfaceContainerHighest to scheme.onSurface
        KeyKind.Operator -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KeyKind.Function -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        KeyKind.Equals -> scheme.primary to scheme.onPrimary
    }
    Surface(
        onClick = { onKey(key) },
        shape = MaterialTheme.shapes.extraLarge,
        color = bg,
        modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = key.spoken },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                key.label,
                color = fg,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 26.sp),
            )
        }
    }
}
