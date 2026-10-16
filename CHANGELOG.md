# Changelog

## 1.3.0

Four new tools, for 44 in all, and a redrawn Purring Cat and Zip.

It installs over 1.2.0 and keeps your scores, favourites and settings. If you still have 1.0.0, see the note under 1.2.0.

### New

- **Block Drop** (Solo Games), a falling-blocks puzzle. Drag sideways to move a piece, tap to turn it, drag down to drop it faster, or flick down to drop it at once. The next piece shows beside the well, a ghost shows where this one will land, and the game speeds up every 10 lines. Your best score is kept, and it pauses when you leave. Searching "tetris" finds it.
- **Charades** (Duo Games), the guessing game with the phone on your forehead. Pick one of eight decks, from Animals to Characters, or make your own, and a round of 30, 60 or 90 seconds. Hold the phone up sideways and it counts in. Tip the screen down when you guess the word, or up to pass, or tap the right or left half instead. At the end you see what you got and what you passed. Cards don't come round again until you've seen the whole deck, even on another day. Searching "heads up" finds it.
- **Infinity Cube** (Fidget Toys), eight little cubes hinged together that fold open, roll over and close into a cube again, endlessly. Every other time, it closes inside out. Drag any part and it follows your finger, then snaps into place with a click. Two fingers turn it round to look at it from another side.
- **Soundscapes** (Calm). Rain, waves, wind, fire, and white, pink and brown noise, each with its own switch and volume, made on the phone rather than played from recordings. It keeps playing with the screen off or the app closed, with play, pause and stop in the notifications, on the lock screen and from headphones, and it pauses for calls. A sleep timer of 15 to 90 minutes fades it out and stops it. Your mix is remembered.

### Changed

- **Purring Cat:** a new, cuter cat, with a big round head, big shiny eyes and rosy cheeks. It still purrs when you stroke it from head to tail, and still grumbles the other way.
- **Zip:** teeth that interlock like a real zip's, and a new slider and pull tab.

### Permissions

- **Playing in the background.** Soundscapes plays through a media service, as music apps do, so it carries on with the screen off. Android grants this when the app is installed, so nothing is asked. Its media controls show even if Toolbox's notifications are turned off.

### Behind the scenes

- About 495 unit tests.
- Your own Charades decks, the cards each deck has played, and the Soundscapes mix and sleep timer go into your Android backup like the rest.

## 1.2.0

New features for eight tools. It also includes everything listed under 1.1.0, which wasn't released on its own.

**Before installing:** 1.0.0 was signed with a development key, and 1.2.0 uses the project's release key. Android won't install one over the other, so uninstall 1.0.0 first. Its scores, favourites and settings are lost. Later versions update in place.

### New

- **The Stopwatch keeps running** after you leave the screen, close the app or restart the phone. While it runs, a notification shows the time ticking, with Pause and Lap buttons, and Resume and Reset once paused. The main button now says Start, Pause and Resume. Laps keep their numbers past 999.
- **Calculator memory and history.** An MC / MR / M+ / M− row sits above the keys (under the display when the phone is sideways), and an "M" on the display shows memory holds a value. The History button lists your last 50 sums: tap one to carry on from its answer, or the pencil to edit the sum. Clear has Undo. Memory and history are kept when the app closes.
- **Compass true north.** Turn on True north and the compass corrects for the difference between magnetic and true north where you are. It asks once for your approximate location, works offline, and keeps only the correction, never the location. If you'd rather not share it, type the correction in yourself.
- **Cooking units** in the Unit Converter: teaspoons, tablespoons, cups (US, imperial and metric), pints, fluid ounces, grams and pounds, with cups to grams for 12 ingredients from flour to honey. Oven temperatures convert between °C, fan °C, °F and gas mark, using the UK recipe chart.
- **Result history** on the Dice, Spinner and Random Number. A bar under the result shows the latest ones, and tapping it opens the last 20 with Clear and Undo. The Coin uses the same bar and list, and now keeps 20 results too. Clearing its list keeps the heads and tails counts.
- **The Team Splitter remembers its last split.** It comes back when you reopen it, until you change the names or reshuffle.

### Changed

- **Metronome:** 6/8 time (six clicks a bar, strong on 1 and lighter on 4), and subdivisions of 2, 3 or 4 clicks per beat, quieter than the beat. Tempo, time signature, accent and subdivision are remembered. Beats stay exactly in time at every tempo.
- **Unit Converter:** each picker groups its units under headings in Cooking, unit names no longer repeat their symbol, and the category row opens scrolled to the chosen category.

