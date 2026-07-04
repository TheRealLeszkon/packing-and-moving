package com.packingandmoving.surveyagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.packingandmoving.surveyagent.ui.theme.Dimens
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.StagedMedia

/** Horizontal strip of staged (not-yet-uploaded) media, each with a remove badge. */
@Composable
fun StagedMediaRow(media: List<StagedMedia>, onRemove: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        items(media, key = { it.id }) { item ->
            Box {
                AsyncImage(
                    model = item.uri,
                    contentDescription = if (item.isVideo) "Staged video" else "Staged photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Dimens.GalleryThumbnail).clip(MaterialTheme.shapes.medium),
                )
                if (item.isVideo) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = null, tint = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                IconButton(
                    onClick = { onRemove(item.id) },
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
