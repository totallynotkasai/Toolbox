package com.toolbox.app.tools.dates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toolbox.app.ui.common.ToolScaffold
import com.toolbox.app.ui.common.rememberHaptics
import com.toolbox.app.ui.common.rememberPref
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

private const val MILLIS_PER_DAY = 86_400_000L
private val TABS = listOf("Between", "Add / subtract", "Age")

private val fullDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
private fun LocalDate.full(): String = format(fullDate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateCalculatorScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today = remember { LocalDate.now() }

    ToolScaffold(title = "Date Calculator", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) {
                        0 -> BetweenTab(today)
                        1 -> AddTab(today)
                        else -> AgeTab(today)
                    }
                }
            }
        }
    }
}

@Composable
private fun BetweenTab(today: LocalDate) {
    val haptics = rememberHaptics()
    var start by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var end by rememberSaveable { mutableLongStateOf(today.plusMonths(1).toEpochDay()) }
    var includeEnd by rememberSaveable { mutableStateOf(false) }
    val diff = dateDiff(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end), includeEnd)

    DateField("Start date", start, today, onPick = { start = it })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { haptics.click(); val s = start; start = end; end = s }) {
            Icon(Icons.Filled.SwapVert, contentDescription = "Swap start and end dates")
        }
    }
    DateField("End date", end, today, onPick = { end = it })
    SwitchRow("Include the end date", includeEnd) { includeEnd = it }

    ResultCard {
        Text(
            (if (diff.negative) "−" else "") + plural(diff.days, "day"),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (diff.negative) {
            Text(
                "The end date is before the start date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        ResultRow("In weeks", describeWeeks(diff.weeks, diff.weekDays))
        ResultRow("In months", describeYmd(diff.years, diff.months, diff.monthDays))
        ResultRow("Working days", "${plural(diff.workingDays, "day")} (Mon–Fri)")
    }
    Hint("Working days skip weekends but not bank holidays. " +
        if (includeEnd) "Both dates are counted." else "The start date counts and the end date doesn't.")
}

private fun describeWeeks(weeks: Long, days: Long): String = when {
    weeks == 0L -> plural(days, "day")
    days == 0L -> plural(weeks, "week")
    else -> "${plural(weeks, "week")}, ${plural(days, "day")}"
}

@Composable
private fun AddTab(today: LocalDate) {
    val haptics = rememberHaptics()
    var start by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var subtract by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("30") }
    var step by rememberSaveable { mutableStateOf(DateStep.Days) }
    val amount = amountText.toLongOrNull()
    val shift = amount?.let { shiftDate(LocalDate.ofEpochDay(start), if (subtract) -it else it, step) }

    DateField("Start date", start, today, onPick = { start = it })

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false, true).forEachIndexed { i, sub ->
            SegmentedButton(
                selected = subtract == sub,
                onClick = { haptics.tick(); subtract = sub },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                icon = { Icon(if (sub) Icons.Filled.Remove else Icons.Filled.Add, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
            ) { Text(if (sub) "Subtract" else "Add") }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { amountText = ((amount ?: 0) - 1).coerceAtLeast(0).toString() }, enabled = (amount ?: 0) > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "Less")
        }
        OutlinedTextField(
            value = amountText,
            onValueChange = { t -> amountText = t.filter(Char::isDigit).take(6) },
            singleLine = true,
            label = { Text("Amount") },
            isError = amount == null,
            supportingText = if (amount == null) {
                { Text("Enter a whole number") }
            } else null,
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { amountText = ((amount ?: 0) + 1).coerceAtMost(999_999).toString() }) {
            Icon(Icons.Filled.Add, contentDescription = "More")
        }
    }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        DateStep.entries.forEachIndexed { i, s ->
            SegmentedButton(
                selected = step == s,
                onClick = { haptics.tick(); step = s },
                shape = SegmentedButtonDefaults.itemShape(i, DateStep.entries.size),
                icon = {},
            ) { Text(s.label, maxLines = 1) }
        }
    }

    ResultCard {
        if (shift == null) {
            Text("—", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                shift.result.full(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                relativeToToday(shift.result, today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (shift.clamped) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "That month is shorter, so it lands on its last day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun relativeToToday(date: LocalDate, today: LocalDate): String {
    val d = ChronoUnit.DAYS.between(today, date)
    return when {
        d == 0L -> "Today"
        d == 1L -> "Tomorrow"
        d == -1L -> "Yesterday"
        d > 0 -> "In ${plural(d, "day")} from today"
        else -> "${plural(-d, "day")} ago"
    }
}

@Composable
private fun AgeTab(today: LocalDate) {
    // The birth date is remembered, so opening the tab again shows your own age straight away.
    var birth by rememberPref("dates_birth_epoch_day", today.minusYears(30).toEpochDay())
    var asOf by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val birthDate = LocalDate.ofEpochDay(birth)
    val age = ageOn(birthDate, LocalDate.ofEpochDay(asOf))

    DateField("Date of birth", birth, today, onPick = { birth = it })
    DateField("Age on", asOf, today, onPick = { asOf = it })

    ResultCard {
        if (age == null) {
            Text(
                "The birth date is after the \"age on\" date",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@ResultCard
        }
        Text(
            plural(age.years.toLong(), "year"),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (age.months != 0 || age.days != 0) {
            Text(
                "and " + describeYmd(0, age.months, age.days),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (age.isBirthday) {
            Text(
                "Happy birthday!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        ResultRow("Born on a", birthDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
        ResultRow("Days old", "%,d".format(age.totalDays))
        ResultRow(
            "Next birthday",
            if (age.isBirthday) "Today, ${age.turning}"
            else "${age.turning} in ${plural(age.daysToBirthday, "day")}",
        )
        ResultRow("That's on", age.nextBirthday.full())
    }
    if (birthDate.monthValue == 2 && birthDate.dayOfMonth == 29) {
        Hint("In years without 29 February, the birthday is counted on 1 March.")
    }
}

// ---- Shared pieces ----

/** A tappable date "field" that opens the Material date picker; the trailing button jumps to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, epochDay: Long, today: LocalDate, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(epochDay)
    Surface(
        onClick = { open = true },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "$label, ${date.full()}. Tap to change."
        },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date.full(), style = MaterialTheme.typography.titleMedium)
            }
            if (date != today) {
                IconButton(onClick = { onPick(today.toEpochDay()) }) {
                    Icon(Icons.Filled.Today, contentDescription = "Set $label to today")
                }
            }
        }
    }

    if (open) {
        // The picker works in UTC midnight milliseconds, which map 1:1 onto epoch days.
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!checked) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
            stateDescription = if (checked) "On" else "Off"
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}
