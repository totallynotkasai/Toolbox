package com.toolbox.app.ui.common

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.math.tanh

/**
 * Base for the app's procedural sound: a background thread asks [render] for blocks of mono samples
 * (roughly -1..1) at [SAMPLE_RATE] and streams them into a low-latency AudioTrack, through [clip].
 *
 * Subclasses keep their own state. Anything the UI thread changes should be @Volatile, or handed over
 * through a thread-safe queue / atomic, and read once at the top of [render].
 * Use [rememberPcmStream] so it only runs while the screen is started.
 *
 * Failures never crash the app or spin: if the track can't be built or played the stream stops (a later
 * [start] tries again), and if the track dies mid-stream (audio server restart, route change) it is rebuilt
 * a few times before giving up.
 */
abstract class PcmStream(
    private val threadName: String,
    private val usage: Int = AudioAttributes.USAGE_GAME,
) {
    private val thread = AtomicReference<Thread?>(null)
    private var previous: Thread? = null // The last run started, which may still be winding down.
    private var track: AudioTrack? = null // Only touched on the audio thread.
    private var framesWritten = 0L        // Into the current [track].

    val isRunning get() = thread.get() != null

    /** Fill [out] (zeroed beforehand) with the next samples. Called on the audio thread, ~190 times a second. */
    protected abstract fun render(out: FloatArray)

    /**
     * Event-driven streams return true when they have nothing to play and nothing queued. After [IDLE_BLOCKS]
     * quiet blocks in a row the track is paused and the thread parks until [wake]. Called on the audio thread.
     * Continuous sounds leave this false so they never pay the wake-up latency.
     */
    protected open fun isQuiet(): Boolean = false

    /** Called on the audio thread before the first block of each run; reset per-run state here. */
    protected open fun onStreamStart() {}

    /** Maps a mixed sample to -1..1. The default soft-clips, so stacked sounds saturate instead of wrapping. */
    protected open fun clip(x: Float): Float = tanh(x)

    /** Frames handed to the track but not yet played: how far ahead of the speaker [render] is. Audio thread only. */
    protected fun queuedFrames(): Long {
        val t = track ?: return 0
        return (framesWritten - (t.playbackHeadPosition.toLong() and 0xFFFF_FFFFL)).coerceAtLeast(0)
    }

    /** Wakes an idle stream. Call after queuing a sound; cheap and safe from any thread. */
    protected fun wake() { thread.get()?.let(LockSupport::unpark) }

    @Synchronized
    open fun start() {
        if (thread.get() != null) return
        // A run that is still winding down holds its own AudioTrack; let it release before opening another.
        previous?.let { if (it.isAlive) it.join(JOIN_MS) }
        val t = Thread({ run() }, threadName)
        thread.set(t)
        previous = t
        t.start()
    }

    open fun stop() {
        val t = thread.getAndSet(null) ?: return
        LockSupport.unpark(t) // In case it's parked while idle.
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val me = Thread.currentThread()
        val block = FloatArray(BLOCK)
        val pcm = ShortArray(BLOCK)
        var failures = 0
        var quietBlocks = 0
        onStreamStart()
        try {
            while (thread.get() === me) {
                val t = track ?: openTrack() ?: break
                if (isQuiet()) {
                    if (++quietBlocks >= IDLE_BLOCKS) {
                        idle(t, me)
                        quietBlocks = 0
                        continue
                    }
                } else {
                    quietBlocks = 0
                }
                block.fill(0f)
                render(block)
                for (i in block.indices) pcm[i] = (clip(block[i]).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                if (writeFully(t, pcm)) {
                    failures = 0
                } else {
                    // The track is dead or misbehaving. Rebuild it after a short back-off, rather than spinning.
                    closeTrack()
                    if (++failures > MAX_FAILURES) break
                    Thread.sleep(BACKOFF_MS * failures)
                }
            }
        } catch (_: InterruptedException) {
            // Stopping.
        } catch (e: RuntimeException) {
            Log.w(TAG, "$threadName failed", e)
        } finally {
            closeTrack()
            // Let a later start() retry, unless start() already replaced this run.
            thread.compareAndSet(me, null)
        }
    }

    /** Pauses [t] and parks until [wake], [stop], or something is queued. */
    private fun idle(t: AudioTrack, me: Thread) {
        t.pause()
        t.flush()
        framesWritten = 0 // flush() rewinds the play head.
        while (thread.get() === me && isQuiet()) LockSupport.park(this)
        if (thread.get() === me) t.play()
    }

    private fun openTrack(): AudioTrack? {
        val t = try {
            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return null
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                // Headroom against underruns when the audio thread is briefly descheduled.
                .setBufferSizeInBytes(minBuf * BUFFER_MULTIPLE)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: RuntimeException) { // UnsupportedOperationException / IllegalArgumentException
            Log.w(TAG, "$threadName: couldn't build AudioTrack", e)
            return null
        }
        try {
            if (t.state != AudioTrack.STATE_INITIALIZED) error("not initialised")
            t.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "$threadName: couldn't play AudioTrack", e)
            t.release()
            return null
        }
        framesWritten = 0
        track = t
        return t
    }

    private fun closeTrack() {
        val t = track ?: return
        track = null
        try {
            t.pause()
            t.flush()
        } catch (_: IllegalStateException) {
            // Already dead; release anyway.
        }
        t.release()
    }

    /** Blocking write of all of [pcm]. False on an error code or no progress. */
    private fun writeFully(t: AudioTrack, pcm: ShortArray): Boolean {
        var off = 0
        while (off < pcm.size) {
            val n = t.write(pcm, off, pcm.size - off)
            if (n <= 0) return false
            off += n
        }
        framesWritten += pcm.size
        return true
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val BLOCK = 256
        private const val TAG = "PcmStream"
        private const val BUFFER_MULTIPLE = 2
        private const val IDLE_BLOCKS = 2 * SAMPLE_RATE / BLOCK // ~2 s of silence
        private const val MAX_FAILURES = 3
        private const val BACKOFF_MS = 100L
        private const val JOIN_MS = 300L
    }
}

/** Creates a [PcmStream] once and runs it while this composable is on screen and the app is started. */
@Composable
fun <T : PcmStream> rememberPcmStream(factory: () -> T): T {
    val stream = remember { factory() }
    LifecycleStartEffect(stream) {
        stream.start()
        onStopOrDispose { stream.stop() }
    }
    return stream
}
