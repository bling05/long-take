package com.longtake.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.longtake.Features
import com.longtake.data.Repository
import com.longtake.data.Verdict
import com.longtake.social.AccountState
import com.longtake.social.SocialRepo
import com.longtake.social.SocialRepo.Companion.toRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(repo: Repository, videoId: String, onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val item by remember(videoId) { repo.db.videos().videoWithState(videoId) }.collectAsStateWithLifecycle(initialValue = null)
    val settings by repo.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val checking by repo.checking.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) } // "ai" or "hidden"
    val account by repo.social.state.collectAsStateWithLifecycle()
    val me = (account as? AccountState.Ready)?.profile
    var community by remember(videoId) { mutableStateOf<SocialRepo.Community?>(null) }
    var communityErr by remember(videoId) { mutableStateOf<String?>(null) }
    var socialMsg by remember { mutableStateOf<String?>(null) }
    // Reload community reviews when this page opens and shortly after you change your own rating/review.
    LaunchedEffect(videoId, account is AccountState.Ready, item?.user?.rating, item?.user?.reviewPublic) {
        if (account is AccountState.Disabled) return@LaunchedEffect
        delay(800)
        try { community = repo.social.community(videoId); communityErr = null } catch (e: Exception) { communityErr = e.message }
    }

    // Note editing with a short autosave delay.
    var note by remember(videoId) { mutableStateOf<String?>(null) }
    LaunchedEffect(item?.user?.note) { if (note == null && item != null) note = item?.user?.note.orEmpty() }
    LaunchedEffect(note) {
        val n = note ?: return@LaunchedEffect
        if (n == item?.user?.note.orEmpty()) return@LaunchedEffect
        delay(600); repo.setNote(videoId, n)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val it = item
        if (it == null) {
            Box(Modifier.padding(pad).fillMaxSize()) { EmptyMessage("Loading…", "") }
            return@Scaffold
        }
        val v = it.video
        val u = it.user
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            AsyncImage(
                model = v.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(12.dp))
            Text(v.title, style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(
                    v.channelTitle,
                    v.views.takeIf { n -> n > 0 }?.let { n -> "${formatCount(n)} views" },
                    v.durationSec.takeIf { d -> d > 0 }?.let { d -> formatDuration(d) },
                    formatAge(v.publishedAt).ifBlank { null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            it.channel?.takeIf { c -> c.subscribers > 0 }?.let { c ->
                Text("${formatCount(c.subscribers)} subscribers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    openOnYouTube(ctx, v.id)
                    if (settings?.markWatchedOnOpen == true) scope.launch { repo.setWatched(v.id, true) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Watch on YouTube")
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { repo.setSaved(v.id, u?.saved != true) } }, modifier = Modifier.weight(1f)) {
                    Icon(if (u?.saved == true) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (u?.saved == true) "Saved" else "Save")
                }
                OutlinedButton(onClick = { scope.launch { repo.setWatched(v.id, u?.watched != true) } }, modifier = Modifier.weight(1f)) {
                    Icon(if (u?.watched == true) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (u?.watched == true) "Watched" else "Mark watched")
                }
            }

            // Rating
            Spacer(Modifier.height(16.dp))
            Text("Your rating", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rating = u?.rating ?: 0
                (1..5).forEach { n ->
                    IconButton(onClick = { scope.launch { repo.setRating(v.id, if (rating == n) 0 else n) } }) {
                        Icon(
                            if (n <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "$n stars",
                            tint = if (n <= rating) Color(0xFFF2A900) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = note.orEmpty(),
                onValueChange = { t -> note = t },
                label = { Text("Your notes") },
                placeholder = { Text("Thoughts, quotes, things to look up…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (me != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Share my note as a public review")
                        Text("Your rating is always shown on your profile.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = u?.reviewPublic == true, onCheckedChange = { on -> scope.launch { repo.setReviewPublic(v.id, on) } })
                }
                val isFav = me.favorites.any { f -> f.videoId == v.id }
                TextButton(onClick = {
                    scope.launch {
                        try { repo.social.setFavorite(v.toRef(), !isFav); socialMsg = null } catch (e: Exception) { socialMsg = e.message }
                    }
                }) {
                    Icon(if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFav) "One of my 4 favourites" else "Add to my 4 favourites")
                }
                socialMsg?.let { m -> Text(m, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            // Essay score
            Spacer(Modifier.height(16.dp))
            val essay = it.essay()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Essay score", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        EssayChip(essay)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (essay.reasons.isEmpty()) "No strong signals either way." else essay.reasons.joinToString(". "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val sp = if (Features.AI_CHECK) it.ai?.speech() else null
                    if (sp != null) {
                        Text(
                            "${"%.1f".format(sp.fillerPer1k)} filler words per 1,000 · ${sp.wordsPerMinute} words per minute",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else if (Features.AI_CHECK && it.ai == null) {
                        Text(
                            "Gets more accurate once the AI check has read the transcript.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (Features.AI_CHECK) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AI-script check", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            AiChip(it.ai, v.id in checking)
                        }
                        val ai = it.ai
                        if (ai != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(ai.reason, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            val method = buildString {
                                append("Quick check score: ${ai.freeScore}/100")
                                if (ai.claudeVerdict.isNotBlank()) append(" · Claude: ${ai.claudeVerdict.replace('_', ' ')} (${ai.claudeConfidence}% sure)")
                                if (ai.transcriptWords > 0) append(" · ${ai.transcriptWords} words of transcript")
                            }
                            Text(method, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ai.verdict == Verdict.AGE_RESTRICTED) {
                                Text("YouTube only shows this video's captions to signed-in adults, so it couldn't be checked.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (ai.verdict == Verdict.NO_TRANSCRIPT) {
                                Text("This video has no captions, so only channel signals were used.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (v.id !in checking) {
                            Text("Not checked yet.", style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { repo.recheck(v.id) }, enabled = v.id !in checking) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(if (ai == null) "Check now" else "Check again")
                        }
                    }
                }
            }

            if (account !is AccountState.Disabled) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        val c = community
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Community", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (c != null && c.count > 0) {
                                Icon(Icons.Filled.Star, null, tint = Color(0xFFF2A900), modifier = Modifier.size(16.dp))
                                Text(" ${"%.1f".format(c.average)} · ${c.count} rating${if (c.count == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        when {
                            communityErr != null -> Text("Couldn't load reviews.", style = MaterialTheme.typography.bodySmall)
                            c == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                            c.entries.isEmpty() -> Text("No one has rated this yet.", style = MaterialTheme.typography.bodySmall)
                            else -> c.entries.filter { e -> e.review.isNotBlank() }.take(20).forEach { e ->
                                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "@${e.username}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { onOpenProfile(e.uid) },
                                        )
                                        Spacer(Modifier.width(6.dp)); Stars(e.rating, 12)
                                        Spacer(Modifier.weight(1f))
                                        if (me != null && e.uid != me.uid) {
                                            TextButton(onClick = {
                                                scope.launch { runCatching { repo.social.report(v.id, e.uid) }; socialMsg = "Thanks, that review was reported." }
                                            }) { Text("Report", style = MaterialTheme.typography.labelSmall) }
                                        }
                                    }
                                    Text(e.review, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirm = "ai" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.SmartToy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("This is AI")
                }
                OutlinedButton(onClick = { confirm = "hidden" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.VisibilityOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Hide channel")
                }
            }

            if (v.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Description", style = MaterialTheme.typography.titleSmall)
                Text(v.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
        }

        confirm?.let { kind ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                icon = { Icon(Icons.Filled.Block, null) },
                title = { Text(if (kind == "ai") "Block ${v.channelTitle} as AI?" else "Hide ${v.channelTitle}?") },
                text = {
                    Text(
                        if (kind == "ai") "All videos from this channel will be hidden and it will be removed from your sources. You can undo this in Settings → Blocked channels."
                        else "Videos from this channel won't appear in Discover or Search. You can undo this in Settings → Blocked channels."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { repo.blockChannel(v.channelId, v.channelTitle, kind); onBack() }
                        confirm = null
                    }) { Text(if (kind == "ai") "Block" else "Hide") }
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
            )
        }
    }
}
