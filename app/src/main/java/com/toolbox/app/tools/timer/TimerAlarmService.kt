package com.toolbox.app.tools.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.toolbox.app.ui.common.Prefs
import com.toolbox.app.ui.common.Vibes

/**
 * Rings while the timer is in [TimerPhase.Ringing]: the phone's alarm sound on a loop at alarm volume, plus a
 * vibration if the app's Vibration switch is on, until Stop or +1 min. It runs only while ringing.
 *
 * The sound is played here rather than by the notification, because some phones (HyperOS) don't reliably play
 * notification sounds, and an insistent notification stops as soon as the shade is opened. A foreground service of
 * type `systemExempted` is what Android allows apps with `USE_EXACT_ALARM` for continuing an alarm in the background,
 * and an exact alarm going off lets the app start one from the background.
 */
class TimerAlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = TimerController(this).state
        // startForeground must come first, even when about to stop, or Android treats the start as failed.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else 0
        try {
            ServiceCompat.startForeground(this, TimerNotifications.ALARM_ID, TimerNotifications(this).alarm(state), type)
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (state.phase != TimerPhase.Ringing) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (player == null) player = startSound()
        if (vibrator == null && Prefs(this).get(Vibes.PREF_KEY, true)) vibrator = startVibration()
        // If Android stops the process, opening the app brings the ringing back (see TimerScreen's refresh).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.run { try { stop() } catch (_: IllegalStateException) { }; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** The chosen alarm sound, or the default alarm, ringtone or notification sound if that can't be played. */
    private fun startSound(): MediaPlayer? {
        val sounds = listOfNotNull(
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in sounds) {
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(ALARM_AUDIO)
                player.setDataSource(this, uri)
                player.isLooping = true
                player.prepare()
                player.start()
                return player
            } catch (_: Exception) {
                player.release() // IOException, IllegalStateException or SecurityException: try the next sound.
            }
        }
        return null
    }

    private fun startVibration(): Vibrator? {
        val vibrator = vibrator(this)?.takeIf { it.hasVibrator() } ?: return null
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, ALARM_AUDIO)
        }
        return vibrator
    }

    companion object {
        private val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private fun vibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }
    }
}
