package com.longtake.ai

import com.longtake.data.ChannelEntity
import com.longtake.data.Verdict

/**
 * The free, on-phone AI-script check. It looks for:
 *  - phrases AI models overuse ("delve", "a testament to", "it's not just X, it's Y"…)
 *  - how rarely the narrator talks about themselves ("I", "my") — real essayists do it a lot
 *  - channels that upload long videos implausibly often
 * It's a rough signal, which is why "unsure" results can be passed to Claude.
 */
object FreeCheck {

    data class Result(val score: Int, val verdict: String, val reasons: List<String>)

    private val strong = listOf(
        "delve", "delves", "delving", "tapestry", "a testament to", "ever-evolving", "ever evolving",
        "it's important to note", "it is important to note", "it's worth noting", "it is worth noting",
        "navigate the complexities", "in the realm of", "the realm of", "multifaceted", "a stark reminder",
        "serves as a reminder", "serves as a testament", "underscores the", "intricate dance", "a beacon of",
        "rich tapestry", "in today's fast-paced", "in today's world", "in today's digital", "let's delve",
        "let's dive in", "let's dive into", "unravel the mystery", "unravel the mysteries", "embark on a journey",
        "stands as a", "a symphony of", "the intricate", "in the grand tapestry", "nestled in",
        "a pivotal moment", "pivotal role", "plays a crucial role", "a crucial role",
        "in conclusion", "to sum up", "ultimately, the", "it's a reminder that", "a reminder that",
        "forever changed", "shrouded in mystery", "left an indelible", "indelible mark",
    )

    private val medium = listOf(
        "fascinating", "captivating", "intriguing", "remarkable", "profound", "nuanced", "intricate",
        "seamlessly", "furthermore", "moreover", "additionally", "landscape", "journey", "resonate",
        "resonates", "showcasing", "showcases", "highlighting", "unveil", "unveils", "unveiling",
        "crucial", "pivotal", "vibrant", "enigmatic", "but here's the thing", "here's where it gets",
        "buckle up", "what if i told you", "but that's not all", "the answer may surprise you",
        "stay tuned", "dive deep", "deep dive", "at its core", "shed light", "sheds light",
    )

    // Stock lines from newer AI models, which have mostly dropped "delve"-style vocabulary.
    private val stockStrong = listOf(
        "there's no denying", "there is no denying", "far more than just", "left a permanent mark",
        "left a lasting mark", "and that is exactly the point", "and that's exactly the point",
        "but here's the kicker", "the stakes have never been higher", "nothing would ever be the same",
        "make no mistake", "one thing is clear", "only time will tell", "a chilling reminder",
        "a brutal reminder", "a powerful reminder", "a sobering reminder", "sends a clear message",
        "so today, we're going to", "so today we're going to", "in the midst of so much",
        "dressed up as entertainment", "and that changes everything", "but it's not that simple",
    )
    private val stockMedium = listOf(
        "let's be honest", "let's face it", "more than just a", "dressed up as", "doesn't think twice",
        "without hesitation", "in the midst of", "a moment of", "the truth is", "it's not that simple",
        "little did", "changed everything", "the real question", "at its heart",
    )

    /** "isn't just X — it's Y", "more than just X, this is Y" */
    private val contrastPattern = Regex(
        "\\b(?:isn't|is not|wasn't|aren't|weren't|more than) (?:just|only|simply|merely)\\b[^.!?]{0,60}?[,;.—–-]\\s*(?:it's|it is|it was|this is|that's|they're|but)\\b"
    )

    /** "more violent, more rigid, more cruel" / "it's brief, it's simple, but it's genuine" */
    private val triplePattern = Regex(
        "\\b([a-z']{2,}) [a-z'-]+(?: [a-z'-]+)?, \\1 [a-z'-]+(?: [a-z'-]+)?,? (?:and |but |or )?\\1 [a-z'-]+"
    )

    /** Back-to-back sentences opening with the same three words ("She isn't going to run. She isn't going to hide.") */
    private fun repeatedOpenings(text: String): Int {
        val openings = text.split(Regex("[.!?]+\\s")).map { s ->
            s.trim().split(' ').filter { it.isNotBlank() }.take(3).joinToString(" ")
        }
        return openings.zipWithNext().count { (a, b) -> a.split(' ').size == 3 && a == b }
    }

    /** Per-signal counts from the last run, for testing. */
    @Volatile var details: Map<String, Int> = emptyMap()
        private set

