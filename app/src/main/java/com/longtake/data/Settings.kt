package com.longtake.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.longtake.BuildConfig
import com.longtake.ai.EssayScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Match phone") }

enum class ClaudeMode(val label: String, val detail: String) {
    OFF("Off", "Only the free on-phone check is used."),
    UNSURE_ONLY("When unsure", "Ask Claude only when the free check can't decide. Cheapest."),
    ALL("Every video", "Ask Claude about every new video. Most accurate."),
}

enum class ClaudeModel(val id: String, val label: String, val costNote: String) {
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "≈ \$0.0005 per video"),
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "≈ \$0.03 per video, more accurate"),
}

data class AppSettings(
    val youtubeApiKey: String,
    val anthropicApiKey: String,
    val claudeMode: ClaudeMode,
    val claudeModel: ClaudeModel,
    val hideAi: Boolean,
    val hideUnsure: Boolean,
    val hideWatched: Boolean,
    val minMinutes: Int,
    val seeded: Boolean,
    /** The highest [com.longtake.data.Seeds.Seed.since] already added to this phone. */
    val seedsVersion: Int,
    val lastRefreshAt: Long,
    val strictness: EssayScore.Strictness,
    val theme: ThemeMode,
) {
    val hasYoutubeKey get() = youtubeApiKey.isNotBlank()
    val claudeEnabled get() = claudeMode != ClaudeMode.OFF && anthropicApiKey.isNotBlank()
}

/** Discover hides videos shorter than this unless you change it in Settings. */
const val DEFAULT_MIN_MINUTES = 15

class SettingsStore(private val context: Context) {
    private object K {
        val ytKey = stringPreferencesKey("yt_key")
        val anthKey = stringPreferencesKey("anthropic_key")
        val mode = stringPreferencesKey("claude_mode")
        val model = stringPreferencesKey("claude_model")
        val hideAi = booleanPreferencesKey("hide_ai")
        val hideUnsure = booleanPreferencesKey("hide_unsure")
        val hideWatched = booleanPreferencesKey("hide_watched")
        val minMinutes = intPreferencesKey("min_minutes")
        val seeded = booleanPreferencesKey("seeded")
        val seedsVersion = intPreferencesKey("seeds_version")
        val lastRefresh = longPreferencesKey("last_refresh")
        val strictness = stringPreferencesKey("essay_strictness")
        val theme = stringPreferencesKey("theme")
        val synopsisSync = longPreferencesKey("synopsis_synced_until")
        // A new key name makes every phone tidy again (after a change to how tidying works).
        val tagsTidied = longPreferencesKey("tags_tidied_at_3")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun now(): AppSettings = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        youtubeApiKey = this[K.ytKey]?.takeIf { it.isNotBlank() } ?: BuildConfig.YOUTUBE_API_KEY,
        anthropicApiKey = this[K.anthKey].orEmpty(),
        claudeMode = this[K.mode]?.let { runCatching { ClaudeMode.valueOf(it) }.getOrNull() } ?: ClaudeMode.UNSURE_ONLY,
        claudeModel = this[K.model]?.let { runCatching { ClaudeModel.valueOf(it) }.getOrNull() } ?: ClaudeModel.HAIKU,
        hideAi = this[K.hideAi] ?: true,
        hideUnsure = this[K.hideUnsure] ?: false,
        hideWatched = this[K.hideWatched] ?: false,
        minMinutes = this[K.minMinutes] ?: DEFAULT_MIN_MINUTES,
        seeded = this[K.seeded] ?: false,
        seedsVersion = this[K.seedsVersion] ?: 1,
        lastRefreshAt = this[K.lastRefresh] ?: 0L,
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
        strictness = this[K.strictness]?.let { runCatching { EssayScore.Strictness.valueOf(it) }.getOrNull() } ?: EssayScore.Strictness.NORMAL,
    )

    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setStrictness(v: EssayScore.Strictness) = context.dataStore.edit { it[K.strictness] = v.name }

    suspend fun setLastRefresh(v: Long) = context.dataStore.edit { it[K.lastRefresh] = v }

    suspend fun setYoutubeKey(v: String) = context.dataStore.edit { it[K.ytKey] = v.trim() }
    suspend fun setAnthropicKey(v: String) = context.dataStore.edit { it[K.anthKey] = v.trim() }
    suspend fun setClaudeMode(v: ClaudeMode) = context.dataStore.edit { it[K.mode] = v.name }
    suspend fun setClaudeModel(v: ClaudeModel) = context.dataStore.edit { it[K.model] = v.name }
    suspend fun setHideAi(v: Boolean) = context.dataStore.edit { it[K.hideAi] = v }
    suspend fun setHideUnsure(v: Boolean) = context.dataStore.edit { it[K.hideUnsure] = v }
    suspend fun setHideWatched(v: Boolean) = context.dataStore.edit { it[K.hideWatched] = v }
    suspend fun setMinMinutes(v: Int) = context.dataStore.edit { it[K.minMinutes] = v }
    /** Newest shared synopsis already copied to this phone. */
    suspend fun synopsisSyncedUntil(): Long = context.dataStore.data.first()[K.synopsisSync] ?: 0
    suspend fun setSynopsisSyncedUntil(v: Long) = context.dataStore.edit { it[K.synopsisSync] = v }

    /** When duplicate recommendation tags were last merged (see SynopsisMaker). */
    suspend fun tagsTidiedAt(): Long = context.dataStore.data.first()[K.tagsTidied] ?: 0
    suspend fun setTagsTidiedAt(v: Long) = context.dataStore.edit { it[K.tagsTidied] = v }
    suspend fun setSeeded(v: Boolean) = context.dataStore.edit { it[K.seeded] = v }
    suspend fun setSeedsVersion(v: Int) = context.dataStore.edit { it[K.seedsVersion] = v }
}
