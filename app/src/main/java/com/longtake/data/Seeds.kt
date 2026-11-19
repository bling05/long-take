package com.longtake.data

/**
 * Starter list of well-known video essay channels. Each is looked up by handle first
 * (1 quota unit) and by name if the handle doesn't match (100 units). Remove any you don't like
 * in the Sources tab.
 */
object Seeds {
    /** [since] is the list version a channel joined in, so phones that already have the list get just the new ones. */
    data class Seed(val handle: String, val name: String, val since: Int = 1)

    val channels = listOf(
        Seed("@JacobGeller", "Jacob Geller"),
        Seed("@hbomberguy", "hbomberguy"),
        Seed("@FoldingIdeas", "Folding Ideas"),
        Seed("@PhilosophyTube", "Philosophy Tube"),
        Seed("@ContraPoints", "ContraPoints"),
        Seed("@JennyNicholson", "Jenny Nicholson"),
        Seed("@NoahCaldwellGervais", "Noah Caldwell-Gervais"),
        Seed("@Nerdwriter1", "Nerdwriter1"),
        Seed("@LessonsfromtheScreenplay", "Lessons from the Screenplay"),
        Seed("@ThomasFlight", "Thomas Flight"),
        Seed("@patrickhwillems", "Patrick (H) Willems"),
        Seed("@SuperEyepatchWolf", "Super Eyepatch Wolf"),
        Seed("@Tantacrul", "Tantacrul"),
        Seed("@BigJoel", "Big Joel"),
        Seed("@MaggieMaeFish", "Maggie Mae Fish"),
        Seed("@BobbyBroccoli", "BobbyBroccoli"),
        Seed("@Defunctland", "Defunctland"),
        Seed("@PeopleMakeGames", "People Make Games"),
        Seed("@LindsayEllisVids", "Lindsay Ellis"),
        Seed("@KaptainKristian", "KaptainKristian"),
        Seed("@Ah_Lecks", "Ah Lecks", since = 2),
    )

    val VERSION = channels.maxOf { it.since }

    /** Curated playlists that collect many different creators' essays (checked Nov 2026). */
    val playlists = listOf(
        PlaylistEntity("PLX4DApdGFnsn8g72Oo1oyMuV3NIPVgR10", "Jacob's Big List of (Other People's) Great Essays", "Jacob Geller"),
        PlaylistEntity("PLC5YgZn_JbiaqRUqFNOsTthdECnyG_kkU", "Top 100 Video Essays Of All Time"),
        PlaylistEntity("PLaDrN74SfdT7rLOyJoWN_whFtEAIAvzze", "Really Good Video Essays on Really Good Video Games"),
    )
}

/** Fixed Discover sections, each filled from one YouTube playlist and sorted like the rest of Discover. */
object Sections {
    data class Section(val key: String, val label: String, val playlistId: String, val maxPages: Int, val curated: Boolean)

    val all = listOf(
        // "The best video essays/long form videos" (244 videos, Nov 2026).
        Section("youtube_recs", "YouTube Recs", "PLUC-4QX1x_XznsngH9OKSsVxNmx9zBYQA", maxPages = 8, curated = true),
        // Every upload on Jacob Geller's channel (122 videos, Nov 2026).
        Section("jacob_geller", "Jacob Geller", "UUeTfBygNb1TahcNpZyELO8g", maxPages = 6, curated = false),
    )

    /** Videos shorter than this (Shorts, trailers) are left out of sections. */
    const val MIN_SECONDS = 120L
}
