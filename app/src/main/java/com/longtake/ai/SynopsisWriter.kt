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
import com.longtake.data.Topics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Asks Claude for a one- or two-sentence synopsis of a video, plus which Discover topics it fits and how heavy it is. */
class SynopsisWriter {
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private fun clientFor(key: String): AnthropicClient {
        if (client == null || clientKey != key) {
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client!!
    }

    data class Result(
        val synopsis: String,
        val topics: List<String>,
        val mood: Topics.Mood,
        val inputTokens: Long,
        val outputTokens: Long,
    )

    private val system = """
        You write synopses for a list of YouTube video essays that people browse to decide what to watch.

        Write one plain sentence (two only if really needed), at most 180 characters in total, saying what
        the video is about and what angle it takes. Start with the subject itself, never with "This video" or "In this essay". Use the
        present tense. Be specific: name the film, game, person or event. No hype words ("fascinating",
        "deep dive", "must-watch"), no spoilers for twists, and no mention of sponsors.

        You get the title, channel, the start of the description and, when available, excerpts from the
        transcript (often auto-generated captions with missing punctuation). If there is no transcript,
        work from the title and description and stay general rather than guess.

        Also pick the topics the video is mainly about, from the given list only: usually one, at most two,
        none if nothing fits. Don't stretch a topic to cover something near it (a video about theme parks
        is not about games).

        Finally, rate the video's mood, for someone choosing what to put on:
        - light: the video is played for laughs, or it's a fun story at heart: a roast, a joke theory, a
          silly or bizarre story, a run of famous blunders, an unlikely underdog win, even when told
          straight. A fun subject alone isn't enough: a straight analysis, history or review of a
          cartoon, game or film is neutral.
        - heavy: centred on real death, abuse, cruelty or crime, or built around dread, despair or mortality.
        - neutral: everything else, which is most videos: analysis, history, explainers, reviews and
          internet mysteries, including ones about dark or grim fiction.
    """.trimIndent()

    private val schema: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty(
            "properties",
            JsonValue.from(
                mapOf(
                    "synopsis" to mapOf("type" to "string", "description" to "One sentence (two at most), at most 180 characters."),
                    "topics" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string", "enum" to Topics.all.map { it.label }),
                    ),
                    "mood" to mapOf("type" to "string", "enum" to Topics.Mood.entries.map { it.name.lowercase() }),
                )
            ),
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("synopsis", "topics", "mood")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()

    suspend fun write(apiKey: String, title: String, channel: String, description: String, transcript: String?): Result =
        withContext(Dispatchers.IO) {
            val prompt = buildString {
                append("Title: $title\nChannel: $channel\n\nDescription (start):\n${description.take(800)}\n\n")
                if (transcript != null) append("Transcript excerpts:\n${transcriptExcerpts(transcript)}")
                else append("No transcript available.")
            }
            val params = MessageCreateParams.builder()
                .model(ClaudeModel.HAIKU.id)
                .maxTokens(2000L)
                .system(system)
                // The answer format is spelled out by hand: the SDK's automatic class-to-schema
                // shortcut uses Java reflection that Android doesn't support.
                .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema).build()).build())
                .addUserMessage(prompt)
                .build()
            try {
                val msg = clientFor(apiKey).messages().create(params)
                val text = msg.content().firstNotNullOfOrNull { block -> block.text().orElse(null)?.text() }
                    ?: throw ClaudeException("Claude didn't return a synopsis.")
                val json = JSONObject(text)
                val labels = Topics.all.map { it.label }.toSet()
                val topicsJson = json.optJSONArray("topics")
                val topics = (0 until (topicsJson?.length() ?: 0)).map { topicsJson!!.optString(it) }.filter { it in labels }
                Result(json.optString("synopsis").trim(), topics, Topics.Mood.parse(json.optString("mood")), msg.usage().inputTokens(), msg.usage().outputTokens())
            } catch (e: UnauthorizedException) {
                throw ClaudeException("The Claude API key isn't valid. Check it in Settings.")
            } catch (e: RateLimitException) {
                throw ClaudeException("Claude is rate-limiting requests. Try again in a minute.")
            } catch (e: AnthropicServiceException) {
                throw ClaudeException("Claude error (${e.statusCode()}): ${e.message}")
            }
        }
}
