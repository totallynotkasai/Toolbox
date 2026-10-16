package com.toolbox.app.tools.soundscapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// The seven sounds, all synthesised from noise. Each generator writes stereo frames (left and right interleaved) at
// about the same loudness, so the sliders mean the same thing on every row. Everything here runs on the audio thread
// and allocates nothing once built.

/** One of the mixer's sounds, writing [frames] stereo frames into `out` over whatever was there. */
internal abstract class Generator(protected val rate: Int, seed: Int) {
    protected val rng = Rng(seed)

    abstract fun render(out: FloatArray, frames: Int)
}

/** White noise: every frequency equally loud, the left and right sides independent. */
internal class WhiteNoise(rate: Int, seed: Int) : Generator(rate, seed) {
    override fun render(out: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            out[2 * f] = rng.bipolar() * LEVEL
            out[2 * f + 1] = rng.bipolar() * LEVEL
        }
    }

    private companion object {
        const val LEVEL = 0.076f
    }
}

/** Pink noise: 3 dB quieter each octave up, so every octave carries the same power. */
internal class PinkNoise(rate: Int, seed: Int) : Generator(rate, seed) {
    private val left = Pink()
    private val right = Pink()

    override fun render(out: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            out[2 * f] = left.next(rng.bipolar()) * LEVEL
            out[2 * f + 1] = right.next(rng.bipolar()) * LEVEL
        }
    }

    private companion object {
        const val LEVEL = 0.338f
    }
}

/** Brown noise: 6 dB quieter each octave up, a deep rumble. */
internal class BrownNoise(rate: Int, seed: Int) : Generator(rate, seed) {
    private val left = Brown(rate)
    private val right = Brown(rate)

    override fun render(out: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            out[2 * f] = left.next(rng.bipolar()) * LEVEL
            out[2 * f + 1] = right.next(rng.bipolar()) * LEVEL
        }
    }

    private companion object {
        const val LEVEL = 0.683f
    }
}

/**
 * Steady rain: a hiss of pink noise without its lows or its very top, a patter of drops striking things (tiny
 * bursts of noise, a few hundred a second, mostly soft), and now and then a drop falling into water (a short "plink"
 * that rises in pitch, as the bubble it traps shrinks). How hard it rains drifts a little every few seconds.
 */
internal class Rain(rate: Int, seed: Int) : Generator(rate, seed) {
    private val hissNoiseL = Pink()
    private val hissNoiseR = Pink()
    private val hissLowL = Lows(rate, 450f)
    private val hissLowR = Lows(rate, 450f)
    private val hissTopL = OnePole(rate, 7000f)
    private val hissTopR = OnePole(rate, 7000f)
    private val heaviness = Drift(rate, rng, 0.75f, 1.15f, holdMin = 3f, holdMax = 8f, seconds = 2f)
    private val patter = Bursts(rate, rng, voices = 32)
    private val patterTopL = OnePole(rate, 6000f)
    private val patterTopR = OnePole(rate, 6000f)
    private val patterLowL = OnePole(rate, 900f)
    private val patterLowR = OnePole(rate, 900f)
    private val patterTimes = Arrivals(rate, rng)
    private val plinks = Plinks(rate, voices = 8)
    private val plinkTimes = Arrivals(rate, rng)

