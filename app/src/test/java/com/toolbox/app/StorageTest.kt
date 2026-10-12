package com.toolbox.app

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.toolbox.app.ui.common.Prefs
import com.toolbox.app.ui.common.Tally
import com.toolbox.app.ui.common.TallyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whole-number reads name their type: assertEquals(long, long) would otherwise turn `get(key, 0)` into a Long read. */
class PrefsTest {
    private val prefs = Prefs(FakePrefs())

    @Test fun everyTypeRoundTrips() {
        prefs.put("s", "metres")
        prefs.put("i", 7)
        prefs.put("l", 1L shl 40)
        prefs.put("f", 1.5f)
        prefs.put("b", true)
        assertEquals("metres", prefs.get("s", ""))
        assertEquals(7, prefs.get<Int>("i", 0))
        assertEquals(1L shl 40, prefs.get<Long>("l", 0L))
        assertEquals(1.5f, prefs.get("f", 0f))
        assertEquals(true, prefs.get("b", false))
    }

    @Test fun missingKeysReadAsTheDefault() {
        assertEquals("m", prefs.get("units_length", "m"))
        assertEquals(42, prefs.get<Int>("count", 42))
        assertFalse(prefs.contains("count"))
    }

    @Test fun aValueSavedUnderAnOlderTypeReadsAsTheDefault() {
        // A setting that changed type between versions mustn't crash the screen that reads it.
        prefs.put("goal", "10000")
        assertEquals(8000, prefs.get<Int>("goal", 8000))
        prefs.put("offset", 3)
        assertEquals(1.5f, prefs.get("offset", 1.5f))
        assertEquals(false, prefs.get("offset", false))
        assertTrue(prefs.contains("goal"))
    }

    @Test fun removeForgetsAKey() {
        prefs.put("tally", 5)
        prefs.remove("tally")
        assertEquals(0, prefs.get<Int>("tally", 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedTypesAreRefused() {
        prefs.put("d", 1.0)
    }
}

class TallyTest {
    @Test fun decodeFallsBackToZeroes() {
        assertEquals(Tally(), Tally.decode(""))
        assertEquals(Tally(), Tally.decode("1,2"))
        assertEquals(Tally(), Tally.decode("1,2,3,4"))
        assertEquals(Tally(), Tally.decode("a,b,c"))
        assertEquals(Tally(4, 0, 2), Tally.decode(Tally(4, 0, 2).encode()))
    }

    @Test fun summaryNamesBothSides() {
        assertEquals("Games: You 3 · CPU 2 · Drawn 1", Tally(3, 2, 1).summary("Games", "You", "CPU"))
        assertEquals("Wins: P1 1 · P2 0", Tally(1, 0, 5).summary("Wins", "P1", "P2", withDraws = false))
    }

    @Test fun aResultAfterAResetCountsFromZero() {
        // A result recorded from a long-running effect reads what's saved now, so it can't undo a reset.
        val stored = mutableStateOf(Tally(5, 5, 5).encode())
        val tally = TallyState(stored)
        tally.reset()
        tally.record(2)
        assertEquals(Tally(0, 1, 0), tally.value)
        stored.value = Tally(9, 0, 0).encode() // changed elsewhere, e.g. another screen on the same key
        tally.record(1)
        assertEquals(Tally(10, 0, 0), tally.value)
    }
}

/** An in-memory [SharedPreferences] that, like the real one, throws [ClassCastException] for a value of another type. */
private class FakePrefs : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    private inline fun <reified T> read(key: String?, default: T): T = if (map.containsKey(key)) map[key] as T else default

    override fun getAll(): Map<String, *> = map.toMap()
    override fun getString(key: String?, defValue: String?): String? = read(key, defValue)
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = read(key, defValues)
    override fun getInt(key: String?, defValue: Int): Int = read(key, defValue)
    override fun getLong(key: String?, defValue: Long): Long = read(key, defValue)
    override fun getFloat(key: String?, defValue: Float): Float = read(key, defValue)
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = read(key, defValue)
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) map.clear()
            removals.forEach { map.remove(it) }
            map.putAll(changes)
            return true
        }
        override fun apply() { commit() }
    }
}
