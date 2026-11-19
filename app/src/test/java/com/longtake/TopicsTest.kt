package com.longtake

import com.longtake.data.SynopsisEntity
import com.longtake.data.Topics
import com.longtake.data.VideoEntity
import com.longtake.data.VideoWithState
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicsTest {
    private fun video(title: String, synopsis: SynopsisEntity? = null) = VideoWithState(
        video = VideoEntity(
            id = "x", title = title, channelId = "c", channelTitle = "Channel", publishedAt = 0, durationSec = 3600,
            views = 0, likes = 0, thumbnailUrl = "", description = "", tags = "", categoryId = "24",
            fromSource = true, fetchedAt = 0,
        ),
        user = null, ai = null, channel = null, synopsis = synopsis,
    )

    private fun topicsOf(item: VideoWithState) = Topics.all.filter { Topics.matches(it, item) }.map { it.label }

    @Test fun keywordsMatchWholeWordsOnly() {
        assertEquals(emptyList<String>(), topicsOf(video("An award-winning startup's part in my heart, and a bandwagon")))
        assertEquals(listOf("History"), topicsOf(video("The Wars of the Roses")))
        assertEquals(listOf("Games"), topicsOf(video("Why these games feel lonely")))
    }

    @Test fun synopsisTopicsWinOverKeywords() {
        val item = video("Game of Thrones and the death of fantasy", SynopsisEntity("x", "…", "Film & TV|Books & art"))
        assertEquals(listOf("Film & TV", "Books & art"), topicsOf(item))
    }

    @Test fun lightheartedNeedsClaudesSay() {
        val pill = Topics.LIGHTHEARTED
        assertEquals(false, Topics.matches(pill, video("A funny little poker story")))
        assertEquals(true, Topics.matches(pill, video("Poker's Greatest Tournament Run", SynopsisEntity("x", "…", "Games|mood:light"))))
        assertEquals(false, Topics.matches(pill, video("Fear of Cold", SynopsisEntity("x", "…", "History|mood:heavy"))))
    }

    @Test fun summoningSaltIsAlwaysLight() {
        val v = video("The History of Super Mario Bros. World Records")
        val salt = v.copy(video = v.video.copy(channelId = "UCtUbO6rBht0daVIOGML3c8w"))
        assertEquals(true, Topics.matches(Topics.LIGHTHEARTED, salt))
    }

    @Test fun moodAloneLeavesSubjectsToKeywords() {
        val item = video("Why these games feel lonely", SynopsisEntity("x", "…", "mood:neutral"))
        assertEquals(listOf("Games"), topicsOf(item))
        assertEquals(false, item.synopsis!!.isCurrent)
        assertEquals(true, SynopsisEntity("x", "…", "Games|mood:light|${Topics.REVISION_TAG}").isCurrent)
    }
}