    override fun render(out: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            val heavy = heaviness.next()
            if (patterTimes.next(PATTER_PER_SECOND * heavy)) {
                val u = rng.unit()
                patter.hit(0.08f + 0.9f * u * u * u * u, rng.range(0.0004f, 0.0022f), rng.unit())
            }
            if (plinkTimes.next(PLINKS_PER_SECOND)) {
                plinks.hit(rng.range(0.1f, 0.35f), rng.range(1300f, 3600f), rng.range(1.25f, 1.6f), rng.range(0.004f, 0.011f), rng.unit())
            }
            patter.next()
            plinks.next()
            val hissL = hissTopL.low(hissLowL.cut(hissNoiseL.next(rng.bipolar())))
            val hissR = hissTopR.low(hissLowR.cut(hissNoiseR.next(rng.bipolar())))
            val patL = patterTopL.low(patter.left) - patterLowL.low(patterTopL.y)
            val patR = patterTopR.low(patter.right) - patterLowR.low(patterTopR.y)
            out[2 * f] = (hissL * HISS * heavy + patL * PATTER + plinks.left) * LEVEL
            out[2 * f + 1] = (hissR * HISS * heavy + patR * PATTER + plinks.right) * LEVEL
        }
    }

    private companion object {
        const val PATTER_PER_SECOND = 700f
        const val PLINKS_PER_SECOND = 3f
        const val HISS = 1.2f
        const val PATTER = 1f
        const val LEVEL = 0.398f
    }
}

/**
 * Waves on a shore, one every 8 to 12 seconds. Each swells (the roar grows louder and brighter as the low-pass on it
 * opens), breaks, and draws back with a fizz of foam that lingers after the crest. Each wave comes in a little to the
 * left or right of the last, and how big it is varies.
 */
internal class Waves(rate: Int, seed: Int) : Generator(rate, seed) {
    private val bodyL = Pink()
    private val bodyR = Pink()
    private val roarL = Svf(rate)
    private val roarR = Svf(rate)
    private val foamLowL = Lows(rate, 1800f)
    private val foamLowR = Lows(rate, 1800f)
    private val fizz = Drift(rate, rng, 0.35f, 1f, holdMin = 0.02f, holdMax = 0.09f, seconds = 0.015f)
    private var length = 1
    private var at = 0
    private var crest = 0.35f
    private var size = 1f
    private var gainL = 1f
    private var gainR = 1f
    private var foam = 0f
    private val foamRise = 1f - exp(-1f / (0.25f * rate))
    private val foamFall = exp(-1f / (1.6f * rate))

    init {
        nextWave()
    }

    private fun nextWave() {
        length = (rate * rng.range(8f, 12f)).toInt()
        at = 0
        crest = rng.range(0.3f, 0.42f)
        size = rng.range(0.7f, 1f)
        val pan = rng.range(0.32f, 0.68f)
        gainL = panLeft(pan)
        gainR = panRight(pan)
    }

    override fun render(out: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            if (at >= length) nextWave()
            val swell = swell(at.toFloat() / length, crest) * size
            if (at % RETUNE == 0) {
                val hz = 220f + 1900f * swell * swell
                roarL.tune(hz, 0.6f)
                roarR.tune(hz * 1.04f, 0.6f)
            }
            at++
            // The foam follows the wave up quickly and fades slowly, so it hisses on as the water draws back.
            foam = if (swell > foam) foam + (swell - foam) * foamRise else foam * foamFall
            val level = FLOOR + (1f - FLOOR) * swell
            val froth = foam * foam * fizz.next()
            roarL.process(bodyL.next(rng.bipolar()))
            roarR.process(bodyR.next(rng.bipolar()))
            val l = roarL.low * level * ROAR + foamLowL.cut(rng.bipolar()) * froth * FOAM
            val r = roarR.low * level * ROAR + foamLowR.cut(rng.bipolar()) * froth * FOAM
            out[2 * f] = l * gainL * LEVEL
            out[2 * f + 1] = r * gainR * LEVEL
        }
    }

    companion object {
        private const val RETUNE = 32
        private const val FLOOR = 0.18f
        private const val ROAR = 1f
        private const val FOAM = 0.15f
        private const val LEVEL = 1.02f

        /** How far a wave has swelled, 0 to 1, at [u] of the way through it, with its crest at [crest]: up smoothly, then a long fall. */
        fun swell(u: Float, crest: Float): Float {
            if (u < crest) {
                val s = sin(0.5f * PI.toFloat() * u / crest)
                return s * s
            }
            val fall = (1f + cos(PI.toFloat() * (u - crest) / (1f - crest))) / 2f
            return fall * sqrt(fall)
        }
    }
}

