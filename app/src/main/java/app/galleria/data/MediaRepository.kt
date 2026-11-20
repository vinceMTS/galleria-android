package app.galleria.data

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The only door to photos and videos (D-032): home and the other screens never talk to
 * MediaStore directly.
 */
class MediaRepository(
    private val context: Context,
    private val db: MediaDatabase,
    private val scope: CoroutineScope,
) {
    private val indexer = MediaIndexer(context, db)
    private val syncLock = Mutex()
    private val _syncing = MutableStateFlow(false)

    /** True while the index is being updated. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val folders: Flow<List<FolderSummary>> = db.media().folders()
    val folderPrefs: Flow<List<FolderPrefsEntity>> = db.folderPrefs().all()
    val mediaCount: Flow<Int> = db.media().count()

    fun newest(limit: Int): Flow<List<MediaEntity>> = db.media().newest(limit)

    /** All (D-021), newest first. */
    fun allPaged(): Flow<PagingData<MediaEntity>> = pager { db.media().allPaged() }
    val allCount: Flow<Int> = db.media().allCount()
    val allMonths: Flow<List<MonthSummary>> = db.media().allMonths()

    /** One folder, newest first (D-024; other orders arrive with "Sort and filter", D-023). */
    fun folderPaged(folderKey: String): Flow<PagingData<MediaEntity>> = pager { db.media().folderPaged(folderKey) }
    fun folderCount(folderKey: String): Flow<Int> = db.media().folderCount(folderKey)

    /**
     * Placeholders keep the full item count, so the grid can jump anywhere (fast scroll, a month
     * tapped in Months) and Room loads that page directly.
     */
    private fun pager(source: () -> androidx.paging.PagingSource<Int, MediaEntity>) =
        Pager(PagingConfig(pageSize = 150, enablePlaceholders = true, jumpThreshold = 600), pagingSourceFactory = source).flow

    suspend fun sync() = withContext(Dispatchers.IO) {
        syncLock.withLock {
            _syncing.value = true
            try {
                indexer.sync()
            } finally {
                _syncing.value = false
            }
        }
    }

    /** Re-syncs whenever MediaStore reports a change (new photo, file deleted by another app…). */
    @OptIn(FlowPreview::class)
    fun watchChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.onStart { emit(Unit) }.debounce(500)

    fun syncInBackground() {
        scope.launch { sync() }
    }

    suspend fun setPinned(folderKey: String, pinned: Boolean) = db.withTransaction {
        val prefs = currentPrefs(folderKey)
        val order = if (pinned) (db.folderPrefs().maxPinnedOrder() ?: -1) + 1 else null
        val visibility =
            if (pinned && prefs.visibility == FolderVisibility.Hidden) FolderVisibility.Normal else prefs.visibility
        db.folderPrefs().upsert(listOf(prefs.copy(pinnedOrder = order, visibility = visibility)))
    }

    /** Hiding a folder also unpins it. */
    suspend fun setVisibility(folderKey: String, visibility: FolderVisibility) = db.withTransaction {
        val prefs = currentPrefs(folderKey)
        val order = if (visibility == FolderVisibility.Hidden) null else prefs.pinnedOrder
        db.folderPrefs().upsert(listOf(prefs.copy(visibility = visibility, pinnedOrder = order)))
    }

    /** Saves the first-run choices (D-009): folder key → shown in Recents and All. */
    suspend fun saveInclusion(choices: Map<String, Boolean>) = db.withTransaction {
        val updated = choices.map { (key, included) ->
            val prefs = currentPrefs(key)
            val visibility = when {
                prefs.visibility == FolderVisibility.Hidden -> FolderVisibility.Hidden
                included -> FolderVisibility.Normal
                else -> FolderVisibility.ExcludedFromAll
            }
            prefs.copy(visibility = visibility)
        }
        db.folderPrefs().upsert(updated)
    }

    private suspend fun currentPrefs(folderKey: String): FolderPrefsEntity =
        db.folderPrefs().get(folderKey) ?: FolderPrefsEntity(folderKey, FolderVisibility.Normal)
}
