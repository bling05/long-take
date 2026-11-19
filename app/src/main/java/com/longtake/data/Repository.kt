package com.longtake.data

import android.content.Context
import android.net.Uri
import com.longtake.Features
import com.longtake.ai.AiChecker
import com.longtake.ai.ClaudeCheck
import com.longtake.social.SocialRepo
import com.longtake.social.SocialRepo.Companion.toEntity
import com.longtake.social.VideoRef
import com.longtake.youtube.TranscriptFetcher
import com.longtake.youtube.YouTubeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant

class Repository(
    private val context: Context,
    val db: AppDatabase,
    val settings: SettingsStore,
    val youtube: YouTubeApi,
    private val transcripts: TranscriptFetcher,
    private val claude: ClaudeCheck,
    val social: SocialRepo,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Status(val busy: Boolean = false, val message: String? = null, val error: String? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    /** The AI-script check (switched off by [com.longtake.Features.AI_CHECK]). */
    val aiCheck = AiChecker(db, settings, transcripts, claude, scope)
    val checking: StateFlow<Set<String>> get() = aiCheck.checking

    fun recheck(id: String) = aiCheck.recheck(id)

    fun clearError() { _status.value = _status.value.copy(error = null) }

    private val refreshLock = Mutex()

    fun refreshInBackground() {
        scope.launch { refresh() }
    }

    /** Called when the app opens: only goes to YouTube if the saved data is over 12 hours old. */
    fun refreshIfStale() {
        scope.launch {
            val last = settings.now().lastRefreshAt
            if (System.currentTimeMillis() - last > 12L * 3_600_000) refresh() else aiCheck.checkUncheckedInBackground()
        }
    }

    suspend fun refresh() = refreshLock.withLock {
        val s = settings.now()
        if (!s.hasYoutubeKey) {
            _status.value = Status(error = "Add your YouTube API key in Settings to load videos.")
            return
        }
        try {
            _status.value = Status(busy = true, message = "Loading channels…")
            if (!s.seeded) seedDefaults()

            val sources = db.channels().sourcesNow()
            val newIds = mutableListOf<String>()
            sources.forEachIndexed { i, ch ->
                _status.value = Status(busy = true, message = "Checking ${ch.title} (${i + 1}/${sources.size})")
                // Newest uploads: 1 unit.
                if (ch.uploadsPlaylistId.isNotBlank()) {
                    newIds += runCatching { youtube.playlistVideoIds(ch.uploadsPlaylistId) }.getOrDefault(emptyList())
                }
                // Most-viewed long videos, once per channel: 100 units.
                if (!ch.topFetched) {
                    newIds += runCatching { youtube.searchVideos("", YouTubeApi.Order.VIEWS, channelId = ch.id) }.getOrDefault(emptyList())
                    db.channels().upsert(listOf(ch.copy(topFetched = true)))
                }
            }
            val curatedIds = mutableListOf<String>()
            db.playlists().allNow().forEach { p ->
                _status.value = Status(busy = true, message = "Checking playlist ${p.title}")
                curatedIds += runCatching { youtube.playlistVideoIds(p.id, maxPages = 4) }.getOrDefault(emptyList())
            }
            val sectionIds = mutableSetOf<String>()
            Sections.all.forEach { sec ->
                _status.value = Status(busy = true, message = "Checking ${sec.label}")
                val ids = runCatching { youtube.playlistVideoIds(sec.playlistId, maxPages = sec.maxPages) }.getOrNull()
                    ?: return@forEach // keep the old list if YouTube didn't answer
                db.sections().replace(sec.key, ids)
                sectionIds += ids
                if (sec.curated) curatedIds += ids
            }
            newIds += curatedIds
            newIds += sectionIds

            val known = db.videos().getVideos(newIds.distinct()).associateBy { it.id }
            val fresh = newIds.distinct().filter { it !in known }
            _status.value = Status(busy = true, message = "Loading ${fresh.size} new videos…")
            val minSec = s.minMinutes * 60L
            // Section videos are hand-picked, so they skip the minimum-length setting.
            val videos = youtube.videos(fresh, fromSource = true)
                .filter { it.durationSec >= minSec || (it.id in sectionIds && it.durationSec >= Sections.MIN_SECONDS) }
            db.videos().upsertVideos(videos)
            db.videos().markFromSource(known.keys.toList())
            db.videos().markCurated(curatedIds.distinct())

            // Make sure we have stats for playlist-sourced channels (for "hidden gems" + posting rate).
            refreshChannelStats(videos.map { it.channelId })

            // Update view/like counts for saved videos older than a week (1 unit per 50 videos).
            val stale = db.videos().staleSourceVideoIds(System.currentTimeMillis() - 7L * 86_400_000, 500)
            if (stale.isNotEmpty()) {
                _status.value = Status(busy = true, message = "Updating view counts…")
                val now = System.currentTimeMillis()
                runCatching { youtube.videos(stale, fromSource = true) }.getOrDefault(emptyList())
                    .forEach { db.videos().updateStats(it.id, it.views, it.likes, now) }
            }

            db.videos().pruneOldSearchResults(System.currentTimeMillis() - 7L * 86_400_000)
            settings.setLastRefresh(System.currentTimeMillis())
            _status.value = Status(message = if (videos.isEmpty()) "Up to date" else "Added ${videos.size} videos")
            aiCheck.checkUncheckedInBackground()
        } catch (e: Exception) {
            _status.value = Status(error = e.message ?: "Something went wrong")
        }
    }

    private suspend fun refreshChannelStats(channelIds: List<String>) {
        val ids = channelIds.distinct()
        if (ids.isEmpty()) return
        val have = db.channels().getAll(ids).associateBy { it.id }
        val missing = ids.filter { it !in have }
        if (missing.isEmpty()) return
        val fetched = runCatching { youtube.channelsById(missing) }.getOrDefault(emptyList())
        db.channels().upsert(fetched)
    }

    private suspend fun seedDefaults() {
        for (seed in Seeds.channels) {
            _status.value = Status(busy = true, message = "Adding ${seed.name}…")
            val ch = runCatching { youtube.channelByHandle(seed.handle) }.getOrNull()
                ?: runCatching { youtube.searchChannel(seed.name) }.getOrNull()
                ?: continue
            db.channels().upsert(listOf(ch.copy(isSource = true)))
        }
        Seeds.playlists.forEach { db.playlists().upsert(it) }
        settings.setSeeded(true)
    }

    /** Accepts a channel URL, @handle, channel ID, playlist URL, or a plain channel name. */
    suspend fun addSource(input: String): String {
        val text = input.trim()
        if (text.isBlank()) return "Type a channel or playlist."
        val playlistId = Regex("[?&]list=([A-Za-z0-9_-]+)").find(text)?.groupValues?.get(1)
            ?: text.takeIf { it.matches(Regex("^(PL|OL|UU|FL)[A-Za-z0-9_-]{10,}$")) }
        if (playlistId != null) {
            val info = youtube.playlistInfo(playlistId) ?: return "Couldn't find that playlist."
            db.playlists().upsert(PlaylistEntity(info.id, info.title, info.channelTitle))
            refreshInBackground()
            return "Added playlist \"${info.title}\""
        }
        val channelId = Regex("channel/(UC[A-Za-z0-9_-]{22})").find(text)?.groupValues?.get(1)
            ?: text.takeIf { it.matches(Regex("^UC[A-Za-z0-9_-]{22}$")) }
        val handle = Regex("youtube\\.com/(@[A-Za-z0-9._-]+)").find(text)?.groupValues?.get(1)
            ?: text.takeIf { it.startsWith("@") }
        val ch = when {
            channelId != null -> youtube.channelsById(listOf(channelId)).firstOrNull()
            handle != null -> youtube.channelByHandle(handle)
            else -> youtube.channelByHandle(text.replace(" ", "")) ?: youtube.searchChannel(text)
        } ?: return "Couldn't find that channel."
        val existing = db.channels().get(ch.id)
        db.channels().upsert(listOf(ch.copy(isSource = true, isBlocked = false, blockReason = "", topFetched = existing?.topFetched ?: false)))
        refreshInBackground()
        return "Added ${ch.title}"
    }

    suspend fun addChannelsAsSources(ids: List<String>) {
        val fetched = youtube.channelsById(ids)
        val existing = db.channels().getAll(ids).associateBy { it.id }
        db.channels().upsert(fetched.map { it.copy(isSource = true, topFetched = existing[it.id]?.topFetched ?: false) })
        refreshInBackground()
    }

    suspend fun removeSource(ch: ChannelEntity) {
        db.channels().upsert(listOf(ch.copy(isSource = false)))
        db.videos().unmarkChannelFromSource(ch.id)
    }

    suspend fun removePlaylist(id: String) = db.playlists().delete(id)

    suspend fun blockChannel(channelId: String, title: String, reason: String) {
        val ch = db.channels().get(channelId) ?: ChannelEntity(id = channelId, title = title)
        db.channels().upsert(listOf(ch.copy(isBlocked = true, blockReason = reason, isSource = false)))
    }

    suspend fun unblockChannel(ch: ChannelEntity) {
        db.channels().upsert(listOf(ch.copy(isBlocked = false, blockReason = "")))
    }

    suspend fun search(query: String, order: YouTubeApi.Order): List<String> {
        val q = if (query.contains("essay", true)) query else "$query video essay"
        val ids = youtube.searchVideos(q, order, max = 30)
        val known = db.videos().getVideos(ids).associateBy { it.id }
        val missing = ids.filter { it !in known }
        val vids = youtube.videos(missing, fromSource = false)
        db.videos().upsertVideos(vids)
        refreshChannelStats(vids.map { it.channelId })
        scope.launch { aiCheck.checkVideos(ids) }
        return ids
    }

    private suspend fun updateUser(id: String, f: (UserVideoEntity) -> UserVideoEntity) {
        val cur = db.userVideos().get(id) ?: UserVideoEntity(videoId = id)
        db.userVideos().upsert(f(cur).copy(updatedAt = System.currentTimeMillis()))
        scope.launch { runCatching { social.push(id) } }
    }

    suspend fun setReviewPublic(id: String, public: Boolean) = updateUser(id) { it.copy(reviewPublic = public) }

    /** Make sure a video seen on someone's profile exists on this phone, so its page can open. */
    suspend fun ensureVideo(ref: VideoRef) {
        if (db.videos().getVideos(listOf(ref.videoId)).isNotEmpty()) return
        db.videos().upsertVideos(listOf(ref.toEntity()))
        scope.launch {
            runCatching { youtube.videos(listOf(ref.videoId), fromSource = false) }.getOrNull()
                ?.let { db.videos().upsertVideos(it) }
            aiCheck.checkVideos(listOf(ref.videoId))
        }
    }

    suspend fun setSaved(id: String, saved: Boolean) =
        updateUser(id) { it.copy(saved = saved, savedAt = if (saved) System.currentTimeMillis() else 0) }

    suspend fun setWatched(id: String, watched: Boolean) =
        updateUser(id) { it.copy(watched = watched, watchedAt = if (watched) System.currentTimeMillis() else 0) }

    suspend fun setRating(id: String, rating: Int) = updateUser(id) { it.copy(rating = rating) }

    suspend fun setNote(id: String, note: String) = updateUser(id) { it.copy(note = note) }

    /** Reads Takeout's "watch-history.json" and marks matching videos as watched. */
    suspend fun importWatchHistory(uri: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return@withContext "Couldn't open that file."
        if (text.trimStart().startsWith("<")) {
            return@withContext "That's the HTML version. In Google Takeout, choose JSON format for YouTube history."
        }
        val arr = runCatching { JSONArray(text) }.getOrNull()
            ?: return@withContext "That doesn't look like watch-history.json."
        val rows = mutableListOf<WatchHistoryEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("titleUrl")
            val id = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1) ?: continue
            val time = runCatching { Instant.parse(o.optString("time")).toEpochMilli() }.getOrDefault(0)
            rows += WatchHistoryEntity(id, o.optString("title").removePrefix("Watched "), time)
        }
        val latest = rows.groupBy { it.videoId }.map { (_, v) -> v.maxBy { it.watchedAt } }
        db.watchHistory().insertAll(latest)
        val users = latest.map { r ->
            val cur = db.userVideos().get(r.videoId) ?: UserVideoEntity(videoId = r.videoId)
            cur.copy(watched = true, watchedAt = maxOf(cur.watchedAt, r.watchedAt))
        }
        db.userVideos().upsertAll(users)
        "Imported ${latest.size} watched videos."
    }
}
