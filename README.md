# <img src="site/favicon.svg" alt="" width="36" align="absmiddle"> Long Take

### [long-take.web.app](https://long-take.web.app): my video essay recommendations

A small Android app I made for finding and discussing YouTube video essays, for my own enjoyment. Everything I rate in it shows up on the site above.

**[Download the app (APK)](https://github.com/bling05/long-take/releases/latest/download/LongTake.apk)** for Android 8.0 or newer. Your phone will ask you to allow installing apps from your browser or files app.

<p>
  <img src="docs/discover.png" alt="Discover tab" width="270">
  <img src="docs/video.png" alt="Video page" width="270">
</p>

## What it does

- **Discover**: long-form essays from hand-picked channels and curated playlists (including a "YouTube Recs" list and every Jacob Geller video), with topic filters, sorting, and an "essay score" that sets apart essays from other long videos.
- **Library**: saved videos, watch history (it can import your YouTube history from Google Takeout), and your ratings and notes.
- **Profiles**: Letterboxd-style ratings and reviews, following friends, and a feed of what they've rated.
- **Blocking**: hide a channel from everything with one tap.

## Stack

- **App:** Kotlin, Jetpack Compose (Material 3), Room, Coroutines/Flow, OkHttp, Coil
- **Data:** YouTube Data API v3
- **Accounts and social:** Firebase Auth (email and Google sign-in) and Cloud Firestore
- **Site:** a single static HTML page on Firebase Hosting that reads Firestore's REST API directly, with no framework or build step

```
app/src/main/java/com/longtake/
  data/      Room database, repository, settings, seed channels, topics
  youtube/   YouTube Data API client
  social/    Firebase sign-in, profiles, follows, reviews
  ui/        Compose screens
  ai/        AI-script check (switched off in Features.kt)
site/        the recommendations page
```

## Building it yourself

1. Copy `secrets.properties.example` to `secrets.properties` and add a YouTube Data API key. Firebase values are optional; without them, profiles are off.
2. `./gradlew assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.

Tests: `./gradlew testDebugUnitTest`.
