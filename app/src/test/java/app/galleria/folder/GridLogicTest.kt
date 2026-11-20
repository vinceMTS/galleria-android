package app.galleria.folder

import app.galleria.data.MonthSummary
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridLogicTest {
    private fun month(m: String, count: Int, cover: Long = count.toLong()) =
        MonthSummary(m, count, 0L, cover, "external_primary", false, null, null)

    // Newest first, like the query.
    private val months = listOf(
        month("2026-11", 10, cover = 1),
        month("2026-03", 5, cover = 2),
        month("2025-12", 7, cover = 3),
        month("2025-01", 2, cover = 4),
    )

    @Test
    fun `pinch steps through 3 4 5 7 and stops at the ends`() {
        assertEquals(3, GridColumns.fewer(4))
        assertEquals(3, GridColumns.fewer(3))
        assertEquals(5, GridColumns.more(4))
        assertEquals(7, GridColumns.more(5))
        assertEquals(7, GridColumns.more(7))
        assertEquals(5, GridColumns.fewer(7))
    }

    @Test
    fun `stored columns that are not a step go to the nearest step`() {
        assertEquals(7, GridColumns.sanitize(8))
        assertEquals(3, GridColumns.sanitize(1))
        assertEquals(5, GridColumns.sanitize(5))
        assertEquals(5, GridColumns.sanitize(6)) // tie: the smaller one
    }

    @Test
    fun `years are built from months with the newest month as cover`() {
        val years = Periods.years(months)
        assertEquals(listOf(2026, 2025), years.map { it.year })
        assertEquals(listOf(15, 9), years.map { it.itemCount })
        assertEquals(listOf(1L, 3L), years.map { it.cover.coverId })
    }

    @Test
    fun `a month starts after all the newer files`() {
        assertEquals(0, Periods.allIndexOf(months, "2026-11"))
        assertEquals(10, Periods.allIndexOf(months, "2026-03"))
        assertEquals(22, Periods.allIndexOf(months, "2025-01"))
        assertNull(Periods.allIndexOf(months, "2024-01"))
    }

    @Test
    fun `a year starts at its newest month`() {
        assertEquals(0, Periods.monthsIndexOf(months, 2026))
        assertEquals(2, Periods.monthsIndexOf(months, 2025))
        assertNull(Periods.monthsIndexOf(months, 2020))
    }

    @Test
    fun `a long period scrolls back one screen less its top row`() {
        // Items 40..139 in 4 columns: rows 10..34. Rows of 100 px, 650 px of grid.
        assertEquals(550, Periods.scrollBackPx(newestIndex = 40, oldestIndex = 139, columns = 4, rowPx = 100, availablePx = 650))
    }

    @Test
    fun `a short period scrolls back only to its newest row`() {
        // Items 40..47: rows 10..11, one row back.
        assertEquals(100, Periods.scrollBackPx(newestIndex = 40, oldestIndex = 47, columns = 4, rowPx = 100, availablePx = 650))
        // A period on a single row does not move.
        assertEquals(0, Periods.scrollBackPx(newestIndex = 41, oldestIndex = 42, columns = 4, rowPx = 100, availablePx = 650))
    }

    @Test
    fun `month labels use the language of the phone`() {
        assertEquals("novembre 2026", Periods.monthLabel("2026-11", Locale.ITALIAN))
        assertEquals("March 2025", Periods.monthLabel("2025-03", Locale.ENGLISH))
    }
}
