package com.toolbox.app

import android.content.Intent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toolbox.app.home.HomeScreen
import com.toolbox.app.tools.timer.TimerController
import com.toolbox.app.tools.timer.TimerPhase
import com.toolbox.app.ui.common.LocalToolRoute
import com.toolbox.app.ui.theme.ToolboxTheme

/** Screens a notification may open through [MainActivity.EXTRA_OPEN], in release builds too. */
private val OPENABLE_ROUTES = setOf(Tool.Timer.route)

/** Debug start route that opens the home screen's Settings sheet instead of a tool. */
private const val SETTINGS_ROUTE = "settings"

/** Screens that follow the phone's auto-rotate setting; every other screen is portrait, as in the manifest. */
private val FREE_ROTATION_ROUTES = setOf(Tool.Calculator.route)

private const val FREE_ROTATION_KEY = "free_rotation"

class MainActivity : ComponentActivity() {
    /** A screen an intent asked for, opened over whatever is showing and then cleared. */
    private var openRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A Calculator recreated in landscape asks for free rotation again before its first frame, so the manifest's
        // portrait lock doesn't turn it back upright while the navigation state is still being restored.
        if (savedInstanceState?.getBoolean(FREE_ROTATION_KEY) == true) applyOrientation(free = true)
        val debugRoute = debugStartRoute()
        // A recreated activity keeps its old intent, which has already been handled.
        if (savedInstanceState == null) {
            openRequest = requestedRoute(intent) ?: debugRoute?.takeIf { it != SETTINGS_ROUTE }
            debugStartTimer()
        }
        val dynamicColor = debugDynamicColor()
        setContent {
            ToolboxTheme(dynamicColor = dynamicColor) {
                ToolboxNavHost(
                    openRequest = openRequest,
                    onOpened = { openRequest = null },
                    startWithSettings = debugRoute == SETTINGS_ROUTE,
                    onRouteShown = { applyOrientation(free = it in FREE_ROTATION_ROUTES) },
                )
            }
        }
    }

    /** Coming back to the app while the timer is ringing goes straight to its Stop button. */
    override fun onStart() {
        super.onStart()
        if (TimerController(this).state.phase == TimerPhase.Ringing) openRequest = Tool.Timer.route
    }

    /** A notification tapped while the app is already open, such as the Timer's. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute(intent)?.let { openRequest = it }
    }

    private fun requestedRoute(intent: Intent?) = intent?.getStringExtra(EXTRA_OPEN)?.takeIf { it in OPENABLE_ROUTES }

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

    /** Debug builds only: `--el timer 60000` starts a one-minute timer, to check the alarm without touching the phone. */
    private fun debugStartTimer() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val ms = intent?.getLongExtra("timer", 0L) ?: 0L
        if (ms > 0) TimerController(this).run { set(ms); start() }
    }

    /**
     * Debug builds only: `--ez dynamic_color false` uses the app's own colour schemes, as Android 8 to 11 do, so they
     * can be checked on a newer phone. A recreated activity keeps its intent, so the choice lasts until a fresh start.
     */
    private fun debugDynamicColor(): Boolean {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return true
        return intent?.getBooleanExtra("dynamic_color", true) ?: true
    }

    companion object {
        /** Intent extra naming a screen to open, one of [OPENABLE_ROUTES]. */
        const val EXTRA_OPEN = "open"
    }
}

@Composable
private fun ToolboxNavHost(
    openRequest: String?,
    onOpened: () -> Unit,
    startWithSettings: Boolean,
    onRouteShown: (String) -> Unit,
) {
    val nav = rememberNavController()
    LaunchedEffect(openRequest) {
        if (openRequest == null) return@LaunchedEffect
        nav.navigate(openRequest) { launchSingleTop = true }
        onOpened()
    }
    // The current entry changes as soon as back is pressed, so leaving the Calculator turns upright straight away.
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val currentOnRouteShown by rememberUpdatedState(onRouteShown)
    LaunchedEffect(route) { route?.let(currentOnRouteShown) }
    NavHost(
        navController = nav,
        startDestination = "home",
        // Opaque, so the fading screens never show the window through them; it is the screens' own surface colour,
        // which under dynamic colour differs a little from the window's.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        // Material shared-axis-ish motion: slide + fade forward, reverse on back.
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(300)) + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(300)) + fadeOut(tween(300)) },
    ) {
        // A screen only navigates while it is resumed. It stops being resumed as soon as it navigates, so a second
        // tap during the transition (another tile, or the back arrow again) is dropped instead of opening a second
        // tool or popping the home screen and leaving a blank one.
        composable("home") { entry ->
            HomeScreen(
                onOpen = { tool ->
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        nav.navigate(tool.route) { launchSingleTop = true }
                    }
                },
                startWithSettings = startWithSettings,
            )
        }
        Tool.entries.forEach { tool ->
            composable(tool.route) {
                val back = dropUnlessResumed { nav.popBackStack() }
                CompositionLocalProvider(LocalToolRoute provides tool.route) { tool.screen(back) }
            }
        }
    }
}
