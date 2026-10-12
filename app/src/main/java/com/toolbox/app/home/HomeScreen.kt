package com.toolbox.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toolbox.app.Category
import com.toolbox.app.Favourites
import com.toolbox.app.Tool
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.theme.GoogleColors

/** Filter chip value for the Favourites group; the others are [Category] names, and null is All. */
private const val FAVOURITES = "favourites"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Tool) -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var storedFavourites by rememberPref(Favourites.PREF_KEY, "")
    val favourites = Favourites.parse(storedFavourites)
    val toggleFavourite = { tool: Tool -> storedFavourites = Favourites.toggle(storedFavourites, tool.route) }

    val matching = Tool.sorted.filter { it.matches(query) }
    val favouriteTools = if (filter == null || filter == FAVOURITES) matching.filter { it.route in favourites } else emptyList()
    val categories = Category.entries.filter { filter == null || filter == it.name }
        .map { category -> category to matching.filter { it.category == category } }
        .filter { it.second.isNotEmpty() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Toolbox") },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                SearchField(query, onChange = { query = it })
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "chips") {
                CategoryChips(filter, onSelect = { filter = it })
            }
            // A favourite shows here and in its own category, so its keys need a prefix to stay unique.
            if (favouriteTools.isNotEmpty()) {
                group("Favourites", "favourites", favouriteTools, favourites, onOpen, toggleFavourite)
            }
            categories.forEach { (category, tools) ->
                group(category.title, category.name, tools, favourites, onOpen, toggleFavourite)
            }
            when {
                filter == FAVOURITES && favouriteTools.isEmpty() && query.isBlank() ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "no-favourites") { NoFavourites() }
                favouriteTools.isEmpty() && categories.isEmpty() ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        NoMatches(onReset = { query = ""; filter = null })
                    }
            }
        }
    }
}

private fun LazyGridScope.group(
    title: String,
    key: String,
    tools: List<Tool>,
    favourites: Set<String>,
    onOpen: (Tool) -> Unit,
    onToggleFavourite: (Tool) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "heading-$key") {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
    items(tools, key = { "$key-${it.route}" }) { tool ->
        ToolCard(tool, favourite = tool.route in favourites, onClick = { onOpen(tool) }, onToggleFavourite = { onToggleFavourite(tool) })
    }
}

/** All, Favourites, then one chip per category. Tapping the selected chip again goes back to All. */
@Composable
private fun CategoryChips(selected: String?, onSelect: (String?) -> Unit) {
    // Let the row scroll edge to edge instead of being clipped by the grid's side padding.
    LazyRow(
        modifier = Modifier.bleedHorizontally(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") { CategoryChip("All", selected == null) { onSelect(null) } }
        item(key = FAVOURITES) {
            CategoryChip("Favourites", selected == FAVOURITES, icon = Icons.Filled.Star) {
                onSelect(if (selected == FAVOURITES) null else FAVOURITES)
            }
        }
        items(Category.entries, key = { it.name }) { category ->
            CategoryChip(category.title, selected == category.name) {
                onSelect(if (selected == category.name) null else category.name)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val leading = if (selected) Icons.Filled.Check else icon
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leading?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        },
    )
}

@Composable
private fun NoFavourites() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.StarBorder, contentDescription = null,
            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("No favourites yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap the star on a tool or game to keep it here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoMatches(onReset: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SearchOff, contentDescription = null,
            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("Nothing matches", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onReset) { Text("Show everything") }
    }
}

/** Widens a full-span item by [amount] on each side so it reaches the screen edges past the parent's padding. */
private fun Modifier.bleedHorizontally(amount: Dp) = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search tools and games") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun ToolCard(tool: Tool, favourite: Boolean, onClick: () -> Unit, onToggleFavourite: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(tool.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.weight(1f))
                // Pulled into the card's padding so the 48dp touch target doesn't push the title down.
                IconButton(onClick = onToggleFavourite, modifier = Modifier.offset(x = 12.dp, y = (-12).dp)) {
                    Icon(
                        if (favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (favourite) "Remove ${tool.title} from favourites" else "Add ${tool.title} to favourites",
                        tint = if (favourite) GoogleColors.Yellow else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(tool.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}
