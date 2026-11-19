package com.longtake.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.longtake.data.Repository
import com.longtake.social.AccountState
import com.longtake.social.Entry
import com.longtake.social.Profile
import com.longtake.social.VideoRef

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(repo: Repository, onOpenVideo: (VideoRef) -> Unit, onOpenProfile: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val account by repo.social.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var feed by remember { mutableStateOf<List<Entry>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val ready = account is AccountState.Ready

    LaunchedEffect(ready) {
        if (ready) try { feed = repo.social.feed() } catch (e: Exception) { err = e.message }
    }
    LaunchedEffect(query) {
        people = if (query.length >= 2 && account !is AccountState.Disabled) runCatching { repo.social.searchUsers(query) }.getOrDefault(emptyList()) else emptyList()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Friends") }) }) { pad ->
        if (account is AccountState.Disabled) {
            Box(Modifier.padding(pad)) { EmptyMessage("Profiles aren't switched on yet", "See the Firebase step in SETUP.md.") }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Find people by username") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(people, key = { "p" + it.uid }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenProfile(p.uid) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p, 40); Spacer(Modifier.width(12.dp))
                    Column { Text(p.displayName); Text("@${p.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item {
                SectionTitle("From people you follow")
                if (!ready) Text("Sign in on the You tab to follow people and see what they rate.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            val f = feed
            if (ready) {
                if (f == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (f.isEmpty()) item { EmptyMessage("Nothing yet", "Find people above and follow them. Their ratings and reviews show up here.") }
                else items(f, key = { it.uid + it.video.videoId }) { e ->
                    EntryRow(e, showUser = true, onOpenVideo = onOpenVideo, onOpenProfile = onOpenProfile)
                }
            }
        }
    }
}
