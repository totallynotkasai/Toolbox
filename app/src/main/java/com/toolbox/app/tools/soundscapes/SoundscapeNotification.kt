package com.toolbox.app.tools.soundscapes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import com.toolbox.app.MainActivity
import com.toolbox.app.R
import com.toolbox.app.Tool

/**
 * Soundscapes' media notification: what's playing, the sleep timer's minutes left, and Pause/Play and Stop. Android
 * draws it as its media controls, from Android 13 in Quick Settings, and shows it without the notification permission.
 */
internal class SoundscapeNotification(private val context: Context, private val token: MediaSession.Token) {
    /** What it says: [title] is the sounds on, [text] the line under it. */
    data class Content(val title: String, val text: String, val playing: Boolean)

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun build(content: Content): Notification {
        createChannel()
        val toggle = if (content.playing) {
            action(R.drawable.ic_media_pause, "Pause", SoundscapeService.ACTION_PAUSE)
        } else {
            action(R.drawable.ic_media_play, "Play", SoundscapeService.ACTION_PLAY)
        }
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_soundscapes)
            .setColor(0xFF1A73E8.toInt()) // The Soundscapes tile's blue.
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(content.playing)
            .setContentIntent(MainActivity.openToolIntent(context, Tool.Soundscapes))
            // Paused, it can be swiped away, which stops it.
            .setDeleteIntent(service(SoundscapeService.ACTION_STOP))
            .addAction(toggle)
            .addAction(action(R.drawable.ic_media_stop, "Stop", SoundscapeService.ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(token).setShowActionsInCompactView(0, 1))
            .build()
    }

    fun show(content: Content) {
        try {
            manager?.notify(ID, build(content))
        } catch (_: SecurityException) {
            // Notifications turned off between building and posting.
        }
    }

    private fun action(icon: Int, title: String, action: String) =
        Notification.Action.Builder(Icon.createWithResource(context, icon), title, service(action)).build()

    private fun service(action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(), SoundscapeService.intent(context, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val system = manager ?: return
        if (system.getNotificationChannel(CHANNEL) != null) return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, "Soundscapes", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows what's playing, with buttons to pause or stop it, and the sleep timer's time left."
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL = "soundscapes"
        const val ID = 1004 // The Timer uses 1001 and 1002, the Stopwatch 1003.
    }
}
