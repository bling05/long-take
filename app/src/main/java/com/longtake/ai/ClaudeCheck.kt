package com.longtake.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.longtake.data.ClaudeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClaudeException(message: String) : Exception(message)

/** Asks Claude whether a transcript reads like an AI-written script. */
class ClaudeCheck {
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private fun clientFor(key: String): AnthropicClient {
        if (client == null || clientKey != key) {
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client!!
    }

    private val system = """
        You review transcripts of YouTube video essays and judge whether the SCRIPT was written by an AI model.
        A human reading an AI-written script counts as AI_SCRIPT. Only the writing matters, not the voice.

        Signs of an AI-written script: generic, even-toned narration with no personal perspective or lived
        experience; stock phrases ("delve", "a testament to", "rich tapestry", "it's not just X, it's Y",
        "in the realm of", "stands as a reminder"); every section built the same way; listing facts
        without real argument; vague hype ("fascinating", "captivating") instead of specifics; neat
        summarising sentences at the end of every section; no jokes, tangents, self-corrections, or opinions
        the narrator actually commits to.

        Signs of a human script: a distinct voice and sense of humour; first-person anecdotes; strong
        or unusual opinions; digressions; specific, checkable details; references to their own past videos,
        audience, or sponsors; uneven pacing.

        The transcript may be auto-generated captions: missing punctuation, misheard words and "[Music]"
        are NOT evidence either way. You are seeing a few excerpts, not the whole thing. Use UNSURE when the
        excerpts are too short or mixed.
    """.trimIndent()

    /** [inputTokens]/[outputTokens] are what the call was billed for (output includes thinking). */
    data class Result(val verdict: String, val confidence: Int, val reason: String, val inputTokens: Long = 0, val outputTokens: Long = 0)

    private val schema: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty(
            "properties",
            JsonValue.from(
                mapOf(
                    "verdict" to mapOf(
                        "type" to "string",
                        "enum" to listOf("HUMAN", "AI_SCRIPT", "UNSURE"),
                        "description" to "HUMAN if a person wrote the script, AI_SCRIPT if it was mostly written by an AI model (even if a human reads it aloud), UNSURE if the evidence is mixed or too thin.",
                    ),
                    "confidence" to mapOf("type" to "integer", "description" to "How confident you are in the verdict, from 0 to 100."),
                    "reason" to mapOf("type" to "string", "description" to "One or two plain sentences explaining the main evidence."),
                )
            ),
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("verdict", "confidence", "reason")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()

    suspend fun check(apiKey: String, model: ClaudeModel, title: String, channel: String, transcript: String): Result =
        withContext(Dispatchers.IO) {
            val excerpt = sample(transcript)
            val params = MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(4000L)
                .system(system)
                // The answer format is spelled out by hand: the SDK's automatic class-to-schema
                // shortcut uses Java reflection that Android doesn't support.
                .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema).build()).build())
                .addUserMessage("Video title: $title\nChannel: $channel\n\nTranscript excerpts:\n$excerpt")
                .build()
            try {
                val msg = clientFor(apiKey).messages().create(params)
                val text = msg.content().firstNotNullOfOrNull { block -> block.text().orElse(null)?.text() }
                    ?: throw ClaudeException("Claude didn't return a verdict.")
                val json = org.json.JSONObject(text)
                val verdict = when (json.optString("verdict")) {
                    "HUMAN" -> com.longtake.data.Verdict.HUMAN
                    "AI_SCRIPT" -> com.longtake.data.Verdict.AI
                    else -> com.longtake.data.Verdict.UNSURE
                }
                Result(
                    verdict, json.optInt("confidence").coerceIn(0, 100), json.optString("reason"),
                    msg.usage().inputTokens(), msg.usage().outputTokens(),
                )
            } catch (e: UnauthorizedException) {
                throw ClaudeException("The Claude API key isn't valid. Check it in Settings.")
            } catch (e: RateLimitException) {
                throw ClaudeException("Claude is rate-limiting requests. Try again in a minute.")
            } catch (e: AnthropicServiceException) {
                throw ClaudeException("Claude error (${e.statusCode()}): ${e.message}")
            }
        }

    /** Beginning, middle and later excerpts (~2,400 words total) keep the cost low. */
    private fun sample(transcript: String): String {
        val words = transcript.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 2400) return words.joinToString(" ")
        val chunk = 800
        val starts = listOf(words.size / 20, words.size / 2 - chunk / 2, (words.size * 0.8).toInt() - chunk / 2)
        return starts.joinToString("\n\n[…]\n\n") { s ->
            val st = s.coerceIn(0, words.size - chunk)
            words.subList(st, st + chunk).joinToString(" ")
        }
    }
}
