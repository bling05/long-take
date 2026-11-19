package com.longtake

import com.longtake.data.DEFAULT_MIN_MINUTES
import com.longtake.data.Sections
import com.longtake.data.Seeds
import com.longtake.data.Snapshot
import com.longtake.data.SynopsisEntity
import com.longtake.youtube.YouTubeApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Makes the starter catalog bundled with the app (app/src/main/assets/catalog.json), doing what a
 * new phone's first refresh would. Run before each release:
 *
 *     SNAPSHOT=1 ./gradlew testDebugUnitTest --tests com.longtake.SnapshotTool
 *
 * Uses about 2,500 units of the YouTube key in secrets.properties.
 */
class SnapshotTool {
    private val http = OkHttpClient()

    @Test fun makeSnapshot() = runBlocking {
        assumeTrue("set SNAPSHOT=1 to run", System.getenv("SNAPSHOT") == "1")
        val youtube = YouTubeApi(http) { BuildConfig.YOUTUBE_API_KEY }

        val channels = Seeds.channels.mapNotNull { seed ->
            (youtube.channelByHandle(seed.handle) ?: youtube.searchChannel(seed.name))?.copy(isSource = true)
                .also { if (it == null) println("Couldn't find ${seed.name}") }
        }
        val ids = mutableListOf<String>()
        for (ch in channels) {
            if (ch.uploadsPlaylistId.isNotBlank()) ids += youtube.playlistVideoIds(ch.uploadsPlaylistId)
            ids += youtube.searchVideos("", YouTubeApi.Order.VIEWS, channelId = ch.id)
        }
        val curated = Seeds.playlists.flatMap { youtube.playlistVideoIds(it.id, maxPages = 4) }.toMutableList()
        val sections = Sections.all.associate { it.key to youtube.playlistVideoIds(it.playlistId, maxPages = it.maxPages) }
        Sections.all.filter { it.curated }.forEach { curated += sections.getValue(it.key) }
        val sectionIds = sections.values.flatten().toSet()
        val curatedIds = curated.toSet()

        // Same rules as Repository.refresh(): section videos skip the minimum length.
        val videos = youtube.videos((ids + curated + sectionIds).distinct(), fromSource = true)
            .filter { it.durationSec >= DEFAULT_MIN_MINUTES * 60L || (it.id in sectionIds && it.durationSec >= Sections.MIN_SECONDS) }
            .map { it.copy(curated = it.id in curatedIds) }
        val known = channels.map { it.id }.toSet()
        val otherChannels = youtube.channelsById(videos.map { it.channelId }.distinct().filter { it !in known })

        val snapshot = Snapshot(
            createdAt = System.currentTimeMillis(),
            seedsVersion = Seeds.VERSION,
            channels = channels + otherChannels,
            playlists = Seeds.playlists,
            videos = videos,
            sections = sections,
            synopses = sharedSynopses(),
        )
        val out = File("src/main/assets/${Snapshot.ASSET}")
        out.parentFile!!.mkdirs()
        out.writeText(snapshot.toJson())
        println("Wrote ${out.path}: ${videos.size} videos, ${snapshot.channels.size} channels, ${snapshot.synopses.size} synopses, ${out.length() / 1024} KB")
    }

    /** Every synopsis in the shared database (public, read through Firestore's REST API). */
    private fun sharedSynopses(): List<SynopsisEntity> {
        val base = "https://firestore.googleapis.com/v1/projects/long-take/databases/(default)/documents/synopses?pageSize=300"
        val all = mutableListOf<SynopsisEntity>()
        var token = ""
        do {
            val url = if (token.isEmpty()) base else "$base&pageToken=$token"
            val j = http.newCall(Request.Builder().url(url).build()).execute().use { JSONObject(it.body!!.string()) }
            val docs = j.optJSONArray("documents")
            for (i in 0 until (docs?.length() ?: 0)) {
                val d = docs!!.getJSONObject(i)
                val f = d.getJSONObject("fields")
                all += SynopsisEntity(
                    videoId = d.getString("name").substringAfterLast('/'),
                    text = f.getJSONObject("text").getString("stringValue"),
                    topics = f.getJSONObject("topics").getString("stringValue"),
                    createdAt = f.getJSONObject("createdAt").getString("integerValue").toLong(),
                )
            }
            token = j.optString("nextPageToken")
        } while (token.isNotEmpty())
        return all
    }
}
