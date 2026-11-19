package com.longtake

/**
 * On/off switches for whole features. Turning one off hides it everywhere but keeps its code,
 * so it can come back by flipping the switch.
 */
object Features {
    /**
     * The AI-script check: downloads transcripts, runs the free on-phone check and (optionally)
     * Claude, and shows "Human / Likely AI" labels plus the hide-AI filters.
     * Off for now: the curated playlists are already human-picked, and transcripts come from an
     * unofficial YouTube endpoint that a Play Store app shouldn't depend on.
     */
    const val AI_CHECK = false
}
