package com.longtake

import android.app.Application
import com.longtake.ai.ClaudeCheck
import com.longtake.ai.SynopsisWriter
import com.longtake.data.AppDatabase
import com.longtake.data.Repository
import com.longtake.data.SettingsStore
import com.longtake.social.SocialRepo
import com.longtake.youtube.TranscriptFetcher
import com.longtake.youtube.YouTubeApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LongTakeApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val settings = SettingsStore(this)
        val youtube = YouTubeApi(http = http, apiKey = { settings.now().youtubeApiKey })
        val db = AppDatabase.create(this)
        repo = Repository(this, db, settings, youtube, TranscriptFetcher(http), ClaudeCheck(), SynopsisWriter(), SocialRepo(this, db))
    }
}
