package com.bionicmusic.util

import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.bionicmusic.App
import com.bionicmusic.R
import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.data.online.Http
import com.bionicmusic.data.online.OnlineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.OutputStream

/**
 * Downloads online tracks to the device Music directory with a progress
 * notification. Saves the file in the requested [AudioFormat] (the bytes are
 * taken as-is from the highest quality source available for that format).
 */
object DownloadManager {

    sealed class Result {
        object Success : Result()
        data class Failure(val reason: String) : Result()
    }

    suspend fun download(
        context: Context,
        song: OnlineSong,
        format: AudioFormat
    ): Result = withContext(Dispatchers.IO) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = (song.mediaId.hashCode() and 0x7FFFFFFF)

        showProgress(context, nm, notifId, song.title, 0, true)

        val url = OnlineRepository.sourceFor(song).resolveUrl(song, format)
            ?: return@withContext fail(context, nm, notifId, song.title)

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "BionicMusic/1.0 (Android)")
                .build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext fail(context, nm, notifId, song.title)
                }
                val body = resp.body ?: return@withContext fail(context, nm, notifId, song.title)
                val total = body.contentLength()
                val fileName = sanitize("${song.artist} - ${song.title}") + "." + format.ext

                val out = openOutput(context, fileName, format)
                    ?: return@withContext fail(context, nm, notifId, song.title)

                out.use { sink ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(8 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastPct = -1
                        while (input.read(buf).also { read = it } != -1) {
                            sink.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    showProgress(context, nm, notifId, song.title, pct, false)
                                }
                            }
                        }
                    }
                }
            }
            complete(context, nm, notifId, song.title)
            Result.Success
        } catch (e: Exception) {
            fail(context, nm, notifId, song.title)
        }
    }

    private fun openOutput(context: Context, fileName: String, format: AudioFormat): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, format.mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/BionicMusic")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val stream = resolver.openOutputStream(uri)
            // Mark not pending once we return; caller writes then we flip flag.
            pendingUri = uri to values
            stream
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "BionicMusic"
            )
            if (!dir.exists()) dir.mkdirs()
            java.io.FileOutputStream(java.io.File(dir, fileName))
        }
    }

    @Volatile
    private var pendingUri: Pair<android.net.Uri, ContentValues>? = null

    private fun finalizePending(context: Context) {
        val (uri, values) = pendingUri ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        pendingUri = null
    }

    private fun showProgress(
        context: Context, nm: NotificationManager, id: Int,
        title: String, pct: Int, indeterminate: Boolean
    ) {
        val n = NotificationCompat.Builder(context, App.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.downloading))
            .setContentText(title)
            .setOngoing(true)
            .setProgress(100, pct, indeterminate)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(id, n)
    }

    private fun complete(context: Context, nm: NotificationManager, id: Int, title: String): Result {
        finalizePending(context)
        val n = NotificationCompat.Builder(context, App.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.download_complete))
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
        return Result.Success
    }

    private fun fail(context: Context, nm: NotificationManager, id: Int, title: String): Result {
        pendingUri = null
        val n = NotificationCompat.Builder(context, App.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.download_failed))
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
        return Result.Failure("download failed")
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
}