/**
 * Wind: pink noise through a band-pass whose pitch wanders slowly and rises with each gust, with a low rumble under
 * it and, in the strongest gusts, a faint whistle. The two sides are tuned a little apart, so it sounds wide.
 */
internal class Wind(rate: Int, seed: Int) : Generator(rate, seed) {
    private val gust = Drift(rate, rng, 0f, 1f, holdMin = 0.8f, holdMax = 3.5f, seconds = 1.1f, skew = 1.4f)
    private val pitch = Drift(rate, rng, 0f, 1f, holdMin = 4f, holdMax = 9f, seconds = 3f)
    private val airL = Pink()
    private val airR = Pink()
    private val bandL = Svf(rate)
    private val bandR = Svf(rate)
    private val whistle = Svf(rate)
    private val rumbleL = Brown(rate)
    private val rumbleR = Brown(rate)
    private val rumbleTopL = OnePole(rate, 160f)
    private val rumbleTopR = OnePole(rate, 160f)
    private var tick = 0

    override fun render(out: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            val g = gust.next()
            val p = pitch.next()
            if (tick++ % RETUNE == 0) {
                val hz = (190f + 230f * p) * (1f + 1.3f * g)
                val q = 0.9f + 2.2f * g
                bandL.tune(hz * 0.95f, q)
                bandR.tune(hz * 1.05f, q)
                whistle.tune(hz * 2.4f, 14f)
            }
            val strength = 0.18f + 0.82f * g * sqrt(g)
            val whistled = (g - 0.65f).coerceAtLeast(0f) * WHISTLE
            val rumble = 0.35f + 0.65f * g
            bandL.process(airL.next(rng.bipolar()))
            bandR.process(airR.next(rng.bipolar()))
            whistle.process(rng.bipolar())
            val w = whistle.peak * whistled
            val l = bandL.peak * strength + w + rumbleTopL.low(rumbleL.next(rng.bipolar())) * rumble * RUMBLE
            val r = bandR.peak * strength + w + rumbleTopR.low(rumbleR.next(rng.bipolar())) * rumble * RUMBLE
            out[2 * f] = l * LEVEL
            out[2 * f + 1] = r * LEVEL
        }
    }

    private companion object {
        const val RETUNE = 32
        const val WHISTLE = 1.2f
        const val RUMBLE = 0.3f
        const val LEVEL = 2.36f
    }
}

/**
 * A wood fire: a flickering roar of pink noise between about 90 and 700 Hz (high enough for a phone's speaker to
 * play), a soft hiss of steam, crackles that come in little runs (each a click a fraction of a millisecond long), and
 * now and then a pop, a dull thump that falls in pitch.
 */
internal class Fire(rate: Int, seed: Int) : Generator(rate, seed) {
    private val roarNoiseL = Pink()
    private val roarNoiseR = Pink()
    private val roarTopL = Highs(rate, 700f)
    private val roarBottomL = Lows(rate, 90f)
    private val roarTopR = Highs(rate, 700f)
    private val roarBottomR = Lows(rate, 90f)
    private val flicker = Drift(rate, rng, 0.55f, 1f, holdMin = 0.08f, holdMax = 0.5f, seconds = 0.07f)
    private val steamLowL = Lows(rate, 2500f)
    private val steamLowR = Lows(rate, 2500f)
    private val steam = Drift(rate, rng, 0.3f, 1f, holdMin = 0.5f, holdMax = 2.5f, seconds = 0.4f)
    private val crackles = Bursts(rate, rng, voices = 24)
    private val crackleLowL = OnePole(rate, 1400f)
    private val crackleLowR = OnePole(rate, 1400f)
    private val runs = Arrivals(rate, rng)
    private var runLeft = 0     // crackles still to come in this run
    private var runWait = 0     // frames until the next one
    private var runPan = 0.5f
    private val pops = Plinks(rate, voices = 4)
    private val popTimes = Arrivals(rate, rng)

