package com.longtake

import com.longtake.ai.FreeCheck
import com.longtake.data.ChannelEntity
import com.longtake.data.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksTest {

    private val aiScript = """
        In the realm of modern cinema, few films stand as a testament to the power of storytelling like this one.
        Let's delve into the rich tapestry of its themes. It's not just a movie, it's a cultural phenomenon.
        The director's multifaceted vision plays a crucial role in shaping the narrative. Moreover, the intricate
        dance between light and shadow serves as a reminder of the ever-evolving landscape of film. It's important
        to note that the score is captivating and profound. Furthermore, the characters embark on a journey that
        resonates with audiences. In conclusion, this film left an indelible mark on the world of cinema.
    """.trimIndent().repeat(6)

    private val humanScript = """
        okay so I have to tell you about the first time I played this game because I was like twelve and my brother
        had borrowed it from a friend and I genuinely did not understand what was happening. I'm still not sure I do.
        I went back to it last month for this video and my opinion has changed a lot, which is embarrassing for me
        personally, because I said some very confident things in a video I made in 2019 that I now think are wrong.
        so let me walk that back. I think the level design is actually doing something weird and I love it, and I'm
        going to spend the next forty minutes trying to convince you, my patient viewer, that I'm right this time.
    """.trimIndent().repeat(6)

    @Test fun aiScriptScoresHigh() {
        val r = FreeCheck.run(aiScript, null)
        println("AI script: ${r.score} ${r.verdict} ${r.reasons}")
        assertEquals(Verdict.AI, r.verdict)
    }

    @Test fun humanScriptScoresLow() {
        val r = FreeCheck.run(humanScript, null)
        println("Human script: ${r.score} ${r.verdict} ${r.reasons}")
        assertEquals(Verdict.HUMAN, r.verdict)
    }

    @Test fun noTranscriptIsReported() {
        val spammy = ChannelEntity(id = "x", title = "x", videoCount = 900, createdAt = System.currentTimeMillis() - 200L * 86_400_000)
        val r = FreeCheck.run(null, spammy)
        println("No transcript: ${r.score} ${r.reasons}")
        assertEquals(Verdict.NO_TRANSCRIPT, r.verdict)
        assertTrue(r.score >= 30)
    }
}
