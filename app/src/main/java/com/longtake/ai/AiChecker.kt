package com.longtake.ai

import android.util.Log
import com.longtake.Features
import com.longtake.data.AiCheckEntity
import com.longtake.data.AppDatabase
import com.longtake.data.ClaudeMode
import com.longtake.data.SettingsStore
import com.longtake.data.Verdict
import com.longtake.youtube.AgeRestrictedException
import com.longtake.youtube.TranscriptFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs the AI-script check in the background: downloads each transcript, runs the free
 * on-phone [FreeCheck] and, depending on settings, asks Claude. Results go to the database.
 */
class AiChecker(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val transcripts: TranscriptFetcher,
    private val claude: ClaudeCheck,
    private val scope: CoroutineScope,
) {
    private val _checking = MutableStateFlow<Set<String>>(emptySet())

    /** Videos being checked right now. */
    val checking: StateFlow<Set<String>> = _checking

    private var checkJob: Job? = null

    fun checkUncheckedInBackground() {
        if (!Features.AI_CHECK) return
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            if (settings.now().claudeEnabled) db.aiChecks().clearFailedClaude()
            // Videos without captions get another try after a week (captions are sometimes added
            // later). Older app versions also wrongly saved failed downloads as "no transcript",
            // so anything saved before this fix is retried straight away.
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            db.aiChecks().clearNoTranscriptBefore(maxOf(weekAgo, TRANSCRIPT_FIX_TIME))
            val done = db.aiChecks().checkedIds().toSet()
            val todo = db.videos().sourceVideoIds().filter { it !in done }
            checkVideos(todo)
        }
    }

    suspend fun checkVideos(ids: List<String>) {
        if (!Features.AI_CHECK) return
        val done = db.aiChecks().checkedIds().toSet()
        var failuresInARow = 0
        for (id in ids) {
            if (id in done) continue
            val ok = runCatching { checkVideo(id) }.getOrDefault(false)
            if (ok) {
                failuresInARow = 0
                delay(1_000) // be gentle with YouTube
            } else {
                // Usually YouTube asking us to slow down. Wait longer each time, and after a few
                // failures give up until the next refresh; unchecked videos are picked up then.
                failuresInARow++
                if (failuresInARow >= 4) return
                delay(15_000L * failuresInARow)
            }
        }
    }

    fun recheck(id: String) {
        if (!Features.AI_CHECK) return
        scope.launch {
            db.aiChecks().delete(id)
            runCatching { checkVideo(id, force = true) }
        }
    }

    /** Returns false if the transcript download failed, in which case nothing is saved. */
    private suspend fun checkVideo(id: String, force: Boolean = false): Boolean {
        if (!force && db.aiChecks().get(id) != null) return true
        val video = db.videos().getVideos(listOf(id)).firstOrNull() ?: return true
        _checking.update { it + id }
        try {
            val s = settings.now()
            val channel = db.channels().get(video.channelId)
            var ageRestricted = false
            val transcript = try {
                transcripts.fetch(id)
            } catch (e: AgeRestrictedException) {
                ageRestricted = true
                null
            } catch (e: Exception) {
                Log.w("LongTake", "Transcript download failed for $id: ${e.message}")
                return false
            }
            val free = FreeCheck.run(transcript?.text, channel)
            val speech = transcript?.let { EssayScore.speech(it.text, video.durationSec) }

            var final = if (ageRestricted) Verdict.AGE_RESTRICTED else free.verdict
            var reason = if (ageRestricted) "Age-restricted on YouTube" else free.reasons.joinToString(". ")
            var claudeVerdict = ""
            var claudeConf = 0
            val askClaude = transcript != null && s.claudeEnabled &&
                (s.claudeMode == ClaudeMode.ALL || free.verdict == Verdict.UNSURE)
            if (askClaude) {
                try {
                    val r = claude.check(s.anthropicApiKey, s.claudeModel, video.title, video.channelTitle, transcript!!.text)
                    claudeVerdict = r.verdict; claudeConf = r.confidence
                    final = r.verdict
                    reason = r.reason
                } catch (e: Throwable) {
                    Log.e("LongTake", "Claude check failed for $id", e)
                    reason += ". (Claude check failed: ${e.message ?: e.javaClass.simpleName})"
                }
            }
            db.aiChecks().upsert(
                AiCheckEntity(
                    videoId = id, verdict = final, freeScore = free.score, freeVerdict = free.verdict,
                    claudeVerdict = claudeVerdict, claudeConfidence = claudeConf, reason = reason,
                    transcriptWords = transcript?.wordCount ?: 0,
                    fillerPer1k = speech?.fillerPer1k ?: -1f,
                    wordsPerMinute = speech?.wordsPerMinute ?: -1,
                )
            )
            return true
        } finally {
            _checking.update { it - id }
        }
    }
}

/** Last change to how "no transcript" is decided (2026-11-19); older results are re-checked. */
private const val TRANSCRIPT_FIX_TIME = 1795062787000L
