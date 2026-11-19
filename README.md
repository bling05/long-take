# <img src="site/favicon.svg" alt="" width="36" align="absmiddle"> Long Take

### [long-take.web.app](https://long-take.web.app): my video essay recommendations

A small Android app I made for finding and discussing YouTube video essays, for my own enjoyment. Everything I rate in it shows up on the site above.

**[Download the app (APK)](https://github.com/bling05/long-take/releases/latest/download/LongTake.apk)** for Android 8.0 or newer. Your phone will ask you to allow installing apps from your browser or files app.

<p>
  <img src="docs/discover.png" alt="Discover tab" width="270">
  <img src="docs/video.png" alt="Video page" width="270">
</p>

## What it does

- **Discover**: long-form essays from hand-picked channels and curated playlists (including a "YouTube Recs" list and every Jacob Geller video), with topic filters (including a "Lighthearted" one for fun, feel-good stories) and an "essay score" that sets apart essays from other long videos.
- **Synopses**: a one-sentence summary under every video, written by Claude from the transcript. Each video is summarised once and shared with everyone using the app.
- **Sorting**: most viewed, newest, longest, or "hidden gems" (videos that did unusually well for the size of their channel), plus a strictness slider for how essay-like a video has to be.
- **Your own sources**: add any YouTube channel or playlist by link or @handle, and its long videos join your feed. Paste a single video link to rate something that isn't listed.
- **Search**: long videos only, so results skip Shorts and clips.
- **Library**: saved videos, watch history (it can import your YouTube history from Google Takeout), and your ratings and notes.
- **Profiles**: Letterboxd-style ratings and reviews, four favourite essays, following friends, and a feed of what they've rated. Notes stay private unless you share them as a review.
- **Blocking**: hide a channel from everything with one tap, and unblock it any time from Settings.

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
