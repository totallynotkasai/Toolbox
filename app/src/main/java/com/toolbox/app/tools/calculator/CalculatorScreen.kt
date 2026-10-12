package com.toolbox.app.tools.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.app.tools.calculator.CalculatorEngine.DIVIDE
import com.toolbox.app.tools.calculator.CalculatorEngine.MINUS
import com.toolbox.app.tools.calculator.CalculatorEngine.PLUS
import com.toolbox.app.tools.calculator.CalculatorEngine.TIMES
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
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

@Composable
fun CalculatorScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
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
            CalculatorEngine.evaluate(expr, answer)?.let(::showAnswer) ?: run { error = true }
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

    ToolScaffold(title = "Calculator", onBack = onBack) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val landscape = maxWidth > maxHeight
            val display = @Composable { modifier: Modifier ->
                Display(
                    expr = expr,
                    preview = if (error) "Can't work that out" else if (showingResult) null else CalculatorEngine.preview(expr, answer)?.let(CalculatorEngine::format),
                    isError = error,
                    isAnswer = showingResult,
                    onBackspace = ::backspace,
                    modifier = modifier,
                )
            }
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    display(Modifier.weight(1f).fillMaxHeight())
                    Keypad(::press, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(16.dp)) {
                    display(Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(16.dp))
                    Keypad(::press, Modifier.fillMaxWidth().weight(1.6f))
                }
            }
        }
    }
}

@Composable
private fun Display(expr: String, preview: String?, isError: Boolean, isAnswer: Boolean, onBackspace: () -> Unit, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
            Spacer(Modifier.weight(1f))
            // Shrinks as the sum gets longer, so most sums fit on one line.
            val size = when {
                expr.length <= 9 -> 52.sp
                expr.length <= 14 -> 40.sp
                expr.length <= 20 -> 30.sp
                else -> 24.sp
            }
            Text(
                expr.ifEmpty { "0" },
                fontSize = size,
                lineHeight = size * 1.15f,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                color = if (expr.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                // The answer after "=" is read out; the sum being typed isn't, as each key already says what it is.
                modifier = Modifier.fillMaxWidth().semantics { if (isAnswer) liveRegion = LiveRegionMode.Polite },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview?.let { if (isError) it else "= $it" } ?: " ",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
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
            Text(key.label, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }
}
