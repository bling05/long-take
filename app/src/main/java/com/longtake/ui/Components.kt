package com.longtake.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.longtake.Features
import com.longtake.ai.EssayScore
import com.longtake.data.AiCheckEntity
import com.longtake.data.Verdict
import com.longtake.data.VideoWithState
import kotlin.math.abs

fun formatCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 1_000 -> "%.0fK".format(n / 1e3)
    else -> n.toString()
}.replace(".0", "")

fun formatDuration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatAge(ms: Long): String {
    if (ms <= 0) return ""
    val days = abs(System.currentTimeMillis() - ms) / 86_400_000
    return when {
        days < 1 -> "today"
        days < 7 -> "$days day${if (days == 1L) "" else "s"} ago"
        days < 30 -> "${days / 7} week${if (days / 7 == 1L) "" else "s"} ago"
        days < 365 -> "${days / 30} month${if (days / 30 == 1L) "" else "s"} ago"
        else -> "${days / 365} year${if (days / 365 == 1L) "" else "s"} ago"
    }
}

fun openOnYouTube(context: Context, videoId: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

data class VerdictLook(val label: String, val color: Color)

fun verdictLook(ai: AiCheckEntity?, checking: Boolean): VerdictLook = when {
    checking -> VerdictLook("Checking…", VerdictColors.none)
    ai == null -> VerdictLook("Not checked", VerdictColors.none)
    ai.verdict == Verdict.HUMAN -> VerdictLook("Human", VerdictColors.human)
    ai.verdict == Verdict.AI -> VerdictLook("Likely AI", VerdictColors.ai)
    ai.verdict == Verdict.UNSURE -> VerdictLook("Unsure", VerdictColors.unsure)
    ai.verdict == Verdict.NO_TRANSCRIPT -> VerdictLook("No transcript", VerdictColors.none)
    ai.verdict == Verdict.AGE_RESTRICTED -> VerdictLook("Age-restricted", VerdictColors.none)
    else -> VerdictLook("Check failed", VerdictColors.none)
}

@Composable
fun AiChip(ai: AiCheckEntity?, checking: Boolean, modifier: Modifier = Modifier) {
    val look = verdictLook(ai, checking)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(look.color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = look.color)
            Spacer(Modifier.width(4.dp))
        } else {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(look.color))
            Spacer(Modifier.width(5.dp))
        }
        Text(look.label, color = look.color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VideoCard(
    item: VideoWithState,
    checking: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    val v = item.video
    val essay = item.essay()
    val watched = item.user?.watched == true
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = v.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (watched) 0.45f else 1f),
            )
            Text(
                formatDuration(v.durationSec),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (watched) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watched", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(v.channelTitle, v.views.takeIf { it > 0 }?.let { "${formatCount(it)} views" }, formatAge(v.publishedAt).ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Features.AI_CHECK) AiChip(item.ai, checking)
                    EssayChip(essay)
                    val rating = item.user?.rating ?: 0
                    if (rating > 0) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFF2A900), modifier = Modifier.size(14.dp))
                        Text("$rating", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = onToggleWatched) {
                Icon(
                    if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (watched) "Mark as not watched" else "I've watched this",
                    tint = if (watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleSave) {
                val saved = item.user?.saved == true
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "Unsave" else "Save for later",
                    tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun VideoList(
    items: List<VideoWithState>,
    checking: Set<String>,
    onOpen: (String) -> Unit,
    onToggleSave: (VideoWithState) -> Unit,
    onToggleWatched: (VideoWithState) -> Unit,
    header: (@Composable () -> Unit)? = null,
    empty: @Composable () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (header != null) item { header() }
        if (items.isEmpty()) item { empty() }
        items(items, key = { it.video.id }) { item ->
            VideoCard(
                item, item.video.id in checking,
                onClick = { onOpen(item.video.id) },
                onToggleSave = { onToggleSave(item) },
                onToggleWatched = { onToggleWatched(item) },
            )
        }
    }
}

@Composable
fun EmptyMessage(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatusBanner(text: String, isError: Boolean, onDismiss: (() -> Unit)? = null) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().let { m -> if (onDismiss != null) m.clickable(onClick = onDismiss) else m },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun EssayChip(e: EssayScore.Result, modifier: Modifier = Modifier) {
    val label = if (e.format == EssayScore.Format.ESSAY) "Essay ${e.score}" else "${e.format.label} · ${e.score}"
    Text(
        label,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
