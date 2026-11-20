package app.galleria.home

import app.galleria.data.FolderPrefsEntity
import app.galleria.data.FolderSummary
import app.galleria.data.FolderVisibility

data class FolderItem(
    val summary: FolderSummary,
    val visibility: FolderVisibility,
    val pinned: Boolean,
)

/** The folder sections of home (D-006): Pinned, Folders, Hidden. */
data class HomeSections(
    val pinned: List<FolderItem>,
    val folders: List<FolderItem>,
    val hidden: List<FolderItem>,
)

object HomeLayout {
    /** Recents columns: provisional, the look of home is topic 08. */
    const val RECENT_COLUMNS = 4

    /** Default rows of Recents (D-007). */
    const val DEFAULT_RECENT_ROWS = 3

    /** Recents shows this many files; the last cell is "All" (D-007). */
    fun recentCount(rows: Int, columns: Int = RECENT_COLUMNS): Int = (rows * columns - 1).coerceAtLeast(0)

    /**
     * Splits [items] into rows of [size] so that the **last** row is full and only the first one
     * may be short: in Recents the newest file and "All" always sit in the bottom row.
     */
    fun <T> rowsEndingFull(items: List<T>, size: Int): List<List<T>> =
        items.asReversed().chunked(size).map { it.asReversed() }.asReversed()

    /** Files in All: only folders that are neither excluded nor hidden (D-008). */
    fun allCount(sections: HomeSections): Int =
        (sections.pinned + sections.folders)
            .filter { it.visibility == FolderVisibility.Normal }
            .sumOf { it.summary.itemCount }

    /**
     * Splits folders into sections.
     * - Pinned: in the order they were pinned (drag to reorder comes later).
     * - Folders: last modified first (D-010 default), then by name.
     * - Hidden: by name.
     * A folder without preferences gets the default rule of [FolderRules].
     */
    fun sections(folders: List<FolderSummary>, prefs: List<FolderPrefsEntity>): HomeSections {
        val prefsByKey = prefs.associateBy { it.folderKey }
        val items = folders.map { summary ->
            val p = prefsByKey[summary.folderKey]
            val visibility = p?.visibility ?: FolderRules.defaultVisibility(summary.relativePath)
            Triple(FolderItem(summary, visibility, pinned = p?.pinnedOrder != null), visibility, p?.pinnedOrder)
        }
        val byName = compareBy<FolderItem, String>(String.CASE_INSENSITIVE_ORDER) { it.summary.folderName }
        return HomeSections(
            pinned = items
                .filter { (_, visibility, order) -> order != null && visibility != FolderVisibility.Hidden }
                .sortedBy { (_, _, order) -> order }
                .map { it.first },
            folders = items
                .filter { (_, visibility, order) -> order == null && visibility != FolderVisibility.Hidden }
                .map { it.first }
                .sortedWith(compareByDescending<FolderItem> { it.summary.lastModifiedMillis }.then(byName)),
            hidden = items
                .filter { (_, visibility, _) -> visibility == FolderVisibility.Hidden }
                .map { it.first }
                .sortedWith(byName),
        )
    }
}
