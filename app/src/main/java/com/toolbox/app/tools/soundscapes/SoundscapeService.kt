package com.toolbox.app.tools.soundscapes

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.toolbox.app.MainActivity
import com.toolbox.app.R
import com.toolbox.app.Tool
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Plays Soundscapes in the background, with the screen off, from the moment Play is pressed until Stop.
 *
 * It's a `mediaPlayback` foreground service with a media session, so the sound shows in Android's media controls (the
 * notification, Quick Settings and the lock screen) and headset buttons work. A media notification shows even when
 * notifications aren't allowed, so nothing is asked for.
 *
 * Like other media apps it gives way to calls and other sound: it pauses for a call or another app's short sound and
 * carries on after, pauses for good when another app starts playing, and pauses when headphones are unplugged. Paused
 * for 10 minutes (other than for a call), it stops.
 */
class SoundscapeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val engine = SoundscapeEngine(onFadedOut = ::fadedOut, onSleepEnd = ::stop)
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private lateinit var session: MediaSession
    private lateinit var notification: SoundscapeNotification

    /** Paused for a call or another app's short sound, to carry on when it's over. */
    private var resumeAfterLoss = false
    private var listeningForNoisy = false
    private var shown: SoundscapeNotification.Content? = null

    private val status get() = Soundscapes.playback.value.status

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    private val giveUp = Runnable { stop() }
    private val tick = Runnable { refresh() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Soundscapes.load(this)
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AUDIO)
            // Android lowers the sound by itself for a satnav prompt or a notification, rather than pausing it.
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(::onFocusChange, main)
            .build()
        session = MediaSession(this, "Soundscapes").apply {
            setSessionActivity(MainActivity.openToolIntent(this@SoundscapeService, Tool.Soundscapes))
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = play()
                    override fun onPause() = pause()
                    override fun onStop() = stop()
                    override fun onCustomAction(action: String, extras: Bundle?) {
                        if (action == CUSTOM_STOP) stop()
                    }
                },
                main,
            )
        }
        notification = SoundscapeNotification(this, session.sessionToken)
        // What the notification says follows the mix and the sleep timer, which the screen changes.
        scope.launch { Soundscapes.mix.combine(Soundscapes.playback) { _, _ -> }.collect { refresh() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
        }
        if (status == Status.Stopped && !engine.isRunning) stopSelf()
        // If Android stops the process, nothing comes back until Play is pressed again.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        engine.stop()
        audio.abandonAudioFocusRequest(focus)
        listenForNoisy(false)
        session.release()
        if (status != Status.Stopped) Soundscapes.setStatus(Status.Stopped)
        super.onDestroy()
    }

    private fun play() {
        main.removeCallbacks(giveUp)
        if (status == Status.Playing) {
            goForeground() // Already playing, but a start from the screen still has to confirm the foreground.
            return
        }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Refused, say during a call: wait, paused, for Play to be pressed again (or for the call to end, if it
            // was the call that paused it).
            if (status == Status.Stopped) Soundscapes.setStatus(Status.Paused)
            if (!resumeAfterLoss) main.postDelayed(giveUp, PAUSED_FOR_MS)
            goForeground()
            return
        }
        resumeAfterLoss = false
        session.isActive = true
        engine.play()
        listenForNoisy(true)
        Soundscapes.setStatus(Status.Playing)
        goForeground()
    }

    /** @param forNow paused for a call or another app's short sound, to carry on after it. */
    private fun pause(forNow: Boolean = false) {
        if (status != Status.Playing) {
            // Paused for a call, then paused on purpose: it stays paused after the call.
            if (!forNow && resumeAfterLoss) giveUpLater()
            return
        }
        engine.fadeOut()
        listenForNoisy(false)
        resumeAfterLoss = forNow
        if (!forNow) giveUpLater()
        Soundscapes.setStatus(Status.Paused)
        refresh()
    }

    /** Paused for good: lets other apps play, and stops if it stays paused. */
    private fun giveUpLater() {
        resumeAfterLoss = false
        audio.abandonAudioFocusRequest(focus)
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, PAUSED_FOR_MS)
    }

    private fun stop() {
        main.removeCallbacks(giveUp)
        main.removeCallbacks(tick)
        resumeAfterLoss = false
        audio.abandonAudioFocusRequest(focus)
        listenForNoisy(false)
        session.isActive = false
        Soundscapes.setStatus(Status.Stopped)
        shown = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // The notification goes at once; the sound fades out first, then the service stops.
        if (engine.isRunning) engine.fadeOut() else stopSelf()
    }

    private fun fadedOut() {
        if (status == Status.Playing) return // Played again while fading out.
        engine.stop()
        if (status == Status.Stopped) stopSelf()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause(forNow = true)
            AudioManager.AUDIOFOCUS_LOSS -> if (status == Status.Playing) pause() else if (resumeAfterLoss) giveUpLater()
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterLoss && status == Status.Paused) play()
        }
    }

    private fun listenForNoisy(on: Boolean) {
        if (on == listeningForNoisy) return
        listeningForNoisy = on
        if (on) {
            ContextCompat.registerReceiver(
                this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            unregisterReceiver(noisy)
        }
    }

    /** Posts the notification as the service's own, which keeps it running in the background. */
    private fun goForeground() {
        val content = content() ?: return
        updateSession(content)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, SoundscapeNotification.ID, notification.build(content), type)
            shown = content
        } catch (_: RuntimeException) {
            // Android wouldn't let it keep running in the background.
            stop()
            return
        }
        scheduleTick()
    }

    /** Brings the session and the notification in line with what's playing, when what they say has changed. */
    private fun refresh() {
        val content = content() ?: return
        if (content != shown && shown != null) {
            updateSession(content)
            notification.show(content)
            shown = content
        }
        scheduleTick()
    }

    /** While the sleep timer runs, refreshes again when its minutes left go down by one. */
    private fun scheduleTick() {
        main.removeCallbacks(tick)
        val playback = Soundscapes.playback.value
        val left = playback.sleep.left(Soundscapes.now()) ?: return
        if (playback.status != Status.Playing || left <= 0) return
        main.postDelayed(tick, (left - 1) % SleepTimer.MINUTE_MS + 1 + TICK_SLACK_MS)
    }

    private fun content(): SoundscapeNotification.Content? {
        val playback = Soundscapes.playback.value
        if (playback.status == Status.Stopped) return null
        val left = playback.sleep.left(Soundscapes.now())
        val text = if (left == null) "Soundscapes" else "Sleep timer: ${SleepTimer.minutesLeft(left)} min left"
        return SoundscapeNotification.Content(Soundscapes.mix.value.title, text, playback.status == Status.Playing)
    }

    private fun updateSession(content: SoundscapeNotification.Content) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, content.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, content.text)
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(
                    if (content.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (content.playing) 1f else 0f,
                )
                // Android 13's media controls only show Stop as a custom action.
                .addCustomAction(PlaybackState.CustomAction.Builder(CUSTOM_STOP, "Stop", R.drawable.ic_media_stop).build())
                .build(),
        )
    }

    companion object {
        internal const val ACTION_PLAY = "com.toolbox.app.soundscapes.PLAY"
        internal const val ACTION_PAUSE = "com.toolbox.app.soundscapes.PAUSE"
        internal const val ACTION_STOP = "com.toolbox.app.soundscapes.STOP"
        private const val CUSTOM_STOP = "stop"
        private const val PAUSED_FOR_MS = 10 * 60_000L
        private const val TICK_SLACK_MS = 50L

        private val AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        /** From the screen, which is in the foreground, so the service may start there. */
        fun play(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_PLAY))

        fun pause(context: Context) {
            context.startService(intent(context, ACTION_PAUSE))
        }

        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }

        internal fun intent(context: Context, action: String) = Intent(context, SoundscapeService::class.java).setAction(action)
    }
}
