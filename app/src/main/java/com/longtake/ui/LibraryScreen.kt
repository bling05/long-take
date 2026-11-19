package com.longtake.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.longtake.data.Repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(repo: Repository, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val saved by repo.db.videos().saved().collectAsStateWithLifecycle(initialValue = emptyList())
    val watched by repo.db.videos().watched().collectAsStateWithLifecycle(initialValue = emptyList())
    val rated by repo.db.videos().ratedOrNoted().collectAsStateWithLifecycle(initialValue = emptyList())
    val checking by repo.checking.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Saved (${saved.size})") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Watched (${watched.size})") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Rated & notes", maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
            val list = when (tab) { 0 -> saved; 1 -> watched; else -> rated }
            VideoList(
                items = list,
                checking = checking,
                onOpen = onOpen,
                onToggleSave = { item -> scope.launch { repo.setSaved(item.video.id, item.user?.saved != true) } },
                onToggleWatched = { item -> scope.launch { repo.setWatched(item.video.id, item.user?.watched != true) } },
                header = if (tab == 2) {
                    {
                        Text(
                            "Videos you've rated or written notes on, most recent first.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else null,
                empty = {
                    when (tab) {
                        0 -> EmptyMessage("Nothing saved yet", "Tap the bookmark on any video to save it for later.")
                        1 -> EmptyMessage("No watched videos yet", "Videos you open from this app are logged here. You can also import your YouTube history in Settings.")
                        else -> EmptyMessage("No ratings or notes yet", "Open a video to rate it or write down your thoughts.")
                    }
                },
            )
        }
    }
}
