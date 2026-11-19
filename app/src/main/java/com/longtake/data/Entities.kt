package com.longtake.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.longtake.Features

/** A YouTube video we know about (from a source channel/playlist or a search). */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val publishedAt: Long,
    val durationSec: Long,
    val views: Long,
    val likes: Long,
    val thumbnailUrl: String,
    val description: String,
    val tags: String,
    val categoryId: String,
    /** True if this came from one of the user's sources (shown in Discover). */
    val fromSource: Boolean,
    val fetchedAt: Long,
    /** True if it appears in one of the curated video essay playlists. */
    val curated: Boolean = false,
)

/** A channel: either a feed source, a blocked channel, or just one we've looked up. */
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val title: String,
    val handle: String = "",
    val thumbnailUrl: String = "",
    val subscribers: Long = 0,
    val videoCount: Long = 0,
    val createdAt: Long = 0,
    val uploadsPlaylistId: String = "",
    val isSource: Boolean = false,
    val isBlocked: Boolean = false,
    /** "ai" when the user marked it as AI, "hidden" when just hidden. */
    val blockReason: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    /** Whether we've already pulled this channel's most-viewed long videos. */
    val topFetched: Boolean = false,
)

/** A playlist used as a feed source (e.g. a curated "best video essays" playlist). */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelTitle: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

object Verdict {
    const val HUMAN = "human"
    const val AI = "ai"
    const val UNSURE = "unsure"
    const val NO_TRANSCRIPT = "no_transcript"
    /** YouTube only shows this video (and its captions) to signed-in adults. */
    const val AGE_RESTRICTED = "age_restricted"
    const val ERROR = "error"
}

/** Result of the AI-script check for one video. */
@Entity(tableName = "ai_checks")
data class AiCheckEntity(
    @PrimaryKey val videoId: String,
    /** Final verdict shown to the user (see [Verdict]). */
    val verdict: String,
    /** 0–100, higher = more AI-like, from the free on-phone check. */
    val freeScore: Int,
    val freeVerdict: String,
    /** Claude's verdict if Claude was asked, else "". */
    val claudeVerdict: String = "",
    val claudeConfidence: Int = 0,
    val reason: String,
    val transcriptWords: Int = 0,
    val checkedAt: Long = System.currentTimeMillis(),
    /** Filler words ("um", "you know"…) per 1,000 words; -1 if no transcript. */
    val fillerPer1k: Float = -1f,
    /** Spoken words per minute; -1 if no transcript. */
    val wordsPerMinute: Int = -1,
) {
    fun speech(): com.longtake.ai.EssayScore.Speech? =
        if (fillerPer1k < 0) null else com.longtake.ai.EssayScore.Speech(fillerPer1k, wordsPerMinute)
}

/** Everything the user has done with a video: saved, watched, rated, noted. */
@Entity(tableName = "user_videos")
data class UserVideoEntity(
    @PrimaryKey val videoId: String,
    val saved: Boolean = false,
    val savedAt: Long = 0,
    val watched: Boolean = false,
    val watchedAt: Long = 0,
    /** 0 = not rated, else 1–5. */
    val rating: Int = 0,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    /** Share the note publicly as a review on your profile. */
    val reviewPublic: Boolean = false,
    /** When the video was first reviewed or its stars last changed (editing the note doesn't count): orders reviews. */
    @ColumnInfo(defaultValue = "0") val reviewedAt: Long = 0,
)

/** A video joined with the user's data and its AI check. */
data class VideoWithState(
    @Embedded val video: VideoEntity,
    @Relation(parentColumn = "id", entityColumn = "videoId")
    val user: UserVideoEntity?,
    @Relation(parentColumn = "id", entityColumn = "videoId")
    val ai: AiCheckEntity?,
    @Relation(parentColumn = "channelId", entityColumn = "id")
    val channel: ChannelEntity?,
    @Relation(parentColumn = "id", entityColumn = "videoId")
    val synopsis: SynopsisEntity?,
) {
    /** Topic labels Claude gave this video, if it has a synopsis. */
    val synopsisTopics: List<String> get() = synopsis?.topics?.split('|')?.filter { it.isNotBlank() }.orEmpty()

    /** How much this looks like a video essay (see EssayScore). */
    fun essay(): com.longtake.ai.EssayScore.Result = com.longtake.ai.EssayScore.score(
        title = video.title,
        description = video.description,
        durationSec = video.durationSec,
        curated = video.curated,
        fromSourceChannel = channel?.isSource == true,
        speech = if (Features.AI_CHECK) ai?.speech() else null,
        noTranscript = Features.AI_CHECK && (ai?.verdict == Verdict.NO_TRANSCRIPT || ai?.verdict == Verdict.AGE_RESTRICTED),
    )
}

/** A row from a Google Takeout watch-history import, for videos not otherwise known. */
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val watchedAt: Long,
)

/** Which videos belong to a Discover section such as "YouTube Recs" (see [Sections]). */
@Entity(tableName = "section_videos", primaryKeys = ["section", "videoId"])
data class SectionVideoEntity(
    val section: String,
    val videoId: String,
)

/** A short "what it's about" written by Claude, plus the Discover topics it picked. Shared through Firestore. */
@Entity(tableName = "synopses")
data class SynopsisEntity(
    @PrimaryKey val videoId: String,
    val text: String,
    /** Topic labels from [Topics.all] plus a mood tag ([Topics.Mood]), separated by "|". */
    val topics: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Written with the current instructions; older synopses get rewritten. */
    val isCurrent: Boolean get() = Topics.REVISION_TAG in topics.split('|')
}
