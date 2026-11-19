package com.longtake.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.longtake.data.Repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(repo: Repository) {
    val scope = rememberCoroutineScope()
    val channels by repo.db.channels().sources().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by repo.db.playlists().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by repo.settings.flow.collectAsStateWithLifecycle(initialValue = null)

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun add() {
        if (input.isBlank()) return
        busy = true
        scope.launch {
            message = try { repo.addSource(input) to false } catch (e: Exception) { (e.message ?: "Failed") to true }
            if (message?.second == false) input = ""
            busy = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Sources") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Discover shows long videos from these channels and playlists. Paste a channel link, @handle, " +
                            "channel name, or a playlist link (great for curated \"best video essays\" playlists).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        label = { Text("Channel or playlist") },
                        placeholder = { Text("@JacobGeller or a youtube.com link") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Button(onClick = { add() }, enabled = !busy && input.isNotBlank()) { Text("Add") }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    message?.let { (m, err) ->
                        Text(m, color = if (err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (playlists.isNotEmpty()) {
                item { SectionHeader("Playlists (${playlists.size})") }
                items(playlists, key = { "p" + it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.channelTitle.isNotBlank()) Text("by ${p.channelTitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { scope.launch { repo.removePlaylist(p.id) } }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                }
            }

            item { SectionHeader("Channels (${channels.size})") }
            if (channels.isEmpty()) item {
                Text(
                    if (settings?.seeded == true) "No channels yet." else "About 20 well-known video essay channels are added the first time Discover loads.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(channels, key = { "c" + it.id }) { c ->
                ChannelRow(c.title, c.thumbnailUrl, if (c.subscribers > 0) "${formatCount(c.subscribers)} subscribers" else "") {
                    scope.launch { repo.removeSource(c) }
                }
            }

            item { Spacer(Modifier.size(32.dp)) }
        }
    }

}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
    }
}

@Composable
private fun ChannelRow(title: String, thumb: String, sub: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") }
    }
}
