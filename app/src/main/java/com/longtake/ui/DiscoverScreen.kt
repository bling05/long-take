package com.longtake.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.longtake.Features
import com.longtake.ai.EssayScore
import com.longtake.data.Repository
import com.longtake.data.Sections
import com.longtake.data.Topics
import com.longtake.data.Verdict
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(repo: Repository, onOpen: (String) -> Unit) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val feed by repo.db.videos().feed().collectAsStateWithLifecycle(initialValue = emptyList())
    val sectionRows by repo.db.sections().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val sectionIds = remember(sectionRows) { sectionRows.groupBy({ it.section }, { it.videoId }).mapValues { it.value.toSet() } }
    val status by repo.status.collectAsStateWithLifecycle()
    val checking by repo.checking.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sort by rememberSaveable { mutableStateOf(SortBy.MOST_VIEWED) }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { repo.refreshIfStale() }

    val s = settings
    val shown = remember(feed, s, sort, topic, sectionIds) {
        if (s == null) emptyList() else {
            val section = Sections.all.firstOrNull { it.label == topic }
            if (section != null) {
                val ids = sectionIds[section.key].orEmpty()
                sortVideos(applySectionFilters(feed.filter { it.video.id in ids }, s), sort)
            } else {
                val t = Topics.all.firstOrNull { it.label == topic }
                sortVideos(applyFilters(feed, s, s.hideWatched).filter { t == null || Topics.matches(t, it.video) }, sort)
            }
        }
    }
    val hiddenAi = feed.count { it.ai?.verdict == Verdict.AI }

    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Long Take") },
                actions = {
                    IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.Tune, "Filters and sorting") }
                    IconButton(onClick = { repo.refreshInBackground() }, enabled = !status.busy) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            status.error?.let { StatusBanner(it, isError = true) { repo.clearError() } }
            if (status.busy && status.message != null) StatusBanner(status.message!!, isError = false)

            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TopicPill("All", topic == null) { topic = null }
                Sections.all.forEach { sec -> TopicPill(sec.label, topic == sec.label) { topic = if (topic == sec.label) null else sec.label } }
                Topics.all.forEach { t -> TopicPill(t.label, topic == t.label) { topic = if (topic == t.label) null else t.label } }
            }
            if (s != null) {
                Text(
                    listOfNotNull(
                        "${shown.size} essays",
                        sort.label.lowercase(),
                        s.strictness.label.lowercase(),
                        s.lastRefreshAt.takeIf { it > 0 }?.let { "updated ${formatAge(it)}" },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showFilters = true }.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            VideoList(
                items = shown,
                checking = checking,
                onOpen = onOpen,
                onToggleSave = { item -> scope.launch { repo.setSaved(item.video.id, item.user?.saved != true) } },
                onToggleWatched = { item -> scope.launch { repo.setWatched(item.video.id, item.user?.watched != true) } },
                empty = {
                    when {
                        s != null && !s.hasYoutubeKey ->
                            EmptyMessage("Set up YouTube access", "Go to Settings and paste your YouTube API key, then come back and tap refresh.")
                        status.busy -> EmptyMessage("Finding video essays…", "The first load pulls videos from about 20 channels and a few curated playlists. It can take a minute.")
                        else -> EmptyMessage("Nothing here yet", "Tap refresh, loosen the filters, or add channels in Sources.")
                    }
                },
            )
        }
    }

    if (showFilters && s != null) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("Sort", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortBy.entries.forEach { o -> TopicPill(o.label, sort == o) { sort = o } }
                }
                Spacer(Modifier.size(12.dp))
                Text("How strict", style = MaterialTheme.typography.titleMedium)
                StrictnessSlider(s.strictness) { v -> scope.launch { repo.settings.setStrictness(v) } }
                Spacer(Modifier.size(8.dp))
                if (Features.AI_CHECK) {
                    SheetToggle("Hide likely AI" + if (hiddenAi > 0) "  ($hiddenAi)" else "", s.hideAi) { v -> scope.launch { repo.settings.setHideAi(v) } }
                    SheetToggle("Hide \"unsure\" videos", s.hideUnsure) { v -> scope.launch { repo.settings.setHideUnsure(v) } }
                }
                SheetToggle("Hide watched", s.hideWatched) { v -> scope.launch { repo.settings.setHideWatched(v) } }
            }
        }
    }
}

/** Soft, borderless pill used for topics and sort options. */
@Composable
fun TopicPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun SheetToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
fun StrictnessSlider(current: EssayScore.Strictness, onChange: (EssayScore.Strictness) -> Unit) {
    val levels = EssayScore.Strictness.entries
    var pos by remember(current) { mutableFloatStateOf(current.ordinal.toFloat()) }
    val shownLevel = levels[pos.toInt().coerceIn(0, levels.lastIndex)]
    Column(Modifier.padding(top = 4.dp)) {
        Text(shownLevel.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(shownLevel.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { onChange(levels[pos.toInt().coerceIn(0, levels.lastIndex)]) },
            valueRange = 0f..levels.lastIndex.toFloat(),
            steps = levels.size - 2,
        )
    }
}
