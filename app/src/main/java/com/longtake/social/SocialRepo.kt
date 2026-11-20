package com.longtake.social

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.longtake.BuildConfig
import com.longtake.data.AppDatabase
import com.longtake.data.SynopsisEntity
import com.longtake.data.UserVideoEntity
import com.longtake.data.VideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class VideoRef(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationSec: Long,
)

data class Profile(
    val uid: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val photoUrl: String,
    val favorites: List<VideoRef>,
)

/** One public rating/review, as shown on profiles, the feed and video pages. */
data class Entry(
    val uid: String,
    val username: String,
    val rating: Int,
    val review: String,
    val video: VideoRef,
    val updatedAt: Long,
)

data class Counts(val ratings: Long, val followers: Long, val following: Long)

sealed interface AccountState {
    /** Firebase isn't configured in this build. */
    data object Disabled : AccountState
    data object SignedOut : AccountState
    data object Loading : AccountState
    data class NeedsUsername(val suggestedName: String) : AccountState
    data class Ready(val profile: Profile) : AccountState
}

class SocialException(message: String) : Exception(message)

/**
 * Accounts, profiles, following and community reviews, stored in Firebase.
 *
 * Firestore layout:
 *  users/{uid}                       public profile
 *  users/{uid}/ratings/{videoId}     public ratings + shared reviews
 *  users/{uid}/private/{videoId}     owner-only backup (saved, watched, notes)
 *  users/{uid}/following/{uid2}      who they follow
 *  users/{uid}/followers/{uid2}      who follows them
 *  usernames/{name}                  makes usernames unique
 *  videos/{videoId}/reviews/{uid}    copy of each public rating, for video pages
 *  reports/{id}                      reported reviews
 */
class SocialRepo(private val context: Context, private val db: AppDatabase) {

    val enabled: Boolean = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()
    val googleSignInAvailable: Boolean = enabled && (BuildConfig.FIREBASE_USE_EMULATOR || BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank())

    private val auth: FirebaseAuth?
    private val fs: FirebaseFirestore?
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AccountState>(if (enabled) AccountState.Loading else AccountState.Disabled)
    val state: StateFlow<AccountState> = _state

    /** Goes up by one each time a sign-in sync finishes, so screens can reload. */
    private val _syncCount = MutableStateFlow(0)
    val syncCount: StateFlow<Int> = _syncCount

    private var profileListener: ListenerRegistration? = null
    private var restoredFor: String? = null

