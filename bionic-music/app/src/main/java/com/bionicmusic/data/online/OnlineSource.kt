package com.bionicmusic.data.online

import com.bionicmusic.data.model.AudioFormat
import com.bionicmusic.data.model.OnlineSong
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Common contract for an online music provider.
 */
interface OnlineSource {
    /** Search the provider for [query]; returns an empty list on failure. */
    suspend fun search(query: String): List<OnlineSong>

    /**
     * Resolve a playable/downloadable URL for [song] in the requested [format].
     * Returns null if it cannot be resolved.
     */
    suspend fun resolveUrl(song: OnlineSong, format: AudioFormat): String?
}

/** Shared OkHttp client tuned for the music APIs. */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun get(url: String): String? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "BionicMusic/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    } catch (e: Exception) {
        null
    }
}
