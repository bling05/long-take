package com.longtake.ai

/**
 * How much a video looks like a video essay (0–100), plus a format label.
 * Uses where the video came from, its title/description/length, and — once the AI check has
 * downloaded the transcript — how scripted the narration sounds.
 */
object EssayScore {

    enum class Format(val label: String) {
        ESSAY("Video essay"),
        LONGFORM("Long-form"),
        STORY("Storytime / commentary"),
        CONVERSATION("Podcast / conversation"),
        TALK("Talk / lecture"),
        RECAP("Recap / summary"),
        STREAM("Stream / VOD"),
        GAMEPLAY("Gameplay"),
        NOT_TALK("Not talk (music, ASMR…)"),
    }

    /** Slider positions on Discover. */
    enum class Strictness(val label: String, val minScore: Int, val detail: String) {
        ANY("Anything long", 1, "Any long video where someone is talking"),
        RELAXED("Relaxed", 30, "Also storytime, commentary and talks"),
        NORMAL("Normal", 50, "Mostly video essays"),
        STRICT("Strict", 70, "Definitely video essays only"),
    }

    data class Result(val score: Int, val format: Format, val reasons: List<String>)

    /** Transcript-derived signals, saved with the AI check. */
    data class Speech(val fillerPer1k: Float, val wordsPerMinute: Int)

    private val junk = Regex(
        "no commentary|full movie|full album|full ost|full soundtrack|music mix|\\blofi\\b|lo-fi|\\basmr\\b|sleep music|for sleep|" +
            "ambience|ambient|white noise|rain sounds|compilation|\\btrailer\\b|\\bhours? loop|10 hours|\\bplaylist\\b|instrumental|" +
            "\\blongplay\\b|\\bkaraoke\\b|audiobook"
    )
    private val gameplay = Regex("let'?s play|playthrough|walkthrough|\\bgameplay\\b|\\bspeedrun\\b|100% guide|full game")
    private val stream = Regex("\\bvod\\b|livestream|live stream|\\bstream\\b|\\blive\\b")
    private val conversation = Regex(
        "podcast|\\bep\\.? ?\\d|episode \\d|#\\d+|interview|conversation with|in conversation|\\bw/ |\\bft\\.|\\bfeat\\.|" +
            "reacts?\\b|reaction|react to|q ?& ?a|\\bama\\b|panel|roundtable|debate"
    )
    private val talk = Regex("lecture|keynote|\\bgdc\\b|\\btedx?\\b|conference|seminar|\\bcourse\\b|lesson \\d|masterclass")
    private val recap = Regex("recap|summary|summarized|ending explained|explained in \\d|full story|everything you need to know|in \\d+ minutes")
    private val story = Regex("storytime|story time|my story|\\bstories\\b|what happened when|i tried|i spent|i went")
    private val essayWord = Regex("video essay|\\| essay|an essay|\\bessay\\b")
    private val analysis = Regex(
        "analysis|analy[sz]ing|examin|retrospective|deep dive|history of|the problem with|rise and fall|critique|what makes|" +
            "^why |^how |the case for|in defen[cs]e of|understanding|the art of|the philosophy of|the death of|lost media|" +
            "tragedy of|the story of|the strange|the weird|a look at|on the |the meaning of"
    )
    private val fillers = listOf(" um ", " uh ", " um, ", " uh, ", " you know ", " you know, ", " i mean ", " i mean, ", " like, ", " kind of ", " sort of ", " basically ", " literally ")

    fun speech(transcript: String, durationSec: Long): Speech {
        val text = " " + transcript.lowercase().replace(Regex("\\s+"), " ") + " "
        val words = text.split(' ').count { it.isNotBlank() }.coerceAtLeast(1)
        val f = fillers.sumOf { countOf(text, it) }
        val wpm = if (durationSec > 60) (words * 60 / durationSec).toInt() else 0
        return Speech(f * 1000f / words, wpm)
    }

    fun score(
        title: String,
        description: String,
        durationSec: Long,
        curated: Boolean,
        fromSourceChannel: Boolean,
        speech: Speech?,
        noTranscript: Boolean = false,
    ): Result {
        val t = title.lowercase()
        val d = description.lowercase().take(600)
        val reasons = mutableListOf<String>()

        if (junk.containsMatchIn(t)) return Result(0, Format.NOT_TALK, listOf("Title suggests music, ASMR, a compilation or similar"))
        if (speech != null && speech.wordsPerMinute in 1..24) return Result(0, Format.NOT_TALK, listOf("Almost no talking in the video"))

        var s = 45
        var format: Format? = null

        if (curated) { s += 30; reasons += "From a curated video essay playlist" }
        else if (fromSourceChannel) { s += 15; reasons += "From one of your source channels" }

        if (essayWord.containsMatchIn(t) || essayWord.containsMatchIn(d.take(200))) { s += 25; reasons += "Calls itself an essay" }
        val analysisHits = analysis.findAll(t).count()
        if (analysisHits > 0) { s += if (analysisHits > 1) 15 else 10; reasons += "Analytical title" }

        when {
            gameplay.containsMatchIn(t) -> { s -= 30; format = Format.GAMEPLAY; reasons += "Looks like a playthrough" }
            stream.containsMatchIn(t) -> { s -= 30; format = Format.STREAM; reasons += "Looks like a stream or VOD" }
            conversation.containsMatchIn(t) -> { s -= 25; format = Format.CONVERSATION; reasons += "Looks like a podcast, interview or reaction" }
            talk.containsMatchIn(t) -> { s -= 15; format = Format.TALK; reasons += "Looks like a talk or lecture" }
            recap.containsMatchIn(t) -> { s -= 15; format = Format.RECAP; reasons += "Looks like a recap or summary" }
            story.containsMatchIn(t) -> { s -= 5; format = Format.STORY }
        }

        when {
            durationSec > 5 * 3600 -> { s -= 10; reasons += "Over 5 hours long" }
            durationSec in (15 * 60)..(240 * 60) -> s += 5
        }

        if (speech != null) {
            when {
                speech.fillerPer1k < 4f -> { s += 15; reasons += "Tightly scripted narration" }
                speech.fillerPer1k < 9f -> { s += 8; reasons += "Mostly scripted narration" }
                speech.fillerPer1k < 16f -> { s -= 8; reasons += "Partly unscripted talk" }
                else -> {
                    s -= 25; reasons += "Conversational (lots of \"um\", \"you know\")"
                    if (format == null) format = Format.CONVERSATION
                }
            }
            if (speech.wordsPerMinute in 25..69) { s -= 20; reasons += "Long stretches without talking" }
        } else if (noTranscript) {
            s -= 10; reasons += "No captions, so it can't tell how scripted it is"
        }

        val score = s.coerceIn(1, 100)
        val label = when {
            format == Format.STORY && score >= 65 -> Format.ESSAY // e.g. "The Untold Stories Behind…"
            format != null -> format
            score >= 65 -> Format.ESSAY
            else -> Format.LONGFORM
        }
        return Result(score, label, reasons)
    }

    private fun countOf(text: String, p: String): Int {
        var i = 0; var n = 0
        while (true) { i = text.indexOf(p, i); if (i < 0) return n; n++; i += p.length - 1 }
    }
}
