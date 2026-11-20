package app.galleria.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import app.galleria.R
import app.galleria.data.MediaEntity
import app.galleria.data.MediaRepository
import app.galleria.data.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderGridViewModel(
    media: MediaRepository,
    private val settings: Settings,
    folderKey: String,
) : ViewModel() {
    val items = media.folderPaged(folderKey).cachedIn(viewModelScope)
    val count = media.folderCount(folderKey).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val columns = settings.gridColumns.stateIn(viewModelScope, SharingStarted.Eagerly, GridColumns.DEFAULT)

    fun setColumns(columns: Int) {
        viewModelScope.launch { settings.setGridColumns(columns) }
    }
}

/** Inside a folder (D-027): only the grid, newest at the bottom (D-024). */
@Composable
fun FolderGridScreen(folderName: String, viewModel: FolderGridViewModel, onOpen: (MediaEntity) -> Unit) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
        PagedMediaGrid(
            items = items,
            columns = columns,
            onColumnsChange = viewModel::setColumns,
            state = gridState,
            contentPadding = gridPadding(bottomExtra = 4.dp),
            onOpen = onOpen,
            modifier = Modifier.fillMaxSize(),
        )
        GridTopBar(folderName, pluralStringResource(R.plurals.items_count, count, count))
    }
}

/** Room for the top bar above and the system bars (plus [bottomExtra]) below. */
@Composable
internal fun gridPadding(bottomExtra: Dp, horizontal: Dp = 0.dp): PaddingValues = PaddingValues(
    start = horizontal,
    end = horizontal,
    top =WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + GRID_TOP_BAR_HEIGHT,
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomExtra,
)
