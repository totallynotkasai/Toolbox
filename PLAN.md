# Toolbox expansion plan

Adds 14 tiles (13 requested plus a split coin), shake-to-decide, and categories on the home screen.
No third-party dependencies: everything uses the Android framework and the libraries already in `app/build.gradle.kts`.

## Progress

- [x] Phase 0: Groundwork (categories, shared helpers, manifest) (done 2026-09-26, see "Phase 0 deviations" below)
- [x] Phase 1: Quick wins (shake, Magic 8-ball, Team splitter, Reaction time, Lights Out) (done 2026-09-26, see "Phase 1 deviations" below; on-device check still to do)
- [x] Phase 2: Unit converter, Date calculator, Hangman, Simon (done 2026-09-26, see "Phase 2 deviations" below; on-device check still to do)
- [x] Phase 3: Compass, Sound meter, Step counter (done 2026-09-26, see "Phase 3 deviations" below; on-device check still to do)
- [x] Phase 4: Coin redesign + Physics coin, Connect Four (done 2026-09-26, see "Phase 4 deviations" below; on-device check still to do)
- [x] Wrap-up: tests, README, device checks (done 2026-09-26, see "Wrap-up deviations" below; **the hands-on device checks are still open**, and the list is there)

---

## Phase 0: Groundwork

### 0.1 Categories (`Tools.kt`, `home/HomeScreen.kt`)

Keep `Section` (Tools / Games) and add a `Category` that belongs to a section. `Tool` swaps `section` for
`category` (the section comes from `category.section`) and gains `keywords: List<String>` for search.

```kotlin
enum class Category(val title: String, val section: Section) {
    Chance("Chance & decisions", Section.Tools), Measure("Measure", Section.Tools),
    Music("Music", Section.Tools), Time("Time", Section.Tools), Everyday("Everyday", Section.Tools),
    Relax("Relax", Section.Tools),
    Arcade("Arcade", Section.Games), Puzzle("Puzzle", Section.Games), Board("Board & 2-player", Section.Games),
}
```

| Category           | Tiles (★ = new)                                                                  |
|--------------------|----------------------------------------------------------------------------------|
| Chance & decisions | Flip a coin (redesigned), ★Coin toss (physics), Dice, Spinner, Random number, ★Magic 8-ball, ★Team splitter |
| Measure            | Bubble level, ★Compass, ★Sound meter, ★Step counter                              |
| Music              | Tuner, Metronome                                                                 |
| Time               | Timer, Stopwatch                                                                 |
| Everyday           | Counter, ★Unit converter, ★Date calculator                                       |
| Relax              | Meditate, Fidget spinner                                                         |
| Arcade             | Snake, Block breaker, ★Reaction time, ★Simon                                     |
| Puzzle             | 2048, Memory, ★Lights Out, ★Hangman                                              |
| Board & 2-player   | Tic-tac-toe, ★Connect Four                                                       |

Home screen:
- A horizontally scrolling row of `FilterChip`s under the search field: All + one per category (`rememberSaveable`).
- The grid keeps the large "Tools" and "Games" headings, with smaller category sub-headings under each.
- Search matches the title, the category title and the keywords (e.g. "decibel" finds Sound meter, "north" finds Compass).

### 0.2 Shared helpers (`ui/common/`)

| File           | Purpose                                                                                           | Used by |
|----------------|---------------------------------------------------------------------------------------------------|---------|
| `Shake.kt`     | `OnShake(enabled, onShake: (strength: Float) -> Unit)`. Uses linear acceleration (or accelerometer minus low-passed gravity). Fires when the reading goes above about 12 m/s², with an 800 ms cooldown, and reports peak strength so the physics coin can scale its throw. | Coins, Dice, Spinner, Random, 8-ball, Teams |
| `Sensors.kt`   | `rememberSensorListener(type, rate, onEvent)`: the register/unregister `DisposableEffect` pattern from `LevelScreen`. Returns `false` if the sensor is missing. | Compass, Steps, Shake |
| `Mic.kt`       | Mic permission gate + `AudioRecord` setup, **moved out of `TunerScreen`**.                        | Tuner, Sound meter |
| `ToneSynth.kt` | Streaming `AudioTrack` voice with an attack/release envelope: `play(freqHz, ms)`, `buzz()`, `clink(intensity)` (a few inharmonic partials). Same structure as `SpinAudio`. | Simon, both coins |
| `Prefs.kt`     | `rememberPref(key, default)` for String/Int/Float/Boolean backed by SharedPreferences, alongside `ScoreStore`. | Teams, calibration, units, shake toggle |

### 0.3 Manifest

- `ACTIVITY_RECOGNITION` (step counter, runtime request on API 29+).
- `RECEIVE_BOOT_COMPLETED` (step counter jobs persist across reboots).
- `<service android:name=".tools.steps.StepSnapshotJob" android:permission="android.permission.BIND_JOB_SERVICE" />`
- `uses-feature required="false"` for `android.hardware.sensor.compass` and `android.hardware.sensor.stepcounter`.

### Phase 0 deviations (as built)

- **`StepSnapshotJob` `<service>` entry deferred to Phase 3.** The permissions and `uses-feature` entries are in.
  The service entry is left out, with a placeholder comment in the manifest, so the manifest never names a class that doesn't exist yet (lint `MissingClass`).
  **Phase 3 must add it** together with the class.
- **Extra file `ui/common/Permissions.kt`.** It holds a generic `PermissionGate(permission, icon, rationale, buttonLabel)`.
  `Mic.kt`'s `MicPermissionGate` is a thin wrapper over it, and the step counter can reuse it for `ACTIVITY_RECOGNITION`.
  It improves on the old Tuner gate in two ways:
  - it re-checks the permission on resume
  - after "Don't ask again" it offers **Open settings**
