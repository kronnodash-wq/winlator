package com.bionicmusic.data.online

import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import com.bionicmusic.data.model.OnlineSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object SoundCloudSource : OnlineSource {

    private const val BASE = "https://api-v2.soundcloud.com"

    // SoundCloud's own web client uses this client_id; refresh dynamically if stale.
    @Volatile private var clientId = "iZIs9mchVcX5lhVRyQGGAYlNPVldzAoX"

    private fun isStale(body: String) =
        "401" in body && ("Invalid client_id" in body || "\"error\"" in body)

    /** Scrapes SoundCloud's homepage JS files to extract a fresh client_id. */
    private fun refreshClientId(): Boolean {
        val html = Http.get("https://soundcloud.com") ?: return false
        val scriptUrls = Regex("""<script[^>]+src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
            .findAll(html).map { it.groupValues[1] }.take(8).toList()
        for (url in scriptUrls) {
            val js = Http.get(url) ?: continue
            val match = Regex("""client_id[=:"]+([a-zA-Z0-9]{20,50})""").find(js) ?: continue
            clientId = match.groupValues[1]
            return true
        }
        return false
    }

    /**
     * Fetches [url], retrying once with a freshly extracted client_id if the
     * response signals a stale/invalid client_id (HTTP 401).
     */
    private fun fetch(url: String): String? {
        val body = Http.get(url)
        if (body != null && !isStale(body)) return body
        if (!refreshClientId()) return null
        val retryUrl = url.replace(Regex("client_id=[^&]+"), "client_id=$clientId")
        val retryBody = Http.get(retryUrl) ?: return null
        return if (isStale(retryBody)) null else retryBody
    }

    override suspend fun search(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val body = fetch("$BASE/search/tracks?q=$q&client_id=$clientId&limit=25")
            ?: return@withContext emptyList()
        try {
            val collection = JSONObject(body).optJSONArray("collection")
                ?: return@withContext emptyList()
            val result = ArrayList<OnlineSong>()
            for (i in 0 until collection.length()) {
                val track = collection.optJSONObject(i) ?: continue
                if (!track.optBoolean("streamable", true)) continue
                val id = track.optLong("id", 0L).takeIf { it != 0L }?.toString() ?: continue
                val title = track.optString("title").ifEmpty { "Unknown" }
                val artist = track.optJSONObject("user")
                    ?.optString("username")?.ifEmpty { "Unknown" } ?: "Unknown"
                val durationMs = track.optLong("duration", 0L)
                val artwork = track.optString("artwork_url").ifEmpty { null }
                    ?.replace("-large.", "-t500x500.")

                // Prefer progressive (direct file) over HLS for easier download/seek
                val transcodings = track.optJSONObject("media")?.optJSONArray("transcodings")
                var progressiveUrl: String? = null
                var hlsUrl: String? = null
                if (transcodings != null) {
                    for (j in 0 until transcodings.length()) {
                        val t = transcodings.optJSONObject(j) ?: continue
                        val tUrl = t.optString("url").ifEmpty { null } ?: continue
                        val protocol = t.optJSONObject("format")?.optString("protocol") ?: continue
                        val mime = t.optJSONObject("format")?.optString("mime_type") ?: ""
                        when {
                            protocol == "progressive" && progressiveUrl == null -> progressiveUrl = tUrl
                            protocol == "hls" && "mpeg" in mime && hlsUrl == null -> hlsUrl = tUrl
                        }
                    }
                }
                val streamEndpoint = progressiveUrl ?: hlsUrl ?: continue

                result.add(
                    OnlineSong(
                        id = id,
                        title = title,
                        artist = artist,
                        durationSec = (durationMs / 1000).toInt(),
                        thumbnailUrl = artwork,
                        source = OnlineSourceType.SOUNDCLOUD,
                        streamUrl = streamEndpoint
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves the transcoding endpoint stored in [song.streamUrl] to the
     * actual CDN URL needed by ExoPlayer and the downloader.
     */
    override suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String? =
        withContext(Dispatchers.IO) {
            val endpoint = song.streamUrl ?: return@withContext null
            val body = fetch("$endpoint?client_id=$clientId") ?: return@withContext null
            try {
                JSONObject(body).optString("url").ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }
}
