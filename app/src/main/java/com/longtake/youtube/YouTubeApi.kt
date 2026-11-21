package com.longtake.youtube

import com.longtake.BuildConfig
import com.longtake.data.ChannelEntity
import com.longtake.data.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

class YouTubeException(message: String) : Exception(message)

/**
 * Thin client for the official YouTube Data API v3.
 * Quota costs (10,000 units/day free): search = 100, everything else used here = 1.
 */
class YouTubeApi(
    private val http: OkHttpClient,
    private val apiKey: suspend () -> String,
) {
    private val base = "https://www.googleapis.com/youtube/v3/"

    private suspend fun get(path: String, params: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val b: HttpUrl.Builder = (base + path).toHttpUrl().newBuilder()
            params.forEach { (k, v) -> b.addQueryParameter(k, v) }
            val req = Request.Builder()
            val key = apiKey()
            if (key.isBlank()) throw YouTubeException("Add your YouTube API key in Settings first.")
            b.addQueryParameter("key", key)
            // Identify the app so a key restricted to "Android apps" in Google Cloud accepts the request.
            req.header("X-Android-Package", BuildConfig.APPLICATION_ID)
            req.header("X-Android-Cert", SIGNING_SHA1)
            http.newCall(req.url(b.build()).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(body).getJSONObject("error").getString("message")
                    }.getOrNull() ?: "HTTP ${resp.code}"
                    val friendly = when {
                        msg.contains("quota", true) -> "YouTube's daily free limit is used up. It resets at midnight Pacific time."
                        msg.contains("API key not valid", true) -> "The YouTube API key isn't valid. Check it in Settings."
                        msg.contains("has not been used", true) || msg.contains("disabled", true) ->
                            "YouTube Data API isn't enabled for your Google Cloud project."
                        else -> "YouTube error: $msg"
                    }
                    throw YouTubeException(friendly)
                }
                JSONObject(body)
            }
        }

    suspend fun channelByHandle(handle: String): ChannelEntity? {
        val h = if (handle.startsWith("@")) handle else "@$handle"
        val j = get("channels", mapOf("part" to "snippet,statistics,contentDetails", "forHandle" to h))
        return j.optJSONArray("items")?.optJSONObject(0)?.let(::parseChannel)
    }

    suspend fun channelsById(ids: List<String>): List<ChannelEntity> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(50).flatMap { chunk ->
            val j = get("channels", mapOf("part" to "snippet,statistics,contentDetails", "id" to chunk.joinToString(","), "maxResults" to "50"))
            j.optJSONArray("items").objects().map(::parseChannel)
        }
    }

    /** 100 quota units. */
    suspend fun searchChannel(name: String): ChannelEntity? {
        val j = get("search", mapOf("part" to "snippet", "type" to "channel", "q" to name, "maxResults" to "1"))
        val id = j.optJSONArray("items")?.optJSONObject(0)?.optJSONObject("id")?.optString("channelId")
        return id?.takeIf { it.isNotBlank() }?.let { channelsById(listOf(it)).firstOrNull() }
    }

    private fun parseChannel(o: JSONObject): ChannelEntity {
        val sn = o.optJSONObject("snippet") ?: JSONObject()
        val st = o.optJSONObject("statistics") ?: JSONObject()
        val cd = o.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")
        return ChannelEntity(
            id = o.getString("id"),
            title = sn.optString("title"),
            handle = sn.optString("customUrl"),
            thumbnailUrl = sn.optJSONObject("thumbnails")?.bestThumb().orEmpty(),
            subscribers = st.optString("subscriberCount").toLongOrNull() ?: 0,
            videoCount = st.optString("videoCount").toLongOrNull() ?: 0,
            createdAt = parseTime(sn.optString("publishedAt")),
            uploadsPlaylistId = cd?.optString("uploads").orEmpty(),
        )
    }

    data class PlaylistInfo(val id: String, val title: String, val channelTitle: String)

    suspend fun playlistInfo(id: String): PlaylistInfo? {
        val j = get("playlists", mapOf("part" to "snippet", "id" to id))
        val o = j.optJSONArray("items")?.optJSONObject(0) ?: return null
        val sn = o.getJSONObject("snippet")
        return PlaylistInfo(id, sn.optString("title"), sn.optString("channelTitle"))
    }

    /** Video IDs in a playlist (newest first for uploads playlists). 1 unit per page of 50. */
    suspend fun playlistVideoIds(playlistId: String, maxPages: Int = 1): List<String> {
        val ids = mutableListOf<String>()
        var page: String? = null
        repeat(maxPages) {
            val params = mutableMapOf("part" to "contentDetails", "playlistId" to playlistId, "maxResults" to "50")
            page?.let { params["pageToken"] = it }
            val j = get("playlistItems", params)
            j.optJSONArray("items").objects().mapNotNullTo(ids) {
                it.optJSONObject("contentDetails")?.optString("videoId")?.takeIf { v -> v.isNotBlank() }
            }
            page = j.optString("nextPageToken").takeIf { it.isNotBlank() } ?: return ids
        }
        return ids
    }

    enum class Order(val api: String) { RELEVANCE("relevance"), VIEWS("viewCount"), DATE("date") }

    /** 100 quota units. Returns video IDs of long (20+ min) videos. */
    /** [longOnly] asks YouTube for videos over 20 minutes only. */
    suspend fun searchVideos(
        query: String, order: Order = Order.RELEVANCE, channelId: String? = null, max: Int = 25, longOnly: Boolean = true,
    ): List<String> {
        val params = mutableMapOf(
            "part" to "id", "type" to "video",
            "order" to order.api, "maxResults" to max.toString(), "relevanceLanguage" to "en",
        )
        if (longOnly) params["videoDuration"] = "long"
        if (query.isNotBlank()) params["q"] = query
        channelId?.let { params["channelId"] = it }
        val j = get("search", params)
        return j.optJSONArray("items").objects().mapNotNull { it.optJSONObject("id")?.optString("videoId") }
    }

    suspend fun videos(ids: List<String>, fromSource: Boolean): List<VideoEntity> {
        if (ids.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return ids.distinct().chunked(50).flatMap { chunk ->
            val j = get("videos", mapOf("part" to "snippet,contentDetails,statistics", "id" to chunk.joinToString(",")))
            j.optJSONArray("items").objects().mapNotNull { o ->
                val sn = o.optJSONObject("snippet") ?: return@mapNotNull null
                if (sn.optString("liveBroadcastContent") == "upcoming" || sn.optString("liveBroadcastContent") == "live") return@mapNotNull null
                val st = o.optJSONObject("statistics") ?: JSONObject()
                val cd = o.optJSONObject("contentDetails") ?: JSONObject()
                VideoEntity(
                    id = o.getString("id"),
                    title = sn.optString("title"),
                    channelId = sn.optString("channelId"),
                    channelTitle = sn.optString("channelTitle"),
                    publishedAt = parseTime(sn.optString("publishedAt")),
                    durationSec = runCatching { Duration.parse(cd.optString("duration")).seconds }.getOrDefault(0),
                    views = st.optString("viewCount").toLongOrNull() ?: 0,
                    likes = st.optString("likeCount").toLongOrNull() ?: 0,
                    thumbnailUrl = sn.optJSONObject("thumbnails")?.bestThumb().orEmpty(),
                    description = sn.optString("description").take(1500),
                    tags = sn.optJSONArray("tags")?.let { t -> (0 until t.length()).joinToString(",") { t.optString(it) } }.orEmpty(),
                    categoryId = sn.optString("categoryId"),
                    fromSource = fromSource,
                    fetchedAt = now,
                )
            }
        }
    }

    companion object {
        /** SHA-1 of keystore/essayfinder.jks, without colons (also entered in Google Cloud and Firebase). */
        private const val SIGNING_SHA1 = "50E96DEA409E02F9AB96DBEC163E0209EA19CD04"

        fun parseTime(s: String): Long = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0)

        private fun JSONObject.bestThumb(): String {
            for (k in listOf("maxres", "standard", "high", "medium", "default")) {
                optJSONObject(k)?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return ""
        }

        fun org.json.JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
