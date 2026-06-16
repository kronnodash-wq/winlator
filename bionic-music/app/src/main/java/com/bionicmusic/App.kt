package com.bionicmusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val playback = NotificationChannel(
            CHANNEL_PLAYBACK,
            getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val download = NotificationChannel(
            CHANNEL_DOWNLOAD,
            getString(R.string.channel_download),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        nm.createNotificationChannel(playback)
        nm.createNotificationChannel(download)
    }

    companion object {
        const val CHANNEL_PLAYBACK = "bionic_playback"
        const val CHANNEL_DOWNLOAD = "bionic_download"
    }
}
