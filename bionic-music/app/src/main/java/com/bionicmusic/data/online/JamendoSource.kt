package com.bionicmusic.data.online

import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.data.model.OnlineSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Jamendo source. Uses the public client id for basic, no-auth access.
 */
object JamendoSource : OnlineSource {

    private const val CLIENT_ID = "b6747d04"
    private const val BASE = "https://api.jamendo.com/v3.0"

    override suspend fun search(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE/tracks/?client_id=$CLIENT_ID&format=jsonpretty&limit=20" +
            "&namesearch=$q&audioformat=mp32&include=musicinfo"
        val body = Http.get(url) ?: return@withContext emptyList()
        val result = ArrayList<OnlineSong>()
        try {
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                result.add(
                    OnlineSong(
                        id = o.optString("id"),
                        title = o.optString("name").ifEmpty { "Unknown" },
                        artist = o.optString("artist_name").ifEmpty { "Unknown" },
                        durationSec = o.optInt("duration", 0),
                        thumbnailUrl = o.optString("album_image").ifEmpty { null },
                        source = OnlineSourceType.JAMENDO,
                        streamUrl = o.optString("audio").ifEmpty { null },
                        flacUrl = "$BASE/tracks/file/?client_id=$CLIENT_ID" +
                            "&id=${o.optString("id")}&audioformat=flac"
                    )
                )
            }
        } catch (_: Exception) {
        }
        result
    }

    override suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String? =
        withContext(Dispatchers.IO) {
            when (format) {
                AudioFormat.FLAC -> song.flacUrl ?: song.streamUrl
                AudioFormat.MP3 -> song.streamUrl
            }
        }
}
