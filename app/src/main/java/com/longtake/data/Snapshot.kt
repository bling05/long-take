package com.longtake.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The starter catalog bundled with the app (assets/catalog.json): the starter channels, curated
 * playlists, Discover sections, their videos and shared synopses. A new install loads it instead of
 * looking everything up on YouTube, which would use about a quarter of the shared daily quota.
 * Made before each release by SnapshotTool (see the README).
 */
data class Snapshot(
    val createdAt: Long,
    val seedsVersion: Int,
    val channels: List<ChannelEntity>,
    val playlists: List<PlaylistEntity>,
    val videos: List<VideoEntity>,
    val sections: Map<String, List<String>>,
    val synopses: List<SynopsisEntity>,
) {
    fun toJson(): String = JSONObject()
        .put("createdAt", createdAt)
        .put("seedsVersion", seedsVersion)
        .put("channels", JSONArray(channels.map { it.toJson() }))
        .put("playlists", JSONArray(playlists.map { JSONObject().put("id", it.id).put("title", it.title).put("channelTitle", it.channelTitle) }))
        .put("videos", JSONArray(videos.map { it.toJson() }))
        .put("sections", JSONObject(sections.mapValues { JSONArray(it.value) }))
        .put("synopses", JSONArray(synopses.map { JSONObject().put("videoId", it.videoId).put("text", it.text).put("topics", it.topics).put("createdAt", it.createdAt) }))
        .toString()

    companion object {
        const val ASSET = "catalog.json"

        fun fromJson(text: String): Snapshot {
            val j = JSONObject(text)
            val sections = j.getJSONObject("sections")
            return Snapshot(
                createdAt = j.getLong("createdAt"),
                seedsVersion = j.getInt("seedsVersion"),
                channels = j.getJSONArray("channels").objects().map { it.toChannel() },
                playlists = j.getJSONArray("playlists").objects().map { PlaylistEntity(it.getString("id"), it.getString("title"), it.optString("channelTitle")) },
                videos = j.getJSONArray("videos").objects().map { it.toVideo() },
                sections = sections.keys().asSequence().associateWith { k -> sections.getJSONArray(k).strings() },
                synopses = j.getJSONArray("synopses").objects().map {
                    SynopsisEntity(it.getString("videoId"), it.getString("text"), it.getString("topics"), it.getLong("createdAt"))
                },
            )
        }

        private fun ChannelEntity.toJson() = JSONObject()
            .put("id", id).put("title", title).put("handle", handle).put("thumbnailUrl", thumbnailUrl)
            .put("subscribers", subscribers).put("videoCount", videoCount).put("createdAt", createdAt)
            .put("uploadsPlaylistId", uploadsPlaylistId).put("isSource", isSource)

        private fun JSONObject.toChannel() = ChannelEntity(
            id = getString("id"), title = getString("title"), handle = optString("handle"),
            thumbnailUrl = optString("thumbnailUrl"), subscribers = optLong("subscribers"),
            videoCount = optLong("videoCount"), createdAt = optLong("createdAt"),
            uploadsPlaylistId = optString("uploadsPlaylistId"), isSource = optBoolean("isSource"),
            // The snapshot already holds each source's most-viewed videos.
            topFetched = optBoolean("isSource"),
        )

        private fun VideoEntity.toJson() = JSONObject()
            .put("id", id).put("title", title).put("channelId", channelId).put("channelTitle", channelTitle)
            .put("publishedAt", publishedAt).put("durationSec", durationSec).put("views", views).put("likes", likes)
            .put("thumbnailUrl", thumbnailUrl).put("description", description).put("tags", tags)
            .put("categoryId", categoryId).put("fetchedAt", fetchedAt).put("curated", curated)

        private fun JSONObject.toVideo() = VideoEntity(
            id = getString("id"), title = getString("title"), channelId = getString("channelId"),
            channelTitle = optString("channelTitle"), publishedAt = optLong("publishedAt"),
            durationSec = optLong("durationSec"), views = optLong("views"), likes = optLong("likes"),
            thumbnailUrl = optString("thumbnailUrl"), description = optString("description"),
            tags = optString("tags"), categoryId = optString("categoryId"),
            fromSource = true, fetchedAt = optLong("fetchedAt"), curated = optBoolean("curated"),
        )

        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    }
}
