package app.galleria.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three levels of "All" (D-021). */
enum class AllLevel(val label: Int) {
    Years(R.string.all_years),
    Months(R.string.all_months),
    All(R.string.all_all),
}

class AllViewModel(media: MediaRepository, private val settings: Settings) : ViewModel() {
    val items = media.allPaged().cachedIn(viewModelScope)
    val count = media.allCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val months = media.allMonths.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val columns = settings.gridColumns.stateIn(viewModelScope, SharingStarted.Eagerly, GridColumns.DEFAULT)

    fun setColumns(columns: Int) {
        viewModelScope.launch { settings.setGridColumns(columns) }
    }
}

/** A period to show in the grid of [target]: cells [newest]..[oldest] (indices, newest first). */
private data class PeriodJump(val target: AllLevel, val newest: Int, val oldest: Int)

/** Columns of Months and Years: provisional, the look is topic 08. */
private const val MONTH_COLUMNS = 3
private const val YEAR_COLUMNS = 2
private val SELECTOR_HEIGHT = 64.dp

/**
 * "All" (D-021): Years | Months | All, chosen at the bottom near the thumb. Opens on All, at the
 * bottom (D-024). A year opens its months, a month opens All at that point.
 */
@Composable
fun AllScreen(viewModel: AllViewModel, onOpen: (MediaEntity) -> Unit) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val months by viewModel.months.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    var level by rememberSaveable { mutableStateOf(AllLevel.All) }

    val allState = rememberLazyGridState()
    val monthsState = rememberLazyGridState()
    val yearsState = rememberLazyGridState()

    val monthCells = remember(months) {
        months.map { PeriodCell(it.month, Periods.monthLabel(it.month), it.itemCount, it) }
    }
    val yearCells = remember(months) {
        Periods.years(months).map { PeriodCell(it.year.toString(), it.year.toString(), it.itemCount, it.cover) }
    }

    val padding = gridPadding(bottomExtra = SELECTOR_HEIGHT)
    val periodPadding = gridPadding(bottomExtra = SELECTOR_HEIGHT, horizontal = 8.dp)
    // Space covered by the top bar and the level selector: the period must fit between them.
    val coveredPx = with(LocalDensity.current) {
        (padding.calculateTopPadding() + padding.calculateBottomPadding()).roundToPx()
    }

    // A tapped year or month: the grid it opens jumps to that period once it is laid out.
    var jump by remember { mutableStateOf<PeriodJump?>(null) }
    LaunchedEffect(jump) {
        val j = jump ?: return@LaunchedEffect
        val (state, cols) = if (j.target == AllLevel.All) allState to columns else monthsState to MONTH_COLUMNS
        state.scrollToItem(j.oldest)
        val info = snapshotFlow { state.layoutInfo }
            .first { li -> li.visibleItemsInfo.any { it.index == j.oldest } }
        val oldestItem = info.visibleItemsInfo.first { it.index == j.oldest }
        val rowPx = oldestItem.size.height + info.mainAxisItemSpacing
        val availablePx = info.viewportSize.height - coveredPx
        // Near the oldest files the grid cannot put the period on the bottom row: start from
        // where the oldest row really is (offsets grow upwards from the bottom edge).
        val target = Periods.scrollBackPx(j.newest, j.oldest, cols, rowPx, availablePx)
        state.scrollBy(-(target - oldestItem.offset.y).toFloat())
        jump = null
    }

    Box(Modifier.fillMaxSize()) {
        when (level) {
            AllLevel.All -> PagedMediaGrid(
                items = items,
                columns = columns,
                onColumnsChange = viewModel::setColumns,
                state = allState,
                contentPadding = padding,
                onOpen = onOpen,
                modifier = Modifier.fillMaxSize(),
            )
            AllLevel.Months -> PeriodGrid(
                cells = monthCells,
                columns = MONTH_COLUMNS,
                state = monthsState,
                contentPadding = periodPadding,
                modifier = Modifier.fillMaxSize(),
                onOpen = { cell ->
                    val newest = Periods.allIndexOf(months, cell.key) ?: return@PeriodGrid
                    val oldest = newest + cell.itemCount - 1
                    allState.requestScrollToItem(oldest)
                    jump = PeriodJump(AllLevel.All, newest, oldest)
                    level = AllLevel.All
                },
            )
            AllLevel.Years -> PeriodGrid(
                cells = yearCells,
                columns = YEAR_COLUMNS,
                state = yearsState,
                contentPadding = periodPadding,
                modifier = Modifier.fillMaxSize(),
                onOpen = { cell ->
                    val year = cell.key.toInt()
                    val newest = Periods.monthsIndexOf(months, year) ?: return@PeriodGrid
                    val oldest = newest + months.count { Periods.yearOf(it) == year } - 1
                    monthsState.requestScrollToItem(oldest)
                    jump = PeriodJump(AllLevel.Months, newest, oldest)
                    level = AllLevel.Months
                },
            )
        }

        GridTopBar(stringResource(R.string.home_all), pluralStringResource(R.plurals.items_count, count, count))
        LevelSelector(level, onChange = { level = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LevelSelector(level: AllLevel, onChange: (AllLevel) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 32.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AllLevel.entries.forEachIndexed { i, l ->
                SegmentedButton(
                    selected = l == level,
                    onClick = { onChange(l) },
                    shape = SegmentedButtonDefaults.itemShape(i, AllLevel.entries.size),
                    icon = {},
                ) { Text(stringResource(l.label)) }
            }
        }
    }
}
