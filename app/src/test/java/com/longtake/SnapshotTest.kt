package com.longtake

import com.longtake.data.ChannelEntity
import com.longtake.data.PlaylistEntity
import com.longtake.data.Snapshot
import com.longtake.data.SynopsisEntity
import com.longtake.data.VideoEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotTest {
    @Test fun survivesARoundTrip() {
        val channel = ChannelEntity(id = "UC1", title = "Jacob Geller", handle = "@JacobGeller", subscribers = 1_000, uploadsPlaylistId = "UU1", isSource = true, addedAt = 0)
        val video = VideoEntity(
            id = "v1", title = "Fear of Cold", channelId = "UC1", channelTitle = "Jacob Geller", publishedAt = 5, durationSec = 1800,
            views = 10, likes = 2, thumbnailUrl = "t", description = "d", tags = "a,b", categoryId = "24",
            fromSource = true, fetchedAt = 7, curated = true,
        )
        val snap = Snapshot(
            createdAt = 1, seedsVersion = 2, channels = listOf(channel), playlists = listOf(PlaylistEntity("PL1", "Best essays", "Someone", addedAt = 0)),
            videos = listOf(video), sections = mapOf("jacob_geller" to listOf("v1")),
            synopses = listOf(SynopsisEntity("v1", "Cold as a threat.", "History|mood:heavy|rev:3", 9, tags = "style:personal|arctic|exploration")),
        )
        val back = Snapshot.fromJson(snap.toJson())

        assertEquals(snap.videos, back.videos)
        assertEquals(snap.sections, back.sections)
        assertEquals(snap.synopses, back.synopses)
        // Sources come back marked as already having their most-viewed videos.
        assertEquals(channel.copy(topFetched = true), back.channels.single().copy(addedAt = 0))
        assertEquals(snap.playlists.single().id, back.playlists.single().id)
    }
}
