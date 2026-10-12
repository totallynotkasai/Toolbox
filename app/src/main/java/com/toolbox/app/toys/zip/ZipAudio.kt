package com.toolbox.app.toys.zip

import com.toolbox.app.ui.common.PcmStream
import com.toolbox.app.ui.common.Ping
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min
import kotlin.random.Random

/**
 * Procedural sound for the zip and the ratchet.
 *
 * Zip: every tooth the slider passes is a tiny brassy [tooth] tick. As [zipRate] (teeth/second) climbs, the
 * ticks fade and a continuous "zzzip" takes over: band-passed noise gated once per tooth, so its buzz pitch
 * *is* the tooth rate. Opening is brighter and higher than closing. [endStop] is the slider hitting a stop.
 *
 * Ratchet: [ratchet] is the pawl lifting and snapping over a gear tooth (a faint tick then a bright ringing
 * steel click). Driving the bolt, [creak] (bolt degrees/second) produces a faint stick-slip creak: sparse
 * little resonant grains whose rate rises with speed. [flip] is the reverse lever.
 */
internal class ZipAudio : PcmStream("zip-audio") {
    @Volatile var zipRate = 0f
    @Volatile var opening = true
    @Volatile var creak = 0f

    private val incoming = ConcurrentLinkedQueue<Ping>()
    private val voices = ArrayList<Ping>()

    private var rateS = 0f
    private var creakS = 0f
    private var zipPhase = 0.0
    private var bp1 = 0f
    private var bp2 = 0f
    private var bpSlow = 0f
    private var creakCountdown = 0

    /** One tooth passing; [index] staggers several teeth crossed in one touch event by ~2 ms each. */
    fun tooth(opening: Boolean, index: Int = 0) {
        // Separate ticks give way to the continuous zip as the rate climbs, so fast zips don't pile up.
        val fade = 1f / (1f + zipRate / 50f)
        val j = jitter(0.06)
        val f = if (opening) 3100.0 else 2500.0
        incoming += Ping(
            freqs = doubleArrayOf(f * j, f * 1.9 * j, 900.0 * j),
            decays = doubleArrayOf(0.0035, 0.002, 0.006),
            amps = floatArrayOf(1f, 0.5f, 0.4f),
            gain = 0.16f * fade,
            clickSeconds = 0.0006,
            clickGain = 0.9f,
            delay = index * 96,
        )
    }

    /** The slider knocking into the top stop or the bottom box. */
    fun endStop() {
        val j = jitter(0.04)
        incoming += Ping(
            freqs = doubleArrayOf(620.0 * j, 1500.0 * j, 3300.0 * j),
            decays = doubleArrayOf(0.018, 0.01, 0.004),
            amps = floatArrayOf(1f, 0.6f, 0.35f),
            gain = 0.3f,
            clickSeconds = 0.0015,
            clickGain = 0.8f,
        )
    }

    /** Pinching the pull tab: a small dry tick of metal on metal. */
    fun grab() {
        incoming += Ping(
            freqs = doubleArrayOf(1800.0, 4200.0),
            decays = doubleArrayOf(0.005, 0.002),
            amps = floatArrayOf(1f, 0.5f),
            gain = 0.1f,
            clickSeconds = 0.0005,
            clickGain = 0.5f,
        )
    }

    /** One pawl click; [index] staggers several clicks from one touch event by ~5 ms each. */
    fun ratchet(index: Int = 0) {
        val j = jitter(0.03)
        val d = index * 240
        // The pawl riding up the tooth.
        incoming += Ping(
            freqs = doubleArrayOf(5200.0 * j, 8300.0 * j),
            decays = doubleArrayOf(0.003, 0.002),
            amps = floatArrayOf(1f, 0.5f),
            gain = 0.06f,
            clickSeconds = 0.0005,
            clickGain = 0.6f,
            delay = d,
        )
        // Snapping down behind it: bright, inharmonic, ringing steel.
        incoming += Ping(
            freqs = doubleArrayOf(4300.0 * j, 7150.0 * j, 2350.0 * j, 10400.0 * j),
            decays = doubleArrayOf(0.02, 0.01, 0.035, 0.004),
            amps = floatArrayOf(1f, 0.6f, 0.35f, 0.3f),
            gain = 0.2f,
            clickSeconds = 0.0012,
            clickGain = 1.1f,
            delay = d + 170,
        )
    }

    /** The reverse lever flicking over: a firm two-part snap. */
    fun flip() {
        incoming += Ping(
            freqs = doubleArrayOf(1800.0, 3600.0, 700.0),
            decays = doubleArrayOf(0.01, 0.005, 0.02),
            amps = floatArrayOf(1f, 0.5f, 0.6f),
            gain = 0.28f,
            clickSeconds = 0.001,
            clickGain = 0.8f,
        )
        incoming += Ping(
            freqs = doubleArrayOf(2600.0, 5100.0),
            decays = doubleArrayOf(0.006, 0.003),
            amps = floatArrayOf(1f, 0.4f),
            gain = 0.14f,
            clickSeconds = 0.0008,
            clickGain = 0.6f,
            delay = 1900, // ~40 ms: the lever settling into its other stop
        )
    }

    override fun render(out: FloatArray) {
        while (true) {
            val v = incoming.poll() ?: break
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices += v
        }
        rateS += (zipRate - rateS) * 0.35f
        creakS += (creak - creakS) * 0.35f

        val zipGain = smoothstep((rateS - 20f) / 130f) * 0.5f
        val cut = if (opening) 0.32f else 0.2f // opening rasps brighter, closing is a duller "zup"
        val creakAmt = min(1f, creakS / 540f)

        for (i in out.indices) {
            var s = 0f
            for (v in voices) s += v.next()

            if (zipGain > 0.001f) {
                zipPhase += rateS / SR
                if (zipPhase >= 1.0) zipPhase -= 1.0
                // Each tooth: a sharp attack decaying across its slot. The mean (0.25) is removed from the buzz.
                val g = 1f - zipPhase.toFloat()
                val gate = g * g * g
                val noise = Random.nextFloat() * 2f - 1f
                bp1 += (noise - bp1) * cut
                bp2 += (bp1 - bp2) * cut
                bpSlow += (bp2 - bpSlow) * 0.02f
                s += (bp2 - bpSlow) * gate * zipGain * 2.2f
                s += (gate - 0.25f) * zipGain * 0.12f
            }

            if (creakAmt > 0.02f && --creakCountdown <= 0) {
                // Stick-slip: irregular little resonant grains, closer together as the bolt turns faster.
                creakCountdown = (SR / (40f + 70f * creakAmt) * (0.6f + 0.8f * Random.nextFloat())).toInt()
                val j = jitter(0.1)
                if (voices.size < MAX_VOICES) {
                    voices += Ping(
                        freqs = doubleArrayOf(620.0 * j, 1540.0 * j, 2750.0 * j),
                        decays = doubleArrayOf(0.01, 0.006, 0.003),
                        amps = floatArrayOf(1f, 0.6f, 0.3f),
                        gain = 0.035f + 0.06f * creakAmt,
                        clickSeconds = 0.0004,
                        clickGain = 0.3f,
                    )
                }
            }
            out[i] = s
        }
        voices.removeAll { it.done }
    }

    private fun jitter(amount: Double) = 1.0 + (Random.nextDouble() - 0.5) * amount

    private fun smoothstep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        const val SR = PcmStream.SAMPLE_RATE
        const val MAX_VOICES = 32
    }
}

