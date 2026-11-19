package com.longtake.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.longtake.data.Repository
import com.longtake.youtube.YouTubeApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(repo: Repository, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(YouTubeApi.Order.RELEVANCE) }
    var ids by rememberSaveable { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val settings by repo.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val checking by repo.checking.collectAsStateWithLifecycle()
    val resultsFlow = remember(ids) { if (ids.isEmpty()) flowOf(emptyList()) else repo.db.videos().videosWithState(ids) }
    val results by resultsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val s = settings
    val ordered = remember(results, ids, s) {
        val byId = results.associateBy { it.video.id }
        val list = ids.mapNotNull { byId[it] }
        if (s == null) list else applyFilters(list, s, hideWatched = false)
    }

    fun run() {
        if (query.isBlank()) return
        loading = true; error = null
        scope.launch {
            try { ids = repo.search(query, order) } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("e.g. horror games, brutalism, Twin Peaks") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(YouTubeApi.Order.RELEVANCE to "Best match", YouTubeApi.Order.VIEWS to "Most viewed", YouTubeApi.Order.DATE to "Newest").forEach { (o, label) ->
                    FilterChip(selected = order == o, onClick = { order = o; if (ids.isNotEmpty()) run() }, label = { Text(label) })
                }
            }
            Text(
                "Searches long (20+ min) videos only. Each search uses about 1% of YouTube's free daily limit.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { StatusBanner(it, isError = true) { error = null } }
            VideoList(
                items = ordered,
                checking = checking,
                onOpen = onOpen,
                onToggleSave = { item -> scope.launch { repo.setSaved(item.video.id, item.user?.saved != true) } },
                onToggleWatched = { item -> scope.launch { repo.setWatched(item.video.id, item.user?.watched != true) } },
                empty = {
                    if (!loading) EmptyMessage(
                        if (ids.isEmpty()) "Find video essays on any topic" else "No results after filters",
                        if (ids.isEmpty()) "\"video essay\" is added to your search automatically." else "Try another search, or show AI videos on the Discover tab.",
                    )
                },
            )
        }
    }
}
