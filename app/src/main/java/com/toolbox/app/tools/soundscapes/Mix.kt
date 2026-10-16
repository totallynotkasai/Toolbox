package com.toolbox.app.tools.soundscapes

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

/** The sounds on offer, in mixer order. [label] is the mixer row's name and [title] the full name. */
enum class Sound(val id: String, val label: String, val title: String, val nature: Boolean) {
    Rain("rain", "Rain", "Rain", true),
    Waves("waves", "Waves", "Waves", true),
    Wind("wind", "Wind", "Wind", true),
    Fire("fire", "Fire", "Fire", true),
    White("white", "White", "White noise", false),
    Pink("pink", "Pink", "Pink noise", false),
    Brown("brown", "Brown", "Brown noise", false);

    internal fun generator(rate: Int, seed: Int): Generator = when (this) {
        Rain -> Rain(rate, seed)
        Waves -> Waves(rate, seed)
        Wind -> Wind(rate, seed)
        Fire -> Fire(rate, seed)
        White -> WhiteNoise(rate, seed)
        Pink -> PinkNoise(rate, seed)
        Brown -> BrownNoise(rate, seed)
    }
}

/** One mixer row: whether the sound is on, and its slider (0 to 1). */
data class Channel(val on: Boolean, val volume: Float)

/** Every row of the mixer, and the master volume. Saved as one string with [encode]. */
data class Mix(val channels: Map<Sound, Channel>, val master: Float) {
    operator fun get(sound: Sound): Channel = channels[sound] ?: OFF

    fun with(sound: Sound, channel: Channel) = copy(channels = channels + (sound to channel))

    val anyOn: Boolean get() = Sound.entries.any { get(it).on }

    /** How loud [sound] plays: its slider times the master, each on a curve that suits the ear, or 0 when it's off. */
    fun gain(sound: Sound): Float = get(sound).let { if (it.on) loudness(it.volume) * loudness(master) else 0f }

    /** What's playing, for the notification: "Rain, waves and pink noise". */
    val title: String
        get() {
            val names = Sound.entries.filter { get(it).on }.mapIndexed { i, s -> if (i == 0) s.title else s.title.lowercase() }
            return when (names.size) {
                0 -> "No sounds on"
                1 -> names[0]
                else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
            }
        }

    /** "master=0.8;rain=on,0.7;waves=off,0.5;…" */
    fun encode(): String = buildString {
        append("master=").append(number(master))
        for (sound in Sound.entries) {
            val c = get(sound)
            append(';').append(sound.id).append('=').append(if (c.on) "on" else "off").append(',').append(number(c.volume))
        }
    }

    companion object {
        private val OFF = Channel(false, 0.8f)

        /** The first time: rain on its own. Every slider starts at 0.8, about 4 dB below full. */
        val Default = Mix(Sound.entries.associateWith { if (it == Sound.Rain) OFF.copy(on = true) else OFF }, master = 1f)

        /** A saved mix. Anything missing, unknown or unreadable keeps [Default]'s value. */
        fun decode(stored: String): Mix {
            var mix = Default
            for (part in stored.split(';')) {
                val key = part.substringBefore('=', "").trim()
                val value = part.substringAfter('=', "").trim()
                if (key == "master") {
                    value.toFloatOrNull()?.let { mix = mix.copy(master = it.coerceIn(0f, 1f)) }
                    continue
                }
                val sound = Sound.entries.firstOrNull { it.id == key } ?: continue
                val on = when (value.substringBefore(',')) {
                    "on" -> true
                    "off" -> false
                    else -> continue
                }
                val volume = value.substringAfter(',', "").toFloatOrNull()?.coerceIn(0f, 1f) ?: continue
                mix = mix.with(sound, Channel(on, volume))
            }
            return mix
        }

        /** A slider's position as a gain: squared, which is closer to how loud it sounds (halfway is 12 dB down). */
        fun loudness(slider: Float): Float = slider.coerceIn(0f, 1f).let { it * it }

        private fun number(v: Float) = String.format(Locale.ROOT, "%.3f", v)
    }
}

/**
 * The sleep timer: [minutes] of playing (0 is off), which only run down while sound is playing, so a pause for a phone
 * call doesn't use it up. [leftMs] is what was left when it last stopped running, and [since] when it started running
 * again (elapsedRealtime), or null while it isn't.
 */
