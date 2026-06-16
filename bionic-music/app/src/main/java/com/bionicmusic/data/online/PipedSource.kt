package com.bionicmusic.data.online

import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.data.model.OnlineSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object PipedSource : OnlineSource {

    // Multiple public Piped instances — tries each until one works
    private val INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://piped-api.privacy.com.de",
        "https://api.piped.projectsegfau.lt",
        "https://pipedapi.syncpundit.io"
    )

    override suspend fun search(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        for (base in INSTANCES) {
            val body = Http.get("$base/search?q=$q&filter=all") ?: continue
            try {
                val items = JSONObject(body).optJSONArray("items") ?: continue
                val result = ArrayList<OnlineSong>()
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    // Only include audio/music-like items (type "stream" or no type)
                    val type = o.optString("type")
                    if (type == "channel" || type == "playlist") continue
                    val url = o.optString("url")
                    val videoId = url.substringAfter("v=", "").substringBefore("&")
                        .takeIf { it.isNotEmpty() }
                        ?: o.optString("videoId").takeIf { it.isNotEmpty() }
                        ?: continue
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
                if (result.isNotEmpty()) return@withContext result
            } catch (_: Exception) {
                continue
            }
        }
        emptyList()
    }

    override suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String? =
        withContext(Dispatchers.IO) {
            for (base in INSTANCES) {
                val body = Http.get("$base/streams/${song.id}") ?: continue
                try {
                    val audioStreams = JSONObject(body).optJSONArray("audioStreams") ?: continue
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
                    if (bestUrl != null) return@withContext bestUrl
                } catch (_: Exception) {
                    continue
                }
            }
            null
        }
}
