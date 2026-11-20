package app.galleria.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.galleria.R
import app.galleria.data.FolderPrefsEntity
import app.galleria.data.FolderSummary
import app.galleria.data.FolderVisibility
import app.galleria.data.MediaRepository
import app.galleria.media.Thumbnail
import app.galleria.media.thumbnailAspect
import kotlinx.coroutines.flow.Flow

/**
 * D-009: the list of folders with "show in Recents and All". Messaging and download folders
 * start unticked. Skipping keeps the suggestion (already stored by the index).
 */
@Composable
fun FirstRunScreen(
    media: MediaRepository,
    onSkip: () -> Unit,
    onConfirm: (Map<String, Boolean>) -> Unit,
) {
    val folders by media.folders.collectAsStateWithLifecycle(initialValue = null)
    val prefs by media.folderPrefs.collectAsStateWithLifecycle(initialValue = emptyList())
    val syncing by media.syncing.collectAsStateWithLifecycle()
    val changes = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.first_run_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(stringResource(R.string.first_run_body), style = MaterialTheme.typography.bodyMedium)

        val list = folders
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            when {
                list == null || (list.isEmpty() && syncing) -> Loading()
                list.isEmpty() -> Text(stringResource(R.string.first_run_empty), Modifier.align(Alignment.Center))
                else -> FolderChecklist(sortedForFirstRun(list), prefs, changes)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.first_run_skip)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onConfirm(changes.toMap()) }, enabled = !list.isNullOrEmpty() || !syncing) {
                Text(stringResource(R.string.first_run_confirm))
            }
        }
    }
}

private fun sortedForFirstRun(folders: List<FolderSummary>) =
    folders.sortedWith(
        compareByDescending<FolderSummary> { it.lastModifiedMillis }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.folderName },
    )

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.first_run_reading), Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun FolderChecklist(
    folders: List<FolderSummary>,
    prefs: List<FolderPrefsEntity>,
    changes: MutableMap<String, Boolean>,
) {
    val prefsByKey = remember(prefs) { prefs.associateBy { it.folderKey } }
    LazyColumn {
        items(folders, key = { it.folderKey }) { folder ->
            val stored = prefsByKey[folder.folderKey]?.visibility
                ?: FolderRules.defaultVisibility(folder.relativePath)
            val checked = changes[folder.folderKey] ?: (stored == FolderVisibility.Normal)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { changes[folder.folderKey] = !checked }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                coil3.compose.AsyncImage(
                    model = Thumbnail(
                        folder.coverId, folder.coverVolume, folder.coverIsVideo,
                        thumbnailAspect(folder.coverWidth, folder.coverHeight),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(folder.folderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val hint = if (FolderRules.isMessagingOrDownload(folder.relativePath)) {
                        " · " + stringResource(R.string.first_run_suggested_off)
                    } else {
                        ""
                    }
                    Text(
                        pluralStringResource(R.plurals.items_count, folder.itemCount, folder.itemCount) + hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        folder.relativePath.trimEnd('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Checkbox(checked = checked, onCheckedChange = { changes[folder.folderKey] = it })
            }
        }
    }
}