data class SleepTimer(val minutes: Int = 0, val leftMs: Long = minutes * MINUTE_MS, val since: Long? = null) {
    val on: Boolean get() = minutes > 0
    val running: Boolean get() = since != null

    /** Time left at [now], or null when the timer is off. */
    fun left(now: Long): Long? = when {
        !on -> null
        since != null -> (leftMs - (now - since)).coerceAtLeast(0)
        else -> leftMs
    }

    /** Starts it running down, as the sound starts playing. */
    fun run(now: Long): SleepTimer = if (running) this else copy(since = now)

    /** Holds it where it is, as the sound pauses. */
    fun hold(now: Long): SleepTimer = if (!running) this else copy(leftMs = left(now) ?: 0, since = null)

    /** Back to the full length, after Stop. */
    fun reset(): SleepTimer = SleepTimer(minutes)

    /** A new length, starting in full and running if this one was. */
    fun set(minutes: Int, now: Long): SleepTimer = SleepTimer(minutes, since = if (running) now else null)

    companion object {
        const val MINUTE_MS = 60_000L
        const val FADE_MS = 30_000L

        /** The lengths on offer, in minutes; 0 is off. */
        val CHOICES = listOf(0, 15, 30, 45, 60, 90)

        /**
         * How loud the timer lets the sound play with [leftMs] to go (null: no timer): full until the last 30 seconds,
         * then a slow fade to silence. Cubed, so the level falls evenly to the ear rather than all at the end.
         */
        fun fade(leftMs: Long?): Float {
            if (leftMs == null) return 1f
            val t = (leftMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
            return t * t * t
        }

        /** "23 min" for the notification, rounding up so it never says 0 while still playing. */
        fun minutesLeft(leftMs: Long): Int = ((leftMs + MINUTE_MS - 1) / MINUTE_MS).toInt()
    }
}

/**
 * Keeps a mixed sample within -1..1. It passes straight through up to 0.6, where every sound sits at its usual level,
 * so normal listening isn't coloured at all, then bends softly towards 1, as a loud mix of several sounds needs. Only
 * those few loud samples pay for a `tanh`.
 */
internal fun softClip(x: Float): Float {
    val size = abs(x)
    if (size <= KNEE) return x
    val bent = KNEE + (1f - KNEE) * tanh((size - KNEE) / (1f - KNEE))
    return if (x < 0f) -bent else bent
}

private const val KNEE = 0.6f

/**
 * Mixes every [Sound] at the gains a [Mix] asks for. Each gain eases towards its new value over a fraction of a second,
 * so moving a slider never crackles and switching a sound on or off fades it. Sounds at 0 aren't worked out at all.
 */
internal class Mixer(private val rate: Int, seed: Int = 1) {
    private val generators = Sound.entries.map { it.generator(rate, seed + it.ordinal * 7919) }
    private val gains = FloatArray(Sound.entries.size)
    private var scratch = FloatArray(0)

    /** The gain [sound] is playing at just now, after easing. */
    fun gain(sound: Sound): Float = gains[sound.ordinal]

    /** Writes [frames] stereo frames into [out], over whatever was there. */
    fun render(out: FloatArray, frames: Int, mix: Mix) {
        out.fill(0f, 0, frames * 2)
        if (scratch.size < frames * 2) scratch = FloatArray(frames * 2)
        val ease = 1f - exp(-frames / (EASE_SECONDS * rate))
        for (sound in Sound.entries) {
            val i = sound.ordinal
            val from = gains[i]
            val target = mix.gain(sound)
            var to = from + (target - from) * ease
            if (abs(target - to) < SETTLED) to = target
            gains[i] = to
            if (from == 0f && to == 0f) continue
            generators[i].render(scratch, frames)
            val step = (to - from) / frames
            for (f in 0 until frames) {
                val g = from + step * (f + 1)
                out[2 * f] += scratch[2 * f] * g
                out[2 * f + 1] += scratch[2 * f + 1] * g
            }
        }
    }

    private companion object {
        const val EASE_SECONDS = 0.25f
        const val SETTLED = 1e-4f
    }
}
