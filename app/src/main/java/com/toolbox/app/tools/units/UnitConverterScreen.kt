package com.toolbox.app.tools.units

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref

private const val ALLOWED_INPUT = "0123456789.,-eE "

@Composable
fun UnitConverterScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    var categoryName by rememberPref("units_category", UnitCategory.Length.name)
    val category = UnitCategory.entries.firstOrNull { it.name == categoryName } ?: UnitCategory.Length

    // The last pair of units is remembered per category.
    var fromId by rememberPref("units_${category.name}_from", category.defaultFrom)
    var toId by rememberPref("units_${category.name}_to", category.defaultTo)
    val from = category.unit(fromId) ?: category.unit(category.defaultFrom)!!
    val to = category.unit(toId) ?: category.unit(category.defaultTo)!!

    // Only the field the user last typed in holds real text; the other one is always derived from it,
    // so changing a unit re-converts instead of leaving a stale number behind.
    var anchor by rememberSaveable { mutableIntStateOf(0) } // 0 = top ("from"), 1 = bottom ("to")
    var anchorText by rememberSaveable { mutableStateOf("1") }
    val anchorValue = parseNumber(anchorText)
    val derivedText = anchorValue?.let {
        formatNumber(if (anchor == 0) convert(it, from, to) else convert(it, to, from))
    } ?: ""
    val topText = if (anchor == 0) anchorText else derivedText
    val bottomText = if (anchor == 1) anchorText else derivedText
    val fromValue = if (anchor == 0) anchorValue else anchorValue?.let { convert(it, to, from) }

    fun edit(field: Int, text: String) {
        anchor = field
        anchorText = text.filter { it in ALLOWED_INPUT }.take(24)
    }

    fun selectCategory(c: UnitCategory) {
        if (c == category) return
        categoryName = c.name
        anchor = 0
        anchorText = "1"
    }

    fun swap() {
        haptics.click()
        // Keep the number on top and swap the units under it: "10 cm = 3.94 in" becomes "10 in = 25.4 cm".
        val keep = topText
        fromId = to.id
        toId = from.id
        anchor = 0
        anchorText = keep
    }

    ToolScaffold(title = "Unit Converter", onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    items(UnitCategory.entries, key = { it.name }) { c ->
                        FilterChip(
                            selected = c == category,
                            onClick = { selectCategory(c) },
                            label = { Text(c.title) },
                            leadingIcon = if (c == category) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                    }
                }
            }

            item("converter") {
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ConversionSide(
                        label = "From",
                        units = category.units,
                        unit = from,
                        text = topText,
                        isError = anchor == 0 && anchorText.isNotBlank() && anchorValue == null,
                        allowNegative = category == UnitCategory.Temperature,
                        onUnit = { fromId = it.id },
                        onText = { edit(0, it) },
                    )
                    FilledTonalIconButton(onClick = ::swap, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Swap units")
                    }
                    ConversionSide(
                        label = "To",
                        units = category.units,
                        unit = to,
                        text = bottomText,
                        isError = anchor == 1 && anchorText.isNotBlank() && anchorValue == null,
                        allowNegative = category == UnitCategory.Temperature,
                        onUnit = { toId = it.id },
                        onText = { edit(1, it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "1 ${from.symbol} = ${formatNumber(convert(1.0, from, to), 8)} ${to.symbol}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (category == UnitCategory.Temperature && fromValue != null && from.toBase(fromValue) < ABSOLUTE_ZERO_C) {
                        Text(
                            "That's below absolute zero",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (category == UnitCategory.Volume) {
                        Text(
                            "Spoons are metric (5 mL / 15 mL); the UK cup is the 250 mL metric cup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (fromValue != null) {
                item("all") {
                    AllUnits(
                        value = fromValue,
                        from = from,
                        selected = to,
                        units = category.units,
                        onPick = { haptics.tick(); toId = it.id; anchor = 0; anchorText = topText },
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversionSide(
    label: String,
    units: List<UnitDef>,
    unit: UnitDef,
    text: String,
    isError: Boolean,
    allowNegative: Boolean,
    onUnit: (UnitDef) -> Unit,
    onText: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = "${unit.name} (${unit.symbol})",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u.name) },
                            trailingIcon = { Text(u.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = if (u === unit) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null,
                            onClick = { onUnit(u); expanded = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                singleLine = true,
                isError = isError,
                textStyle = MaterialTheme.typography.headlineSmall,
                suffix = { Text(unit.symbol, style = MaterialTheme.typography.titleMedium) },
                supportingText = if (isError) {
                    { Text("Enter a number") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (allowNegative) {
                            TextButton(onClick = {
                                onText(if (text.startsWith("-")) text.drop(1) else "-$text")
                            }) { Text("±", style = MaterialTheme.typography.titleLarge) }
                        }
                        IconButton(
                            enabled = text.isNotBlank() && !isError,
                            onClick = {
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, "Copied $text ${unit.symbol}", Toast.LENGTH_SHORT).show()
                            },
                        ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy value") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The current value in every other unit of the category. Tapping a row makes it the "To" unit. */
@Composable
private fun AllUnits(
    value: Double,
    from: UnitDef,
    selected: UnitDef,
    units: List<UnitDef>,
    onPick: (UnitDef) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            "${formatNumber(value)} ${from.symbol} is",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                val others = units.filter { it !== from }
                others.forEachIndexed { i, u ->
                    val isSelected = u === selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Convert to ${u.name}") { onPick(u) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            u.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(Modifier.padding(start = 12.dp)) {
                            Text(
                                "${formatNumber(convert(value, from, u))} ${u.symbol}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (i < others.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
