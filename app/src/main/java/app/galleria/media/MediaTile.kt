package app.galleria.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.galleria.data.MediaEntity
import coil3.compose.AsyncImage

/**
 * A square thumbnail of a photo or video, used by Recents and by every grid.
 * [media] is null while its page is not loaded yet (a grey cell keeps the grid's shape).
 */
@Composable
fun MediaTile(media: MediaEntity?, onClick: () -> Unit, corner: Dp = 4.dp) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (media != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (media == null) return@Box
        AsyncImage(
            model = Thumbnail(media.id, media.volume, media.isVideo, thumbnailAspect(media.width, media.height)),
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // D-027: only videos get an indicator, their duration bottom right.
        if (media.isVideo && media.durationMillis != null) {
            Text(
                formatDuration(media.durationMillis),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
