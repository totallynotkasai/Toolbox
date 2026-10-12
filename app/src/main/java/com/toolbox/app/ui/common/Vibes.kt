package com.toolbox.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.pow

/**
 * Every vibration in the app: the fidget toys' detents and rumbles, and (through [Haptics]) the taps in tools and games.
 *
 * These go out as media/game vibrations rather than View.performHapticFeedback's "touch" feedback. Many phones ship
 * with touch haptics switched off (Xiaomi does), which silently drops touch vibrations, but media ones still play.
 * One app-wide switch ([rememberVibrationEnabled], in the home screen's Settings sheet) turns them all off.
 *
 * All strengths are 0..1. Calls are cheap and safe from the main thread; on phones without amplitude control
 * the strength is ignored and short pulses stand in.
 */
class Vibes(context: Context, private val clock: () -> Long = SystemClock::uptimeMillis) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    private val canScale = vibrator?.hasAmplitudeControl() == true
    private val gate = BuzzGate()

    @Volatile var enabled = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) stop()
        }

    /** A crisp, very short tap: detents, notches, zip teeth. */
    fun tick(strength: Float = 0.5f) = pulse(8, strength, floor = 60, ceil = 200)

    /** A firmer click: switches, buttons, a bubble popping. */
    fun click(strength: Float = 0.7f) = pulse(16, strength, floor = 90, ceil = 255)

    /** A dull, longer knock: impacts, a heavy switch landing. */
    fun thud(strength: Float = 0.7f) = pulse(40, strength, floor = 70, ceil = 255)

    /** A free-form one-shot: [timings] in ms alternate off/on (starting with an off delay), [strengths] per segment. */
    fun pattern(timings: LongArray, strengths: FloatArray, repeat: Int = -1) {
        val v = vibrator ?: return
        if (!enabled) return
        val effect = if (canScale) {
            VibrationEffect.createWaveform(timings, IntArray(strengths.size) { amp(strengths[it], 0, 255) }, repeat)
        } else {
            VibrationEffect.createWaveform(timings, repeat)
        }
        v.send(effect)
        gate.pulse(clock(), if (repeat >= 0) Long.MAX_VALUE else timings.sum())
    }

    /**
     * A continuous buzz at [level] (0 stops it), for things that rumble: a spinner, a purring cat, pouring grains.
     * Call it every frame; it only re-arms the motor every ~90 ms or when the level moves noticeably, and waits
     * for a one-shot ([tick], [click], [thud], [pattern]) to finish instead of cutting it off.
     * Without amplitude control it becomes light ticks that speed up with [level].
     */
    fun buzz(level: Float) {
        val v = vibrator ?: return
        if (!enabled || level < 0.04f) { stopBuzz(); return }
        val now = clock()
        if (canScale) {
            val a = amp(level.pow(1.3f), floor = 35, ceil = 190)
            if (gate.arm(now, a, gapMs = 90, minStep = 8)) v.send(VibrationEffect.createOneShot(140, a))
        } else {
            if (gate.arm(now, 1, gapMs = (420 - 340 * level).toLong(), minStep = Int.MAX_VALUE)) {
                v.send(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    fun stopBuzz() {
        if (gate.stopBuzz()) vibrator?.cancel()
    }

    fun stop() {
        vibrator?.cancel()
        gate.reset()
    }

    private fun pulse(ms: Long, strength: Float, floor: Int, ceil: Int) {
        val v = vibrator ?: return
        if (!enabled || strength <= 0f) return
        val a = if (canScale) amp(strength, floor, ceil) else VibrationEffect.DEFAULT_AMPLITUDE
        v.send(VibrationEffect.createOneShot(ms, a))
        gate.pulse(clock(), ms)
    }

    /** Linear motors barely register below ~15% amplitude, so strengths map onto [floor]..[ceil]. */
    private fun amp(strength: Float, floor: Int, ceil: Int): Int =
        if (strength <= 0f) 0 else (floor + (ceil - floor) * strength.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)

    /** Files every vibration as media (API 33+) or game audio, so it isn't dropped with touch feedback. */
    private fun Vibrator.send(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            @Suppress("DEPRECATION")
            vibrate(effect, gameAudio)
        }
    }

    private val gameAudio = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    companion object {
        /** The app-wide vibration switch (default on). The key predates the switch covering more than the toys. */
        const val PREF_KEY = "toy_vibration"
    }
}

/**
 * When [Vibes.buzz] may re-arm the motor. Every vibration replaces the one before it, so a buzz sent mid-pulse
 * would cut the pulse off: a one-shot holds the buzz back until it has played out. Times are in ms on one clock.
 */
internal class BuzzGate {
    private var holdUntil = Long.MIN_VALUE
    private var lastMs = 0L
    private var amp = 0 // amplitude of the running buzz; 0 when none

    /** A one-shot lasting [lengthMs] started at [nowMs], replacing any running buzz. */
    fun pulse(nowMs: Long, lengthMs: Long) {
        holdUntil = if (lengthMs == Long.MAX_VALUE) Long.MAX_VALUE else nowMs + lengthMs
        amp = 0
    }

    /**
     * Whether a buzz at amplitude [a] should be sent now. It waits for a one-shot to finish, and while a buzz is
     * already running it is re-sent only after [gapMs] or once the amplitude moves by [minStep].
     */
    fun arm(nowMs: Long, a: Int, gapMs: Long, minStep: Int): Boolean {
        if (nowMs < holdUntil) return false
        if (amp != 0 && nowMs - lastMs < gapMs && abs(a - amp) < minStep) return false
        amp = a
        lastMs = nowMs
        return true
    }

    /** Ends the buzz; true if one was running and the motor needs cancelling. A one-shot is left to finish. */
    fun stopBuzz(): Boolean {
        if (amp == 0) return false
        amp = 0
        return true
    }

    /** Everything was cancelled. */
    fun reset() {
        holdUntil = Long.MIN_VALUE
        amp = 0
    }
}

/** The app-wide vibration switch (default on), shared by the Settings sheet and every [Vibes]. */
@Composable
fun rememberVibrationEnabled(): MutableState<Boolean> = rememberPref(Vibes.PREF_KEY, true)

/** A [Vibes] that follows the app-wide switch and stops the motor when the app or the screen goes away. */
@Composable
fun rememberVibes(): Vibes {
    val context = LocalContext.current
    val vibes = remember(context) { Vibes(context.applicationContext) }
    vibes.enabled = rememberVibrationEnabled().value
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(vibes, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vibes.stop() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vibes.stop()
        }
    }
    return vibes
}

/** Three fixed taps for tools and games, so they don't each pick strengths. Backed by [Vibes]. */
class Haptics(private val vibes: Vibes) {
    /** A light tap: a key press, a step, a tick of a counter. */
    fun tick() = vibes.tick(0.4f)

    /** A firm tap: a button, a move, a hit. */
    fun click() = vibes.click(0.55f)

    /** A heavy knock: a result, a win, a mistake. */
    fun heavy() = vibes.thud(0.8f)
}

@Composable
fun rememberHaptics(): Haptics {
    val vibes = rememberVibes()
    return remember(vibes) { Haptics(vibes) }
}

/**
 * Bookkeeping a dragged toy shares between its gesture and its frame loop. [space] keeps detent taps at least
 * [minGapMs] apart, so a fast turn doesn't run them together into a mush, and [fast] says the frame loop's buzz has
 * taken over from separate taps.
 */
class Feel(private val minGapMs: Long = 25L, private val clock: () -> Long = SystemClock::uptimeMillis) {
    var fast = false
    private var lastMs = Long.MIN_VALUE / 2

    /** Runs [play] (a tap) unless one went off less than [minGapMs] ago. */
    fun space(play: () -> Unit) {
        val now = clock()
        if (now - lastMs >= minGapMs) {
            play()
            lastMs = now
        }
    }

    /** Counts a vibration played some other way, so the next [space] waits for it. */
    fun mark() { lastMs = clock() }
}
