package com.longtake.data

/** Keyword-based topic tags for the Discover filter pills. */
object Topics {
    data class Topic(val label: String, val keywords: List<String>, val categoryIds: Set<String> = emptySet())

    val all = listOf(
        Topic("Film & TV", listOf("film", "movie", "cinema", "director", "screenplay", "tv show", "series", "episode", "sitcom", "anime", "hollywood", "oscar"), setOf("1", "30", "44")),
        Topic("Games", listOf("game", "gaming", "nintendo", "playstation", "xbox", "rpg", "fromsoftware", "zelda", "mario", "minecraft", "steam"), setOf("20")),
        Topic("Internet culture", listOf("youtube", "youtuber", "internet", "tiktok", "twitter", "reddit", "influencer", "meme", "online", "streamer", "creepypasta", "lost media"), emptySet()),
        Topic("History", listOf("history", "historical", "war", "century", "ancient", "empire", "cold war", "defunct", "1980s", "1990s", "medieval"), emptySet()),
        Topic("Philosophy & politics", listOf("philosophy", "politics", "political", "capitalism", "ideology", "ethics", "society", "religion", "conspiracy", "economics", "gender"), setOf("25", "29")),
        Topic("Music", listOf("music", "album", "song", "band", "composer", "soundtrack", "rapper", "musician"), setOf("10")),
        Topic("Books & art", listOf("book", "novel", "author", "literature", "poetry", "art", "painting", "architecture", "comic", "manga", "design"), emptySet()),
        Topic("Science & tech", listOf("science", "technology", "tech", "physics", "engineering", "computer", "ai ", "software", "biology", "space", "math"), setOf("28", "27")),
    )

    fun matches(t: Topic, v: VideoEntity): Boolean {
        if (v.categoryId in t.categoryIds) return true
        val hay = (v.title + " " + v.tags + " " + v.description.take(400)).lowercase()
        return t.keywords.any { hay.contains(it) }
    }
}