- **`Mic.kt` API:** `openMicRecord(sampleRate, minBufferBytes, vararg sources)` tries each source in order and returns `null` if none initialise.
  Previously the Tuner crashed if even the `MIC` fallback failed. The Tuner now shows "microphone unavailable" instead.
  Mic.kt also has two more helpers:
  - `supportsUnprocessedMic(context)`, for the sound meter
  - `AudioRecord.readFully(buffer)`
- **`rememberSensorListener` has extra optional parameters:** `fallbackType` (Level: gravity → accelerometer; Shake: linear → accelerometer), `enabled` and `onAccuracyChanged` (for the compass banner).
  It uses `LifecycleStartEffect`, so sensors stop when the app is backgrounded, not only when the screen leaves composition.
  `LevelScreen` was migrated to it.
- **Shake:** the logic is in a pure, unit-tested `ShakeDetector`.
  After crossing 12 m/s² it collects the peak for up to 120 ms, or until the reading drops back below the threshold, before firing.
  That adds up to 120 ms of latency but gives the physics coin a true peak.
- **`ToneSynth`** also has `silence()` (fade out everything, e.g. on Simon game over) and a `rememberToneSynth()` composable that runs the audio thread only while the screen is started.
- **`Prefs`** supports `Long` as well. `rememberPref` states on the same key stay in sync through a SharedPreferences listener.
  `Prefs.SHAKE_ENABLED` is defined for Phase 1. List prefs (the Teams names) are not built in: store them as a newline-joined String.
- **Home screen extras:**
  - a "Nothing matches → Show everything" empty state
  - tapping the selected chip again returns to All
  - the chip row scrolls edge to edge
  - `Tool` entries are reordered by category, and search logic is in a pure `matchesSearch()`, which is tested
- **Tests:** new `CommonTest.kt` (`ShakeDetectorTest`, `SearchTest`). `LogicTest.kt` is untouched; the per-feature split is still in Wrap-up.
- **Build note:** Android Studio's bundled JBR is Java 25, which Gradle 8.13 can't run on.
  Build from the command line with `JAVA_HOME=%USERPROFILE%\.jdks\jbr-21.0.11`.

---

## Phase 1: Quick wins

### Shake to decide
Call `OnShake { … }` from Dice, Spinner, Random number, Magic 8-ball, Team splitter and both coins. Each of those
screens gets a shake-toggle icon in the top bar, stored in the shared pref `shake_enabled` (default on).
Shakes are ignored while an animation is running.

### Magic 8-ball: `tools/eightball/`
- The ball is drawn on a Canvas: black sphere, radial highlight, dark window.
- The answer sits in a blue triangle. It uses the 20 classic answers (10 yes, 5 non-committal, 5 no).
- Shake or tap makes the ball jiggle (spring `Animatable` offset). The triangle fades out, rotates slightly and floats back up with the new answer.

### Team / group splitter: `tools/teams/`
- Type a name and press Enter to add it as a chip. The name list persists via `Prefs`, and each chip can be removed.
- Segmented button: **Number of teams** / **People per team**, with a stepper.
- Pure `splitTeams(names, teams): List<List<String>>` shuffles and deals round-robin, so team sizes differ by at most 1.
- Teams show as coloured cards. Reshuffle with the button or a shake. Share sends the teams as plain text (`ACTION_SEND`).

### Reaction time: `games/reaction/`
- Screen flow: Tap to start → red "Wait…" for a random 1.5–4 s → green "TAP!" → result in ms. Tapping on red shows "Too soon!".
- Timing: the start time is read in `withFrameNanos` on the first green frame, and the tap time comes from the pointer event's `uptimeMillis`.
- Five rounds, then the average. The best average is saved with `ScoreStore(lowerIsBetter = true)`.

### Lights Out: `games/lightsout/`
- 5×5 grid. Tapping a cell toggles it and its orthogonal neighbours.
- Every puzzle is solvable: start from the solved board and apply 8–15 random presses.
- Move counter, New puzzle button, best (fewest) moves.
- Hint: solve over GF(2) with Gaussian elimination and pulse one correct press.
- Pure `LightsOutBoard`, with tests.

### Phase 1 deviations (as built)

- **Shake toggle is shared UI:** `rememberShakeEnabled()` and `ShakeToggleButton(shake, action)` live in `Shake.kt`.
  - The icon is `Vibration` when on and `MobileOff` when off.
  - Toggling shows a short toast ("Shake to roll: off") so the state change is obvious.
- **"Ignored while animating" is done in the action, not the sensor.** Each screen calls `OnShake(shake.value) { roll() }`, and `roll()`/`flip()`/`spin()`/`ask()` already return early while busy.
  Putting `!busy` into `enabled` would unregister and re-register the sensor on every roll, and reset the gravity filter each time.
  The Spinner also ignores shakes while its options sheet is open.
- **Shake is wired into Coin, Dice, Spinner, Random number, 8-ball and Teams.** The physics coin doesn't exist yet, so **Phase 4.2 must add `OnShake`** itself.
  The coin and spinner hint text now mentions shaking when it's on.
- **Tiles are registered now, not in Wrap-up.** They use the Wrap-up icons: 8-ball `QuestionMark`, Teams `Groups`, Reaction `Bolt`, Lights Out `Lightbulb`.
  Each has search keywords, and accents were chosen so neighbouring tiles differ.
