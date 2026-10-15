package com.toolbox.app.games.charades

import android.content.res.Resources
import androidx.annotation.RawRes
import com.toolbox.app.R

/**
 * The decks that come with the game, one card per line in `res/raw/charades_<id>.txt`. All family-friendly, in UK
 * English. The characters are made up (fairy tales, books and cartoons), so they don't date like celebrities.
 */
enum class BuiltInDeck(val id: String, val title: String, @param:RawRes val res: Int) {
    Animals("animals", "Animals", R.raw.charades_animals),
    Food("food", "Food and drink", R.raw.charades_food),
    Jobs("jobs", "Jobs", R.raw.charades_jobs),
    Act("act", "Act it out", R.raw.charades_act),
    Sports("sports", "Sports", R.raw.charades_sports),
    Places("places", "Places", R.raw.charades_places),
    Home("home", "Things at home", R.raw.charades_home),
    Characters("characters", "Characters", R.raw.charades_characters);

    fun load(resources: Resources): Deck =
        Deck(id, title, parseCards(resources.openRawResource(res).bufferedReader().use { it.readText() }), builtIn = true)
}
