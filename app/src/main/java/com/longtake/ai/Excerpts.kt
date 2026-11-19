package com.longtake.ai

/** Beginning, middle and later excerpts of a transcript (about 2,400 words in all), to keep Claude calls cheap. */
fun transcriptExcerpts(transcript: String): String {
    val words = transcript.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.size <= 2400) return words.joinToString(" ")
    val chunk = 800
    val starts = listOf(words.size / 20, words.size / 2 - chunk / 2, (words.size * 0.8).toInt() - chunk / 2)
    return starts.joinToString("\n\n[…]\n\n") { s ->
        val st = s.coerceIn(0, words.size - chunk)
        words.subList(st, st + chunk).joinToString(" ")
    }
}
