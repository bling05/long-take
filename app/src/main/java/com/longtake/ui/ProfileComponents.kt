package com.longtake.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.longtake.social.Entry
import com.longtake.social.Profile
import com.longtake.social.VideoRef

@Composable
fun EntryRow(e: Entry, showUser: Boolean, onOpenVideo: (VideoRef) -> Unit, onOpenProfile: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpenVideo(e.video) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AsyncImage(
            e.video.thumbnailUrl, null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showUser) {
                Text(
                    "@${e.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenProfile(e.uid) },
                )
            }
            Text(e.video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(e.video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (e.rating > 0) Stars(e.rating)
            if (e.review.isNotBlank()) Text("“${e.review}”", style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(formatAge(e.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Stars(rating: Int, size: Int = 14) {
    Row {
        repeat(rating) { Icon(Icons.Filled.Star, null, tint = Color(0xFFF2A900), modifier = Modifier.size(size.dp)) }
    }
}

@Composable
fun Avatar(p: Profile, sizeDp: Int) {
    val m = Modifier.size(sizeDp.dp).clip(CircleShape)
    if (p.photoUrl.isNotBlank()) {
        AsyncImage(p.photoUrl, null, contentScale = ContentScale.Crop, modifier = m)
    } else {
        Box(m.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                p.displayName.take(1).uppercase(), fontSize = (sizeDp / 2.4).sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
}
