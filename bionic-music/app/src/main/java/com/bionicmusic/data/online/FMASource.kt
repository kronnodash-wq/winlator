package com.bionicmusic.data.online

import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.data.model.OnlineSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Free Music Archive source.
 */
object FMASource : OnlineSource {

    private const val API_KEY = "60BLHNQCAOUFPIBZ"
    private const val BASE = "https://freemusicarchive.org/api/get"

    override suspend fun search(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE/tracks.json?api_key=$API_KEY&limit=20&search=$q"
        val body = Http.get(url) ?: return@withContext emptyList()
        val result = ArrayList<OnlineSong>()
        try {
            val dataset = JSONObject(body).optJSONArray("dataset") ?: return@withContext emptyList()
            for (i in 0 until dataset.length()) {
                val o = dataset.optJSONObject(i) ?: continue
                val seconds = parseDuration(o.optString("track_duration"))
                result.add(
                    OnlineSong(
                        id = o.optString("track_id"),
                        title = o.optString("track_title").ifEmpty { "Unknown" },
                        artist = o.optString("artist_name").ifEmpty { "Unknown" },
                        durationSec = seconds,
                        thumbnailUrl = o.optString("track_image_file").ifEmpty { null },
                        source = OnlineSourceType.FMA,
                        streamUrl = o.optString("track_listen_url").ifEmpty { null }
                    )
                )
            }
        } catch (_: Exception) {
        }
        result
    }

    override suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String? = song.streamUrl

    /** FMA returns durations as "mm:ss" or "hh:mm:ss". */
    private fun parseDuration(raw: String): Int {
        if (raw.isBlank()) return 0
        val parts = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }
}

/** Aggregates all online sources, querying them concurrently. */
object OnlineRepository {
    private val sources = listOf(PipedSource, JamendoSource, FMASource)

    suspend fun searchAll(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val all = ArrayList<OnlineSong>()
        for (s in sources) {
            try {
                all.addAll(s.search(query))
            } catch (_: Exception) {
            }
        }
        all
    }

    fun sourceFor(song: OnlineSong): OnlineSource = when (song.source) {
        OnlineSourceType.PIPED -> PipedSource
        OnlineSourceType.JAMENDO -> JamendoSource
        OnlineSourceType.FMA -> FMASource
    }
}