- **Magic 8-ball:**
  - It never repeats the previous answer, so every ask visibly changes something.
  - Shake strength scales the jiggle. A tap counts as a medium shake.
  - The answer is also shown as text under the ball, as a polite live region for TalkBack.
  - It starts with an empty window rather than an "8" face.
- **Team splitter:**
  - Pasting "Sam, Alex; Jo" or a multi-line list adds several names at once (`parseNames`).
  - Duplicate names (case-insensitive) are rejected with an inline error. The list is capped at 60 people and names at 30 characters.
  - "Clear all" asks for confirmation.
  - The mode, team count and per-team size persist through `Prefs`, as well as the names. They are clamped for display only, so a saved choice comes back when more people are added.
  - A summary line shows the result, e.g. "3 teams of 2–3".
  - Editing the list clears the dealt teams so no stale names show. Dealt teams survive rotation through a custom `Saver`.
  - Share is a top-bar action.
  - Extra pure helpers `teamsForSize` and `teamsAsText` are tested.
- **Reaction time:**
  - The tap is registered on finger-down (`awaitFirstDown`), not on release.
  - Colours switch instantly, because animating would blur the measured moment.
  - Anything under 100 ms (`MIN_REACTION_MS`) counts as "Too soon!", since it's a guess.
  - A "too soon" round is repeated and doesn't count.
  - A rating line ("Great") shows under each time. The per-round times are listed at the end, with round-progress dots.
  - There is a "Start over" top-bar action.
  - The measured time includes the display pipeline latency after the vsync timestamp (roughly 1–2 frames), so it reads slightly slow. This is the same for every attempt.
- **Lights Out:**
  - `solution()` returns the **fewest** presses: it tries all 4 solutions from the 2-dimensional null space. It returns null if a board is unsolvable.
  - Random puzzles press 8–15 **distinct** cells and retry if the result is blank.
  - The screen shows "can be solved in N" (par), and "Perfect!" when you match it.
  - There is a restart-this-puzzle top-bar action.
  - **Using a hint means that game doesn't count towards the best score.**
- **Tests:** these went straight into new per-feature files: `TeamsTest.kt`, `LightsOutTest.kt` and `ReactionTest.kt` (36 tests in total, all passing).
  `LogicTest.kt` is still to be split in Wrap-up.
