package com.toolbox.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.toolbox.app.games.airhockey.AirHockeyScreen
import com.toolbox.app.games.battleships.BattleshipsScreen
import com.toolbox.app.games.breaker.BlockBreakerScreen
import com.toolbox.app.games.connectfour.ConnectFourScreen
import com.toolbox.app.games.dotsboxes.DotsBoxesScreen
import com.toolbox.app.games.hangman.HangmanScreen
import com.toolbox.app.games.lightsout.LightsOutScreen
import com.toolbox.app.games.memory.MemoryScreen
import com.toolbox.app.games.reaction.ReactionScreen
import com.toolbox.app.games.simon.SimonScreen
import com.toolbox.app.games.snake.SnakeScreen
import com.toolbox.app.games.stack.StackScreen
import com.toolbox.app.games.tictactoe.TicTacToeScreen
import com.toolbox.app.games.twentyfortyeight.Game2048Screen
import com.toolbox.app.tools.calculator.CalculatorScreen
import com.toolbox.app.tools.coin.CoinScreen
import com.toolbox.app.tools.compass.CompassScreen
import com.toolbox.app.tools.counter.CounterScreen
import com.toolbox.app.tools.dates.DateCalculatorScreen
import com.toolbox.app.tools.dice.DiceScreen
import com.toolbox.app.tools.eightball.EightBallScreen
import com.toolbox.app.toys.fidget.FidgetSpinnerScreen
import com.toolbox.app.tools.level.LevelScreen
import com.toolbox.app.tools.meditate.MeditateScreen
import com.toolbox.app.tools.metronome.MetronomeScreen
import com.toolbox.app.tools.random.RandomNumberScreen
import com.toolbox.app.tools.soundmeter.SoundMeterScreen
import com.toolbox.app.tools.spinner.SpinnerScreen
import com.toolbox.app.tools.steps.StepCounterScreen
import com.toolbox.app.tools.stopwatch.StopwatchScreen
import com.toolbox.app.tools.teams.TeamSplitterScreen
import com.toolbox.app.tools.timer.TimerScreen
import com.toolbox.app.tools.tuner.TunerScreen
import com.toolbox.app.tools.units.UnitConverterScreen
import com.toolbox.app.toys.bubblewrap.BubbleWrapScreen
import com.toolbox.app.toys.cat.CatScreen
import com.toolbox.app.toys.dial.DialScreen
import com.toolbox.app.toys.rainstick.RainStickScreen
import com.toolbox.app.toys.switches.SwitchesScreen
import com.toolbox.app.toys.zip.ZipScreen
import com.toolbox.app.ui.common.ToolIcons
import com.toolbox.app.ui.theme.GoogleColors

/** Home screen groups, in display order. Tools inside each one are listed alphabetically (see [Tool.sorted]). */
enum class Category(val title: String) {
    Decisions("Decision Making"),
    Measurements("Measurements"),
    Time("Time"),
    Toys("Fidget Toys"),
    Calm("Calm"),
    Solo("Solo Games"),
    Duo("Duo Games"),
}

/**
 * Single source of truth for everything on the home grid. Adding a tool = one entry here.
 * [screen] receives an onBack callback so every screen stays navigation-agnostic.
 * [keywords] are extra search terms, e.g. "bpm" finds the metronome.
 */
