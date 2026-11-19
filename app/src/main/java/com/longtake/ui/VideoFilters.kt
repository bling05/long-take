package com.longtake.ui

import androidx.compose.foundation.layout.size
import com.longtake.Features
import com.longtake.data.AppSettings
import com.longtake.data.Sections
import com.longtake.data.Verdict
import com.longtake.data.VideoWithState

enum class SortBy(val label: String) {
    MOST_VIEWED("Most viewed"),
    HIDDEN_GEMS("Hidden gems"),
    NEWEST("Newest"),
    LONGEST("Longest"),
}

/** Shared filtering for any list of videos. */
fun applyFilters(list: List<VideoWithState>, s: AppSettings, hideWatched: Boolean): List<VideoWithState> =
    list.filter { item ->
        val ai = if (Features.AI_CHECK) item.ai?.verdict else null
        when {
            item.channel?.isBlocked == true -> false
            s.hideAi && ai == Verdict.AI -> false
            s.hideUnsure && ai == Verdict.UNSURE -> false
            hideWatched && item.user?.watched == true -> false
            item.video.durationSec < s.minMinutes * 60L -> false
            item.essay().score < s.strictness.minScore -> false
            else -> true
        }
    }

/**
 * Sections are hand-picked lists, so they skip the essay-strictness and minimum-length filters.
 * Blocked channels, your AI/watched settings and very short videos (Shorts, trailers) still apply.
 */
fun applySectionFilters(list: List<VideoWithState>, s: AppSettings): List<VideoWithState> =
    list.filter { item ->
        val ai = if (Features.AI_CHECK) item.ai?.verdict else null
        when {
            item.channel?.isBlocked == true -> false
            s.hideAi && ai == Verdict.AI -> false
            s.hideUnsure && ai == Verdict.UNSURE -> false
            s.hideWatched && item.user?.watched == true -> false
            item.video.durationSec < Sections.MIN_SECONDS -> false
            else -> true
        }
    }

fun sortVideos(list: List<VideoWithState>, sort: SortBy): List<VideoWithState> = when (sort) {
    SortBy.MOST_VIEWED -> list.sortedByDescending { it.video.views }
    SortBy.NEWEST -> list.sortedByDescending { it.video.publishedAt }
    SortBy.LONGEST -> list.sortedByDescending { it.video.durationSec }
    // Views relative to the channel's size: favours great videos from smaller channels.
    SortBy.HIDDEN_GEMS -> list.sortedByDescending {
        val subs = (it.channel?.subscribers ?: 0L).coerceAtLeast(5_000L)
        it.video.views.toDouble() / subs
    }
}
