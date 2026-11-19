package com.longtake

import com.longtake.ai.FreeCheck
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Re-scores transcripts saved by LiveVideoTest (transcript_<id>.txt in the temp folder) with the
 * current free check, to see how a change affects known videos. Run with SAVED_TRANSCRIPTS=1.
 */
class SavedTranscriptsTest {
    @Test fun rescoreSaved() {
        assumeTrue("set SAVED_TRANSCRIPTS=1 to run", System.getenv("SAVED_TRANSCRIPTS") != null)
        val files = File(System.getProperty("java.io.tmpdir")).listFiles { f -> f.name.startsWith("transcript_") }.orEmpty()
        files.sortedBy { it.name }.forEach { f ->
            val r = FreeCheck.run(f.readText(), null)
            println("RESCORE ${f.name.removePrefix("transcript_").removeSuffix(".txt")} score=${r.score} verdict=${r.verdict}")
        }
    }
}
