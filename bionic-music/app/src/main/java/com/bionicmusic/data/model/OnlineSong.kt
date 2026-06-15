package com.bionicmusic.data.model

import android.net.Uri
import android.os.Parcelable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.parcelize.Parcelize

/** Source of an online track. */
enum class OnlineSourceType { PIPED, JAMENDO, FMA }

/** Audio download/stream container format. */
enum class AudioFormat(val ext: String, val mime: String) {
    MP3("mp3", "audio/mpeg"),
    FLAC("flac", "audio/flac")
}

/**
 * A track returned by an online music source. The [streamUrl] may need to be
 * resolved lazily (e.g. Piped) before playback/download.
 */
@Parcelize
data class OnlineSong(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val thumbnailUrl: String?,
    val source: OnlineSourceType,
    val streamUrl: String? = null,
    val flacUrl: String? = null
) : Parcelable {

    val mediaId: String get() = "online:${source.name}:$id"

    val durationLabel: String
        get() {
            val m = durationSec / 60
            val s = durationSec % 60
            return "%d:%02d".format(m, s)
        }

    fun toMediaItem(resolvedUrl: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply { thumbnailUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(resolvedUrl)
            .setMediaMetadata(metadata)
            .build()
    }
}
