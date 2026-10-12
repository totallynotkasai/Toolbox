package com.toolbox.app.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toolbox.app.Category
import com.toolbox.app.Favourites
import com.toolbox.app.Tool
import com.toolbox.app.ui.common.rememberAnnouncer
import com.toolbox.app.ui.common.rememberPref
import com.toolbox.app.ui.theme.GoogleColors
import com.toolbox.app.ui.theme.ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What TalkBack says once a search settles, from the number of tools and games that match. */
internal fun searchSummary(found: Int): String = when (found) {
    0 -> "Nothing matches"
    1 -> "1 match"
    else -> "$found matches"
}

/** Filter chip value for the Favourites group; the others are [Category] names, and null is All. */
private const val FAVOURITES = "favourites"

private val GRID_PADDING = 16.dp
private val GRID_SPACING = 12.dp
private val MIN_TILE_WIDTH = 156.dp
private val TILE_PADDING = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Tool) -> Unit, startWithSettings: Boolean = false) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(startWithSettings) }
    var storedFavourites by rememberPref(Favourites.PREF_KEY, "")
    val favourites = Favourites.parse(storedFavourites)
    val toggleFavourite = { tool: Tool -> storedFavourites = Favourites.toggle(storedFavourites, tool.route) }

    val matching = Tool.sorted.filter { it.matches(query) }
    val favouriteTools = if (filter == null || filter == FAVOURITES) matching.filter { it.route in favourites } else emptyList()
    val categories = Category.entries.filter { filter == null || filter == it.name }
        .map { category -> category to matching.filter { it.category == category } }
        .filter { it.second.isNotEmpty() }

    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    fun showEverything() {
        query = ""
        filter = null
        focus.clearFocus()
        scroll.state.heightOffset = 0f
        scope.launch { grid.scrollToItem(0) }
    }
    // Back undoes a search or filter before it leaves the app. With the keyboard up, Android closes that first.
    BackHandler(enabled = query.isNotEmpty() || filter != null) { showEverything() }

    // TalkBack hears how many tools match once typing pauses, since the grid changing says nothing by itself. A
    // search still typed when coming back to the screen isn't counted again.
    val say = rememberAnnouncer()
    val found = if (filter == FAVOURITES) favouriteTools.size else categories.sumOf { it.second.size }
    var counted by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isBlank()) counted = ""
        if (query.isBlank() || query == counted) return@LaunchedEffect
        delay(800)
        counted = query
        say(searchSummary(found))
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Toolbox", modifier = Modifier.semantics { heading() }) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        BoxWithConstraints {
            // As many columns of at least MIN_TILE_WIDTH as fit, like GridCells.Adaptive, worked out here so the
            // tile titles can be measured at the width they'll get. At large font sizes that can be fewer, so that
            // every title fits in two lines.
            val gridWidth = maxWidth - GRID_PADDING * 2
            val mostColumns = ((gridWidth + GRID_SPACING) / (MIN_TILE_WIDTH + GRID_SPACING)).toInt().coerceAtLeast(1)
            val titleStyle = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading)
            val (columns, titleLines) = rememberGridColumns(gridWidth, mostColumns, titleStyle)
            val tileTitle = TileTitle(titleStyle, titleLines)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                contentPadding = PaddingValues(
                    start = GRID_PADDING, end = GRID_PADDING,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                    SearchField(query, onChange = { query = it }, onSearch = { focus.clearFocus() })
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "chips") {
                    CategoryChips(filter, onSelect = { filter = it })
                }
                // A favourite shows here and in its own category, so its keys need a prefix to stay unique.
                if (favouriteTools.isNotEmpty()) {
                    group("Favourites", "favourites", favouriteTools, favourites, tileTitle, onOpen, toggleFavourite)
                }
                categories.forEach { (category, tools) ->
                    group(category.title, category.name, tools, favourites, tileTitle, onOpen, toggleFavourite)
                }
                when {
                    filter == FAVOURITES && favouriteTools.isEmpty() && query.isBlank() ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "no-favourites") { NoFavourites() }
                    favouriteTools.isEmpty() && categories.isEmpty() ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                            NoMatches(onReset = ::showEverything)
                        }
                }
            }
        }
    }

    if (showSettings) SettingsSheet(onDismiss = { showSettings = false })
}

private fun LazyGridScope.group(
    title: String,
    key: String,
    tools: List<Tool>,
    favourites: Set<String>,
    tileTitle: TileTitle,
    onOpen: (Tool) -> Unit,
    onToggleFavourite: (Tool) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "heading-$key") {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
        )
    }
    items(tools, key = { "$key-${it.route}" }) { tool ->
        ToolCard(
            tool, favourite = tool.route in favourites, title = tileTitle,
            onClick = { onOpen(tool) }, onToggleFavourite = { onToggleFavourite(tool) },
        )
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

/** How tile titles are set: [lines] is 1, or 2 once any title needs a second line at this width and font size. */
private data class TileTitle(val style: TextStyle, val lines: Int)

/**
 * The grid's columns, from [mostColumns] down, and the lines the longest tool title takes in a tile (1 or 2): the
 * most columns in which every title fits in two lines, or one column if none do. Every tool is measured, not just
 * those shown, so the tiles don't change size while searching.
 */
@Composable
private fun rememberGridColumns(gridWidth: Dp, mostColumns: Int, style: TextStyle): Pair<Int, Int> {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    return remember(gridWidth, mostColumns, style, density) {
        fun titleLines(columns: Int): Int? {
            val tileWidth = (gridWidth - GRID_SPACING * (columns - 1)) / columns
            // A pixel to spare, in case the grid rounds a column narrower than this.
            val px = (with(density) { (tileWidth - TILE_PADDING * 2).roundToPx() } - 1).coerceAtLeast(1)
            val titles = Tool.entries.map { measurer.measure(it.title, style, maxLines = 2, constraints = Constraints(maxWidth = px)) }
            return if (titles.any { it.hasVisualOverflow }) null else titles.maxOf { it.lineCount }
        }
        (mostColumns downTo 2).firstNotNullOfOrNull { c -> titleLines(c)?.let { c to it } } ?: (1 to (titleLines(1) ?: 2))
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search tools and games") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The results already show as you type, so the search key just puts the keyboard away to show them all.
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun ToolCard(tool: Tool, favourite: Boolean, title: TileTitle, onClick: () -> Unit, onToggleFavourite: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Card(
        // TalkBack reads the tile as its title, a button that opens.
        modifier = Modifier.clip(shape).clickable(onClickLabel = "Open", role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
    ) {
        Column(Modifier.fillMaxWidth().padding(TILE_PADDING)) {
            Row(verticalAlignment = Alignment.Top) {
                // The tint keeps the bright accent; the glyph on it is the darker light-mode tone, so it shows up.
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(tool.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tool.icon, contentDescription = null, tint = tool.accent.ink(), modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.weight(1f))
                // Pulled into the card's padding so the 48dp touch target doesn't push the title down.
                IconButton(onClick = onToggleFavourite, modifier = Modifier.offset(x = 12.dp, y = (-12).dp)) {
                    Icon(
                        if (favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (favourite) "Remove ${tool.title} from favourites" else "Add ${tool.title} to favourites",
                        tint = if (favourite) GoogleColors.Yellow.ink() else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            // At large font sizes a title wraps, then ends in an ellipsis. Every tile keeps room for as many lines
            // as the longest title needs, so the tiles in a row stay the same height.
            Text(tool.title, style = title.style, minLines = title.lines, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
