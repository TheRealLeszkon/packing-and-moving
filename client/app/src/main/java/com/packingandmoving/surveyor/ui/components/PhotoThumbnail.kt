package com.packingandmoving.surveyor.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** A captured or picked photo with a delete button, used on the Review screen. */
@Composable
fun PhotoThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.aspectRatio(1f)) {
        AsyncImage(
            model = uri,
            contentDescription = "Captured photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .padding(4.dp)
                .align(Alignment.TopEnd)
                .size(28.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove photo",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
