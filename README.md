# Toolbox

Forty-four everyday tools, fidget toys and mini-games in one Android app, styled after Google's Material You. No ads, no accounts, no internet access and no third-party libraries.

**Website:** https://totallynotkasai.github.io/Toolbox/
**Download:** [latest APK](https://github.com/totallynotkasai/Toolbox/releases/latest/download/Toolbox.apk) (Android 8.0 and up) · [what's new](CHANGELOG.md)

> **Upgrading from 1.0.0?** Version 1.0.0 was signed with a development key, and 1.2.0 onwards uses the project's release key. Android won't install one over the other, so uninstall 1.0.0 first. Its scores, favourites and settings are lost. Later versions update in place.

<p align="center">
  <img src="docs/screenshots/home.png" width="15.5%" alt="The home screen: search, category chips and a two-column tool grid">
  <img src="docs/screenshots/coin.png" width="15.5%" alt="Coin in Throw mode: tails, the counts, the run and the history bar">
  <img src="docs/screenshots/dice.png" width="15.5%" alt="Dice in Throw mode: three six-sided dice totalling 14, with the history bar">
  <img src="docs/screenshots/calculator.png" width="15.5%" alt="The Calculator with a value in memory, recalled into a sum">
  <img src="docs/screenshots/connect-four.png" width="15.5%" alt="Connect Four, won against the computer">
  <img src="docs/screenshots/bubble-wrap.png" width="15.5%" alt="Bubble Wrap with a third of the sheet popped">
</p>

## What's inside

- **Decision Making:** Coin (Quick flip, or Throw: a physics coin you swipe or flick) · Dice (d4 to d20, up to 6 dice, Quick or Throw) · Magic 8-Ball · Random Number · Spinner (your own options) · Team Splitter
- **Measurements:** Bubble Level · Calculator (memory and history) · Compass (magnetic or true north) · Sound Meter · Step Counter · Tally Counter · Tuner (chromatic, guitar, bass, ukulele, violin) · Unit Converter (UK and US measures, and cooking: cups to grams and gas marks)
- **Time:** Date Calculator · Metronome (2/4 to 6/8, with subdivisions) · Stopwatch (laps; keeps running when the app is closed) · Timer (a real alarm)
- **Fidget Toys:** Bubble Wrap · Clicky Switches · Fidget Spinner · Infinity Cube (folds open and shut, endlessly) · Purring Cat · Rain Stick · Rotary Dial · Zip & Ratchet
- **Calm:** Meditate (guided breathing) · Soundscapes (rain, waves, wind, fire and noise, mixed your way, with a sleep timer)
- **Solo Games:** 2048 · Block Breaker · Block Drop (falling blocks) · Lights Out · Memory Game · Reaction Time · Simon · Snake · Stack Tower
- **Duo Games:** Air Hockey · Battleships · Charades (phone on your forehead, with your own decks too) · Connect Four · Dots and Boxes · Snowman (Hangman with a melting snowman) · Tic-Tac-Toe. Charades is for a group, and the rest are against the computer or another player on the same phone

Search finds a tool by its name, category or keywords ("decibel" finds the Sound Meter, "north" the Compass, "heads up" Charades). Star any tile to pin it under **Favourites**. Shake the phone to decide on the Coin, Dice, Spinner, Random Number, Magic 8-Ball and Team Splitter. The Coin, Dice, Spinner and Random Number keep their last 20 results, and the Team Splitter its last split. The gear on the home screen holds the two app-wide switches, Vibration and Shake to decide.

Every screen works with TalkBack and at large font sizes, and the turn-based games can be played with TalkBack.

## Permissions

The microphone (Tuner, Sound Meter) and physical activity (Step Counter) are asked for only when their screen first opens.

Approximate location is asked for only when True north is first turned on in the Compass, and never precise location. It's used to work out the declination (the angle between magnetic and true north where you are) on the phone, with no internet, at most once a day. Only the declination is kept, never the location. Without it, the declination can be typed in.

Notifications are asked for the first time a timer or the stopwatch starts. They show the Timer's countdown, with Stop and +1 min, and the Stopwatch's running time, with Pause and Lap (or Resume and Reset). Without them the alarm still rings, opening Toolbox stops it, and the stopwatch still keeps running. The Timer sets an alarm-clock alarm so it rings on time with the phone asleep, and plays the phone's alarm sound at alarm volume until it is stopped. A timer that is running when the phone restarts is lost.

Toolbox also starts after a reboot, so the Step Counter's day carries on and a running or paused stopwatch's notification comes back. The stopwatch keeps the right time across a restart either way.

Soundscapes plays through a media playback service, as music apps do, so it carries on with the screen off or the app closed. Android grants that when the app is installed, so nothing is asked. While it plays or is paused, its media controls show in the notifications, Quick Settings and the lock screen, even if Toolbox's notifications are turned off.

## Your data

Scores, favourites, settings, Calculator memory and history, the recent results of the Coin, Dice, Spinner and Random Number, your Charades decks and the cards each deck has played, and the Soundscapes mix and sleep timer are kept on the phone. If Android backup is on, they go into your own backup like any app's, so a new phone gets them back. The Step Counter's history and the Stopwatch are left out, because their readings only make sense on the phone that took them. The Compass keeps only its declination, never your location.

## Building

Requires JDK 17 or newer and the Android SDK. The JDK bundled with Android Studio works (tested with 21 and 25). Open the folder in Android Studio and press Run, or:

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected phone or emulator
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
```

GitHub Actions runs the unit tests and lint on every pull request.

Built with Kotlin, Jetpack Compose and Material 3 (dynamic colour on Android 12+). Pitch detection, audio synthesis, the coin and dice physics and the game AIs are all written in the app.

### Release builds

`./gradlew assembleRelease` signs the APK with the key named in a `keystore.properties` file in the project root:

```properties
storeFile=toolbox-release.jks
storePassword=…
keyAlias=toolbox
keyPassword=…
```

`storeFile` is relative to the project root, or an absolute path. The file and the key are gitignored. Without the file, `assembleRelease` still works but the APK comes out unsigned. Releases must keep using the same key, or Android won't update over them.

## Project layout

```
app/src/main/java/com/toolbox/app/
  Tools.kt        every tile, its category and search keywords (adding a tool = one entry)
  MainActivity.kt navigation and transitions
  Migrations.kt   moves saved data along when tools change (e.g. the old coin and dice tools into Coin and Dice)
  home/           home grid, search, category chips and the Settings sheet
  tools/<name>/   one package per tool
  toys/<name>/    one package per fidget toy
  games/<name>/   one package per game
  ui/theme/       colours, including the darker light-mode accents
  ui/common/      pieces shared by many screens:
                    Common.kt       the tool scaffold, best scores and favourite star
                    GameChrome.kt   game modes, turn line, game-over card; Tally.kt the win tallies
                    A11y.kt         TalkBack helpers (announcements, items over drawn boards)
                    ToolIcons.kt    the hand-drawn icons, also exported for the website
                    Prefs.kt        saved settings; Vibes.kt every vibration
                    PcmStream.kt    streamed procedural audio; ToneSynth.kt and Dsp.kt build sounds on it
                    ResultHistory.kt  recent results: the bar and the full list
                    NotificationAccess.kt  asking for notifications (Timer, Stopwatch)
                    SteadyText.kt   text that keeps its size as it changes; Geometry.kt angles for the turning toys
                    Landscape.kt    turning a screen sideways and full screen (Charades)
                    Sensors.kt, Shake.kt, Mic.kt, Permissions.kt, KeepScreenOn.kt
                    GameLoop.kt     frame timing for the real-time games
                    Toss.kt, Quat.kt, Rot.kt  shared by the coin and dice physics
docs/             the website (GitHub Pages), its icons and the screenshots
```

Where a feature has real logic (physics, game rules and AIs, maths, unit tables), it lives in a plain Kotlin file beside the screen, such as `Units.kt`, `CoinPhysics.kt` or `ConnectFour.kt`, and the unit tests cover it. Smaller screens keep their few rules in the screen file.

The website's tool icons in `docs/icons/` are written from the app's own icons by a unit test, which fails if they drift apart. `./gradlew testDebugUnitTest -PupdateIcons` rewrites them.

## Licence

[MIT](LICENSE). The Material icons in `docs/icons/` are from Google's Material Icons, under the Apache License 2.0.
