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
import com.anthropic.models.messages.Usage
import com.longtake.data.ClaudeModel
import com.longtake.data.Tags
import com.longtake.data.Topics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Asks Claude for a one- or two-sentence synopsis of a video, plus which Discover topics it fits, how
 * heavy it is, and hidden tags for recommendations ([Tags]).
 */
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
        val styles: List<Tags.Style>,
        val tags: List<String>,
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
          silly or bizarre story, a run of famous blunders, an unlikely underdog win, the story of a
          speedrun or world record, even when told straight. A fun subject alone isn't enough: a
          straight analysis, history or review of a cartoon, game or film is neutral.
        - heavy: centred on real death, abuse, cruelty or crime, or built around dread, despair or mortality.
        - neutral: everything else, which is most videos: analysis, history, explainers, reviews and
          internet mysteries, including ones about dark or grim fiction.

        Then two kinds of tags. Nobody sees these: they're only used to recommend videos similar to
        ones a person loved, so aim for what a fan of this video would want more of.
        - styles: one or two from this list that best describe how the video is made:
        STYLES
        - tags: 5 to MAX specific tags for what's in it: the works, franchises, creators, people,
          places, genres, themes and formats it's about (for example "pixar", "liminal spaces",
          "speedrun", "cosmic horror", "cold war", "con artist", "fromsoftware", "architecture").
          Lowercase, one to three words each, the common name for the thing. Skip words true of almost
          every video here ("video essay", "youtube", "analysis", "story", "history").
          You also get the tags already used for other videos. Reuse one whenever it means the same
          thing, spelt exactly as listed; only make up a new tag when nothing listed fits.
    """.trimIndent()
        .replace("STYLES", Tags.Style.entries.joinToString("\n") { "  -${it.key}: ${it.detail}" })
        .replace("MAX", Tags.MAX_SPECIFIC.toString())

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
                    "styles" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string", "enum" to Tags.Style.entries.map { it.key }),
                    ),
                    "tags" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                )
            ),
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("synopsis", "topics", "mood", "styles", "tags")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()

    /** [usedTags] is the specific tags already given to other videos, for Claude to reuse. */
    suspend fun write(
        apiKey: String, title: String, channel: String, description: String, transcript: String?,
        usedTags: Collection<String> = emptyList(),
    ): Result =
        withContext(Dispatchers.IO) {
            val prompt = buildString {
                append("Title: $title\nChannel: $channel\n\nDescription (start):\n${description.take(800)}\n\n")
                if (transcript != null) append("Transcript excerpts:\n${transcriptExcerpts(transcript)}")
                else append("No transcript available.")
                append("\n\nTags already used for other videos: ")
                append(if (usedTags.isEmpty()) "none yet." else usedTags.sorted().joinToString(", "))
            }
            val (json, usage) = ask(apiKey, system, schema, prompt, maxTokens = 2000L)
            val labels = Topics.all.map { it.label }.toSet()
            val topics = json.optJSONArray("topics").strings().filter { it in labels }
            val styles = json.optJSONArray("styles").strings().mapNotNull(Tags.Style::parse).take(2)
            Result(
                json.optString("synopsis").trim(), topics, Topics.Mood.parse(json.optString("mood")), styles,
                json.optJSONArray("tags").strings(), usage.inputTokens(), usage.outputTokens(),
            )
        }

    private val mergeSystem = """
        You tidy a list of tags given to YouTube video essays. The tags are only used to find videos
        similar to each other, so two tags for the same idea split videos that should match. Each tag
        is followed by how many videos have it.

        Find every group of tags that mean the same thing and map each one to the tag to keep (usually
        the most used of the group, and always one of the listed tags). That includes:
        - singular and plural ("video game" and "video games"), spelling and punctuation variants;
        - a name with and without a prefix or a cut-off ending ("the legend of zelda" and "zelda",
          "dark soul" and "dark souls", "fallout new vega" and "fallout new vegas");
        - synonyms and near-identical phrasings ("film analysis", "movie analysis" and "film critique";
          "theme park" and "amusement park"; "video game design" and "game design").
        Don't merge tags that are only related: a broader and a narrower idea stay separate ("horror" and
        "cosmic horror", "nintendo" and "mario", "video game" and "horror game"). Leave out tags that
        don't need changing.
    """.trimIndent()

    private val mergeSchema: JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty(
            "properties",
            JsonValue.from(
                mapOf(
                    "merges" to mapOf(
                        "type" to "array",
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf("from" to mapOf("type" to "string"), "to" to mapOf("type" to "string")),
                            "required" to listOf("from", "to"),
                            "additionalProperties" to false,
                        ),
                    ),
                )
            ),
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("merges")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()

    /** Asks Claude which tags in [counts] are the same idea; returns a map from each duplicate to the tag to keep. */
    suspend fun findDuplicateTags(apiKey: String, counts: Map<String, Int>): Map<String, String> = withContext(Dispatchers.IO) {
        val prompt = counts.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} (${it.value})" }
        // One request over the whole list, where judgement matters more than cost, so Opus does it.
        val (json, _) = ask(apiKey, mergeSystem, mergeSchema, prompt, maxTokens = 32_000L, model = ClaudeModel.OPUS)
        val merges = json.optJSONArray("merges")
        val pairs = (0 until (merges?.length() ?: 0)).associate { i ->
            val m = merges!!.getJSONObject(i)
            m.optString("from") to m.optString("to")
        }.filter { (from, to) -> from != to && from in counts && to in counts }
        // Follow chains ("a" → "b" → "c" becomes "a" → "c"), stopping if one loops back.
        pairs.mapValues { (from, to) ->
            var end = to
            val seen = mutableSetOf(from)
            while (end in pairs && seen.add(end)) end = pairs.getValue(end)
            end
        }.filter { (from, to) -> from != to }
    }

    private fun ask(
        apiKey: String, system: String, schema: JsonOutputFormat.Schema, prompt: String, maxTokens: Long,
        model: ClaudeModel = ClaudeModel.HAIKU,
    ): Pair<JSONObject, Usage> {
        val params = MessageCreateParams.builder()
            .model(model.id)
            .maxTokens(maxTokens)
            .system(system)
            // The answer format is spelled out by hand: the SDK's automatic class-to-schema
            // shortcut uses Java reflection that Android doesn't support.
            .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema).build()).build())
            .addUserMessage(prompt)
            .build()
        try {
            val msg = clientFor(apiKey).messages().create(params)
            val text = msg.content().firstNotNullOfOrNull { block -> block.text().orElse(null)?.text() }
                ?: throw ClaudeException("Claude didn't return an answer.")
            return JSONObject(text) to msg.usage()
        } catch (e: UnauthorizedException) {
            throw ClaudeException("The Claude API key isn't valid. Check it in Settings.")
        } catch (e: RateLimitException) {
            throw ClaudeException("Claude is rate-limiting requests. Try again in a minute.")
        } catch (e: AnthropicServiceException) {
            throw ClaudeException("Claude error (${e.statusCode()}): ${e.message}")
        }
    }
}

internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
