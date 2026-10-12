package com.toolbox.app.ui.common

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toolbox.app.Favourites
import com.toolbox.app.ui.theme.GoogleColors

/** Route of the tool on screen, provided by the nav host so [ToolScaffold] can show its favourite star. */
val LocalToolRoute = staticCompositionLocalOf<String?> { null }

/** Standard frame for every tool: centred title, back arrow, favourite star, optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    LocalToolRoute.current?.let { FavouriteButton(it) }
                    actions()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        // The keyboard pushes the body up instead of covering it; ToolBody and the scrolling screens then keep the
        // focused field in view.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        content = content,
    )
}

/** Star that adds [route] to, or removes it from, the Favourites group on the home screen. */
@Composable
fun FavouriteButton(route: String, modifier: Modifier = Modifier) {
    var stored by rememberPref(Favourites.PREF_KEY, "")
    val on = route in Favourites.parse(stored)
    IconButton(onClick = { stored = Favourites.toggle(stored, route) }, modifier = modifier) {
        Icon(
            if (on) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (on) "Remove from favourites" else "Add to favourites",
            tint = if (on) GoogleColors.Yellow else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Centred column that fills the scaffold body; most tools are a single centred card of content.
 *
 * When the content is taller than the screen (large font sizes, a short window, the keyboard up) it scrolls, so the
 * controls at the bottom stay reachable. It only takes scroll gestures while there is something to scroll, and
 * [scroll] = false turns them off for a screen whose own swipe covers the whole body.
 *
 * The content is never wider than three quarters of the window's height. Portrait phones never reach that, but on a
 * large screen in landscape it stops a full-width square board growing taller than the screen.
 */
@Composable
fun ToolBody(padding: PaddingValues, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberScrollState()
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxWidth = if (windowHeight > 0) with(LocalDensity.current) { (windowHeight * 0.75f).toDp() } else Dp.Unspecified
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(state, enabled = scroll && state.maxValue > 0)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = maxWidth), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** Tiny persisted best-score store; SharedPreferences is plenty for a handful of ints. */
class ScoreStore(context: Context) {
    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    fun best(key: String): Int = prefs.getInt(key, 0)
    fun submit(key: String, score: Int, lowerIsBetter: Boolean = false): Int {
        val old = prefs.getInt(key, 0)
        val better = if (lowerIsBetter) old == 0 || score < old else score > old
        if (better) prefs.edit().putInt(key, score).apply()
        return if (better) score else old
    }
}

/** Best score for [key], re-read whenever [submit] is called. */
@Composable
fun rememberBestScore(key: String, lowerIsBetter: Boolean = false): Pair<Int, (Int) -> Unit> {
    val store = ScoreStore(LocalContext.current)
    var best by remember(key) { mutableIntStateOf(store.best(key)) }
    return best to { score: Int -> best = store.submit(key, score, lowerIsBetter) }
}
