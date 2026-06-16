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

    // Known public client_ids used by SoundCloud's own web client (tried in order).
    private val KNOWN_IDS = listOf(
        "iZIs9mchVcX5lhVRyQGGAYlNPVldzAoX",
        "a3e059563d7fd3372b49b37f00a00bcf",
        "2t9loNQH90kzJcsFCODdigxfp325aq4z",
        "XsONPOTSoTzFG4bImBSzEMCP2LmxmKoO"
    )

    @Volatile private var clientId: String = KNOWN_IDS[0]
    @Volatile private var ready: Boolean = false

    /**
     * Scrapes SoundCloud's homepage JS bundles to find the active client_id.
     * SoundCloud embeds it in minified JS as client_id:"<id>" or client_id="<id>".
     */
    @Synchronized
    private fun scrapeClientId(): String? {
        val html = Http.get("https://soundcloud.com") ?: return null
        val scriptUrls = Regex(
            """<script[^>]+src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)""""
        ).findAll(html).map { it.groupValues[1] }.take(12).toList()

        val patterns = listOf(
            Regex("""[,{(\s]client_id[=:"]{1,2}([a-zA-Z0-9]{20,60})[,"'\s}]"""),
            Regex("""client_id:"([a-zA-Z0-9]{20,60})""""),
            Regex("""client_id=([a-zA-Z0-9]{20,60})[&,\s]""")
        )
        for (url in scriptUrls) {
            val js = Http.get(url) ?: continue
            for (pattern in patterns) {
                val m = pattern.find(js) ?: continue
                val id = m.groupValues[1]
                if (id.length in 20..60) return id
            }
        }
        return null
    }

    /** Initializes client_id on first use: tries JS scraping then falls back to known IDs. */
    @Synchronized
    private fun ensureReady() {
        if (ready) return
        val scraped = scrapeClientId()
        if (scraped != null) {
            clientId = scraped
            ready = true
            return
        }
        // Scraping failed — try each known ID with a lightweight probe
        for (id in KNOWN_IDS) {
            val probe = Http.get("$BASE/resolve?url=https://soundcloud.com&client_id=$id")
            if (probe != null) {
                clientId = id
                ready = true
                return
            }
        }
        // Last resort: use the first known ID and hope for the best
        clientId = KNOWN_IDS[0]
        ready = true
    }

    /** Forces a re-scrape on next use (called after a failed request). */
    @Synchronized
    private fun invalidate() {
        ready = false
    }

    /**
     * Makes a GET request using the current client_id. If it returns null
     * (network error or 401), invalidates the cached ID and retries once with
     * a freshly scraped one.
     */
    private fun scGet(url: String): String? {
        ensureReady()
        val first = Http.get(url)
        if (first != null) return first

        // First attempt failed — refresh and retry
        invalidate()
        ensureReady()
        val retried = url.replace(Regex("""client_id=[^&]+"""), "client_id=$clientId")
        return Http.get(retried)
    }

    override suspend fun search(query: String): List<OnlineSong> = withContext(Dispatchers.IO) {
        ensureReady()
        val q = URLEncoder.encode(query, "UTF-8")
        val body = scGet("$BASE/search/tracks?q=$q&client_id=$clientId&limit=25")
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

                // Prefer progressive (direct MP3 file) over HLS for easier download & seek
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
                        // flacUrl left null — SoundCloud only provides MP3
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves the transcoding endpoint in [song.streamUrl] to the actual CDN
     * URL. SoundCloud only provides MP3; FLAC format falls back to MP3 as well.
     */
    override suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String? =
        withContext(Dispatchers.IO) {
            val endpoint = song.streamUrl ?: return@withContext null
            val body = scGet("$endpoint?client_id=$clientId") ?: return@withContext null
            try {
                JSONObject(body).optString("url").ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }
}
