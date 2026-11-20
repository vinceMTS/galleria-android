package app.galleria.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import app.galleria.R
import app.galleria.data.MediaEntity
import app.galleria.data.MonthSummary
import app.galleria.media.MediaTile
import app.galleria.media.Thumbnail
import app.galleria.media.thumbnailAspect
import coil3.compose.AsyncImage

/** The photo grid of a folder and of "All" → Tutte: paged, bottom-up, pinch for columns. */
@Composable
fun PagedMediaGrid(
    items: LazyPagingItems<MediaEntity>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (MediaEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomUpGrid(
        columns = columns,
        state = state,
        contentPadding = contentPadding,
        modifier = modifier.pinchColumns(columns, onColumnsChange),
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.id },
            contentType = items.itemContentType { "media" },
        ) { index ->
            val media = items[index]
            Ltr { MediaTile(media, onClick = { media?.let(onOpen) }, corner = 0.dp) }
        }
    }
}

/** A month or a year in "All" (D-022): cover, name of the period, number of items. */
data class PeriodCell(val key: String, val label: String, val itemCount: Int, val cover: MonthSummary)

@Composable
fun PeriodGrid(
    cells: List<PeriodCell>,
    columns: Int,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onOpen: (PeriodCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomUpGrid(
        columns = columns,
        state = state,
        contentPadding = contentPadding,
        modifier = modifier,
        spacing = 8.dp,
    ) {
        items(cells, key = { it.key }) { cell ->
            Ltr { PeriodTile(cell, onClick = { onOpen(cell) }) }
        }
    }
}

@Composable
private fun PeriodTile(cell: PeriodCell, onClick: () -> Unit) {
    val cover = cell.cover
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        AsyncImage(
            model = Thumbnail(cover.coverId, cover.coverVolume, cover.coverIsVideo, thumbnailAspect(cover.coverWidth, cover.coverHeight)),
            contentDescription = cell.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            cell.label.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
        Text(
            pluralStringResource(R.plurals.items_count, cell.itemCount, cell.itemCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
