package com.bionicmusic.data.model

import android.content.ContentUris
import android.net.Uri
import android.os.Parcelable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.parcelize.Parcelize

/**
 * A locally stored audio track scanned from MediaStore.
 */
@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val data: String,
    val year: Int,
    val track: Int
) : Parcelable {

    /** Content URI for the audio file. */
    val uri: Uri
        get() = ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
        )

    /** Album art URI derived from the album id. */
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), albumId
        )

    /** Stable id used as the Media3 media id. */
    val mediaId: String get() = "local:$id"

    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        const val SORT_NAME = "name"
    }
}
