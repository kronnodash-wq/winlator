package com.bionicmusic.service

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.bionicmusic.App
import com.bionicmusic.R
import com.bionicmusic.data.model.Song
import com.bionicmusic.notification.MusicNotificationManager
import com.bionicmusic.player.BionicPlayer
import com.google.common.collect.ImmutableList

/**
 * Provides the custom, square-cornered playback notification to the
 * MediaSessionService. It reuses [MusicNotificationManager] to build the
 * RemoteViews-based notification (showing only the song name when collapsed,
 * full controls + album-art background when expanded) and falls back to a
 * minimal notification when no track is loaded.
 */
@UnstableApi
class BionicMediaNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    private val builder = MusicNotificationManager(context)

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val notification: Notification = try {
            val song: Song? = BionicPlayer.current
            val isPlaying = try {
                mediaSession.player.playWhenReady &&
                    mediaSession.player.playbackState != Player.STATE_IDLE
            } catch (_: Exception) { false }

            if (song != null) {
                builder.build(song, isPlaying, null)
            } else {
                NotificationCompat.Builder(context, App.CHANNEL_PLAYBACK)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .build()
            }
        } catch (_: Exception) {
            NotificationCompat.Builder(context, App.CHANNEL_PLAYBACK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .build()
        }

        return MediaNotification(MusicNotificationManager.NOTIF_ID, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle
    ): Boolean = false
}