    init {
        if (enabled) {
            val options = FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID.ifBlank { "1:000000000000:android:0000000000000000" })
                .setApiKey(BuildConfig.FIREBASE_API_KEY.ifBlank { "local-emulator-key" })
                .build()
            val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context, options)
            auth = FirebaseAuth.getInstance(app)
            fs = FirebaseFirestore.getInstance(app)
            if (BuildConfig.FIREBASE_USE_EMULATOR) {
                // 10.0.2.2 is "this computer" as seen from the Android emulator.
                auth.useEmulator("10.0.2.2", 9099)
                fs.useEmulator("10.0.2.2", 8080)
            }
            auth.addAuthStateListener { a -> onUserChanged(a.currentUser) }
        } else {
            auth = null; fs = null
        }
    }

    val uid: String? get() = auth?.currentUser?.uid
    val readyProfile: Profile? get() = (state.value as? AccountState.Ready)?.profile

    private fun store(): FirebaseFirestore = fs ?: throw SocialException("Profiles aren't switched on in this build.")
    private fun authOrThrow(): FirebaseAuth = auth ?: throw SocialException("Profiles aren't switched on in this build.")
    private fun me(): String = uid ?: throw SocialException("Sign in first.")

    private fun onUserChanged(user: FirebaseUser?) {
        profileListener?.remove(); profileListener = null
        if (user == null) { _state.value = AccountState.SignedOut; restoredFor = null; return }
        _state.value = AccountState.Loading
        profileListener = store().collection("users").document(user.uid).addSnapshotListener { snap, err ->
            if (err != null) { _state.value = AccountState.NeedsUsername(suggest(user)); return@addSnapshotListener }
            val p = snap?.takeIf { it.exists() }?.toProfile()
            if (p == null) {
                _state.value = AccountState.NeedsUsername(suggest(user))
            } else {
                _state.value = AccountState.Ready(p)
                if (restoredFor != user.uid) {
                    restoredFor = user.uid
                    scope.launch { runCatching { syncOnSignIn() }; _syncCount.value += 1 }
                }
            }
        }
    }

    private fun suggest(u: FirebaseUser): String =
        (u.displayName ?: u.email?.substringBefore('@') ?: "").lowercase().replace(Regex("[^a-z0-9_]"), "").take(20)

    suspend fun signUpWithEmail(email: String, password: String) = wrap {
        if (password.length < 8) throw SocialException("Use at least 8 characters for your password.")
        authOrThrow().createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signInWithEmail(email: String, password: String) = wrap {
        authOrThrow().signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) = wrap {
        authOrThrow().sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signInWithGoogle(activity: Activity) = wrap {
        val a = authOrThrow()
        if (BuildConfig.FIREBASE_USE_EMULATOR) {
            // The local test server accepts a made-up Google account.
            val fake = """{"sub":"test-google-user","email":"tester@example.com","name":"Test Google User","email_verified":true}"""
            a.signInWithCredential(GoogleAuthProvider.getCredential(fake, null)).await()
            return@wrap
        }
        // The full "Sign in with Google" screen, which also lets you add an account
        // when the phone doesn't have one yet.
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val result = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
            a.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        } else throw SocialException("Google didn't return an account.")
    }

    fun signOut() { auth?.signOut() }

    private suspend fun <T> wrap(block: suspend () -> T): T = try { block() } catch (e: FirebaseAuthException) {
        throw SocialException(
            when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "There's already an account with that email. Try signing in."
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Wrong email or password."
                "ERROR_WEAK_PASSWORD" -> "Choose a stronger password."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a minute and try again."
                "ERROR_REQUIRES_RECENT_LOGIN" -> "For safety, sign out and back in, then try again."
                "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method isn't switched on yet."
                else -> if (e.message.orEmpty().contains("CONFIGURATION_NOT_FOUND")) "Sign-in isn't set up yet. Please try again later."
                else e.message ?: "Sign-in failed."
            }
        )
    } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
        throw SocialException("Sign-in was cancelled.")
    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
        throw SocialException("Google sign-in isn't available: ${e.message}")
    }

    suspend fun claimUsername(username: String, displayName: String) {
        val name = username.trim().lowercase()
        if (!name.matches(Regex("^[a-z0-9_]{3,20}$"))) throw SocialException("Usernames are 3–20 letters, numbers or underscores.")
        val uid = me()
        val f = store()
        val photo = auth?.currentUser?.photoUrl?.toString().orEmpty()
        f.runTransaction { tx ->
            val taken = tx.get(f.collection("usernames").document(name))
            if (taken.exists() && taken.getString("uid") != uid) throw SocialException("@$name is taken. Try another.")
            tx.set(f.collection("usernames").document(name), mapOf("uid" to uid))
            tx.set(
                f.collection("users").document(uid),
                mapOf(
                    "username" to name,
                    "displayName" to displayName.trim().ifBlank { name }.take(40),
                    "bio" to "",
                    "photoUrl" to photo,
                    "favorites" to emptyList<Map<String, Any>>(),
                    "createdAt" to System.currentTimeMillis(),
                ),
            )
        }.await()
    }

    suspend fun updateProfile(displayName: String, bio: String) {
        store().collection("users").document(me())
            .update(mapOf("displayName" to displayName.trim().take(40), "bio" to bio.trim().take(300))).await()
    }

    suspend fun setFavorite(video: VideoRef, favorite: Boolean) {
        val p = readyProfile ?: throw SocialException("Sign in first.")
        val list = p.favorites.filter { it.videoId != video.videoId }.toMutableList()
        if (favorite) {
            if (list.size >= 4) throw SocialException("You can have 4 favourites. Remove one from your profile first.")
            list += video
        }
        store().collection("users").document(p.uid).update("favorites", list.map { it.toMap() }).await()
    }

    suspend fun profile(uid: String): Profile? =
        store().collection("users").document(uid).get().await().takeIf { it.exists() }?.toProfile()

    suspend fun counts(uid: String): Counts = coroutineScope {
        val user = store().collection("users").document(uid)
        val r = async { user.collection("ratings").count().get(AggregateSource.SERVER).await().count }
        val a = async { user.collection("followers").count().get(AggregateSource.SERVER).await().count }
        val b = async { user.collection("following").count().get(AggregateSource.SERVER).await().count }
        Counts(r.await(), a.await(), b.await())
    }

    suspend fun entries(uid: String, limit: Long = 50): List<Entry> =
        store().collection("users").document(uid).collection("ratings")
            .orderBy("updatedAt", Query.Direction.DESCENDING).limit(limit).get().await()
            .documents.mapNotNull { it.toEntry() }

    suspend fun searchUsers(text: String): List<Profile> {
        val q = text.trim().lowercase().removePrefix("@")
        if (q.isBlank()) return emptyList()
        return store().collection("users")
            .whereGreaterThanOrEqualTo("username", q).whereLessThan("username", q + "")
            .limit(20).get().await().documents.mapNotNull { it.toProfile() }
    }

    suspend fun isFollowing(other: String): Boolean =
        store().collection("users").document(me()).collection("following").document(other).get().await().exists()

    suspend fun setFollowing(other: String, follow: Boolean) {
        val mine = me()
        if (mine == other) return
        val f = store()
        val a = f.collection("users").document(mine).collection("following").document(other)
        val b = f.collection("users").document(other).collection("followers").document(mine)
        val batch = f.batch()
        if (follow) {
            val now = mapOf("at" to System.currentTimeMillis())
            batch.set(a, now); batch.set(b, now)
        } else {
            batch.delete(a); batch.delete(b)
        }
        batch.commit().await()
    }

    /** Recent ratings from everyone you follow, newest first. */
    suspend fun feed(): List<Entry> = coroutineScope {
        val following = store().collection("users").document(me()).collection("following")
            .limit(100).get().await().documents.map { it.id }
        following.map { u -> async { runCatching { entries(u, 10) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten().sortedByDescending { it.updatedAt }.take(80)
    }

    data class Community(val average: Double, val count: Int, val entries: List<Entry>)

    suspend fun community(videoId: String): Community {
        val docs = store().collection("videos").document(videoId).collection("reviews")
            .orderBy("updatedAt", Query.Direction.DESCENDING).limit(200).get().await()
            .documents.mapNotNull { it.toEntry() }
        val rated = docs.filter { it.rating > 0 }
        return Community(if (rated.isEmpty()) 0.0 else rated.map { it.rating }.average(), rated.size, docs)
    }

    suspend fun report(videoId: String, reviewerUid: String) {
        store().collection("reports").add(
            mapOf("videoId" to videoId, "reviewerUid" to reviewerUid, "reporterUid" to me(), "at" to System.currentTimeMillis())
        ).await()
    }

    /** Synopses added to the shared collection after [since] (milliseconds), oldest first. */
    suspend fun synopsesSince(since: Long): List<SynopsisEntity> {
        val docs = store().collection("synopses").whereGreaterThan("createdAt", since)
            .orderBy("createdAt").get().await().documents
        return docs.mapNotNull { d ->
            val text = d.getString("text") ?: return@mapNotNull null
            SynopsisEntity(d.id, text, d.getString("topics").orEmpty(), d.getLong("createdAt") ?: 0, d.getString("tags").orEmpty())
        }
    }

    /** Shares a synopsis with everyone. Only the site owner's account may write these (see firestore.rules). */
    suspend fun shareSynopsis(s: SynopsisEntity) {
        if (uid != SYNOPSIS_WRITER_UID) return
        store().collection("synopses").document(s.videoId)
            .set(mapOf("text" to s.text, "topics" to s.topics, "createdAt" to s.createdAt, "tags" to s.tags)).await()
    }

    /** Push one video's rating/review (public) and saved/watched/note (private backup). */
    suspend fun push(videoId: String) {
        val p = readyProfile ?: return
        val video = db.videos().getVideos(listOf(videoId)).firstOrNull() ?: return
        val user = db.userVideos().get(videoId) ?: return
        val f = store()
        val userDoc = f.collection("users").document(p.uid)
        val publicRef = userDoc.collection("ratings").document(videoId)
        val videoRef = f.collection("videos").document(videoId).collection("reviews").document(p.uid)
        val privateRef = userDoc.collection("private").document(videoId)
        val review = if (user.reviewPublic) user.note.trim().take(2000) else ""
        val batch = f.batch()
        if (user.rating > 0 || review.isNotBlank()) {
            val data = mapOf(
                "uid" to p.uid, "username" to p.username, "rating" to user.rating, "review" to review,
                // Reviews are ordered by this, so it only moves when the rating or review changes.
                "video" to video.toRef().toMap(), "updatedAt" to (user.reviewedAt.takeIf { it > 0 } ?: user.updatedAt),
            )
            batch.set(publicRef, data); batch.set(videoRef, data)
        } else {
            batch.delete(publicRef); batch.delete(videoRef)
        }
        batch.set(
            privateRef,
            mapOf(
                "saved" to user.saved, "savedAt" to user.savedAt, "watched" to user.watched, "watchedAt" to user.watchedAt,
                "rating" to user.rating, "note" to user.note.take(5000), "reviewPublic" to user.reviewPublic,
                "updatedAt" to user.updatedAt, "reviewedAt" to user.reviewedAt, "video" to video.toRef().toMap(),
            ),
        )
        batch.commit().await()
    }

    /** After signing in: restore the backup to this phone, then upload anything newer from this phone. */
    private suspend fun syncOnSignIn() {
        val p = readyProfile ?: return
        val remote = store().collection("users").document(p.uid).collection("private").get().await().documents
        val remoteIds = mutableSetOf<String>()
        for (d in remote) {
            val id = d.id; remoteIds += id
            val ref = (d.get("video") as? Map<*, *>)?.toRef() ?: continue
            val remoteUpdated = d.getLong("updatedAt") ?: 0
            val local = db.userVideos().get(id)
            if (local != null && local.updatedAt >= remoteUpdated) continue
            if (db.videos().getVideos(listOf(id)).isEmpty()) db.videos().upsertVideos(listOf(ref.toEntity()))
            db.userVideos().upsert(
                UserVideoEntity(
                    videoId = id,
                    saved = d.getBoolean("saved") ?: false, savedAt = d.getLong("savedAt") ?: 0,
                    watched = d.getBoolean("watched") ?: false, watchedAt = d.getLong("watchedAt") ?: 0,
                    rating = (d.getLong("rating") ?: 0).toInt(), note = d.getString("note").orEmpty(),
                    reviewPublic = d.getBoolean("reviewPublic") ?: false, updatedAt = remoteUpdated,
                    reviewedAt = d.getLong("reviewedAt") ?: remoteUpdated,
                )
            )
        }
        // Upload this phone's ratings, notes, saved and (app-logged) watched videos.
        val localIds = db.userVideos().allIds()
        val known = db.videos().getVideos(localIds).map { it.id }.toSet()
        for (u in db.userVideos().getAll(localIds.filter { it in known })) {
            val interesting = u.saved || u.rating > 0 || u.note.isNotBlank() || u.watched
            if (interesting) runCatching { push(u.videoId) }
        }
    }

    /** Required by Google Play: removes the profile, ratings, reviews, follows, backup and the account itself. */
    suspend fun deleteAccount() = withContext(NonCancellable) {
        val p = readyProfile ?: throw SocialException("Sign in first.")
        // Stop watching the profile so the screen doesn't jump to "pick a username" halfway through.
        profileListener?.remove(); profileListener = null
        _state.value = AccountState.Loading
        try {
            deleteEverything(p)
        } catch (e: Exception) {
            onUserChanged(auth?.currentUser) // put the screen back in a sensible state
            throw e
        }
    }

    private suspend fun deleteEverything(p: Profile) {
        val f = store()
        val user = f.collection("users").document(p.uid)
        suspend fun deleteAll(q: Query, extra: (DocumentSnapshot) -> Unit = {}) {
            for (d in q.get().await().documents) { extra(d); d.reference.delete().await() }
        }
        deleteAll(user.collection("ratings")) { d ->
            f.collection("videos").document(d.id).collection("reviews").document(p.uid).delete()
        }
        deleteAll(user.collection("private"))
        deleteAll(user.collection("following")) { d ->
            f.collection("users").document(d.id).collection("followers").document(p.uid).delete()
        }
        // Other people's "following" lists can only be changed by them; a leftover entry just shows nothing.
        deleteAll(user.collection("followers"))
        f.collection("usernames").document(p.username).delete().await()
        user.delete().await()
        wrap { authOrThrow().currentUser?.delete()?.await() }
    }

    private fun DocumentSnapshot.toProfile(): Profile? {
        val username = getString("username") ?: return null
        @Suppress("UNCHECKED_CAST")
        val favs = (get("favorites") as? List<Map<*, *>>).orEmpty().mapNotNull { it.toRef() }
        return Profile(id, username, getString("displayName") ?: username, getString("bio").orEmpty(), getString("photoUrl").orEmpty(), favs)
    }

    private fun DocumentSnapshot.toEntry(): Entry? {
        val ref = (get("video") as? Map<*, *>)?.toRef() ?: return null
        return Entry(
            uid = getString("uid") ?: return null,
            username = getString("username").orEmpty(),
            rating = (getLong("rating") ?: 0).toInt(),
            review = getString("review").orEmpty(),
            video = ref,
            updatedAt = getLong("updatedAt") ?: 0,
        )
    }

    private fun Map<*, *>.toRef(): VideoRef? {
        val id = this["videoId"] as? String ?: return null
        return VideoRef(
            videoId = id,
            title = this["title"] as? String ?: "",
            channelId = this["channelId"] as? String ?: "",
            channelTitle = this["channelTitle"] as? String ?: "",
            thumbnailUrl = this["thumbnailUrl"] as? String ?: "",
            durationSec = (this["durationSec"] as? Number)?.toLong() ?: 0,
        )
    }

    private fun VideoRef.toMap() = mapOf(
        "videoId" to videoId, "title" to title, "channelId" to channelId,
        "channelTitle" to channelTitle, "thumbnailUrl" to thumbnailUrl, "durationSec" to durationSec,
    )

    companion object {
        fun VideoEntity.toRef() = VideoRef(id, title, channelId, channelTitle, thumbnailUrl, durationSec)

        fun VideoRef.toEntity() = VideoEntity(
            id = videoId, title = title, channelId = channelId, channelTitle = channelTitle, publishedAt = 0,
            durationSec = durationSec, views = 0, likes = 0, thumbnailUrl = thumbnailUrl, description = "",
            tags = "", categoryId = "", fromSource = false, fetchedAt = 0,
        )
    }
}

/** The one account allowed to add shared synopses (@brantonling); everyone else only reads them. */
const val SYNOPSIS_WRITER_UID = "P2mzQNCtGRYQVUX4Z5DFPg9lb322"
