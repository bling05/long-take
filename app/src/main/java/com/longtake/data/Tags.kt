package com.longtake.data

/**
 * Hidden tags Claude gives each video along with its synopsis. They're never shown; they feed the
 * "For you" recommendations ([Recommender]).
 *
 * Two kinds, stored together in [SynopsisEntity.tags] separated by "|":
 * - a style or two from the fixed [Style] list, stored as "style:personal" and so on;
 * - specific tags ("liminal spaces", "speedrun", "pixar"). Claude picks these freely, reusing tags
 *   already given to other videos where they fit, so the list grows as it goes instead of
 *   ending up with five spellings of the same idea.
 */
object Tags {
    enum class Style(val detail: String) {
        PERSONAL("the creator thinks out loud and ties the subject to their own life or ideas"),
        COMEDIC("jokes carry the video"),
        RESEARCH("a long, heavily sourced breakdown"),
        RETELLING("narrates an event, a run or a piece of fiction as a story"),
        INVESTIGATIVE("digs into a mystery, a scam or a person"),
        CRITIQUE("judges a film, game, book or other work"),
        EXPLAINER("teaches how something works"),
        DOCUMENTARY("a straight, narrated history of an event or person");

        val key get() = name.lowercase()
        val tag get() = "style:$key"

        companion object {
            fun parse(s: String) = entries.firstOrNull { it.key == s.trim().lowercase() }
        }
    }

    const val MAX_SPECIFIC = 10

    /**
     * Lowercase, single spaces, no stray punctuation. Plurals aren't folded here (that mangles names
     * like "sherlock holmes"): Claude reuses listed spellings, and the tidy step in SynopsisMaker
     * merges any singular and plural pairs left over.
     */
    fun normalize(raw: String): String = raw.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}&'+ -]"), " ")
        .replace('-', ' ')
        .split(' ').filter { it.isNotBlank() }
        .joinToString(" ")

    fun join(styles: List<Style>, specific: List<String>): String =
        (styles.distinct().map { it.tag } + specific.map(::normalize).filter { it.isNotBlank() }.distinct().take(MAX_SPECIFIC))
            .joinToString("|")

    fun split(tags: String): List<String> = tags.split('|').filter { it.isNotBlank() }

    /** The specific (non-style) tags in a stored tag string. */
    fun specific(tags: String): List<String> = split(tags).filterNot { it.startsWith("style:") }

    /**
     * Tags listed both with and without a final "s" ("video game" and "video games"), mapped to the
     * more used spelling. Only pairs where both spellings exist, so names ending in "s" are safe.
     */
    fun pluralPairs(counts: Map<String, Int>): Map<String, String> =
        counts.keys.filter { (it + "s") in counts }.associate { one ->
            val many = one + "s"
            if (counts.getValue(one) >= counts.getValue(many)) many to one else one to many
        }
}
