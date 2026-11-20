package com.longtake

import com.longtake.data.Recommender
import com.longtake.data.Snapshot
import com.longtake.data.UserVideoEntity
import com.longtake.data.VideoEntity
import com.longtake.data.VideoWithState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the "For you" ranking against @brantonling's real ratings, using the bundled catalog:
 *
 *     RECS=1 ./gradlew testDebugUnitTest --tests com.longtake.RecommenderCheck
 *
 * For each 4- or 5-star video it hides that rating, ranks the whole catalog from the other ratings,
 * and reports where the hidden video landed (a random order would put it in the middle). Then it
 * prints the current "For you" list. Reads a few dozen Firestore documents.
 */
class RecommenderCheck {
    @Test fun check() {
        assumeTrue("set RECS=1 to run", System.getenv("RECS") == "1")
        val snap = Snapshot.fromJson(File("src/main/assets/${Snapshot.ASSET}").readText())
        val synopses = snap.synopses.associateBy { it.videoId }
        val channels = snap.channels.associateBy { it.id }
        fun item(v: VideoEntity, rating: Int = 0) =
            VideoWithState(v, if (rating > 0) UserVideoEntity(v.id, rating = rating) else null, null, channels[v.channelId], synopses[v.id])

        val catalog = snap.videos.map { item(it) }
        val byId = snap.videos.associateBy { it.id }
        val rated = ratings().map { (ref, rating) -> item(byId[ref.id] ?: ref, rating) }
        println("${rated.size} ratings, ${catalog.size} catalog videos, ${catalog.count { it.synopsis?.tags?.isNotEmpty() == true }} tagged")

        val ranks = mutableListOf<Double>()
        for (held in rated.filter { it.user!!.rating >= 4 }) {
            val others = rated.filter { it.video.id != held.video.id }
            val pool = (catalog.filter { it.video.id != held.video.id } + held.copy(user = null))
            val order = Recommender.scores(pool, others).map { it.first.video.id }
            val pos = order.indexOf(held.video.id)
            if (pos < 0) continue
            val pct = 100.0 * pos / order.size
            ranks += pct
            println("  %5.1f%%  #%-4d %s★ %s".format(pct, pos + 1, held.user!!.rating, held.video.title.take(70)))
        }
        if (ranks.isNotEmpty()) println("Hidden favourites landed in the top %.1f%% on average (random: 50%%)".format(ranks.average()))

        println("\nFor you:")
        Recommender.recommend(catalog, rated).forEach {
            println("  ${it.video.channelTitle} — ${it.video.title.take(70)}\n      ${it.synopsis?.tags.orEmpty()}")
        }
    }

    private fun ratings(): List<Pair<VideoEntity, Int>> {
        val http = OkHttpClient()
        val base = "https://firestore.googleapis.com/v1/projects/long-take/databases/(default)/documents/users/P2mzQNCtGRYQVUX4Z5DFPg9lb322/ratings?pageSize=300"
        val out = mutableListOf<Pair<VideoEntity, Int>>()
        var token = ""
        do {
            val url = if (token.isEmpty()) base else "$base&pageToken=$token"
            val j = http.newCall(Request.Builder().url(url).build()).execute().use { JSONObject(it.body!!.string()) }
            check(!j.has("error")) { j.getJSONObject("error").optString("message") }
            val docs = j.optJSONArray("documents")
            for (i in 0 until (docs?.length() ?: 0)) {
                val f = docs!!.getJSONObject(i).getJSONObject("fields")
                val rating = f.optJSONObject("rating")?.optString("integerValue")?.toIntOrNull() ?: 0
                if (rating == 0) continue
                val v = f.getJSONObject("video").getJSONObject("mapValue").getJSONObject("fields")
                fun str(k: String) = v.optJSONObject(k)?.optString("stringValue").orEmpty()
                out += VideoEntity(
                    id = str("videoId"), title = str("title"), channelId = str("channelId"), channelTitle = str("channelTitle"),
                    publishedAt = 0, durationSec = 0, views = 0, likes = 0, thumbnailUrl = "", description = "", tags = "",
                    categoryId = "", fromSource = false, fetchedAt = 0,
                ) to rating
            }
            token = j.optString("nextPageToken")
        } while (token.isNotEmpty())
        return out
    }
}
