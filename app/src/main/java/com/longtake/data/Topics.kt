package com.longtake.data

/**
 * Topic tags for the Discover filter pills. Videos with a synopsis use the topics Claude picked;
 * others fall back to whole-word keyword matching on the title, tags and description.
 */
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
        Topic("Science & tech", listOf("science", "technology", "tech", "physics", "engineering", "computer", "ai", "software", "biology", "space", "math"), setOf("28", "27")),
    )

    /**
     * Synopses without this tag get rewritten. Bump it when a change to the instructions is worth
     * redoing existing synopses for; it's stored with the topics as "rev:N".
     */
    const val REVISION_TAG = "rev:3"

    /** Tags like "mood:light" and "rev:2" sit alongside the topic labels; labels never contain ':'. */
    fun isTag(s: String) = ':' in s

    /** How heavy a video is to watch, judged by Claude along with the synopsis and stored as "mood:light" and so on. */
    enum class Mood {
        LIGHT, NEUTRAL, HEAVY;

        val tag get() = "mood:" + name.lowercase()

        companion object {
            fun parse(s: String) = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: NEUTRAL
        }
    }

    /** A mood pill rather than a subject. Keywords can't tell tone, so it shows videos Claude rated light, plus [lightChannels]. */
    val LIGHTHEARTED = Topic("Lighthearted", emptyList())

    /** Channels whose videos always count as lighthearted, whatever Claude says. */
    private val lightChannels = setOf(
        "UCtUbO6rBht0daVIOGML3c8w", // Summoning Salt
        "UC5UYMeKfZbFYnLHzoTJB1xA", // Schaffrillas Productions
    )

    /** Everything shown as a Discover pill: the mood first, then the subjects. */
    val pills = listOf(LIGHTHEARTED) + all

    // "war" should match "war" and "wars", not "award" or "software".
    private val patterns: Map<String, Regex> = all.associate { t ->
        t.label to Regex(t.keywords.joinToString("|", """\b(?:""", """)(?:s|es)?\b""") { Regex.escape(it) })
    }

    fun matches(t: Topic, item: VideoWithState): Boolean {
        val picked = item.synopsisTopics
        if (t == LIGHTHEARTED) return item.video.channelId in lightChannels || Mood.LIGHT.tag in picked
        val subjects = picked.filterNot(::isTag)
        if (subjects.isNotEmpty()) return t.label in subjects
        val v = item.video
        if (v.categoryId in t.categoryIds) return true
        val hay = (v.title + " " + v.tags + " " + v.description.take(400)).lowercase()
        return patterns.getValue(t.label).containsMatchIn(hay)
    }
}
