package com.toolbox.app.tools.units

import android.content.ClipData
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberPref
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ALLOWED_INPUT = "0123456789.,-eE "

@Composable
fun UnitConverterScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val decimal = decimalMark(LocalLocale.current.platformLocale)
    var categoryName by rememberPref("units_category", UnitCategory.Length.name)
    val category = UnitCategory.entries.firstOrNull { it.name == categoryName } ?: UnitCategory.Length
    val cooking = category == UnitCategory.Cooking
    var ingredientId by rememberPref("units_cooking_ingredient", DEFAULT_INGREDIENT)
    val ingredient = ingredient(ingredientId)

    // The last pair of units is remembered per category.
    var fromId by rememberPref("units_${category.name}_from", category.defaultFrom)
    var toId by rememberPref("units_${category.name}_to", category.defaultTo)
    val from = category.unit(fromId) ?: category.unit(category.defaultFrom)!!
    // Picking a unit keeps a Cooking pair convertible, so this only guards against a stray saved pair.
    val to = (category.unit(toId) ?: category.unit(category.defaultTo)!!)
        .let { if (cooking && !pairs(from, it)) partnerFor(from) else it }
    val showIngredient = cooking && needsIngredient(from, to)

    fun conv(value: Double, a: UnitDef, b: UnitDef) =
        if (cooking) convertCooking(value, a, b, ingredient) else convert(value, a, b)

    /** [value] in [a], as text in [b]. Gas marks snap to the chart and say when they're approximate. */
    fun show(value: Double, a: UnitDef, b: UnitDef, significant: Int = 10) =
        if (cooking && b.id == GAS_MARK_ID) gasMarkText(value, a, decimal) else formatNumber(conv(value, a, b), significant, decimal)

    // Only the field the user last typed in holds real text; the other one is always derived from it,
    // so changing a unit re-converts instead of leaving a stale number behind.
    var anchor by rememberSaveable { mutableIntStateOf(0) } // 0 = top ("from"), 1 = bottom ("to")
    // Null until something is typed, picked or swapped, which starts from 1, or gas mark 4 for an oven temperature.
    var typed by rememberSaveable { mutableStateOf<String?>(null) }
    val anchorUnit = if (anchor == 0) from else to
    val anchorText = typed ?: if (cooking) formatNumber(startValue(anchorUnit), decimal = decimal) else "1"
    val anchorValue = parseNumber(anchorText, decimal)
    val derivedUnit = if (anchor == 0) to else from
    val derivedText = anchorValue?.let { show(it, anchorUnit, derivedUnit) } ?: ""
    val topText = if (anchor == 0) anchorText else derivedText
    val bottomText = if (anchor == 1) anchorText else derivedText
    val fromValue = (if (anchor == 0) anchorValue else anchorValue?.let { conv(it, to, from) })?.takeIf { it.isFinite() }
    // The top number as plain text, for when it becomes the one typed in ("≈ 4" and "½" can't be read back).
    val topNumber = if (anchor == 0) anchorText else fromValue?.let { formatNumber(it, decimal = decimal) } ?: ""
    val ovenLine = if (cooking && from.group == UnitGroup.Oven) anchorValue?.let { gasChartLine(it, anchorUnit) } else null

    // Typing in one field, or changing a unit, quietly changes the number in the other. TalkBack hears the new
    // number once the changes stop for a second, rather than after every key.
    val say = rememberAnnouncer()
    var changes by remember { mutableIntStateOf(0) }
    val result by rememberUpdatedState(
        derivedText.ifEmpty { null }?.let {
            when {
                it == "—" && ovenLine != null -> ovenLine
                cooking && derivedUnit.id == GAS_MARK_ID ->
                    "equals ${if (it.startsWith("≈")) "about " else ""}gas mark ${it.removePrefix("≈ ")}"
                else -> "equals $it ${derivedUnit.name}"
            }
        },
    )
    LaunchedEffect(changes) {
        if (changes == 0) return@LaunchedEffect
        delay(1000)
        result?.let(say)
    }

    fun edit(field: Int, text: String) {
        anchor = field
        typed = text.filter { it in ALLOWED_INPUT }.take(24)
        changes++
    }

    fun selectCategory(c: UnitCategory) {
        if (c == category) return
        categoryName = c.name
        anchor = 0
        typed = null
    }

    // In Cooking, picking an oven unit on one side while the other is a volume or weight (or the other way round)
    // switches the other side to match, and starts again from a typical number: 1 cup, or gas mark 4.
    fun pick(field: Int, unit: UnitDef) {
        val other = if (field == 0) to else from
        if (field == 0) fromId = unit.id else toId = unit.id
        if (cooking && !pairs(unit, other)) {
            val partner = partnerFor(unit)
            if (field == 0) toId = partner.id else fromId = partner.id
            anchor = 0
            typed = formatNumber(startValue(if (field == 0) unit else partner), decimal = decimal)
        }
        changes++
    }

    fun swap() {
        haptics.click()
        fromId = to.id
        toId = from.id
        if (cooking && from.group == UnitGroup.Oven) {
            // An oven temperature keeps its unit, so "185 °C ≈ gas mark 4" becomes "gas mark ≈ 4 = 185 °C" rather
            // than "gas mark 185", which is off the chart.
            anchor = 1 - anchor
        } else {
            // Keep the number on top and swap the units under it: "10 cm = 3.94 in" becomes "10 in = 25.4 cm".
            val keep = topNumber
            anchor = 0
            typed = keep
        }
        changes++
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
                // Start with the chosen category in view, with a little of the one before it showing.
                val state = rememberLazyListState(initialFirstVisibleItemIndex = (category.ordinal - 1).coerceAtLeast(0))
                LazyRow(
                    state = state,
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
                        onUnit = { pick(0, it) },
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
                        onUnit = { pick(1, it) },
                        onText = { edit(1, it) },
                    )
                }
            }

            if (showIngredient) {
                item("ingredient") {
                    IngredientChips(
                        selected = ingredient,
                        onPick = { haptics.tick(); ingredientId = it.id; changes++ },
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }

            item("notes") {
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            ovenLine != null -> ovenLine
                            showIngredient -> "1 ${from.symbol} of ${ingredient.name.lowercase()} = ${show(1.0, from, to, 8)} ${to.symbol}"
                            else -> "1 ${from.symbol} = ${show(1.0, from, to, 8)} ${to.symbol}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (category == UnitCategory.Temperature && fromValue != null && from.toBase(fromValue) < ABSOLUTE_ZERO_C) {
                        Text(
                            "That's below absolute zero",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val note = when {
                        showIngredient -> "Spoons are metric (5 mL / 15 mL). Cup weights are averages, so weigh when it matters."
                        category == UnitCategory.Volume || (cooking && from.group == UnitGroup.Volume) ->
                            "Spoons are metric (5 mL / 15 mL); the metric cup is 250 mL."
                        cooking && from.group == UnitGroup.Oven -> "Fan ovens are set 20 °C lower."
                        else -> null
                    }
                    if (note != null) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // The list starts from the top number, except for an oven temperature typed under a gas mark: the mark
            // is rounded, so the list starts from what was typed, and tapping a row changes the top unit instead.
            val fromTyped = cooking && from.group == UnitGroup.Oven && anchor == 1
            val listUnit = if (fromTyped) to else from
            val listValue = if (fromTyped) anchorValue else fromValue
            if (listValue != null) {
                item("all") {
                    // In Cooking, only the units it converts to: oven temperatures together, and volumes with
                    // weights only while an ingredient is chosen.
                    val units = when {
                        !cooking -> category.units
                        showIngredient -> category.units.filter { it.group != UnitGroup.Oven }
                        else -> category.units.filter { it.group == listUnit.group }
                    }
                    val amount = if (cooking && listUnit.id == GAS_MARK_ID) {
                        "Gas mark ${formatGasMark(listValue, decimal)}"
                    } else {
                        "${formatNumber(listValue, decimal = decimal)} ${listUnit.symbol}"
                    }
                    AllUnits(
                        heading = "$amount${if (showIngredient) " of ${ingredient.name.lowercase()}" else ""} is",
                        units = units.filter { it !== listUnit },
                        selected = if (fromTyped) from else to,
                        valueText = { u ->
                            if (cooking && u.id == GAS_MARK_ID) {
                                val mark = show(listValue, listUnit, u)
                                when {
                                    mark == "—" -> mark
                                    mark.startsWith("≈ ") -> "about gas mark ${mark.removePrefix("≈ ")}"
                                    else -> "gas mark $mark"
                                }
                            } else {
                                "${show(listValue, listUnit, u)} ${u.symbol}"
                            }
                        },
                        onPick = {
                            haptics.tick()
                            if (fromTyped) {
                                fromId = it.id
                            } else {
                                toId = it.id
                                anchor = 0
                                typed = topNumber
                            }
                            changes++
                        },
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/** The Cooking category's ingredients, shown while a volume meets a weight. */
@Composable
private fun IngredientChips(selected: Ingredient, onPick: (Ingredient) -> Unit, modifier: Modifier) {
    // Start with the chosen one in view, with a little of the one before it showing.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (INGREDIENTS.indexOf(selected) - 1).coerceAtLeast(0))
    Column(modifier) {
        Text(
            "Ingredient",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, bottom = 4.dp).semantics { heading() },
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(INGREDIENTS, key = { it.id }) { i ->
                FilterChip(
                    selected = i === selected,
                    onClick = { onPick(i) },
                    label = { Text(i.name) },
                    leadingIcon = if (i === selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    // "Cups (US)" rather than "Cups (US) (cup (US))", and "Gas mark" rather than "Gas mark (gas mark)".
                    value = if ('(' in unit.symbol || unit.symbol.equals(unit.name, ignoreCase = true)) unit.name
                    else "${unit.name} (${unit.symbol})",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.forEachIndexed { i, u ->
                        // Cooking's units come in groups (Volume, Weight, Oven), each under a heading.
                        if (u.group != null && u.group != units.getOrNull(i - 1)?.group) {
                            Text(
                                u.group.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                            )
                        }
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
                            TextButton(
                                onClick = { onText(if (text.startsWith("-")) text.drop(1) else "-$text") },
                                modifier = Modifier.semantics { contentDescription = "Change sign" },
                            ) { Text("±", style = MaterialTheme.typography.titleLarge) }
                        }
                        IconButton(
                            enabled = text.isNotBlank() && !isError,
                            onClick = {
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(unit.name, text))) }
                                Toast.makeText(context, "Copied $text ${unit.symbol}", Toast.LENGTH_SHORT).show()
                            },
                        ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy value") }
                    }
                },
                // No contentDescription here: on a text field TalkBack can read it in place of the value.
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The current value in each of [units], as [valueText] gives it. Tapping a row makes it the "To" unit. */
@Composable
private fun AllUnits(
    heading: String,
    units: List<UnitDef>,
    selected: UnitDef,
    valueText: (UnitDef) -> String,
    onPick: (UnitDef) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() },
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                units.forEachIndexed { i, u ->
                    val isSelected = u === selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Convert to ${u.name}", role = Role.Button) { onPick(u) }
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
                                valueText(u),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (i < units.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
