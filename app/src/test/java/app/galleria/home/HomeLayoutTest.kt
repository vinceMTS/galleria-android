package app.galleria.home

import app.galleria.data.FolderPrefsEntity
import app.galleria.data.FolderSummary
import app.galleria.data.FolderVisibility
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {

    private fun folder(name: String, path: String = "Pictures/$name/", modified: Long = 0, count: Int = 1) =
        FolderSummary(
            folderKey = "external_primary:$path",
            folderName = name,
            relativePath = path,
            itemCount = count,
            videoCount = 0,
            lastModifiedMillis = modified,
            coverId = 1,
            coverVolume = "external_primary",
            coverIsVideo = false,
            coverWidth = null,
            coverHeight = null,
        )

    private fun prefs(f: FolderSummary, visibility: FolderVisibility = FolderVisibility.Normal, pinned: Int? = null) =
        FolderPrefsEntity(f.folderKey, visibility, pinned)

    @Test fun recentCountLeavesTheLastCellToAll() {
        assertEquals(11, HomeLayout.recentCount(rows = 3))
        assertEquals(7, HomeLayout.recentCount(rows = 2))
        assertEquals(0, HomeLayout.recentCount(rows = 0))
    }

    @Test fun foldersByLastModifiedThenName() {
        val a = folder("a", modified = 10)
        val b = folder("B", modified = 30)
        val c = folder("c", modified = 10)
        val sections = HomeLayout.sections(listOf(a, b, c), emptyList())
        assertEquals(listOf("B", "a", "c"), sections.folders.map { it.summary.folderName })
    }

    @Test fun pinnedInPinOrderAndOutOfFolders() {
        val a = folder("a")
        val b = folder("b")
        val c = folder("c")
        val sections = HomeLayout.sections(listOf(a, b, c), listOf(prefs(b, pinned = 0), prefs(a, pinned = 1)))
        assertEquals(listOf("b", "a"), sections.pinned.map { it.summary.folderName })
        assertEquals(listOf("c"), sections.folders.map { it.summary.folderName })
    }

    @Test fun hiddenOnlyInHiddenEvenIfPinned() {
        val a = folder("a")
        val b = folder("b")
        val sections = HomeLayout.sections(listOf(a, b), listOf(prefs(a, FolderVisibility.Hidden, pinned = 0)))
        assertEquals(emptyList<String>(), sections.pinned.map { it.summary.folderName })
        assertEquals(listOf("b"), sections.folders.map { it.summary.folderName })
        assertEquals(listOf("a"), sections.hidden.map { it.summary.folderName })
    }

    @Test fun excludedStaysInHome() {
        val a = folder("a")
        val sections = HomeLayout.sections(listOf(a), listOf(prefs(a, FolderVisibility.ExcludedFromAll)))
        assertEquals(FolderVisibility.ExcludedFromAll, sections.folders.single().visibility)
    }

    @Test fun folderWithoutPrefsGetsTheDefaultRule() {
        val telegram = folder("Telegram", path = "Pictures/Telegram/")
        val sections = HomeLayout.sections(listOf(telegram), emptyList())
        assertEquals(FolderVisibility.ExcludedFromAll, sections.folders.single().visibility)
    }

    @Test fun allCountsOnlyIncludedFolders() {
        val camera = folder("Camera", count = 100)
        val whatsapp = folder("WhatsApp", count = 50)
        val secret = folder("Secret", count = 7)
        val pinned = folder("Pinned", count = 3)
        val sections = HomeLayout.sections(
            listOf(camera, whatsapp, secret, pinned),
            listOf(
                prefs(whatsapp, FolderVisibility.ExcludedFromAll),
                prefs(secret, FolderVisibility.Hidden),
                prefs(pinned, pinned = 0),
            ),
        )
        assertEquals(103, HomeLayout.allCount(sections))
    }

    @Test fun recentRowsEndFull() {
        // 11 recents + "All" = 12 cells: three full rows.
        val cells = (1..11).map { "r$it" } + "All"
        assertEquals(
            listOf(
                listOf("r1", "r2", "r3", "r4"),
                listOf("r5", "r6", "r7", "r8"),
                listOf("r9", "r10", "r11", "All"),
            ),
            HomeLayout.rowsEndingFull(cells, 4),
        )
    }

    @Test fun recentRowsShortRowOnTop() {
        // A phone with few photos: the short row is the top one, "All" still bottom right.
        val cells = listOf("r1", "r2", "r3", "r4", "r5", "All")
        assertEquals(
            listOf(listOf("r1", "r2"), listOf("r3", "r4", "r5", "All")),
            HomeLayout.rowsEndingFull(cells, 4),
        )
    }

    @Test fun durations() {
        assertEquals("0:07", formatDuration(7_400))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
