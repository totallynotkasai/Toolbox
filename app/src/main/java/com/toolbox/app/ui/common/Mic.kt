package com.toolbox.app.ui.common

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import androidx.annotation.RequiresPermission
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.runtime.Composable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** [PermissionGate] for RECORD_AUDIO, with the mic-off icon. */
@Composable
fun MicPermissionGate(rationale: String, content: @Composable () -> Unit) = PermissionGate(
    permission = Manifest.permission.RECORD_AUDIO,
    icon = Icons.Filled.MicOff,
    rationale = rationale,
    buttonLabel = "Allow microphone",
    content = content,
)

/**
 * Opens a mono 16-bit [AudioRecord] from the first of [sources] (MediaRecorder.AudioSource values) that initialises,
 * with a buffer of at least [minBufferBytes]. Returns null if none work (mic busy, or no mic at all).
 * The caller owns the record: `startRecording()`, then `stop()` and `release()` when done.
 */
@RequiresPermission(Manifest.permission.RECORD_AUDIO)
fun openMicRecord(sampleRate: Int, minBufferBytes: Int, vararg sources: Int): AudioRecord? {
    val systemMin = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (systemMin <= 0) return null // Sample rate or format not supported.
    for (source in sources) {
        val record = try {
            AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(systemMin, minBufferBytes))
        } catch (_: IllegalArgumentException) {
            continue
        }
        if (record.state == AudioRecord.STATE_INITIALIZED) return record
        record.release()
    }
    return null
}

/** True if the device promises a raw, unprocessed mic path (no AGC or noise suppression). */
fun supportsUnprocessedMic(context: Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java) ?: return false
    return audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
}

/**
 * Blocks until [buffer] is full. Returns false if the recorder reports an error (e.g. the mic was taken away)
 * or the coroutine was cancelled, in which case the buffer may be only partly filled.
 */
suspend fun AudioRecord.readFully(buffer: ShortArray): Boolean {
    var read = 0
    while (read < buffer.size) {
        if (!currentCoroutineContext().isActive) return false
        val n = read(buffer, read, buffer.size - read)
        if (n <= 0) return false
        read += n
    }
    return true
}
