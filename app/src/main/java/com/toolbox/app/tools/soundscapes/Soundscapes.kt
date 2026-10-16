package com.toolbox.app.tools.soundscapes

import android.content.Context
import android.os.SystemClock
import com.toolbox.app.ui.common.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Status { Stopped, Playing, Paused }

/** What's playing, and the sleep timer, which runs down only while playing and starts again in full after Stop. */
data class Playback(val status: Status = Status.Stopped, val sleep: SleepTimer = SleepTimer()) {
    fun to(next: Status, now: Long): Playback = copy(
        status = next,
        sleep = when (next) {
            Status.Playing -> sleep.run(now)
            Status.Paused -> sleep.hold(now)
            Status.Stopped -> sleep.reset()
        },
    )
}

/**
 * What the screen, [SoundscapeService] and the audio thread share, one per process: the mix, saved in [Prefs] (so it's
 * backed up), and what's playing. The screen changes the mix and the sleep timer; only the service changes the status.
 */
internal object Soundscapes {
    private const val KEY_MIX = "soundscapes_mix"
    private const val KEY_SLEEP = "soundscapes_sleep"

    private val mixState = MutableStateFlow(Mix.Default)
    private val playbackState = MutableStateFlow(Playback())
    @Volatile private var loaded = false

    val mix: StateFlow<Mix> = mixState.asStateFlow()
    val playback: StateFlow<Playback> = playbackState.asStateFlow()

    /** Reads the saved mix and sleep timer, the first time only. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = Prefs(context.applicationContext)
            mixState.value = Mix.decode(prefs.get(KEY_MIX, ""))
            val minutes = prefs.get(KEY_SLEEP, 0).takeIf { it in SleepTimer.CHOICES } ?: 0
            playbackState.update { it.copy(sleep = SleepTimer(minutes)) }
            loaded = true
        }
    }

    /** Plays [mix] at once. It's saved with [save], once a slider is let go, rather than on every step of a drag. */
    fun setMix(mix: Mix) {
        mixState.value = mix
    }

    fun save(context: Context) = Prefs(context.applicationContext).put(KEY_MIX, mixState.value.encode())

    fun setSleep(context: Context, minutes: Int) {
        Prefs(context.applicationContext).put(KEY_SLEEP, minutes)
        playbackState.update { it.copy(sleep = it.sleep.set(minutes, now())) }
    }

    fun setStatus(status: Status) = playbackState.update { it.to(status, now()) }

    fun now() = SystemClock.elapsedRealtime()
}
