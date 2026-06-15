package com.bionicmusic.data.local

import android.content.Context
import android.provider.MediaStore
import com.bionicmusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the device MediaStore for local audio tracks.
 */
object MusicScanner {

    suspend fun scan(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (c.moveToNext()) {
                val duration = c.getLong(durCol)
                if (duration < 1000) continue // skip sub-second clips
                songs.add(
                    Song(
                        id = c.getLong(idCol),
                        title = c.getString(titleCol) ?: "Unknown",
                        artist = c.getString(artistCol) ?: "Unknown",
                        album = c.getString(albumCol) ?: "Unknown",
                        albumId = c.getLong(albumIdCol),
                        durationMs = duration,
                        data = c.getString(dataCol) ?: "",
                        year = c.getInt(yearCol),
                        track = c.getInt(trackCol)
                    )
                )
            }
        }
        songs
    }
}
