package app.galleria.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.galleria.data.FolderVisibility
import app.galleria.data.MediaEntity
import app.galleria.data.MediaRepository
import app.galleria.data.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val loaded: Boolean = false,
    /** Oldest first: the newest file sits right before the "All" cell (decided 2026-11-20). */
    val recents: List<MediaEntity> = emptyList(),
    val allCount: Int = 0,
    val sections: HomeSections = HomeSections(emptyList(), emptyList(), emptyList()),
)

class HomeViewModel(
    private val media: MediaRepository,
    settings: Settings,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = settings.recentRows
        .flatMapLatest { rows ->
            combine(
                media.newest(HomeLayout.recentCount(rows)),
                media.folders,
                media.folderPrefs,
            ) { newest, folders, prefs ->
                val sections = HomeLayout.sections(folders, prefs)
                HomeState(
                    loaded = true,
                    recents = newest.asReversed(),
                    allCount = HomeLayout.allCount(sections),
                    sections = sections,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setPinned(item: FolderItem, pinned: Boolean) {
        viewModelScope.launch { media.setPinned(item.summary.folderKey, pinned) }
    }

    fun setExcluded(item: FolderItem, excluded: Boolean) {
        val visibility = if (excluded) FolderVisibility.ExcludedFromAll else FolderVisibility.Normal
        viewModelScope.launch { media.setVisibility(item.summary.folderKey, visibility) }
    }

    fun setHidden(item: FolderItem, hidden: Boolean) {
        val visibility = if (hidden) {
            FolderVisibility.Hidden
        } else {
            FolderRules.defaultVisibility(item.summary.relativePath)
        }
        viewModelScope.launch { media.setVisibility(item.summary.folderKey, visibility) }
    }
}
