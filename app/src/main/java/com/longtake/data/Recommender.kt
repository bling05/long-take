package com.longtake.data

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Ranks videos for the "For you" pill by how much they resemble the ones you rated.
 *
 * Each video is described by its features: its creator, Claude's style and specific tags, its
 * Discover topics and its mood. Features shared by many videos count for less than rare ones (two
 * videos both about "liminal spaces" says more than both being about games), and the creator
 * counts most. A video's score adds up its likeness to each video you rated, weighted by the stars:
 * 5 stars pulls similar videos up strongly, 1 or 2 stars pushes them down.
 */
object Recommender {
    /** Fewer ratings than this says too little about your taste, so the pill stays hidden. */
    const val MIN_RATINGS = 3
    /** At most this many videos from one creator, so one channel can't fill the list. */
    const val PER_CREATOR = 2
    const val LIMIT = 40

    // How much each kind of feature counts, before the rarity weighting.
    private const val CREATOR = 2.0
    private const val SPECIFIC = 1.0
    private const val STYLE = 0.7
    private const val TOPIC = 0.4
    private const val MOOD = 0.3

    fun features(item: VideoWithState): Map<String, Double> {
        val f = HashMap<String, Double>()
        f["creator:" + item.video.channelId] = CREATOR
        item.synopsis?.tags?.let(Tags::split)?.forEach { t -> f[t] = if (t.startsWith("style:")) STYLE else SPECIFIC }
        item.synopsisTopics.forEach { t ->
            when {
                t.startsWith("mood:") -> f[t] = MOOD
                !Topics.isTag(t) -> f["topic:$t"] = TOPIC
            }
        }
        return f
    }

    /** Stars to pull: 5 → 2.5, 4 → 1.5, 3 → 0.5, 2 → -0.5, 1 → -1.5. */
    private fun pull(rating: Int) = rating - 2.5

    /**
     * The best matches among [candidates] for someone who gave [rated] their ratings, best first,
     * at most [PER_CREATOR] per creator. Returns nothing until there are [MIN_RATINGS] ratings.
     */
    fun recommend(candidates: List<VideoWithState>, rated: List<VideoWithState>, limit: Int = LIMIT): List<VideoWithState> {
        val perCreator = HashMap<String, Int>()
        return scores(candidates, rated).asSequence()
            .filter { it.second > 0 }
            .map { it.first }
            .filter { perCreator.merge(it.video.channelId, 1, Int::plus)!! <= PER_CREATOR }
            .take(limit)
            .toList()
    }

    /** Every candidate not already rated, with its score, highest first. */
    fun scores(candidates: List<VideoWithState>, rated: List<VideoWithState>): List<Pair<VideoWithState, Double>> {
        val ratings = rated.filter { (it.user?.rating ?: 0) > 0 }
        if (ratings.size < MIN_RATINGS) return emptyList()
        val ratedIds = ratings.map { it.video.id }.toSet()
        val pool = candidates.filter { it.video.id !in ratedIds }

        // Rarity: features found on few videos count for more.
        val all = (pool + ratings).distinctBy { it.video.id }
        val raw = all.associate { it.video.id to features(it) }
        val docCount = HashMap<String, Int>()
        raw.values.forEach { f -> f.keys.forEach { k -> docCount.merge(k, 1, Int::plus) } }
        val n = all.size.toDouble()
        fun vector(id: String): Map<String, Double> {
            val v = raw.getValue(id).mapValues { (k, w) -> w * (ln((n + 1) / (docCount.getValue(k) + 1)) + 1) }
            val len = sqrt(v.values.sumOf { it * it })
            return if (len == 0.0) v else v.mapValues { it.value / len }
        }

        val likes = ratings.map { vector(it.video.id) to pull(it.user!!.rating) }
        return pool.map { item ->
            val v = vector(item.video.id)
            item to likes.sumOf { (r, pull) -> pull * v.entries.sumOf { (k, x) -> x * (r[k] ?: 0.0) } }
        }.sortedByDescending { it.second }
    }
}
