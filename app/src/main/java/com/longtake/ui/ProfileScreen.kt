package com.longtake.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.longtake.data.Repository
import com.longtake.social.AccountState
import com.longtake.social.Counts
import com.longtake.social.Entry
import com.longtake.social.Profile
import com.longtake.social.VideoRef
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(repo: Repository, uid: String, onBack: () -> Unit, onOpenVideo: (VideoRef) -> Unit, onOpenProfile: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            ProfileContent(repo, uid, isMe = uid == repo.social.uid, onOpenVideo = onOpenVideo, onOpenProfile = onOpenProfile)
        }
    }
}

/** Shared by your own profile and other people's. */
@Composable
fun ProfileContent(repo: Repository, uid: String, isMe: Boolean, onOpenVideo: (VideoRef) -> Unit, onOpenProfile: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val account by repo.social.state.collectAsStateWithLifecycle()
    val myProfile = (account as? AccountState.Ready)?.profile
    var profile by remember(uid) { mutableStateOf<Profile?>(null) }
    var counts by remember(uid) { mutableStateOf<Counts?>(null) }
    var entries by remember(uid) { mutableStateOf<List<Entry>?>(null) }
    var following by remember(uid) { mutableStateOf<Boolean?>(null) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val synced by repo.social.syncCount.collectAsStateWithLifecycle()

    LaunchedEffect(uid, synced, myProfile) {
        try {
            profile = if (isMe) myProfile else repo.social.profile(uid)
            entries = repo.social.entries(uid)
            counts = repo.social.counts(uid)
            if (!isMe && myProfile != null) following = repo.social.isFollowing(uid)
        } catch (e: Exception) { error = e.message }
    }

    val p = profile
    if (p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) EmptyMessage("Couldn't load this profile", error!!) else CircularProgressIndicator()
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 64)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${p.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (p.bio.isNotBlank()) Text(p.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(counts?.ratings, "Essays rated")
                    Stat(counts?.followers, "Followers")
                    Stat(counts?.following, "Following")
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isMe) {
                        OutlinedButton(onClick = { editing = true }) { Text("Edit profile") }
                        OutlinedButton(onClick = { repo.social.signOut() }) { Text("Sign out") }
                    } else if (myProfile != null && following != null) {
                        val f = following == true
                        val action: suspend () -> Unit = {
                            repo.social.setFollowing(uid, !f); following = !f; counts = repo.social.counts(uid)
                        }
                        if (f) OutlinedButton(onClick = { scope.launch { runCatching { action() } } }) { Text("Following") }
                        else Button(onClick = { scope.launch { runCatching { action() } } }) { Text("Follow") }
                    } else if (myProfile == null) {
                        Text("Sign in on the You tab to follow people.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (p.favorites.isNotEmpty()) {
            item {
                SectionTitle("Favourite essays")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.favorites.take(4).forEach { v ->
                        Column(Modifier.weight(1f).clickable { onOpenVideo(v) }) {
                            AsyncImage(
                                v.thumbnailUrl, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Text(v.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - p.favorites.size.coerceAtMost(4)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item { SectionTitle(if (isMe) "Your ratings" else "Recent ratings") }
        val list = entries
        when {
            list == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            list.isEmpty() -> item {
                EmptyMessage(
                    "No ratings yet",
                    if (isMe) "Rate a video from its page and it shows up here." else "They haven't rated anything yet.",
                )
            }
            else -> items(list, key = { it.video.videoId }) { e ->
                EntryRow(e, showUser = false, onOpenVideo = onOpenVideo, onOpenProfile = onOpenProfile)
            }
        }
        if (isMe) item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(16.dp)) {
                Text("Delete my account", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (editing) {
        var name by remember { mutableStateOf(p.displayName) }
        var bio by remember { mutableStateOf(p.bio) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit profile") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Display name") }, singleLine = true)
                    OutlinedTextField(bio, { bio = it.take(300) }, label = { Text("Bio") }, minLines = 3, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { runCatching { repo.social.updateProfile(name, bio) }; editing = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        var deleting by remember { mutableStateOf(false) }
        var delErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Column {
                    Text("This permanently removes your profile, public ratings and reviews, follows and online backup. Ratings and notes on this phone are kept.")
                    delErr?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    scope.launch {
                        try { repo.social.deleteAccount(); confirmDelete = false } catch (e: Exception) { delErr = e.message }
                        deleting = false
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(n: Long?, label: String) {
    Column {
        Text(n?.toString() ?: "–", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
