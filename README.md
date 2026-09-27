# Toolbox

Android app of small everyday tools and mini-games, styled after Google's search tools (Material You).

## Categories

Tiles are listed alphabetically within each category, and titles capitalise each word except small ones like "a" and "and". Games with a computer opponent offer Solo (against the computer) and 2-player modes.

- **Favourites:** any tool or game starred, either from its tile's star on the home screen or the star in its top bar
- **Decision Making:** Flip a Coin (embossed 3D coin, streaks and history) · Coin Toss (physics: swipe or shake harder to throw higher) · Roll a Die (d4–d20, up to 6 dice) · Dice Toss (physics: 1–5 rigid-body dice thrown across a walled table) · Spinner (number wheel) · Random Number · Magic 8-Ball · Team Splitter (by number of teams or people per team, share as text)
- **Measurements:** Bubble Level · Compass (magnetic, with calibration and interference warnings, mark a bearing) · Sound Meter (dB gauge, min/avg/max, 30 s history, calibration) · Step Counter (counts from midnight, goal ring, distance and calories, 7-day chart) · Tally Counter · Unit Converter (10 categories, UK and US measures) · Calculator (brackets, %, live preview, exact decimals) · Tuner (chromatic, guitar, bass, ukulele, violin)
- **Time:** Date Calculator (days between, add/subtract, age) · Timer · Stopwatch · Metronome (tap tempo, accents)
- **Fidget Toys** (sound and vibration, with one shared vibration switch): Meditate (guided breathing; a steady vibration while breathing in) · Fidget Spinner · Bubble Wrap · Clicky Switches · Rotary Dial · Purring Cat · Rain Stick · Zip & Ratchet
- **Solo Games:** Snake · Block Breaker · Reaction Time · Simon · Memory Game · 2048 · Lights Out (with hints and par) · Stack Tower (tap to drop; overhangs are sliced off, perfect drops regrow the block)
- **Duo Games:** Snowman (Hangman with a melting snowman instead of a gallows; solo, or two players where one types the secret word) · Tic-Tac-Toe (Solo against the computer, or 2-player) · Connect Four (Solo against the computer, or 2-player) · Dots and Boxes (3×3 to 5×5; Solo against the computer, or 2-player) · Air Hockey (two players on one screen, multi-touch; or Solo against the computer) · Battleships (pass-and-play with a hand-off screen, or Solo against the computer)

The coins and dice share one periwinkle palette (`CoinColors` in `tools/coin/CoinArt.kt`).
Shake to decide works on the coins, dice, spinner, random number, 8-ball and team splitter. Each has a top-bar toggle, shared across all of them.

## Home screen

Every tile belongs to one **category** (`Category` and `Tool` in `Tools.kt`), shown as a heading on the home grid.
Favourites appear in their own group at the top as well as in their usual category; they're stored as a comma-separated list of routes under the `favourites` pref.
A row of filter chips under the search field narrows the grid to Favourites or one category; tap the selected chip again to go back to All.
Search matches a tile's title, its category and its keywords, so "decibel" finds the sound meter and "north" finds the compass.

## Stack

- Kotlin 2.0, Jetpack Compose, Material 3 with dynamic colour (Android 12+), Google-blue fallback theme
- Single activity, Navigation Compose; min SDK 26, target SDK 35
- No third-party dependencies. Pitch detection (YIN), audio synthesis (streamed `AudioTrack`), the coin physics and the game AIs are all in-repo.
- Permissions: microphone (tuner, sound meter) and activity recognition (step counter, Android 10+), each requested only when its screen opens. The step counter also uses `RECEIVE_BOOT_COMPLETED` so its snapshot jobs survive a reboot.

## Build & run

Open the folder in Android Studio and press Run, or from a terminal:

```
./gradlew assembleDebug          # APK -> app/build/outputs/apk/debug/
./gradlew installDebug           # install on a connected device / emulator
./gradlew testDebugUnitTest      # unit tests for all the pure logic
```

Gradle 8.13 can't run on Java 25, which is what recent Android Studio versions bundle. From a terminal, point `JAVA_HOME` at a JDK 17–21.

## Layout

```
app/src/main/java/com/toolbox/app/
  Tools.kt            registry: every tile, its category and search keywords (add a tool = add one entry)
  MainActivity.kt     NavHost + transitions
  home/               home grid with search and category chips
  ui/theme/           Material 3 theme, Google accent colours
  ui/common/          ToolScaffold, haptics, scores, prefs, sensors, shake, mic, permissions, tone synth
  tools/<name>/       one package per tool
  games/<name>/       one package per game
app/src/test/java/com/toolbox/app/
  <Feature>Test.kt    one test file per feature
```

Screens stay thin: the logic for each feature lives in a pure Kotlin file next to it (e.g. `Units.kt`, `StepMath.kt`, `CoinPhysics.kt`, `ConnectFour.kt`), which is what the unit tests cover.

## Roadmap ideas

Minesweeper · Solitaire · Pac-Man-style maze · Colour picker · Decision maker (custom wheel labels) · Scoreboard for board games · Home-screen widgets / Quick Settings tile for coin & dice
