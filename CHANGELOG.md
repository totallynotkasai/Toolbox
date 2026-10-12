# Changelog

## 1.1.0

A big round of fixes and polish after a full review of the app.

**Before installing:** 1.0.0 was signed with a development key, and 1.1.0 uses the project's release key. Android won't install one over the other, so uninstall 1.0.0 first. Its scores, favourites and settings are lost. Later versions update in place.

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
