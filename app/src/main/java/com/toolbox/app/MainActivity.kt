package com.toolbox.app

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toolbox.app.home.HomeScreen
import com.toolbox.app.ui.common.LocalToolRoute
import com.toolbox.app.ui.theme.ToolboxTheme

/** Debug start route that opens the home screen's Settings sheet instead of a tool. */
private const val SETTINGS_ROUTE = "settings"

/** Screens that follow the phone's auto-rotate setting; every other screen is portrait, as in the manifest. */
private val FREE_ROTATION_ROUTES = setOf(Tool.Calculator.route)

private const val FREE_ROTATION_KEY = "free_rotation"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A Calculator recreated in landscape asks for free rotation again before its first frame, so the manifest's
        // portrait lock doesn't turn it back upright while the navigation state is still being restored.
        if (savedInstanceState?.getBoolean(FREE_ROTATION_KEY) == true) applyOrientation(free = true)
        setContent {
            ToolboxTheme {
                ToolboxNavHost(debugStartRoute(), onRouteShown = { applyOrientation(free = it in FREE_ROTATION_ROUTES) })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(FREE_ROTATION_KEY, requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
    }

    private fun applyOrientation(free: Boolean) {
        val wanted = if (free) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    /**
     * Debug builds only: `adb shell am start -n com.toolbox.app/.MainActivity --es route compass` opens a screen
     * directly, for screenshots on devices that don't allow adb input. `--es route settings` opens the Settings sheet.
     */
    private fun debugStartRoute(): String? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        return intent?.getStringExtra("route")?.takeIf { it == SETTINGS_ROUTE || Tool.byRoute(it) != null }
    }
}

@Composable
private fun ToolboxNavHost(startRoute: String? = null, onRouteShown: (String) -> Unit = {}) {
    val nav = rememberNavController()
    var startHandled by rememberSaveable { mutableStateOf(false) } // so recreation doesn't reopen it
    LaunchedEffect(startRoute) {
        if (startRoute != null && startRoute != SETTINGS_ROUTE && !startHandled) nav.navigate(startRoute) { launchSingleTop = true }
        startHandled = true
    }
    // The current entry changes as soon as back is pressed, so leaving the Calculator turns upright straight away.
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val currentOnRouteShown by rememberUpdatedState(onRouteShown)
    LaunchedEffect(route) { route?.let(currentOnRouteShown) }
    NavHost(
        navController = nav,
        startDestination = "home",
        // Material shared-axis-ish motion: slide + fade forward, reverse on back.
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(300)) + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(300)) + fadeOut(tween(300)) },
    ) {
        composable("home") {
            HomeScreen(
                onOpen = { tool -> nav.navigate(tool.route) { launchSingleTop = true } },
                startWithSettings = startRoute == SETTINGS_ROUTE,
            )
        }
        Tool.entries.forEach { tool ->
            composable(tool.route) {
                CompositionLocalProvider(LocalToolRoute provides tool.route) { tool.screen { nav.popBackStack() } }
            }
        }
    }
}
