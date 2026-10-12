package com.toolbox.app

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toolbox.app.home.HomeScreen
import com.toolbox.app.ui.common.LocalToolRoute
import com.toolbox.app.ui.theme.ToolboxTheme

/** Debug start route that opens the home screen's Settings sheet instead of a tool. */
private const val SETTINGS_ROUTE = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ToolboxTheme { ToolboxNavHost(debugStartRoute()) }
        }
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
private fun ToolboxNavHost(startRoute: String? = null) {
    val nav = rememberNavController()
    var startHandled by rememberSaveable { mutableStateOf(false) } // so rotation doesn't reopen it
    LaunchedEffect(startRoute) {
        if (startRoute != null && startRoute != SETTINGS_ROUTE && !startHandled) nav.navigate(startRoute) { launchSingleTop = true }
        startHandled = true
    }
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
