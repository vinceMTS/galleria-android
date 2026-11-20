package app.galleria.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Height of [GridTopBar] below the status bar; grids add it to their top padding. */
val GRID_TOP_BAR_HEIGHT = 64.dp

/**
 * A grid drawn from the bottom (D-024): item 0, the newest, is the bottom-right cell, and the grid
 * opens already at the bottom and stays anchored there when a new photo arrives.
 *
 * `reverseLayout` stacks rows bottom to top; a right-to-left direction then puts each row in
 * chronological order from left to right, so only the oldest (top) row can be short, like Recents
 * in home (D-049). Cells must draw their content with [Ltr].
 */
@Composable
fun BottomUpGrid(
    columns: Int,
    state: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp,
    content: LazyGridScope.() -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = modifier,
            reverseLayout = true,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Restores the normal direction inside a cell of [BottomUpGrid]. */
@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/**
 * Pinch on a grid (D-027): fingers apart → [onFewer] columns, together → [onMore]. Only gestures
 * with two fingers are taken; one finger still scrolls the grid underneath.
 */
private fun Modifier.pinchGesture(onFewer: () -> Unit, onMore: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    event.changes.forEach { it.consume() } // the grid must not scroll meanwhile
                    // One step per threshold crossed; the anchor cell stays in place (item keys).
                    if (zoom > PINCH_STEP) {
                        onFewer(); zoom = 1f
                    } else if (zoom < 1f / PINCH_STEP) {
                        onMore(); zoom = 1f
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private const val PINCH_STEP = 1.3f

/** Pinch to change [columns] along the steps of [GridColumns]. */
@Composable
fun Modifier.pinchColumns(columns: Int, onColumnsChange: (Int) -> Unit): Modifier {
    val current by rememberUpdatedState(columns)
    val change by rememberUpdatedState(onColumnsChange)
    return pinchGesture(
        onFewer = { change(GridColumns.fewer(current)) },
        onMore = { change(GridColumns.more(current)) },
    )
}

/**
 * Header of a grid (D-027): name and number of items, over the content so the photos scroll
 * under it. "Select" and "Sort and filter" arrive in the next steps.
 */
@Composable
fun GridTopBar(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
