package com.bionicmusic.data.online

import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.data.model.OnlineSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * Free Music Archive source — disabled because it requires a paid/gated API key.
 * Returns an empty list gracefully so other sources are unaffected.
 */
object FMASource : OnlineSource {
    override suspend fun search(query: String): List<OnlineSong> = emptyList()
    override suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String? = song.streamUrl
}

/** Aggregates all online sources, querying them independently so one failure doesn't kill all results. */
object OnlineRepository {
    // SoundCloud is the primary source; Jamendo provides CC music as a secondary.
    private val sources = listOf<OnlineSource>(SoundCloudSource, JamendoSource)

    suspend fun searchAll(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val all = ArrayList<OnlineSong>()
        for (source in sources) {
            try {
                all.addAll(source.search(query))
            } catch (_: Exception) {
                // One source failing must not prevent results from other sources.
            }
        }
        all
    }

    fun sourceFor(song: OnlineSong): OnlineSource = when (song.source) {
        OnlineSourceType.SOUNDCLOUD -> SoundCloudSource
        OnlineSourceType.JAMENDO -> JamendoSource
        OnlineSourceType.PIPED -> PipedSource
        OnlineSourceType.FMA -> FMASource
    }
}
