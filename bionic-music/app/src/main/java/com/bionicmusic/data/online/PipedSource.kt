package com.bionicmusic.data.online

import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.data.model.OnlineSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Piped (YouTube proxy) source. No auth required.
 */
object PipedSource : OnlineSource {

    private const val BASE = "https://pipedapi.kavin.rocks"

    override suspend fun search(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val body = Http.get("$BASE/search?q=$q&filter=music_songs") ?: return@withContext emptyList()
        val result = ArrayList<OnlineSong>()
        try {
            val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val url = o.optString("url") // /watch?v=ID
                val videoId = url.substringAfter("v=", "").substringBefore("&")
                if (videoId.isEmpty()) continue
                result.add(
                    OnlineSong(
                        id = videoId,
                        title = o.optString("title").ifEmpty { "Unknown" },
                        artist = o.optString("uploaderName").ifEmpty { "Unknown" },
                        durationSec = o.optInt("duration", 0),
                        thumbnailUrl = o.optString("thumbnail").ifEmpty { null },
                        source = OnlineSourceType.PIPED
                    )
                )
            }
        } catch (_: Exception) {
        }
        result
    }

    override suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String? =
        withContext(Dispatchers.IO) {
            val body = Http.get("$BASE/streams/${song.id}") ?: return@withContext null
            try {
                val audioStreams = JSONObject(body).optJSONArray("audioStreams")
                    ?: return@withContext null
                var bestUrl: String? = null
                var bestBitrate = -1
                for (i in 0 until audioStreams.length()) {
                    val s = audioStreams.optJSONObject(i) ?: continue
                    val bitrate = s.optInt("bitrate", 0)
                    if (bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = s.optString("url").ifEmpty { null }
                    }
                }
                bestUrl
            } catch (_: Exception) {
                null
            }
        }
}