    override fun render(out: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            if (runs.next(RUNS_PER_SECOND) && runLeft == 0) {
                val u = rng.unit()
                runLeft = 1 + (u * u * 7f).toInt()
                runWait = 0
                runPan = rng.range(0.15f, 0.85f)
            }
            if (runLeft > 0 && --runWait <= 0) {
                val u = rng.unit()
                crackles.hit(0.12f + 0.6f * u * u * u, rng.range(0.00008f, 0.0005f), (runPan + rng.range(-0.12f, 0.12f)).coerceIn(0f, 1f))
                runLeft--
                runWait = (rate * rng.range(0.003f, 0.04f)).toInt()
            }
            if (popTimes.next(POPS_PER_SECOND)) {
                val pan = rng.range(0.2f, 0.8f)
                pops.hit(rng.range(0.15f, 0.35f), rng.range(110f, 260f), rng.range(0.6f, 0.8f), rng.range(0.012f, 0.028f), pan)
                crackles.hit(0.5f, 0.0006f, pan)
            }
            crackles.next()
            pops.next()
            val fl = flicker.next()
            val st = steam.next() * STEAM
            val roarL = roarBottomL.cut(roarTopL.cut(roarNoiseL.next(rng.bipolar()))) * fl * ROAR
            val roarR = roarBottomR.cut(roarTopR.cut(roarNoiseR.next(rng.bipolar()))) * fl * ROAR
            val crackL = crackles.left - crackleLowL.low(crackles.left)
            val crackR = crackles.right - crackleLowR.low(crackles.right)
            out[2 * f] = (roarL + steamLowL.cut(rng.bipolar()) * st + crackL * CRACKLE + pops.left) * LEVEL
            out[2 * f + 1] = (roarR + steamLowR.cut(rng.bipolar()) * st + crackR * CRACKLE + pops.right) * LEVEL
        }
    }

    private companion object {
        const val RUNS_PER_SECOND = 5f
        const val POPS_PER_SECOND = 0.3f
        const val ROAR = 2.6f
        const val STEAM = 0.025f
        const val CRACKLE = 0.9f
        const val LEVEL = 0.496f
    }
}

// Building blocks.

/** xorshift32: quick noise that comes out the same for the same seed, which the tests rely on. */
internal class Rng(seed: Int) {
    private var s = if (seed == 0) 0x2545F491 else seed

    fun next(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }

    /** Uniform in -1..1. */
    fun bipolar(): Float = next() * (1f / 2147483648f)

    /** Uniform in 0..1. */
    fun unit(): Float = (next() ushr 8) * (1f / 16777216f)

    fun range(lo: Float, hi: Float): Float = lo + (hi - lo) * unit()
}

/** Paul Kellet's refined pink-noise filter: white noise in, -3 dB per octave out, to within a fraction of a dB. */
internal class Pink {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    fun next(white: Float): Float {
        b0 = 0.99886f * b0 + white * 0.0555179f
        b1 = 0.99332f * b1 + white * 0.0750759f
        b2 = 0.96900f * b2 + white * 0.1538520f
        b3 = 0.86650f * b3 + white * 0.3104856f
        b4 = 0.55000f * b4 + white * 0.5329522f
        b5 = -0.7616f * b5 - white * 0.0168980f
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
        b6 = white * 0.115926f
        return out * 0.11f
    }
}

/**
 * Brown noise from white: a running sum that leaks back towards zero, which gives -6 dB per octave above about 20 Hz
 * and stops the sum wandering off.
 */
internal class Brown(rate: Int) {
    private val leak = exp(-2f * PI.toFloat() * CORNER / rate)
    private var y = 0f

    fun next(white: Float): Float {
        y = leak * y + white * STEP
        return y
    }

    private companion object {
        const val CORNER = 20f
        const val STEP = 0.025f
    }
}

/** A one-pole low-pass at [hz]; [y] is its latest output. */
internal class OnePole(rate: Int, hz: Float) {
    private val a = 1f - exp(-2f * PI.toFloat() * hz / rate)
    var y = 0f
        private set

    fun low(x: Float): Float {
        y += a * (x - y)
        return y
    }
}