### Permissions

- **Approximate location**, only if you turn on True north on the Compass. The location is used once a day to work out the correction and isn't kept.
- **Notifications** are now also asked for the first time the Stopwatch starts, so it can show its time and buttons outside the app. Without them it still keeps running.
- **Starting after a reboot** now also brings back a running or paused stopwatch's notification.

### Behind the scenes

- About 430 unit tests.
- The stopwatch isn't copied into Android backups, because its time only makes sense on the phone that started it. Calculator memory and history, result history and the other new settings are backed up like the rest.

## 1.1.0

Not released on its own. Its changes are all in 1.2.0.

A big round of fixes and polish after a full review of the app.

### New

- **Coin and Dice.** Flip a Coin and Coin Toss are now one **Coin**, and Roll a Die and Dice Toss are one **Dice**. Each has a Quick / Throw switch that's remembered. Shake works in both modes, and the coin counts from both old tools are added together. 42 tools became 40.
- **A Calm category**, holding Meditate.
- **A Settings sheet** behind the gear on the home screen, with one Vibration switch and one Shake to decide switch for the whole app. They replace the switches that were spread over 18 screens.
- **The Timer is a real alarm.** It rings on time with the phone asleep or the app closed, plays the phone's alarm sound at alarm volume until you stop it, and shows a countdown notification with Stop and +1 min.
- **TalkBack and large text.** Every screen works with TalkBack and at 200% font size. The turn-based games can be played with TalkBack: each square, column or line is its own item, and the computer's moves are read out. The toys have TalkBack actions too.
- **New Fidget Spinner art**, a three-lobed glossy spinner with a motion blur at speed. A finger on it brakes it.
- **Redrawn icons** with one line weight, and new ones for Coin, Dice, Simon and Meditate.

### Changed

- **Fairer computer opponents.** Air Hockey and Connect Four were close to unbeatable and are now winnable. In Battleships, the computer sometimes loses track of a ship it has hit. Every duo game keeps a saved tally per mode, and in Tic-Tac-Toe who starts alternates.
- **Shared game layout.** The board games have the same mode chips, scoreboard, turn line and game-over card, with New game in the top bar. Starting a new game mid-game asks first.
- **Games pause when you leave.** Snake, Block Breaker, Air Hockey, Stack and Meditate pause, Simon replays its sequence when you come back, and a game survives the app being closed in the background.
- **The app stays upright** (portrait), except the Calculator, which turns with the phone.
- **Vibration works on more phones**, including those with touch vibration turned off.
- **Calculator:** answers to 12 digits, phone-style % (200 + 20% = 240), and rounding half up.
- **Sound Meter:** calibration per microphone with a wider range, and readings labelled "approx.".
- **Bubble Level:** "Set as level" and labelled axes.
- **Tally Counter:** Undo after Reset, and a bigger minus button.
- **Spinner:** a flick always turns it at least twice, so it can't be aimed. Removing a result lasts for the session only.
- **Lights Out** is scored against par, and **Snake** can be won by filling the board.
- **Light mode** uses darker yellow, green and red where they're drawn as icons, marks or text, so they're easier to see.
- **Home screen:** titles wrap at large font sizes, the search key works, and Back clears a search or filter before leaving.

### Fixed

- Sounds that carried on after leaving the app (the spinner's whirr, a Simon tone burst on returning), and the Tuner keeping the microphone in the background.
- The Step Counter crediting missed days to today, and losing count after a reboot. Step history is no longer copied into backups.
- A blank screen after tapping Back twice quickly, two tools opening from one double tap, and dark mode flashing between screens.
- Random Number's Generate button staying greyed out after rotating, and the Unit Converter reading "1,5" as 15.
- An Air Hockey puck stuck where nobody could reach it, Tic-Tac-Toe taking taps on the computer's turn, and a peek at the next Memory deal.
- Many smaller game, toy and layout bugs.

### Behind the scenes

- Updated to the latest Android tools and libraries, targeting Android 16.
- Unit tests and lint run on every pull request, with about 370 tests.
- Release builds are signed with a key kept out of the repository.

## 1.0.0

First release: 42 tools, fidget toys and games.
