package com.longtake

import com.longtake.data.Recommender
import com.longtake.data.SynopsisEntity
import com.longtake.data.Tags
import com.longtake.data.UserVideoEntity
import com.longtake.data.VideoEntity
import com.longtake.data.VideoWithState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private fun video(id: String, channel: String, tags: String, topics: String = "", rating: Int = 0) = VideoWithState(
        video = VideoEntity(
            id = id, title = id, channelId = channel, channelTitle = channel, publishedAt = 0, durationSec = 3600,
            views = 0, likes = 0, thumbnailUrl = "", description = "", tags = "", categoryId = "24",
            fromSource = true, fetchedAt = 0,
        ),
        user = if (rating > 0) UserVideoEntity(videoId = id, rating = rating) else null,
        ai = null, channel = null,
        synopsis = SynopsisEntity(id, "…", topics, 0, tags = tags),
    )

    // Filler so tags like "games" are common and "liminal spaces" is rare.
    private val filler = (1..30).map { video("f$it", "other$it", "game|style:critique", "Games|mood:neutral") }

    private val rated = listOf(
        video("geller1", "geller", "liminal spaces|architecture|style:personal", "Books & art", rating = 5),
        video("geller2", "geller", "horror|style:personal", rating = 5),
        video("bad", "rants", "drama|style:comedic", rating = 1),
        video("meh", "x", "game|style:critique", "Games", rating = 3),
    )

    @Test fun lovedCreatorsAndRareTagsComeFirst() {
        val candidates = filler + listOf(
            video("geller3", "geller", "film|style:personal"),
            video("backrooms", "kane", "liminal spaces|horror|style:retelling"),
            video("drama2", "rants", "drama|style:comedic"),
        )
        val top = Recommender.recommend(candidates, rated).map { it.video.id }
        assertTrue(top.take(2).containsAll(listOf("geller3", "backrooms")))
        // A creator you rated 1 star doesn't show up.
        assertTrue("drama2" !in top)
    }

    @Test fun atMostTwoPerCreator() {
        val candidates = (4..8).map { video("geller$it", "geller", "style:personal") }
        assertEquals(2, Recommender.recommend(candidates, rated).size)
    }

    @Test fun needsAFewRatingsFirst() {
        assertEquals(emptyList<VideoWithState>(), Recommender.recommend(filler, rated.take(2)))
    }

    @Test fun ratedVideosAreLeftOut() {
        val top = Recommender.recommend(filler + rated, rated).map { it.video.id }
        assertTrue(top.none { it in setOf("geller1", "geller2", "bad", "meh") })
    }

    @Test fun tagsAreTidied() {
        assertEquals("liminal spaces", Tags.normalize("  Liminal-Spaces! "))
        assertEquals("texas hold'em", Tags.normalize("Texas Hold'em"))
        assertEquals("sherlock holmes", Tags.normalize("Sherlock Holmes"))
        assertEquals(
            mapOf("video games" to "video game", "dark soul" to "dark souls"),
            Tags.pluralPairs(mapOf("video game" to 48, "video games" to 3, "dark soul" to 2, "dark souls" to 9, "sherlock holmes" to 4)),
        )
        assertEquals("style:personal|pixar|cosmic horror", Tags.join(listOf(Tags.Style.PERSONAL), listOf("Pixar", "cosmic horror", "pixar")))
    }
}