    private val notJustPattern =Regex("\\b(?:it's|it is|this is|that's|that is|isn't) not just (?:a |an |about )?\\w+[^.]{0,60}?(?:it's|it is|but)\\b")

    fun run(transcript: String?, channel: ChannelEntity?): Result {
        val reasons = mutableListOf<String>()
        var score = 0.0

        val channelPerDay = channel?.let { uploadsPerDay(it) } ?: 0.0
        if (channelPerDay >= 1.0) {
            score += 30; reasons += "Channel posts about ${"%.1f".format(channelPerDay)} videos a day"
        } else if (channelPerDay >= 0.5) {
            score += 15; reasons += "Channel posts very often"
        }

        if (transcript == null) {
            return Result(score.toInt().coerceIn(0, 100), Verdict.NO_TRANSCRIPT, reasons.ifEmpty { listOf("No transcript available") })
        }

        val text = " " + transcript.lowercase().replace(Regex("\\s+"), " ") + " "
        val words = text.split(' ').count { it.isNotBlank() }.coerceAtLeast(1)
        val per1k = 1000.0 / words

        val strongHits = (strong + stockStrong).map { p -> p to count(text, p) }.filter { it.second > 0 }
        val mediumHits = (medium + stockMedium).map { p -> p to count(text, p) }.filter { it.second > 0 }
        val notJust = notJustPattern.findAll(text).count() + contrastPattern.findAll(text).count()
        val triples = triplePattern.findAll(text).count()
        val repeats = repeatedOpenings(text)

        // Short transcripts are noisy: pad with 2,000 "neutral" words so a couple of stray phrases can't flag a 7-minute video.
        val phrasePer1k = 1000.0 / (words + 2000)
        val phraseRate = (strongHits.sumOf { it.second } * 3 + mediumHits.sumOf { it.second } +
            notJust * 3 + triples * 3 + repeats * 2) * phrasePer1k
        // Humans use some of these too (up to ~4 per 1,000 words in testing); only a heavy dose counts.
        score += ((phraseRate - 2.5) * 15).coerceIn(0.0, 60.0)
        details = mapOf(
            "strong" to strongHits.sumOf { it.second }, "medium" to mediumHits.sumOf { it.second },
            "contrast" to notJust, "triples" to triples, "repeats" to repeats, "words" to words,
            "phrasePer1k" to (phraseRate * 10).toInt(),
        )
        if (strongHits.isNotEmpty()) {
            reasons += "AI-style phrases: " + strongHits.sortedByDescending { it.second }.take(4).joinToString { "\"${it.first}\"" }
        }
        if (notJust >= 3) reasons += "Leans on \"it's not just X, it's Y\" contrasts ($notJust times)"
        if (triples >= 3) reasons += "Lots of three-part lines like \"more X, more Y, more Z\" ($triples times)"
        if (repeats >= 4) reasons += "Many back-to-back sentences start the same way ($repeats times)"

        // Self-reference: human essayists say "I"/"my"/"me" constantly.
        val selfRate = (count(text, " i ") + count(text, " i'm ") + count(text, " i've ") + count(text, " my ") + count(text, " me ")) * per1k
        when {
            selfRate < 3 -> { score += 25; reasons += "Narrator almost never says \"I\" or \"my\"" }
            selfRate < 8 -> score += 10
            selfRate > 20 -> score -= 15
        }

        val s = score.toInt().coerceIn(0, 100)
        val verdict = when {
            words < 300 -> Verdict.UNSURE
            s >= 60 -> Verdict.AI
            s <= 25 -> Verdict.HUMAN
            else -> Verdict.UNSURE
        }
        if (reasons.isEmpty()) reasons += "No common AI-writing patterns found"
        return Result(s, verdict, reasons)
    }

    fun uploadsPerDay(c: ChannelEntity): Double {
        if (c.createdAt <= 0 || c.videoCount <= 0) return 0.0
        val days = ((System.currentTimeMillis() - c.createdAt) / 86_400_000.0).coerceAtLeast(30.0)
        return c.videoCount / days
    }

    private fun count(text: String, phrase: String): Int {
        var i = 0; var n = 0
        val p = if (phrase.startsWith(" ")) phrase else " $phrase"
        while (true) {
            i = text.indexOf(p, i)
            if (i < 0) return n
            n++; i += p.length
        }
    }
}
