package com.bionicmusic.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.bionicmusic.App
import com.bionicmusic.MainActivity
import com.bionicmusic.R
import com.bionicmusic.data.model.Song

/**
 * Builds the custom, square-cornered playback notification used for the
 * status bar, expanded notification shade and lock screen. The collapsed
 * (status bar chip) form shows only the song name; the expanded form shows
 * album art, song info and the shuffle / prev / play / next / lyrics controls.
 *
 * The "Volumen" label replaces the system "Salida multimedia" wording via the
 * custom RemoteViews layout.
 */
class MusicNotificationManager(private val context: Context) {

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.bionicmusic.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.bionicmusic.action.NEXT"
        const val ACTION_PREV = "com.bionicmusic.action.PREV"
        const val ACTION_SHUFFLE = "com.bionicmusic.action.SHUFFLE"
        const val ACTION_LYRICS = "com.bionicmusic.action.LYRICS"
    }

    fun build(song: Song, isPlaying: Boolean, sessionToken: Any?): android.app.Notification {
        val art = loadArt(song)

        val collapsed = RemoteViews(context.packageName, R.layout.notification_player)
        val expanded = RemoteViews(context.packageName, R.layout.notification_player)

        listOf(collapsed, expanded).forEach { rv ->
            rv.setTextViewText(R.id.notif_title, song.title)
            rv.setTextViewText(R.id.notif_artist, song.artist)
            rv.setImageViewResource(
                R.id.notif_play,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            if (art != null) {
                rv.setImageViewBitmap(R.id.notif_art, art)
                rv.setImageViewBitmap(R.id.notif_bg, art)
            } else {
                rv.setImageViewResource(R.id.notif_art, R.drawable.ic_album_placeholder)
            }
            rv.setOnClickPendingIntent(R.id.notif_shuffle, pending(ACTION_SHUFFLE))
            rv.setOnClickPendingIntent(R.id.notif_prev, pending(ACTION_PREV))
            rv.setOnClickPendingIntent(R.id.notif_play, pending(ACTION_PLAY_PAUSE))
            rv.setOnClickPendingIntent(R.id.notif_next, pending(ACTION_NEXT))
            rv.setOnClickPendingIntent(R.id.notif_lyrics, pending(ACTION_LYRICS))
        }

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, App.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(contentIntent)
            .setColorized(true)
            .setColor(0xFF000000.toInt())
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)

        return builder.build()
    }

    private fun pending(action: String): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
    }

    private fun loadArt(song: Song): Bitmap? = try {
        context.contentResolver.openInputStream(song.albumArtUri)?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (_: Exception) {
        null
    }
}
