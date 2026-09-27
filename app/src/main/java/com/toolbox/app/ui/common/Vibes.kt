package com.toolbox.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.pow

/**
 * Vibration for the fidget toys, where the buzz *is* the toy.
 *
 * Unlike [Haptics] (View.performHapticFeedback, filed as "touch" feedback), these go out as media/game
 * vibrations. Many phones ship with touch haptics switched off (Xiaomi does), which silently drops touch
 * vibrations, but media ones still play. Each toy has its own on/off switch ([VibesToggleButton]) instead.
 *
 * All strengths are 0..1. Calls are cheap and safe from the main thread; on phones without amplitude control
 * the strength is ignored and short pulses stand in.
 */
class Vibes(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    private val canScale = vibrator?.hasAmplitudeControl() == true

    @Volatile var enabled = true
        set(value) { field = value; if (!value) stop() }

    private var buzzLastMs = 0L
    private var buzzAmp = 0

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
    }

    /**
     * A continuous buzz at [level] (0 stops it), for things that rumble: a spinner, a purring cat, pouring grains.
     * Call it every frame; it only re-arms the motor every ~90 ms or when the level moves noticeably.
     * Without amplitude control it becomes light ticks that speed up with [level].
     */
    fun buzz(level: Float, nowMs: Long) {
        val v = vibrator ?: return
        if (!enabled || level < 0.04f) { stopBuzz(); return }
        if (canScale) {
            val a = amp(level.pow(1.3f), floor = 35, ceil = 190)
            if (nowMs - buzzLastMs < 90 && abs(a - buzzAmp) < 8) return
            v.send(VibrationEffect.createOneShot(140, a))
            buzzAmp = a
        } else {
            if (nowMs - buzzLastMs < (420 - 340 * level).toLong()) return
            v.send(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            buzzAmp = 1
        }
        buzzLastMs = nowMs
    }

    fun stopBuzz() {
        if (buzzAmp == 0) return
        vibrator?.cancel()
        buzzAmp = 0
    }

    fun stop() {
        vibrator?.cancel()
        buzzAmp = 0
    }

    private fun pulse(ms: Long, strength: Float, floor: Int, ceil: Int) {
        val v = vibrator ?: return
        if (!enabled || strength <= 0f) return
        val a = if (canScale) amp(strength, floor, ceil) else VibrationEffect.DEFAULT_AMPLITUDE
        v.send(VibrationEffect.createOneShot(ms, a))
        buzzAmp = 0 // a one-shot replaces any running buzz
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
        /** One switch shared by every fidget toy (default on). */
        const val PREF_KEY = "toy_vibration"
    }
}

/** A [Vibes] that follows the shared on/off pref and stops the motor when the screen goes away. */
@Composable
fun rememberVibes(): Vibes {
    val context = LocalContext.current
    val vibes = remember(context) { Vibes(context.applicationContext) }
    vibes.enabled = rememberPref(Vibes.PREF_KEY, true).value
    DisposableEffect(vibes) { onDispose { vibes.stop() } }
    return vibes
}

/** Top-bar on/off switch for the fidget toys' vibration. Put it in [ToolScaffold]'s `actions`. */
@Composable
fun VibesToggleButton() {
    val context = LocalContext.current
    val on: MutableState<Boolean> = rememberPref(Vibes.PREF_KEY, true)
    IconToggleButton(
        checked = on.value,
        onCheckedChange = {
            on.value = it
            Toast.makeText(context, "Vibration ${if (it) "on" else "off"}", Toast.LENGTH_SHORT).show()
        },
    ) {
        Icon(if (on.value) Icons.Filled.Vibration else Icons.Filled.MobileOff, contentDescription = "Vibration")
    }
}