- **Not yet checked on a device.** The attached phone was locked and rejects adb input injection (Xiaomi's "USB debugging (Security settings)" is off), and no emulator image is installed.
  The debug APK is installed on the phone. Things to check by hand:
  - shake sensitivity on each screen
  - text fit inside the 8-ball triangle for the longest answers
  - reaction timing feel

---

## Phase 2: Everyday tools and word/sound games

### Unit converter: `tools/units/`
- Categories: Length, Mass, Temperature, Volume, Area, Speed, Time, Data, Pressure, Fuel economy.
- UK-aware units: stone; UK and US pints and gallons; UK and US cups; tbsp and tsp; mpg (UK), mpg (US), L/100 km.
- Most units are a factor to a base unit. Temperature (offset) and L/100 km (inverse) are special-cased.
- Two value + unit rows with a swap button. Both fields are editable and convert live. The last pair is remembered per category.
- Pure `Units.kt`. Tests: round trips, 0 °C = 32 °F, 1 st = 6.35029 kg, 1 UK gal = 4.54609 L.

### Date calculator: `tools/dates/`
Uses `java.time` (available from minSdk 26) and the Material 3 `DatePickerDialog`. Three tabs:
1. **Between**: days; weeks + days; years/months/days; optional working days (Mon–Fri).
2. **Add / subtract**: date ± N days, weeks, months or years, with the weekday.
3. **Age**: age, days to next birthday, weekday of birth.

Pure functions with tests covering leap years, 31 Jan + 1 month, and end date before start date.

### Hangman (melting snowman): `games/hangman/`
- Words from `res/raw/words.txt`, about 600 common 5–10 letter words.
- On-screen A–Z keyboard: keys turn green if the letter is in the word, grey if not. A physical keyboard also works.
- **The snowman melts instead of a gallows being drawn:** it starts complete (three snowballs, carrot nose, coal eyes, scarf, hat, stick arms, snowy ground) under a sun.
  - Each wrong guess (6 allowed) moves the sun a bit higher, loses one piece, and shrinks the body into a growing puddle.
  - Stages: hat, arms, scarf, head, middle, and at the end just a puddle with a carrot.
  - The shrink animates with `animateFloatAsState`, with a few drips falling.
- Winning refreezes it with a sparkle. The best win streak is saved.
- Pure `HangmanState`, with tests.

### Simon: `games/simon/`
- Four quadrant pads in the Google colours, using the original Simon tones: E4 329.6, C♯4 277.2, A4 440, E3 164.8 Hz (via `ToneSynth`).
- The sequence grows by one each round. Playback speeds up at rounds 5, 9 and 13. Input is locked while the sequence plays.
- A wrong press plays a buzz and ends the game. The best round is saved.

### Phase 2 deviations (as built)

- **Tiles are registered now, not in Wrap-up.** They use the Wrap-up icons: Units `SwapHoriz` (Green), Dates `CalendarMonth` (Red), Simon `MusicNote` (Yellow), Hangman `AcUnit` (Blue).
  Each has search keywords (e.g. "celsius", "pints", "working days", "birthday", "snowman").
- **Unit converter:**
  - More units than listed: nautical miles, tonnes, long/short tons, m³, ft³, UK/US fl oz, knots, ft/s, KiB–TiB, hPa, mmHg, inHg, km/L, and years (365.25 days) in Time.
  - **"UK cup" is the 250 mL metric cup** used in modern UK recipes, not the old imperial cup (284 mL). tsp and tbsp are metric (5 mL / 15 mL); there are no separate US spoons. A note under Volume says so.
  - Fuel economy's base unit is km/L, so mpg are plain factors and L/100 km is the inverse. 0 mpg shows "—" rather than "Infinity".
  - Only the field last typed in holds text; the other is derived, so changing a unit always re-converts.
  - **Swap keeps the number on top and swaps the units** ("10 cm = 3.94 in" becomes "10 in = 25.4 cm").
  - The last category is remembered too, as well as the pair per category (`units_category`, `units_<Category>_from/_to`).
  - Extras: a "1 ft = 0.3048 m" rate line; an "is" list of the value in every unit of the category (tap a row to make it the To unit); a copy button on each field; a ± button for Temperature (some decimal keypads have no minus); a "below absolute zero" warning.
  - `parseNumber` ignores spaces and thousands commas ("1,000" = 1000). `formatNumber` shows up to 10 significant digits with no float noise (0.1 + 0.2 → "0.3"), and scientific notation only beyond 1e15 or under 1e-6.
- **Date calculator:**
  - **Between:** the end date is excluded by default (Mon → next Mon = 7 days, 5 working days). An **"Include the end date"** switch counts both ends. Working days skip weekends but not bank holidays, and the screen says so.
    An end before the start shows a "−" count with a note, rather than an error. There's a swap button.
  - **Add / subtract:** when months/years clamp (31 Jan + 1 month = 28 Feb) a note explains it. The result also shows how far it is from today ("In 30 days from today").
  - **Age:** has an **"Age on"** date (default today) as well as the birth date. The birth date is remembered in Prefs (`dates_birth_epoch_day`) so your own age shows straight away.
    **29 February birthdays count on 1 March in common years.** This is the UK legal rule and matches `Period.between`, so the age and the countdown never disagree. It also shows "Happy birthday!", days old and the weekday of the next birthday.
  - Each date field has a "set to today" button, and dates use the locale's full format (e.g. "Saturday 26 September 2026").
  - `DateMath.kt` holds `dateDiff`, `workingDaysBetween` (O(1) plus at most 6 steps), `shiftDate`, `ageOn`, `birthdayAt`.
- **Hangman:**
  - **The word list has 812 words**, not ~600 (all 5–10 lowercase letters, no duplicates; a unit test checks the file).
  - **The on-screen keyboard is QWERTY, not alphabetical**, since that's what phone users' thumbs expect. Misses fade out rather than turning plain grey.
  - The current streak persists across visits (`hangman_streak` pref); the best is in `ScoreStore` (`hangman-streak`). **Skipping a word you've started counts as a loss**, so streaks can't be farmed by skipping hard words. A new word never repeats the previous one.
  - Snowman details: the sky warms as the sun climbs; the eyes and carrot slide down into the puddle as the head melts, and the coal buttons fade with the middle; drips run from the lowest standing snowball on each miss. Dark theme gets a night-sky palette.
  - A row of six snowflakes shows the guesses left. On a loss the missing letters appear in red.
  - A physical keyboard types letters; Enter/Space starts a new word when the game is over.
- **Simon:**
  - The logic is a pure, tested `SimonGame` (not asked for in the plan). The screen state is saved as a compact string, so rotation replays the current round.
  - Tone lengths: 420 ms (rounds 1–4), 340 ms (5–8), 270 ms (9–12), 210 ms (13+), with a gap of 40 % of the tone. "Faster now!" shows on the rounds where it speeds up.
  - Presses register on finger-down. After a mistake, the pad that should have been pressed blinks three times and the text names it.
  - Best = rounds fully repeated (`simon-round`).
  - Pads are four quarter-disc tiles around a centre hub that shows Start / round number / Again. Each pad has TalkBack click semantics.
  - **Not done:** the original's 3-second input timeout (left out as it makes the game harsher). Also, if the app is backgrounded mid-playback, the playback carries on silently; you come back to your turn.
- **Tests:** four new per-feature files: `UnitsTest.kt` (8), `DatesTest.kt` (13), `HangmanTest.kt` (10), `SimonTest.kt` (5). The full suite is 72 tests, all passing. Lint reports nothing in the new files.
- **Not yet checked on a device:** no phone was attached this session. Things to check by hand:
  - The Simon tones and speed, and that a finger resting on the last pad doesn't stay lit
  - Snowman proportions and the melt stages on a small screen
  - The unit dropdown width with long names such as "Fluid ounces (UK)"
  - The four-segment Days/Weeks/Months/Years row on a 360 dp-wide phone

---

## Phase 3: Sensor and mic tools

### Compass: `tools/compass/`
- Uses `TYPE_ROTATION_VECTOR` (falling back to accelerometer + magnetometer), then `getRotationMatrix` → `remapCoordinateSystem` for display rotation → azimuth.
- Smoothing is a low-pass filter on sin/cos, so 359° → 0° doesn't swing the long way round.
- UI: a rotating dial with N/E/S/W and ticks every 5° and 30°, a fixed red lubber line, and a large "274° W" reading.
- A haptic tick fires when a cardinal point passes the lubber line.
- If `onAccuracyChanged` reports low accuracy, a "move your phone in a figure-8" banner appears.
- Magnetic north only, so no location permission is needed.

### Sound level meter: `tools/soundmeter/`
- Shares `Mic.kt`. Audio source is `UNPROCESSED` if `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` is true, otherwise `VOICE_RECOGNITION`.
- About every 100 ms it computes the RMS, then dBFS = 20·log₁₀(rms / 32768), then dB ≈ dBFS + offset (default +90).
- UI:
  - arc gauge with a peak-hold marker
  - Min / Avg (energy-averaged Leq) / Max
  - scrolling 30 s history graph
  - reference scale: whisper 30, conversation 60, traffic 80, concert 100, pain 120
- A calibration slider is saved in Prefs, with a note that phone readings are approximate.

### Step counter: `tools/steps/`: counts from midnight
`TYPE_STEP_COUNTER` counts in hardware even when the app is closed, but it reports the total since boot and resets to 0 on reboot.
To get a count that starts at midnight, the app records the counter value at set times:

- **`StepStore`** (SharedPreferences):
  - `dayStartRaw`: the counter value at the start of today
  - `lastRaw`: the last value seen
  - `carried`: steps banked before a reboot today
  - `date`
  - a 7-day history map
- **`StepSnapshotJob`** (a `JobService` scheduled through `JobScheduler`, persisted across reboots) briefly registers the sensor, reads one value and updates the store:
  - It runs **as close to midnight as possible**. Under Doze it may run a few minutes late, and the steps in that gap count towards the new day, which is negligible at night.
  - A **periodic job (~every 60 min)** keeps `lastRaw` fresh. After a reboot, at most the steps since the last snapshot are lost.
- **Rollover:** on the first reading after the date changes, today's total is written to history, `dayStartRaw` becomes the midnight reading, and `carried` resets to 0.
- **Reboot** (`raw < lastRaw`): add `lastRaw - dayStartRaw` to `carried` and set `dayStartRaw = 0`. The jobs are persisted, so they are restored after boot.
- **Today** = `carried + (raw - dayStartRaw)`.
- **UI:**
  - Today ring with an editable goal (default 10,000)
  - distance and kcal estimates from an editable stride length
  - This session, resettable
  - 7-day bar chart
- Scheduling starts the first time the permission is granted and the screen is opened.
- Fallback: `TYPE_STEP_DETECTOR` counts only while the screen is open. With neither sensor, the screen says "not supported".
- Pure rollover/reboot logic in `StepMath.kt`, with tests for day change, reboot mid-day, reboot plus day change, and a missed midnight job.

### Phase 3 deviations (as built)

- **Tiles are registered now, not in Wrap-up.** Compass `Explore` (Red), Sound meter `VolumeUp` (Yellow), Step counter `DirectionsWalk` (Blue), all in Measure after Bubble level.
  - `VolumeUp` and `DirectionsWalk` use the **`Icons.AutoMirrored`** versions, because the plain ones are deprecated. The Wrap-up table is updated.
  - Search keywords include "north", "bearing", "decibel", "db", "noise", "pedometer", "walking" and "calories".
- **The manifest `<service>` for `StepSnapshotJob` is in**, with `exported="false"`, replacing the Phase 0 placeholder comment.
- **Compass:**
  - **Heading isn't taken from `getOrientation`'s azimuth.** That breaks down when the phone is held upright.
    Instead, `headingFromMatrix` adds the horizontal parts of the screen's top edge (+Y) and the back camera (−Z). The reading stays steady from flat through upright (tested at 0–110° of tilt), so there's no "hold flat" warning.
    `remapCoordinateSystem` for the display rotation is still applied first, as planned.
  - The magnetometer is always read, even when the rotation vector is used. It supplies the calibration accuracy and the field strength.
    The banner covers two cases: low accuracy shows "Compass needs calibrating" with a small animated figure-8, and a field outside 20–70 µT shows "Magnetic interference".
  - The cardinal haptic tick uses a pure `CardinalTicker`. After a tick, that point can't tick again until the heading has moved 6° away, so jitter while pointing due north doesn't buzz constantly.
  - Extras:
    - a red/grey needle in the centre of the card
    - degree numbers every 30°
    - the rotation vector's own "±N°" accuracy estimate, where the phone reports one
    - **"Mark this bearing"**, which pins a blue marker on the card and shows "Turn 15° right" or "On course" (within 3°)
  - The dial shrinks to fit landscape, and the screen scrolls.
- **Sound meter:**
  - Readings are kept in dBFS, and the calibration offset is added only for display. **Moving the calibration slider shifts the live reading, Min/Avg/Max and the history graph together.**
  - The source order is `UNPROCESSED` (if supported) → `VOICE_RECOGNITION` → `MIC`. DC offset is removed before the RMS.
  - The gauge runs from 20 to 120 dB and is coloured by hearing-risk band: Quiet <50, Moderate <70, Loud <85, then red "Risk to hearing".
    The peak marker holds for 1.5 s and then falls at 20 dB/s. The history graph has a dashed 85 dB line.
  - The reference scale is a card, and the band you're in is highlighted ("≈ now").
  - Calibration is a bottom sheet (Tune icon in the top bar). It has a 60–120 dB offset slider (pref `soundmeter_offset`, default +90), a live reading, a reset button, and a line saying whether the unprocessed mic is in use.
  - Extras:
    - Pause/Resume and Reset buttons
    - the screen stays on while measuring
    - **the mic is only held while the app is in the foreground** (`repeatOnLifecycle(STARTED)`)
- **Step counter:**
  - **New-day baseline is the last reading before midnight, not the midnight reading.** Steps between the last snapshot and the midnight job (at most about an hour) count towards the new day, as the plan intended, instead of being lost.
    If the midnight job is missed entirely, the whole gap goes to the day of the next reading, and **skipped whole days get no history entry** (the chart shows them as empty).
    A clock going backwards (a time zone change) is treated as the same day.
  - `StepState` has a **`lifetime`** total that only goes up. "This session" is `lifetime − session start`, so a session carries on correctly over midnight and reboots.
    The session start is stored in Prefs (`steps_session_start`/`_millis`), and the first session starts at the first reading.
  - **The midnight job alternates between two job ids** (each run schedules the other one). Calling `schedule()` again with the id of the job that's running would cancel it.
    The jobs are a persisted hourly periodic job (15 min flex) and a one-off job about 1 minute after midnight (30 min deadline). A job gives up after 10 s if the sensor sends nothing.
    `StepScheduler.ensureScheduled` runs every time the screen opens (once permission is granted). It only schedules jobs that are missing.
  - **Settings include weight and units as well as goal and stride.** Calories are ≈ 0.5 kcal per kg per km of walking, which needs a weight (default 70 kg).
    Distance shows in miles by default in GB/US and in km elsewhere.
    All four settings (goal 10,000, stride 70 cm, weight, miles) are in a dialog from the top-bar Tune icon. You can also tap "of 10,000" under the ring to change the goal.
  - The 7-day chart has tappable bars (shows "Tue 22 Sep: 8,432 steps"), a dashed goal line and a 7-day average. Days that met the goal are green.
  - On the first day, a note says "Counting since 14:05", because earlier steps can't be recovered.
  - `ACTIVITY_RECOGNITION` is only requested on API 29+. On API 26–28 the sensors need no permission.
  - Pure logic is in `StepMath.kt` (`applyReading`, history encoding, distance/kcal) and persistence in `StepStore.kt`.
- **Tests:** three new files: `CompassTest.kt` (9), `SoundMeterTest.kt` (6), `StepsTest.kt` (13).
  The steps tests cover everything the plan asked for, plus two reboots in one day, a clock going backwards, history pruning and a stale state after midnight.
  The full suite is 100 tests, all passing. Lint reports nothing in the new files.
- **Known risk to check on a device:** Android 9+ stops sensor events for apps idle in the background.
  If this applies to `StepSnapshotJob` on a given phone, its jobs will time out without a reading. The count then still comes out right when the screen is next opened, because the last-reading baseline and the reboot handling don't need the jobs.
  But the day boundary becomes "last time the app was open" rather than midnight, and a reboot loses the steps since then.
  If this happens, the fix is a short foreground service for the snapshot, or a `TYPE_STEP_COUNTER` wake-up listener.
- **Not yet checked on a device:** no phone was attached this session. Things to check by hand:
  - compass direction against a known bearing, flat and upright, in portrait and landscape
  - how often the calibration banner shows
  - sound meter readings against a reference, and the `UNPROCESSED` level on this phone
  - step counter over midnight and after a reboot (look at the history bar and the "This session" total)
  - whether the snapshot job gets readings while the app is closed (see the risk above)

---

## Phase 4: Coins and Connect Four

### 4.1 Flip a coin, classic (redesign of `tools/coin/`)
Keeps the current tap-to-flip behaviour, where the result is picked up front. Only the visuals change:
- Faces with an embossed design:
  - heads: a crowned-profile-style motif and "HEADS" around the rim
  - tails: a laurel/shield motif
  - both drawn as vector paths on a Canvas, with no image assets
- A reeded rim (radial tick marks), a metallic gradient that shifts with the flip angle (a moving specular highlight), and a visible **edge thickness** when side-on.
- A soft ground shadow that shrinks as the coin rises and is sharpest when it lands.
- A small landing settle (a slight squash, then a two-frame bob) and a `ToneSynth` clink.
- Stats: heads/tails counts, a streak ("4 heads in a row") and a mini history strip of the last 10 results.
- Shake to flip. In this classic version the shake is only a trigger; its strength isn't used.

### 4.2 Coin toss, physics (new: `tools/cointoss/`)
The result comes from the simulation, so a harder swipe or shake throws the coin higher and spins it faster.

- **`CoinPhysics.kt`** (pure, deterministic given a seed):
  - state: `y`, `vy`, `x`, `vx`, flip angle `θ`, spin `ω`, wobble tilt `φ` and wobble phase
  - stepped every frame via `withFrameNanos` with gravity
- **Input → force:**
  - **Swipe up:** fling velocity from `VelocityTracker` sets `vy` (clamped), and `ω ∝ |v|`. Horizontal swipe velocity becomes `vx`.
  - **Shake:** `OnShake`'s peak strength sets `vy` and `ω` on the same scale.
  - A small random jitter (±5 %) on `ω` so identical gestures still vary.
  - A weak swipe gives a low, lazy flip. A hard one gives a tall, fast spin.
- **Bounces:** on contact, `vy = -vy × 0.3`. The spin loses about 60 % and gets a small kick in a random direction. Bounces are **slight**, at most 2–3, and the side walls also bounce the coin.
- **Settle:** below the energy threshold, the coin falls flat onto whichever face is up (sign of cos θ). It then **wobbles**: the tilt decays while the wobble speeds up, like a coin spinning down on a table, over about 0.8–1.2 s.
- **Rendering:** reuses the new face/rim/edge drawing from 4.1:
  - `rotationX = θ`, `rotationZ` wobble, `translationY = -y`
  - scale grows slightly with height
  - the ground shadow tracks height and wobble
- **Feedback:** a haptic and a clink on every bounce, both scaled by impact; a quickening ticking rattle during the wobble.
- **Fairness test:** 10,000 random gestures across the force range must give 48–52 % heads.
- Same stats as the classic coin, stored separately.

### 4.3 Connect Four: `games/connectfour/`
- 7×6 board. Tapping a column drops a disc with a gravity + bounce animation.
- Modes: Easy / Hard / 2-player, as in tic-tac-toe.
- AI: negamax with alpha-beta pruning, centre-first move ordering and a window-scoring heuristic, run on `Dispatchers.Default`.
  - Hard searches about 8 plies.
  - Easy searches 2 plies and makes a random move 30 % of the time.
- The winning four are highlighted, and a win tally is kept.
- Tests: horizontal, vertical and both diagonal wins; takes an immediate win; blocks an immediate loss; a full board is a draw.

### Phase 4 deviations (as built)

- **Tiles are registered now, not in Wrap-up.** Coin toss `Toll` (Blue) sits after Flip a coin; Connect Four `Circle` (Blue) sits after Tic-tac-toe.
  - **The classic coin's icon changes from `Toll` to `Contrast`** (a half-filled circle, for two sides), so the two coin tiles look different. The Wrap-up table is updated.
  - The classic coin's keyword "toss" becomes "flip", since "toss" already matches the Coin toss title. Coin toss keywords include "physics", "throw", "flick" and "swipe"; Connect Four's include "connect 4" and "four in a row".
- **Shared coin rendering (`tools/coin/`):**
  - **It isn't drawn with `graphicsLayer` `rotationX`/`rotationZ`.** A flat layer rotated in 3D has no thickness, so there would be no edge.
    Instead, `CoinArt` projects the coin itself. It takes a 3D orientation (`Rot` in the pure `CoinPose.kt`) and a `CoinCamera` looking down at the table (80° for the classic coin, 62° for the physics coin, a three-quarter view).
    - The visible face is drawn through an affine transform onto the tilted ellipse.
    - The edge is 120 lit strips with alternating reeding, drawn only where they face the viewer. It is 0.14 of the radius thick.
  - **Lighting:** a fixed light gives the face a brightness and a specular spot. The spot slides across the face as the coin turns, and the edge is shaded per strip.
  - **The tails face is mirrored so it reads upright after a flip** (medal alignment), rather than showing upside down.
  - **Both faces are gold.** The old coin had a gold heads side and a silver tails side; one metal looks like a real coin, and the two designs are distinct enough on their own.
  - **Designs** are vector paths in a radius-100 space, embossed with a shadow offset and a catch-light:
    - heads: a crowned bust facing left, with eye, ear and hair lines
    - tails: a quartered shield with two stars and two roundels, inside a laurel wreath with a knot
    - both: a raised rim with 96 radial reeding marks, a bead circle, and the legend **incised** round the top (it reads better than raised lettering)
  - The artwork and the projection were checked by porting them to an HTML canvas in the browser, at several flip and wobble angles. They have not been seen on a device yet.
- **Stats (`CoinStats`, pure and tested) are persisted in Prefs** (`coin_stats`, `cointoss_stats`), where before they were only saved across rotation.
  - The panel shows counts with percentages, the streak line, and a 10-result strip (newest on the right; heads chips are filled, tails chips outlined).
  - The streak is stored on its own, so it can run longer than the strip. The reset button clears everything.
  - The classic coin comes back resting on the last result's face.
- **Classic coin:**
  - The landing is a 300 ms squash on the bottom edge then a small bob, with `clink(0.75)` and a heavy haptic.
  - The shadow moves away and softens as the coin rises.
  - In landscape the coin sits beside the controls.
- **Physics coin, `CoinPhysics`:**
  - Units are coin radii and seconds, stepped in **fixed 1/240 s sub-steps**, so the result doesn't depend on frame rate.
  - Throw range: gravity 44 r/s²; launch speed 11.5–27 r/s (peak height about 2.5–9 radii); spin 24–57 rad/s. The throw starts one radius above the table.
  - A yaw spin about the vertical is added for looks. It can't change which face is up.
  - The side walls come from the stage width.
  - Contact uses the true lowest point of the tilted disc, including its thickness.
  - Bouncing: restitution is 0.3, and the spin keeps 40 % plus a ±4 rad/s kick. The coin starts settling on the third bounce, or earlier once a rebound drops below 1.5 r/s, so there are **1–3 bounces** (tested).
  - **The face is decided when settling starts** (the sign of cos θ). Settling continues the flip smoothly:
    - a fall of up to 0.16 s if the coin is more than 20° from flat
    - then a wobble whose tilt decays over 0.8–1.2 s, while its precession speeds up as the coin leans lower
    - a rattle tick on every half-turn of the wobble, so the ticks come faster and softer (tested)
  - **Fairness needed one change: ±6 rad/s of absolute spin noise on top of the planned ±5 %.**
    With only ±5 %, a weak throw turns over just a few times and lands almost the same way every time. The weakest tenth of throws landed heads only 33 % of the time, and the overall share was 48.5 % over 100,000 throws.
    With the noise the overall share is 50.2 % over 100,000 throws, and the fairness test (10,000 throws) gives 50.24 %.
    The weakest tenth still lands on its starting face about 54 % of the time, which is a mild version of real coins' same-side bias.
- **Physics coin, screen:**
  - Swipe speeds from 350 to 4,000 dp/s map to power 0–1, and shakes from 12 to 40 m/s² map to the same scale. A horizontal swipe gives sideways drift.
  - A slow or downward swipe shows "Swipe upwards to throw". **A tap does a lazy flip.** A **Toss** button (medium power) and a TalkBack action are there for accessibility.
  - After each throw it shows "Turned over N times".
  - Bounce haptics are scaled by impact (tick / click / heavy). **New `ToneSynth.rattle()`** plays the short metallic ticks of the spin-down.
  - A throw in the air is not saved across rotation or leaving the screen. The result is only recorded once the coin settles.
- **Connect Four:**
  - AI details:
    - Win scores are 1,000,000 − moves, so it takes the fastest win and puts off a loss.
    - Windows of four score 1 / 4 / 20 for one / two / three discs, and the opponent's threes count slightly more.
    - The centre column adds +3 per disc.
    - Root ties go to the more central column.
    - Hard (8 plies) takes about 12 ms on a desktop JVM for an early position (there's a test with a 1.5 s ceiling). It beats random play in every test game.
  - **Against the computer, the first player alternates each game**, because the first player has an advantage. With 2 players, red always starts.
  - The mode is remembered (`connect4_mode`). **Tallies are kept per mode** (`connect4_tally_<mode>`): you/CPU or red/yellow, plus draws. They can be reset from the overflow menu.
  - Extras:
    - press to preview a ghost disc, slide to change column, release to drop
    - **Undo** (against the computer it goes back to your turn; it's off once the game is over)
    - the drop is timed by gravity, with two small bounces and a haptic on impact
    - the winning four pulse, with a line through them, and the other discs dim
    - one TalkBack target per column ("Column 3, 4 free")
    - "Thinking…" shows for at least 450 ms
  - Discs make no sound when they drop, only haptics.
- **Tests:** two new files: `CoinTest.kt` (`CoinStatsTest` 3, `CoinPhysicsTest` 6) and `ConnectFourTest.kt` (10).
  The full suite is 119 tests, all passing. Lint reports nothing in the new files.
- **Not yet checked on a device:** no phone was attached this session. Things to check by hand:
  - swipe feel and the power range on the physics coin, and whether a hard throw stays on screen on a small phone
  - edge and highlight rendering, and frame rate while the coin flies (the edge is up to about 60 draw calls a frame)
  - legend text sharpness (it's drawn on a path under a scaled matrix)
  - rattle loudness
  - Hard Connect Four think time on the phone

---

## Wrap-up

- Registry icons (material-icons-extended):

  | Tile | Icon |
  |---|---|
  | Coin toss | `Toll` |
  | Flip a coin (classic) | `Contrast` (was `Toll`; changed in Phase 4) |
  | Compass | `Explore` |
  | Sound meter | `VolumeUp` (AutoMirrored) |
  | Steps | `DirectionsWalk` (AutoMirrored) |
  | Teams | `Groups` |
  | 8-ball | `QuestionMark` |
  | Units | `SwapHoriz` |
  | Dates | `CalendarMonth` |
  | Reaction | `Bolt` |
  | Simon | `MusicNote` |
  | Lights Out | `Lightbulb` |
  | Connect Four | `Circle` |
  | Hangman | `AcUnit` |

- Unit tests for all pure logic: `splitTeams`, `LightsOutBoard`, `Units`, date functions, `HangmanState`, `StepMath`, `CoinPhysics` fairness, Connect Four AI. Split `LogicTest.kt` into per-feature test files.
- Device checks: compass, shake, sound meter, step counter over midnight and across a reboot, and swipe feel on the physics coin. The emulator can fake orientation and shake, but not steps or real audio levels.
- README: update the tools and games lists, move shipped items off the roadmap, and document categories.
- Estimated size: about 4,000–5,000 new lines.

### Wrap-up deviations (as built)

- **Registry icons were already done phase by phase.** Every icon in the table above is in `Tools.kt`, and the only change is the classic coin (see Phase 4).
- **Unit tests:** each phase added its own per-feature test file, so the only work left was **splitting `LogicTest.kt`** into `TunerTest.kt` (the pitch detector), `Game2048Test.kt` and `TicTacToeTest.kt`, and deleting it.
  There are now 16 test files, one per feature plus `CommonTest.kt` for the shared helpers. The suite is still 119 tests, all passing.
- **README:**
  - the tools and games lists are grouped by category
  - it has sections on shake to decide, the home screen and categories, and permissions
  - it has the JDK note for command-line builds, the test folder layout, and a note that each feature's logic lives in a pure file beside its screen
  - Unit converter, Sound level meter and Compass come off the roadmap
- **New debug-only launch extra.** `adb shell am start -n com.toolbox.app/.MainActivity --es route <route>` opens any tile directly, e.g. `--es route cointoss`.
  It only works when the app is debuggable, only accepts routes in `Tool`, and is handled once, so rotating doesn't reopen it.
  It's there because the test phone rejects adb input, so screens can't be reached by tapping. It isn't in the plan.
- **Device checks are still to do; they need a person.** The latest debug APK is installed on the phone.
  Only screenshots could be scripted, and the phone locked straight after the install, so none were taken.
  Shaking, walking, midnight, reboots, swipes and real sound levels all need someone holding the phone. The full list, gathered from each phase:
  - **Shake** (Phase 1): sensitivity on every shake screen, and that a hard shake throws the physics coin higher than a light one
  - **8-ball** (Phase 1): the longest answers fit inside the triangle
  - **Reaction time** (Phase 1): the timing feels right
  - **Simon** (Phase 2): tones and speed, and that a finger resting on the last pad doesn't stay lit
  - **Hangman** (Phase 2): snowman proportions and melt stages on a small screen
  - **Unit converter** (Phase 2): dropdown width with "Fluid ounces (UK)"
  - **Date calculator** (Phase 2): the Days/Weeks/Months/Years row at 360 dp wide
  - **Compass** (Phase 3): direction against a known bearing, flat and upright, in portrait and landscape; how often the calibration banner shows
  - **Sound meter** (Phase 3): readings against a reference, and the `UNPROCESSED` level
  - **Step counter** (Phase 3): over midnight and after a reboot, and whether `StepSnapshotJob` gets readings while the app is closed
  - **Coins** (Phase 4): swipe feel and power range; whether a hard throw stays on screen; edge, highlight and legend rendering; smoothness in flight; rattle loudness
  - **Connect Four** (Phase 4): how long Hard takes to think on the phone
