package com.toolbox.app

import com.toolbox.app.tools.soundscapes.Channel
import com.toolbox.app.tools.soundscapes.Mix
import com.toolbox.app.tools.soundscapes.Mixer
import com.toolbox.app.tools.soundscapes.Playback
import com.toolbox.app.tools.soundscapes.SleepTimer
import com.toolbox.app.tools.soundscapes.Sound
import com.toolbox.app.tools.soundscapes.Status
import com.toolbox.app.tools.soundscapes.Waves
import com.toolbox.app.tools.soundscapes.sleepLine
import com.toolbox.app.tools.soundscapes.softClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class SoundscapesTest {
    private val rate = 48_000

    /** [seconds] of [sound] on its own, straight from its generator: left and right interleaved. */
    private fun render(sound: Sound, seconds: Int, seed: Int = 7): FloatArray {
        val generator = sound.generator(rate, seed)
        val out = FloatArray(seconds * rate * 2)
        val block = FloatArray(BLOCK * 2)
        var at = 0
        while (at < out.size) {
            val frames = minOf(BLOCK, (out.size - at) / 2)
            generator.render(block, frames)
            System.arraycopy(block, 0, out, at, frames * 2)
            at += frames * 2
        }
        return out
    }

    private fun side(stereo: FloatArray, right: Boolean) = FloatArray(stereo.size / 2) { stereo[2 * it + if (right) 1 else 0] }

    @Test
    fun everySoundStaysInRangeWithNoDrift() {
        for (sound in Sound.entries) {
            val out = render(sound, 60)
            val peak = out.maxOf { abs(it) }
            assertTrue("$sound peaks at $peak", peak < 1f)
            for (right in listOf(false, true)) {
                val x = side(out, right)
                assertTrue("$sound drifts: mean ${x.average()}", abs(x.average()) < 0.01)
                // Every 10 seconds is centred, and about as loud as the rest: nothing wanders off or dies away.
                val window = 10 * rate
                val levels = (0 until x.size / window).map { w ->
                    val part = x.copyOfRange(w * window, (w + 1) * window)
                    assertTrue("$sound drifts in window $w: ${part.average()}", abs(part.average()) < 0.03)
                    rms(part)
                }
                val spread = db(levels.max() / levels.min())
                assertTrue("$sound level wanders by $spread dB", spread < 10)
            }
        }
    }

    @Test
    fun noiseColoursHaveTheirSlopes() {
        assertEquals(0.0, octaveSlope(Sound.White), 0.5)
        assertEquals(-3.0, octaveSlope(Sound.Pink), 0.5)
        assertEquals(-6.0, octaveSlope(Sound.Brown), 0.6)
    }

    /** At full volume every sound measures -21 LUFS, so a slider means the same on every row. */
    @Test
    fun soundsAreEquallyLoud() {
        val levels = Sound.entries.associateWith { loudness(render(it, 40)) }
        for ((sound, level) in levels) assertEquals("$sound: $levels", -21.0, level, 1.0)
    }

    @Test
    fun sidesAreDifferent() {
        for (sound in Sound.entries) {
            val out = render(sound, 10)
            val l = side(out, false)
            val r = side(out, true)
            var lr = 0.0
            for (i in l.indices) lr += l[i] * r[i]
            val correlation = lr / (rms(l) * rms(r) * l.size)
            assertTrue("$sound sides correlate $correlation", correlation < 0.8)
        }
    }

    @Test
    fun aWaveSwellsBreaksAndDrawsBack() {
        val crest = 0.35f
        assertEquals(0f, Waves.swell(0f, crest), 1e-6f)
        assertEquals(1f, Waves.swell(crest, crest), 1e-6f)
        assertEquals(0f, Waves.swell(1f, crest), 1e-6f)
        var last = -1f
        for (i in 0..35) {
            val s = Waves.swell(i / 100f, crest)
            assertTrue(s >= last)
            last = s
        }
        for (i in 35..100) {
            val s = Waves.swell(i / 100f, crest)
            assertTrue(s <= last)
            last = s
        }
        // The roar comes and goes: louder at the crests than between them.
        val x = side(render(Sound.Waves, 40), false)
        val levels = (0 until 80).map { rms(x.copyOfRange(it * rate / 2, (it + 1) * rate / 2)) }
        assertTrue(db(levels.max() / levels.min()) > 12)
    }

    @Test
    fun gainsFollowTheSlidersAndTheMaster() {
        val mix = Mix.Default
            .with(Sound.Rain, Channel(true, 1f))
            .with(Sound.Wind, Channel(true, 0.5f))
            .with(Sound.Fire, Channel(false, 1f))
            .copy(master = 1f)
        assertEquals(1f, mix.gain(Sound.Rain), 1e-6f)
        assertEquals(0.25f, mix.gain(Sound.Wind), 1e-6f)
        assertEquals(0f, mix.gain(Sound.Fire), 0f)
        val half = mix.copy(master = 0.5f)
        assertEquals(0.25f, half.gain(Sound.Rain), 1e-6f)
        assertEquals(0.0625f, half.gain(Sound.Wind), 1e-6f)
        assertEquals(0f, mix.copy(master = 0f).gain(Sound.Rain), 0f)
        assertEquals(0f, Mix.loudness(0f), 0f)
        assertEquals(1f, Mix.loudness(2f), 0f)
    }

    @Test
    fun mixerEasesToTheMixAndPlaysEachSoundAtItsGain() {
        val frames = 1024
        val mixer = Mixer(rate, seed = 1)
        val mix = Mix.Default.with(Sound.Rain, Channel(false, 0.5f)).with(Sound.Fire, Channel(true, 0.6f)).copy(master = 0.9f)
        val out = FloatArray(frames * 2)
        // A couple of seconds for the gains to settle on the mix.
        repeat(3 * rate / frames) { mixer.render(out, frames, mix) }
        assertEquals(mix.gain(Sound.Fire), mixer.gain(Sound.Fire), 0f)
        assertEquals(0f, mixer.gain(Sound.Rain), 0f)
        // Once settled, the mix is exactly the fire, at its gain: the fire's generator, run alongside, matches it.
        val fire = Sound.Fire.generator(rate, 1 + Sound.Fire.ordinal * 7919)
        val alone = FloatArray(frames * 2)
        // The mixer rendered the fire from the first block, as it eased in.
        repeat(3 * rate / frames) { fire.render(alone, frames) }
        mixer.render(out, frames, mix)
        fire.render(alone, frames)
        for (i in out.indices) assertEquals(alone[i] * mix.gain(Sound.Fire), out[i], 1e-6f)

        // Switched off, it fades out over a fraction of a second rather than stopping dead.
        val off = mix.with(Sound.Fire, Channel(false, 0.6f))
        mixer.render(out, frames, off)
        assertTrue(mixer.gain(Sound.Fire) > 0f)
        repeat(3 * rate / frames) { mixer.render(out, frames, off) }
        assertEquals(0f, mixer.gain(Sound.Fire), 0f)
        mixer.render(out, frames, off)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun softClipLeavesNormalLevelsAloneAndBendsLoudOnes() {
        for (x in listOf(0f, 0.1f, -0.3f, 0.6f, -0.6f)) assertEquals(x, softClip(x), 0f)
        var last = 0.6f
        for (i in 61..200) {
            val y = softClip(i / 100f)
            assertTrue(y > last && y < 1f)
            assertEquals(-y, softClip(-i / 100f), 0f)
            last = y
        }
        assertTrue(softClip(50f) <= 1f)
        // It bends smoothly: just past the knee it still rises one for one.
        assertEquals(1f, (softClip(0.601f) - softClip(0.6f)) / 0.001f, 0.01f)
    }

    @Test
    fun theMixIsSavedAndReadBack() {
        val mix = Mix.Default
            .with(Sound.Waves, Channel(true, 0.25f))
            .with(Sound.Brown, Channel(true, 1f))
            .with(Sound.Rain, Channel(false, 0.125f))
            .copy(master = 0.6f)
        assertEquals(mix, Mix.decode(mix.encode()))
        assertEquals(Mix.Default, Mix.decode(""))
        assertEquals(Mix.Default, Mix.decode("nonsense;;rain=maybe,0.4;sleet=on,1;master=loud"))
        // Out-of-range values are brought back in, and a mix saved before a sound existed keeps that sound's default.
        val old = Mix.decode("master=3;rain=on,-1;fire=on,0.5")
        assertEquals(1f, old.master, 0f)
        assertEquals(Channel(true, 0f), old[Sound.Rain])
        assertEquals(Channel(true, 0.5f), old[Sound.Fire])
        assertEquals(Mix.Default[Sound.Pink], old[Sound.Pink])
    }

    @Test
    fun theNotificationSaysWhatsPlaying() {
        val none = Mix(emptyMap(), 1f)
        assertEquals("No sounds on", none.title)
        assertFalse(none.anyOn)
        assertEquals("Rain", Mix.Default.title)
        assertEquals("Rain and pink noise", Mix.Default.with(Sound.Pink, Channel(true, 1f)).title)
        assertEquals(
            "Waves, fire and brown noise",
            none.with(Sound.Brown, Channel(true, 1f)).with(Sound.Waves, Channel(true, 1f)).with(Sound.Fire, Channel(true, 0f)).title,
        )
    }

    @Test
    fun sleepTimerRunsOnlyWhilePlaying() {
        val off = SleepTimer()
        assertNull(off.left(1_000))
        assertEquals(1f, SleepTimer.fade(null), 0f)

        val timer = SleepTimer(15)
        assertEquals(15 * 60_000L, timer.left(1_000))
        val playing = timer.run(10_000)
        assertEquals(15 * 60_000L - 5_000, playing.left(15_000))
        // Paused, it holds; playing again, it carries on from there.
        val paused = playing.hold(70_000)
        assertEquals(15 * 60_000L - 60_000, paused.left(500_000))
        val again = paused.run(500_000)
        assertEquals(15 * 60_000L - 120_000, again.left(560_000))
        assertEquals(0L, again.left(10_000_000))
        // A new length starts in full, still running; Stop puts it back to its full length.
        assertEquals(30 * 60_000L, again.set(30, 600_000).left(600_000))
        assertTrue(again.set(30, 600_000).running)
        assertEquals(SleepTimer(15), again.reset())
        assertEquals(14, SleepTimer.minutesLeft(13 * 60_000L + 1))
        assertEquals(1, SleepTimer.minutesLeft(1))
    }

    @Test
    fun playingRunsTheSleepTimerAndStopStartsItAgain() {
        val start = Playback(sleep = SleepTimer(30))
        val playing = start.to(Status.Playing, 1_000)
        assertTrue(playing.sleep.running)
        val paused = playing.to(Status.Paused, 61_000)
        assertFalse(paused.sleep.running)
        assertEquals(29 * 60_000L, paused.sleep.left(999_999))
        assertEquals(SleepTimer(30), paused.to(Status.Stopped, 70_000).sleep)

        assertNull(sleepLine(SleepTimer(), Status.Playing, 0))
        assertEquals("Stops after 30 minutes of playing", sleepLine(start.sleep, Status.Stopped, 0))
        assertEquals("Stops in 29:59", sleepLine(playing.sleep, Status.Playing, 2_000))
        assertEquals("29:00 left on the sleep timer", sleepLine(paused.sleep, Status.Paused, 100_000))
    }

    @Test
    fun sleepTimerFadesOverTheLastHalfMinute() {
        assertEquals(1f, SleepTimer.fade(10 * 60_000L), 0f)
        assertEquals(1f, SleepTimer.fade(30_000L), 0f)
        assertEquals(0.125f, SleepTimer.fade(15_000L), 1e-6f)
        assertEquals(0f, SleepTimer.fade(0L), 0f)
        var last = 0f
        for (ms in 0L..30_000L step 500) {
            val f = SleepTimer.fade(ms)
            assertTrue(f >= last)
            last = f
        }
    }

    /**
     * With `-PwriteSounds`, writes half a minute of each sound and of two mixes to WAV files in `app/build/sounds`, for
     * listening to on a computer, and each sound's measurements to `levels.txt`. Each plays at the default slider (0.8)
     * and master (1), through the same soft clip as on the phone.
     */
    @Test
    fun writeSounds() {
        val dir = System.getProperty("soundsDir").orEmpty()
        assumeTrue(dir.isNotEmpty())
        File(dir).mkdirs()
        File(dir, "levels.txt").writeText(
            Sound.entries.joinToString("") {
                val x = render(it, 40)
                "%-6s %6.2f LUFS %6.2f dB(A)  peak %.3f%n".format(it.name, loudness(x), aWeighted(x), x.maxOf { s -> abs(s) })
            },
        )
        val none = Mix(emptyMap(), 1f)
        val mixes = Sound.entries.associate { it.id to none.with(it, Channel(true, 0.8f)) } +
            ("rain-and-fire" to none.with(Sound.Rain, Channel(true, 0.8f)).with(Sound.Fire, Channel(true, 0.7f))) +
            ("waves-and-wind" to none.with(Sound.Waves, Channel(true, 0.8f)).with(Sound.Wind, Channel(true, 0.6f)))
        for ((name, mix) in mixes) {
            val mixer = Mixer(rate)
            val frames = 1024
            val block = FloatArray(frames * 2)
            val pcm = ShortArray(30 * rate * 2)
            var at = 0
            while (at < pcm.size) {
                mixer.render(block, frames, mix)
                for (i in block.indices) if (at + i < pcm.size) pcm[at + i] = (softClip(block[i]) * Short.MAX_VALUE).toInt().toShort()
                at += block.size
            }
            writeWav(File(dir, "$name.wav"), pcm)
        }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            fun int(v: Int) = out.writeInt(Integer.reverseBytes(v))
            fun short(v: Int) = out.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
            out.writeBytes("RIFF"); int(36 + pcm.size * 2); out.writeBytes("WAVEfmt ")
            int(16); short(1); short(2); int(rate); int(rate * 4); short(4); short(16)
            out.writeBytes("data"); int(pcm.size * 2)
            for (s in pcm) short(s.toInt())
        }
    }

    // Measuring.

    /** Average power spectrum of both sides (Welch's method, Hann window), as power per bin of [FFT] points. */
    private fun spectrum(stereo: FloatArray): DoubleArray {
        val power = DoubleArray(FFT / 2)
        val window = DoubleArray(FFT) { 0.5 - 0.5 * cos(2 * PI * it / FFT) }
        val windowPower = window.sumOf { it * it }
        var segments = 0
        for (right in listOf(false, true)) {
            val x = side(stereo, right)
            var start = 0
            while (start + FFT <= x.size) {
                val re = DoubleArray(FFT) { x[start + it] * window[it] }
                val im = DoubleArray(FFT)
                fft(re, im)
                for (k in 0 until FFT / 2) power[k] += (re[k] * re[k] + im[k] * im[k]) / windowPower
                segments++
                start += FFT
            }
        }
        for (k in power.indices) power[k] /= segments
        return power
    }

    /** dB per octave of [sound]'s spectrum from 125 Hz to 4 kHz: a straight line fitted through six octave bands. */
    private fun octaveSlope(sound: Sound): Double {
        val power = spectrum(render(sound, 30))
        val binHz = rate.toDouble() / FFT
        val centres = listOf(125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0)
        // Power per Hz in each band, so a flat (white) spectrum reads 0 dB per octave.
        val levels = centres.map { c ->
            val lo = (c / sqrt(2.0) / binHz).toInt()
            val hi = (c * sqrt(2.0) / binHz).toInt()
            10 * log10((lo until hi).sumOf { power[it] } / (hi - lo))
        }
        val xs = centres.map { log2(it) }
        val mx = xs.average()
        val my = levels.average()
        return xs.indices.sumOf { (xs[it] - mx) * (levels[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
    }

    /** A-weighted level in dB, roughly how loud it sounds. */
    private fun aWeighted(stereo: FloatArray): Double {
        val power = spectrum(stereo)
        val binHz = rate.toDouble() / FFT
        var total = 0.0
        for (k in 1 until power.size) {
            val f = k * binHz
            val f2 = f * f
            val ra = 12194.0 * 12194.0 * f2 * f2 /
                ((f2 + 20.6 * 20.6) * sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9)) * (f2 + 12194.0 * 12194.0))
            total += power[k] * ra * ra * 1.2589 // +2 dB, so 1 kHz is 0 dB
        }
        return 10 * log10(total * 2 / FFT)
    }

    /**
     * Loudness in LUFS (ITU-R BS.1770, without gating): both sides through the K-weighting filter, a gentle cut of the
     * deep bass and a lift of the treble, the way loudness is measured for broadcast and streaming.
     */
    private fun loudness(stereo: FloatArray): Double {
        val power = spectrum(stereo)
        var total = 0.0
        for (k in 1 until power.size) {
            val w = 2 * PI * k / FFT
            total += power[k] * biquadGain(K_SHELF, w) * biquadGain(K_HIGH_PASS, w)
        }
        // The spectrum is per side; BS.1770 adds the two sides' mean squares.
        return -0.691 + 10 * log10(2 * total * 2 / FFT)
    }

    /** |H|² of a biquad (b0, b1, b2, a1, a2) at [w] radians a sample. */
    private fun biquadGain(c: DoubleArray, w: Double): Double {
        fun mag2(c0: Double, c1: Double, c2: Double): Double {
            val re = c0 + c1 * cos(w) + c2 * cos(2 * w)
            val im = -(c1 * sin(w) + c2 * sin(2 * w))
            return re * re + im * im
        }
        return mag2(c[0], c[1], c[2]) / mag2(1.0, c[3], c[4])
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cos(angle * k)
                    val wi = sin(angle * k)
                    val a = i + k
                    val b = a + len / 2
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
            }
            len = len shl 1
        }
    }

    private fun rms(x: FloatArray): Double = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    private fun db(ratio: Double) = 20 * log10(max(ratio, 1e-12))

    private fun FloatArray.average(): Double = sumOf { it.toDouble() } / size

    private companion object {
        const val BLOCK = 1024
        const val FFT = 8192

        // BS.1770's two K-weighting stages at 48 kHz.
        val K_SHELF = doubleArrayOf(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585)
        val K_HIGH_PASS = doubleArrayOf(1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621)
    }
}