/** Takes away what's below [hz], with two one-pole stages (12 dB per octave). */
internal class Lows(rate: Int, hz: Float) {
    private val first = OnePole(rate, hz)
    private val second = OnePole(rate, hz)

    fun cut(x: Float): Float {
        val once = x - first.low(x)
        return once - second.low(once)
    }
}

/** Takes away what's above [hz], with two one-pole stages (12 dB per octave). */
internal class Highs(rate: Int, hz: Float) {
    private val first = OnePole(rate, hz)
    private val second = OnePole(rate, hz)

    fun cut(x: Float): Float = second.low(first.low(x))
}

/**
 * A state-variable filter in Zavalishin's trapezoidal form, which stays steady while its frequency moves. After
 * [process], [low] is the low-pass output and [peak] the band-pass, scaled to 1 at its centre.
 */
internal class Svf(private val rate: Int) {
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var k = 1f
    private var s1 = 0f
    private var s2 = 0f
    var low = 0f
        private set
    var peak = 0f
        private set

    init {
        tune(1000f, 0.7f)
    }

    fun tune(hz: Float, q: Float) {
        val g = tan(PI * min(hz, rate * 0.45f) / rate).toFloat()
        k = 1f / q
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun process(x: Float) {
        val v3 = x - s2
        val v1 = a1 * s1 + a2 * v3
        val v2 = s2 + a2 * s1 + a3 * v3
        s1 = 2f * v1 - s1
        s2 = 2f * v2 - s2
        low = v2
        peak = v1 * k
    }
}

/**
 * A value that wanders between [lo] and [hi]: it picks a new target every [holdMin] to [holdMax] seconds and eases
 * towards it with a time constant of [seconds]. A [skew] over 1 spends more time near [lo].
 */
internal class Drift(
    private val rate: Int,
    private val rng: Rng,
    private val lo: Float,
    private val hi: Float,
    private val holdMin: Float,
    private val holdMax: Float,
    seconds: Float,
    private val skew: Float = 1f,
) {
    private val ease = 1f - exp(-1f / (seconds * rate))
    private var target = pick()
    private var value = target
    private var hold = 0

    private fun pick(): Float {
        val u = rng.unit()
        return lo + (hi - lo) * (if (skew == 1f) u else Math.pow(u.toDouble(), skew.toDouble()).toFloat())
    }

    fun next(): Float {
        if (--hold <= 0) {
            target = pick()
            hold = (rate * rng.range(holdMin, holdMax)).toInt().coerceAtLeast(1)
        }
        value += (target - value) * ease
        return value
    }
}

/** Events at random times, [next]'s `perSecond` of them a second on average (a Poisson process). */
internal class Arrivals(private val rate: Int, private val rng: Rng) {
    private var wait = -1

    /** True on the frames where an event happens. */
    fun next(perSecond: Float): Boolean {
        if (wait < 0) wait = draw(perSecond)
        if (wait-- > 0) return false
        wait = draw(perSecond)
        return true
    }

    private fun draw(perSecond: Float): Int = (-ln(1f - rng.unit() * 0.9999999f) * rate / perSecond).toInt()
}

/** Up to [voices] short bursts of noise at once, each fading away and placed somewhere from left (0) to right (1). */
internal class Bursts(rate: Int, private val rng: Rng, voices: Int) {
    private val inverseRate = 1f / rate
    private val amp = FloatArray(voices)
    private val decay = FloatArray(voices)
    private val toLeft = FloatArray(voices)
    private val toRight = FloatArray(voices)
    private var active = 0
    var left = 0f
        private set
    var right = 0f
        private set

    /** A burst [level] loud that falls to a third of that in [seconds]. When every voice is busy, it's dropped. */
    fun hit(level: Float, seconds: Float, pan: Float) {
        if (active == amp.size) return
        val k = active++
        amp[k] = level
        decay[k] = exp(-inverseRate / seconds)
        toLeft[k] = panLeft(pan)
        toRight[k] = panRight(pan)
    }

