package com.longtake.ai

import android.util.Log
import com.longtake.data.AppDatabase
import com.longtake.data.SettingsStore
import com.longtake.data.SynopsisEntity
import com.longtake.data.Topics
import com.longtake.social.SocialRepo
import com.longtake.youtube.AgeRestrictedException
import com.longtake.youtube.TranscriptFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps video synopses up to date. Each video is summarised once: synopses are shared through
 * Firestore, so phones first copy down what exists and only write the missing ones (which needs a
 * Claude key in Settings).
 */
class SynopsisMaker(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val transcripts: TranscriptFetcher,
    private val writer: SynopsisWriter,
    private val social: SocialRepo,
    private val scope: CoroutineScope,
) {
    data class Status(val writing: Boolean = false, val error: String? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private var job: Job? = null
    private val oneAtATime = Mutex()

    /** Copy down shared synopses, then write any still missing for the feed. */
    fun syncInBackground() {
        if (job?.isActive == true) return
        job = scope.launch {
            pullShared()
            if (settings.now().anthropicApiKey.isBlank()) return@launch
            _status.value = Status(writing = true)
            var failuresInARow = 0
            for (id in db.synopses().missing()) {
                // Read the key each time, so removing it in Settings stops the run straight away.
                val key = settings.now().anthropicApiKey
                if (key.isBlank()) break
                val ok = runCatching { write(id, key) }
                    .onFailure { e ->
                        Log.w("LongTake", "Synopsis failed for $id: ${e.message}")
                        if (e is ClaudeException) { _status.value = Status(error = e.message); return@launch }
                    }
                    .getOrDefault(false)
                // A run of failures usually means YouTube wants us to slow down: stop until next time.
                failuresInARow = if (ok) 0 else failuresInARow + 1
                if (failuresInARow >= 4) break
                delay(if (ok) 500L else 10_000L * failuresInARow)
            }
            _status.value = Status()
        }
    }

    /** Make sure one video (e.g. the one you just opened) has a synopsis. */
    fun ensure(id: String) {
        scope.launch {
            if (db.synopses().get(id)?.isCurrent == true) return@launch
            val key = settings.now().anthropicApiKey
            if (key.isNotBlank()) runCatching { write(id, key) }
        }
    }

    private suspend fun pullShared() {
        if (!social.enabled) return
        runCatching {
            val fresh = social.synopsesSince(settings.synopsisSyncedUntil())
            if (fresh.isNotEmpty()) {
                db.synopses().upsertAll(fresh)
                settings.setSynopsisSyncedUntil(fresh.maxOf { it.createdAt })
            }
        }.onFailure { Log.w("LongTake", "Couldn't copy shared synopses: ${it.message}") }
    }

    /** Returns false when the transcript download failed, so nothing was written. */
    private suspend fun write(id: String, key: String): Boolean = oneAtATime.withLock {
        if (db.synopses().get(id)?.isCurrent == true) return true
        val video = db.videos().getVideos(listOf(id)).firstOrNull() ?: return true
        val transcript = try {
            transcripts.fetch(id)?.text
        } catch (e: AgeRestrictedException) {
            null // no captions without signing in; summarise from the title and description
        } catch (e: Exception) {
            return false
        }
        val r = writer.write(key, video.title, video.channelTitle, video.description, transcript)
        if (r.synopsis.isBlank()) return true
        // The mood is stored alongside the subjects, so the Discover pills can treat it the same way.
        val topics = r.topics + r.mood.tag + Topics.REVISION_TAG
        val synopsis = SynopsisEntity(id, r.synopsis, topics.joinToString("|"))
        db.synopses().upsert(synopsis)
        runCatching { social.shareSynopsis(synopsis) }
            .onFailure { Log.w("LongTake", "Couldn't share synopsis for $id: ${it.message}") }
        true
    }
}
