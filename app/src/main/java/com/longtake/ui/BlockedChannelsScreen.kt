package com.longtake.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.longtake.data.ChannelEntity
import com.longtake.data.Repository
import kotlinx.coroutines.launch

/** Every channel you've blocked, with a way to bring each one back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedChannelsScreen(repo: Repository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val blocked by repo.db.channels().blocked().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked channels") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        if (blocked.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                EmptyMessage(
                    "No blocked channels",
                    "On any video, tap \"This is AI\" or \"Hide channel\" to stop seeing that channel.",
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Text(
                    "Videos from these channels don't show up in Discover or Search. Unblock one to see it again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(blocked, key = { it.id }) { c ->
                BlockedRow(c) { scope.launch { repo.unblockChannel(c) } }
                HorizontalDivider(Modifier.padding(start = 64.dp))
            }
        }
    }
}

@Composable
private fun BlockedRow(c: ChannelEntity, onUnblock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (c.thumbnailUrl.isNotBlank()) {
            AsyncImage(model = c.thumbnailUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
        } else {
            // Channels blocked straight from a video may not have a picture saved; show their initial.
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text(c.title.take(1).uppercase(), style = MaterialTheme.typography.titleSmall) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (c.blockReason == "ai") "Marked as AI" else "Hidden",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onUnblock) { Text("Unblock") }
    }
}
