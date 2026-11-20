package app.galleria.folder

import app.galleria.data.MonthSummary
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Columns of the grids, changed with a pinch (D-027): one value for every grid. */
object GridColumns {
    val STEPS = listOf(3, 4, 5, 7)
    const val DEFAULT = 4

    /** Fingers apart: bigger thumbnails, fewer columns. */
    fun fewer(current: Int): Int = STEPS.lastOrNull { it < current } ?: STEPS.first()

    /** Fingers together: smaller thumbnails, more columns. */
    fun more(current: Int): Int = STEPS.firstOrNull { it > current } ?: STEPS.last()

    /** A stored value that is not a step (e.g. after a change of steps) goes to the nearest one. */
    fun sanitize(value: Int): Int = STEPS.minBy { kotlin.math.abs(it - value) }
}

/** One year of "All" (D-022): built from its months, cover = cover of its newest month. */
data class YearSummary(
    val year: Int,
    val itemCount: Int,
    val cover: MonthSummary,
)

/**
 * Positions in the three levels of "All" (D-021). All lists are newest first, the same order as
 * the paged grid, so the position of a period is the number of files (or months) newer than it.
 */
object Periods {
    fun yearOf(month: MonthSummary): Int = month.month.substring(0, 4).toInt()

    /** [months] newest first → years newest first. */
    fun years(months: List<MonthSummary>): List<YearSummary> =
        months.groupBy(::yearOf).map { (year, ms) -> YearSummary(year, ms.sumOf { it.itemCount }, ms.first()) }

    /** Index in "All" of the newest file of [month]; null if the month is not there. */
    fun allIndexOf(months: List<MonthSummary>, month: String): Int? {
        var newer = 0
        for (m in months) {
            if (m.month == month) return newer
            newer += m.itemCount
        }
        return null
    }

    /** Index in Months of the newest month of [year]; null if the year is not there. */
    fun monthsIndexOf(months: List<MonthSummary>, year: Int): Int? =
        months.indexOfFirst { yearOf(it) == year }.takeIf { it >= 0 }

    /**
     * Showing a period in a bottom-up grid: where its oldest row must end up, in pixels above the
     * bottom edge (= how far to scroll back once the oldest row sits on the bottom edge). Result:
     * the oldest row at the top of the screen when the period is taller than the screen,
     * otherwise the whole period with its newest row at the bottom.
     * Cell `i` is on row `i / columns`, counting from the bottom.
     */
    fun scrollBackPx(newestIndex: Int, oldestIndex: Int, columns: Int, rowPx: Int, availablePx: Int): Int {
        val periodRows = oldestIndex / columns - newestIndex / columns
        return minOf(availablePx - rowPx, periodRows * rowPx).coerceAtLeast(0)
    }

    /** "novembre 2026" in the phone's language. */
    fun monthLabel(month: String, locale: Locale = Locale.getDefault()): String {
        val ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"))
        return ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + ym.year
    }
}