enum class Tool(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val category: Category,
    val keywords: List<String>,
    val screen: @Composable (onBack: () -> Unit) -> Unit,
) {
    Coin("coin", "Coin", ToolIcons.Coin.vector, GoogleColors.Yellow, Category.Decisions,
        listOf("heads", "tails", "flip", "toss", "coin toss", "flip a coin", "physics", "throw", "flick", "swipe", "decide"),
        { CoinScreen(it) }),
    Dice("dice", "Dice", ToolIcons.Dice.vector, GoogleColors.Green, Category.Decisions,
        listOf("die", "roll", "roll a die", "dice toss", "throw", "physics", "swipe", "d4", "d6", "d8", "d10", "d12", "d20",
            "board game"),
        { DiceScreen(it) }),
    Spinner("spinner", "Spinner", ToolIcons.Spinner.vector, GoogleColors.Green, Category.Decisions,
        listOf("wheel", "spin", "pick", "choose"), { SpinnerScreen(it) }),
    Random("random", "Random Number", Icons.Filled.Numbers, GoogleColors.Blue, Category.Decisions,
        listOf("generator", "range", "pick", "lottery"), { RandomNumberScreen(it) }),
    EightBall("eightball", "Magic 8-Ball", ToolIcons.EightBall.vector, GoogleColors.Red, Category.Decisions,
        listOf("8 ball", "eight ball", "fortune", "yes or no", "ask", "answer", "decide"), { EightBallScreen(it) }),
    Teams("teams", "Team Splitter", Icons.Filled.Groups, GoogleColors.Yellow, Category.Decisions,
        listOf("groups", "split", "divide", "pick teams", "random teams", "players"), { TeamSplitterScreen(it) }),

    Level("level", "Bubble Level", Icons.Filled.Straighten, GoogleColors.Green, Category.Measurements,
        listOf("spirit level", "tilt", "flat", "angle", "inclinometer"), { LevelScreen(it) }),
    Compass("compass", "Compass", Icons.Filled.Explore, GoogleColors.Red, Category.Measurements,
        listOf("north", "bearing", "heading", "direction", "magnetic", "navigation", "orienteering"), { CompassScreen(it) }),
    SoundMeter("soundmeter", "Sound Meter", Icons.AutoMirrored.Filled.VolumeUp, GoogleColors.Yellow, Category.Measurements,
        listOf("decibel", "decibels", "db", "noise", "loud", "loudness", "volume", "spl"), { SoundMeterScreen(it) }),
    Steps("steps", "Step Counter", Icons.AutoMirrored.Filled.DirectionsWalk, GoogleColors.Blue, Category.Measurements,
        listOf("pedometer", "walk", "walking", "steps", "distance", "calories", "fitness"), { StepCounterScreen(it) }),
    Counter("counter", "Tally Counter", Icons.Filled.PlusOne, GoogleColors.Blue, Category.Measurements,
        listOf("number counter", "tally", "count", "clicker"), { CounterScreen(it) }),
    Units("units", "Unit Converter", Icons.Filled.SwapHoriz, GoogleColors.Green, Category.Measurements,
        listOf("convert", "metric", "imperial", "length", "weight", "mass", "temperature", "celsius", "fahrenheit",
            "volume", "pints", "gallons", "stone", "kg", "miles", "km", "speed", "mpg", "pressure", "area", "data", "bytes"),
        { UnitConverterScreen(it) }),
    Calculator("calculator", "Calculator", Icons.Filled.Calculate, GoogleColors.Red, Category.Measurements,
        listOf("sum", "add", "subtract", "multiply", "divide", "percent", "maths", "math", "arithmetic"), { CalculatorScreen(it) }),
    Tuner("tuner", "Tuner", Icons.Filled.GraphicEq, GoogleColors.Blue, Category.Measurements,
        listOf("guitar", "bass", "ukulele", "violin", "pitch", "note", "tune", "music"), { TunerScreen(it) }),

    Dates("dates", "Date Calculator", Icons.Filled.CalendarMonth, GoogleColors.Red, Category.Time,
        listOf("days between", "days until", "countdown", "add days", "working days", "age", "birthday", "calendar", "weekday"),
        { DateCalculatorScreen(it) }),
    Timer("timer", "Timer", Icons.Filled.HourglassBottom, GoogleColors.Yellow, Category.Time,
        listOf("countdown", "alarm", "kitchen"), { TimerScreen(it) }),
    Stopwatch("stopwatch", "Stopwatch", Icons.Filled.Timer, GoogleColors.Red, Category.Time,
        listOf("lap", "split", "chronograph"), { StopwatchScreen(it) }),
    Metronome("metronome", "Metronome", Icons.Filled.Timeline, GoogleColors.Red, Category.Time,
        listOf("tempo", "bpm", "beat", "rhythm", "tap", "music"), { MetronomeScreen(it) }),

    Meditate("meditate", "Meditate", ToolIcons.Lotus.vector, GoogleColors.Green, Category.Calm,
        listOf("breathe", "breathing", "calm", "mindfulness", "relax"), { MeditateScreen(it) }),
    Fidget("fidget", "Fidget Spinner", ToolIcons.FidgetSpinner.vector, GoogleColors.Blue, Category.Toys,
        listOf("spin", "toy", "stress", "relax", "fidget", "vibrate"), { FidgetSpinnerScreen(it) }),
    BubbleWrap("bubblewrap", "Bubble Wrap", ToolIcons.BubbleWrap.vector, GoogleColors.Green, Category.Toys,
        listOf("pop", "bubbles", "toy", "stress", "relax", "fidget", "vibrate"), { BubbleWrapScreen(it) }),
    Switches("switches", "Clicky Switches", Icons.Filled.ToggleOn, GoogleColors.Red, Category.Toys,
        listOf("click", "toggle", "button", "rocker", "slider", "pen", "toy", "fidget", "vibrate"), { SwitchesScreen(it) }),
    Dial("dial", "Rotary Dial", ToolIcons.Dial.vector, GoogleColors.Yellow, Category.Toys,
        listOf("knob", "notches", "turn", "twist", "rotate", "toy", "fidget", "vibrate"), { DialScreen(it) }),
    Cat("cat", "Purring Cat", Icons.Filled.Pets, GoogleColors.Yellow, Category.Toys,
        listOf("purr", "stroke", "pet", "kitten", "toy", "relax", "fidget", "vibrate"), { CatScreen(it) }),
    RainStick("rainstick", "Rain Stick", ToolIcons.RainStick.vector, GoogleColors.Green, Category.Toys,
        listOf("sand", "zen", "rain", "grains", "tilt", "pour", "toy", "relax", "fidget", "vibrate"), { RainStickScreen(it) }),
    Zip("zip", "Zip & Ratchet", ToolIcons.Zip.vector, GoogleColors.Blue, Category.Toys,
        listOf("zipper", "zip", "ratchet", "wrench", "click", "teeth", "toy", "fidget", "vibrate"), { ZipScreen(it) }),

    Snake("snake", "Snake", ToolIcons.Snake.vector, GoogleColors.Green, Category.Solo,
        listOf("classic", "nokia", "arcade"), { SnakeScreen(it) }),
    Breaker("breaker", "Block Breaker", ToolIcons.BlockBreaker.vector, GoogleColors.Blue, Category.Solo,
        listOf("breakout", "bricks", "paddle", "arkanoid", "arcade"), { BlockBreakerScreen(it) }),
    Reaction("reaction", "Reaction Time", Icons.Filled.Bolt, GoogleColors.Red, Category.Solo,
        listOf("reflex", "reflexes", "speed", "tap", "test", "ms"), { ReactionScreen(it) }),
    Simon("simon", "Simon", ToolIcons.Simon.vector, GoogleColors.Yellow, Category.Solo,
        listOf("simon says", "memory", "sequence", "pattern", "colours", "sounds", "repeat"), { SimonScreen(it) }),
    Memory("memory", "Memory Game", Icons.Filled.Style, GoogleColors.Yellow, Category.Solo,
        listOf("pairs", "match", "cards", "concentration", "puzzle"), { MemoryScreen(it) }),
    Game2048("2048", "2048", ToolIcons.Merge2048.vector, GoogleColors.Yellow, Category.Solo,
        listOf("tiles", "merge", "slide", "puzzle"), { Game2048Screen(it) }),
    LightsOut("lightsout", "Lights Out", Icons.Filled.Lightbulb, GoogleColors.Green, Category.Solo,
        listOf("lights", "toggle", "logic", "grid", "switch", "puzzle"), { LightsOutScreen(it) }),
    Stack("stack", "Stack Tower", Icons.Filled.Layers, GoogleColors.Red, Category.Solo,
        listOf("tower", "blocks", "timing", "tap", "arcade", "build"), { StackScreen(it) }),

    Hangman("hangman", "Snowman", Icons.Filled.AcUnit, GoogleColors.Blue, Category.Duo,
        listOf("hangman", "word", "words", "letters", "guess", "spelling", "two player"), { HangmanScreen(it) }),
    TicTacToe("tictactoe", "Tic-Tac-Toe", ToolIcons.TicTacToe.vector, GoogleColors.Red, Category.Duo,
        listOf("noughts and crosses", "x o", "two player"), { TicTacToeScreen(it) }),
    ConnectFour("connectfour", "Connect Four", ToolIcons.ConnectFour.vector, GoogleColors.Blue, Category.Duo,
        listOf("connect 4", "four in a row", "discs", "drop", "two player"), { ConnectFourScreen(it) }),
    DotsBoxes("dotsboxes", "Dots and Boxes", Icons.Filled.Grain, GoogleColors.Green, Category.Duo,
        listOf("dots", "boxes", "lines", "squares", "pen and paper", "two player"), { DotsBoxesScreen(it) }),
    AirHockey("airhockey", "Air Hockey", Icons.Filled.SportsHockey, GoogleColors.Red, Category.Duo,
        listOf("hockey", "puck", "table", "air", "two player", "multiplayer"), { AirHockeyScreen(it) }),
    Battleships("battleships", "Battleships", Icons.Filled.Anchor, GoogleColors.Blue, Category.Duo,
        listOf("battleship", "ships", "navy", "sea", "grid", "two player", "pass and play"), { BattleshipsScreen(it) });

    /** Case-insensitive match on the title, the category title or any keyword. A blank query matches everything. */
    fun matches(query: String): Boolean = matchesSearch(query, listOf(title, category.title) + keywords)

    companion object {
        /** Every tool, alphabetically by title: the order used everywhere on the home screen. */
        val sorted: List<Tool> by lazy { entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }) }

        fun byRoute(route: String?): Tool? = entries.firstOrNull { it.route == route }
    }
}

/** Pure search rule behind [Tool.matches], kept separate so it can be unit-tested without Compose. */
fun matchesSearch(query: String, fields: List<String>): Boolean {
    val q = query.trim()
    return q.isEmpty() || fields.any { it.contains(q, ignoreCase = true) }
}

/** Favourites are stored in [com.toolbox.app.ui.common.Prefs] as one comma-separated string of routes. */
object Favourites {
    const val PREF_KEY = "favourites"

    fun parse(stored: String): Set<String> = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun toggle(stored: String, route: String): String {
        val current = parse(stored)
        return (if (route in current) current - route else current + route).joinToString(",")
    }

    /** [stored] with routes renamed by [renames], keeping just one of any that end up the same. */
    fun rename(stored: String, renames: Map<String, String>): String =
        parse(stored).map { renames[it] ?: it }.distinct().joinToString(",")
}