    fun next() {
        var l = 0f
        var r = 0f
        var k = 0
        while (k < active) {
            val n = rng.bipolar() * amp[k]
            l += n * toLeft[k]
            r += n * toRight[k]
            amp[k] *= decay[k]
            if (amp[k] < SILENT) remove(k) else k++
        }
        left = l
        right = r
    }

    private fun remove(k: Int) {
        val last = --active
        amp[k] = amp[last]
        decay[k] = decay[last]
        toLeft[k] = toLeft[last]
        toRight[k] = toRight[last]
    }
}

/**
 * Up to [voices] short, fading tones at once that glide in pitch, such as a drop falling into water. Each is a
 * phasor turned a little every frame, and the turn itself turned, which glides the pitch with no sine per frame.
 */
internal class Plinks(private val rate: Int, voices: Int) {
    private val re = FloatArray(voices)
    private val im = FloatArray(voices)
    private val stepRe = FloatArray(voices)
    private val stepIm = FloatArray(voices)
    private val glideRe = FloatArray(voices)
    private val glideIm = FloatArray(voices)
    private val gliding = IntArray(voices)
    private val amp = FloatArray(voices)
    private val decay = FloatArray(voices)
    private val toLeft = FloatArray(voices)
    private val toRight = FloatArray(voices)
    private var active = 0
    var left = 0f
        private set
    var right = 0f
        private set

    /**
     * A tone [level] loud at [hz], gliding to [glide] times that over its first [seconds] while it falls to a third
     * of its level, then fading on at the new pitch.
     */
    fun hit(level: Float, hz: Float, glide: Float, seconds: Float, pan: Float) {
        if (active == amp.size) return
        val k = active++
        val w = 2.0 * PI * hz / rate
        val frames = (seconds * rate).coerceAtLeast(1f)
        val turn = w * (glide - 1f) / frames
        re[k] = 1f
        im[k] = 0f
        stepRe[k] = cos(w).toFloat()
        stepIm[k] = sin(w).toFloat()
        glideRe[k] = cos(turn).toFloat()
        glideIm[k] = sin(turn).toFloat()
        gliding[k] = frames.toInt()
        amp[k] = level
        decay[k] = exp(-1f / frames)
        toLeft[k] = panLeft(pan)
        toRight[k] = panRight(pan)
    }

    fun next() {
        var l = 0f
        var r = 0f
        var k = 0
        while (k < active) {
            val nr = re[k] * stepRe[k] - im[k] * stepIm[k]
            val ni = re[k] * stepIm[k] + im[k] * stepRe[k]
            re[k] = nr
            im[k] = ni
            if (gliding[k] > 0) {
                gliding[k]--
                val sr = stepRe[k] * glideRe[k] - stepIm[k] * glideIm[k]
                stepIm[k] = stepRe[k] * glideIm[k] + stepIm[k] * glideRe[k]
                stepRe[k] = sr
            }
            val s = ni * amp[k]
            l += s * toLeft[k]
            r += s * toRight[k]
            amp[k] *= decay[k]
            if (amp[k] < SILENT) remove(k) else k++
        }
        left = l
        right = r
    }

    private fun remove(k: Int) {
        val last = --active
        re[k] = re[last]
        im[k] = im[last]
        stepRe[k] = stepRe[last]
        stepIm[k] = stepIm[last]
        glideRe[k] = glideRe[last]
        glideIm[k] = glideIm[last]
        gliding[k] = gliding[last]
        amp[k] = amp[last]
        decay[k] = decay[last]
        toLeft[k] = toLeft[last]
        toRight[k] = toRight[last]
    }
}

private const val SILENT = 1e-4f

/** Equal-power panning from left (0) to right (1), with both sides at 1 in the middle. */
internal fun panLeft(pan: Float): Float = cos(pan * PI.toFloat() / 2f) * SQRT2

internal fun panRight(pan: Float): Float = sin(pan * PI.toFloat() / 2f) * SQRT2

private val SQRT2 = sqrt(2f)
