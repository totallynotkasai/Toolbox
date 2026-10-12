package com.toolbox.app.ui.common

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/**
 * Small typed settings store (String, Int, Long, Float, Boolean) for things like calibration offsets,
 * last-used units and the vibration and shake switches. Best scores stay in [ScoreStore].
 */
class Prefs(context: Context) {
    internal val prefs: SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, default: T): T = try {
        when (default) {
            is String -> prefs.getString(key, default) ?: default
            is Int -> prefs.getInt(key, default)
            is Long -> prefs.getLong(key, default)
            is Float -> prefs.getFloat(key, default)
            is Boolean -> prefs.getBoolean(key, default)
            else -> throw IllegalArgumentException("Unsupported pref type for '$key'")
        } as T
    } catch (_: ClassCastException) {
        default // Stored under an older type; fall back rather than crash.
    }

    fun <T> put(key: String, value: T) {
        prefs.edit {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                else -> throw IllegalArgumentException("Unsupported pref type for '$key'")
            }
        }
    }

    companion object {
        /** Shared by every screen with shake-to-decide. */
        const val SHAKE_ENABLED = "shake_enabled"
    }
}

/**
 * A [MutableState] backed by [Prefs]: `var units by rememberPref("units_length", "m")`.
 * Writes are saved immediately, and other states on the same key update too (e.g. a toggle and its reader).
 */
@Composable
fun <T> rememberPref(key: String, default: T): MutableState<T> {
    val context = LocalContext.current
    val state = remember(context, key) { PrefState(Prefs(context.applicationContext), key, default) }
    DisposableEffect(state) {
        state.startListening()
        onDispose { state.stopListening() }
    }
    return state
}

private class PrefState<T>(private val store: Prefs, private val key: String, private val default: T) : MutableState<T> {
    private val backing = mutableStateOf(store.get(key, default))

    // SharedPreferences only holds listeners weakly, so this field keeps it alive.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
        if (changed == null || changed == key) backing.value = store.get(key, default)
    }

    override var value: T
        get() = backing.value
        set(v) {
            if (v == backing.value) return
            backing.value = v
            store.put(key, v)
        }

    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }

    fun startListening() {
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        backing.value = store.get(key, default) // Catch writes made while not listening.
    }

    fun stopListening() = store.prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
