package app.galleria.home

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.galleria.R
import app.galleria.data.FolderVisibility
import app.galleria.data.MediaEntity
import app.galleria.media.Thumbnail
import app.galleria.media.thumbnailAspect
import coil3.compose.AsyncImage

private const val SPAN = 12 // Recents 4 columns (span 3), Pinned 2 (span 6), Folders 3 (span 4)

/** One visual row of home, top to bottom. */
private sealed interface Row {
    val key: String

    data class Full(override val key: String, val content: @Composable () -> Unit) : Row

    data class Cells(override val key: String, val span: Int, val cells: List<Cell>) : Row
}

private data class Cell(val key: String, val content: @Composable () -> Unit)

/**
 * Home upside down (decided 2026-11-20): Recents and "All" at the bottom, near the thumb, and the
 * page opens already at the bottom, like the grids (D-024). Scrolling up: Pinned, Folders, Hidden.
 *
 * The grid uses `reverseLayout`, so it starts at the bottom and stays anchored there when a new
 * photo arrives; rows are therefore emitted bottom to top.
 */
@Composable
fun HomeScreen(state: HomeState, viewModel: HomeViewModel) {
    val context = LocalContext.current
    val notYet = { Toast.makeText(context, R.string.not_yet, Toast.LENGTH_SHORT).show() }
    var hiddenOpen by rememberSaveable { mutableStateOf(false) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val rows = homeRows(state, viewModel, hiddenOpen, onToggleHidden = { hiddenOpen = !hiddenOpen }, notYet)

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(SPAN),
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.asReversed().forEach { emit(it) }
        }
        // Keeps the clock and icons readable when content scrolls under the status bar.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        )
    }
}

private fun LazyGridScope.emit(row: Row) {
    when (row) {
        is Row.Full -> item(key = row.key, span = { GridItemSpan(maxLineSpan) }) { row.content() }
        is Row.Cells -> {
            row.cells.forEach { cell -> item(key = cell.key, span = { GridItemSpan(row.span) }) { cell.content() } }
            // A short row is padded, or the row above would fill its gap.
            val used = row.cells.size * row.span
            if (used < SPAN) {
                item(key = "${row.key}-pad", span = { GridItemSpan(SPAN - used) }) { Spacer(Modifier) }
            }
        }
    }
}

@Composable
private fun homeRows(
    state: HomeState,
    viewModel: HomeViewModel,
    hiddenOpen: Boolean,
    onToggleHidden: () -> Unit,
    notYet: () -> Unit,
): List<Row> {
    val rows = mutableListOf<Row>()
    val sections = state.sections
    val emptyPhone = state.loaded && sections.pinned.isEmpty() && sections.folders.isEmpty() && sections.hidden.isEmpty()

    rows += Row.Full("title") {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
    }
    if (emptyPhone) {
        rows += Row.Full("empty") { Text(stringResource(R.string.home_empty), Modifier.padding(4.dp)) }
        return rows
    }

    fun folderRows(prefix: String, items: List<FolderItem>, perRow: Int) =
        items.chunked(perRow).mapIndexed { i, chunk ->
            Row.Cells(
                key = "$prefix-row$i",
                span = SPAN / perRow,
                cells = chunk.map { item ->
                    Cell("$prefix-${item.summary.folderKey}") { FolderTile(item, viewModel, onClick = notYet) }
                },
            )
        }

    // Top: Hidden, closed.
    if (sections.hidden.isNotEmpty()) {
        val count = sections.hidden.size
        rows += Row.Full("hidden-header") {
            Text(
                (if (hiddenOpen) "▾ " else "▸ ") + pluralStringResource(R.plurals.home_hidden_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleHidden)
                    .padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        if (hiddenOpen) rows += folderRows("hidden", sections.hidden, perRow = 3)
    }

    if (sections.folders.isNotEmpty()) {
        rows += sectionTitle(R.string.home_folders)
        rows += folderRows("folder", sections.folders, perRow = 3)
    }

    if (sections.pinned.isNotEmpty()) {
        rows += sectionTitle(R.string.home_pinned)
        rows += folderRows("pinned", sections.pinned, perRow = 2)
    }

    // Bottom: Recents (D-007), newest file right before "All", which closes the page.
    rows += sectionTitle(R.string.home_recents)
    val cells = state.recents.map { media -> Cell("recent-${media.id}") { MediaTile(media, onClick = notYet) } } +
        Cell("all") { AllTile(state.allCount, onClick = notYet) }
    rows += HomeLayout.rowsEndingFull(cells, HomeLayout.RECENT_COLUMNS).mapIndexed { i, row ->
        Row.Cells("recent-row$i", SPAN / HomeLayout.RECENT_COLUMNS, row)
    }
    return rows
}

private fun sectionTitle(title: Int) = Row.Full("title$title") {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun MediaTile(media: MediaEntity, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
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

@Composable
private fun AllTile(count: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.home_all),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(item: FolderItem, viewModel: HomeViewModel, onClick: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val summary = item.summary
    Column(Modifier.padding(bottom = 8.dp)) {
        Box {
            AsyncImage(
                model = Thumbnail(
                    summary.coverId, summary.coverVolume, summary.coverIsVideo,
                    thumbnailAspect(summary.coverWidth, summary.coverHeight),
                ),
                contentDescription = summary.folderName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
            )
            FolderMenu(item, menuOpen, onDismiss = { menuOpen = false }, viewModel)
        }
        Text(
            summary.folderName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
        val count = pluralStringResource(R.plurals.items_count, summary.itemCount, summary.itemCount)
        Text(
            if (item.visibility == FolderVisibility.ExcludedFromAll) {
                count + " · " + stringResource(R.string.home_excluded_badge)
            } else {
                count
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun FolderMenu(item: FolderItem, open: Boolean, onDismiss: () -> Unit, viewModel: HomeViewModel) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (item.visibility == FolderVisibility.Hidden) {
            MenuAction(R.string.folder_unhide, onDismiss) { viewModel.setHidden(item, false) }
        } else {
            if (item.pinned) {
                MenuAction(R.string.folder_unpin, onDismiss) { viewModel.setPinned(item, false) }
            } else {
                MenuAction(R.string.folder_pin, onDismiss) { viewModel.setPinned(item, true) }
            }
            if (item.visibility == FolderVisibility.ExcludedFromAll) {
                MenuAction(R.string.folder_include, onDismiss) { viewModel.setExcluded(item, false) }
            } else {
                MenuAction(R.string.folder_exclude, onDismiss) { viewModel.setExcluded(item, true) }
            }
            MenuAction(R.string.folder_hide, onDismiss) { viewModel.setHidden(item, true) }
        }
    }
}

@Composable
private fun MenuAction(text: Int, onDismiss: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = { action(); onDismiss() })
}

internal fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
