package app.galleria.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One photo or video, as read from MediaStore, plus the date computed by [app.galleria.media.FileDates].
 * This table is only an index (D-030): it can be deleted and rebuilt at any time and it never
 * touches the files.
 */
@Entity(
    tableName = "media",
    indices = [Index("folderKey", "dateMillis"), Index("dateMillis"), Index("volume")],
)
data class MediaEntity(
    /** MediaStore `_ID`. */
    @PrimaryKey val id: Long,
    val volume: String,
    /** See [folderKeyOf]. */
    val folderKey: String,
    val folderName: String,
    val relativePath: String,
    val name: String,
    val isVideo: Boolean,
    val mimeType: String?,
    val dateTakenMillis: Long?,
    val dateModifiedMillis: Long,
    /** The file's date (D-029): taken → name → modified. */
    val dateMillis: Long,
    val sizeBytes: Long,
    val durationMillis: Long?,
    val width: Int?,
    val height: Int?,
)

enum class FolderVisibility {
    /** In home, and its files are in Recents and All. */
    Normal,

    /** In home, but its files stay out of Recents and All (D-008). */
    ExcludedFromAll,

    /** Only in the Hidden section; its files stay out of Recents and All too. */
    Hidden,
}

/**
 * The user's choices for a folder (D-008, D-010). Real user data, unlike [MediaEntity]:
 * a row exists for every folder the app has ever seen, so new folders can get the default
 * silently (D-009) and the choices survive a rebuild of the index.
 */
@Entity(tableName = "folder_prefs")
data class FolderPrefsEntity(
    @PrimaryKey val folderKey: String,
    val visibility: FolderVisibility,
    /** Position among pinned folders; null if not pinned. */
    val pinnedOrder: Int? = null,
    /** Cover chosen by hand (MediaStore id); null = latest file (D-010). Not used yet. */
    val coverMediaId: Long? = null,
)

/** How far the index got for one storage volume, using MediaStore's version and generation. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val volume: String,
    val mediaStoreVersion: String,
    val generation: Long,
)

/** A folder as shown in home: computed from the index. */
data class FolderSummary(
    val folderKey: String,
    val folderName: String,
    val relativePath: String,
    val itemCount: Int,
    val videoCount: Int,
    /** Latest modification of a file in the folder: "folder with news goes up" (D-010). */
    val lastModifiedMillis: Long,
    val coverId: Long,
    val coverVolume: String,
    val coverIsVideo: Boolean,
    val coverWidth: Int?,
    val coverHeight: Int?,
)

@Dao
interface MediaDao {
    @Upsert
    suspend fun upsert(items: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media WHERE volume = :volume")
    suspend fun deleteVolume(volume: String)

    @Query("DELETE FROM media WHERE volume NOT IN (:volumes)")
    suspend fun deleteVolumesExcept(volumes: List<String>)

    @Query("SELECT id FROM media WHERE volume = :volume")
    suspend fun idsOfVolume(volume: String): List<Long>

    @Query("SELECT DISTINCT folderKey FROM media")
    suspend fun folderKeys(): List<String>

    @Query("SELECT COUNT(*) FROM media")
    fun count(): Flow<Int>

    @Query(
        """
        SELECT f.*, c.volume AS coverVolume, c.isVideo AS coverIsVideo,
            c.width AS coverWidth, c.height AS coverHeight FROM (
            SELECT m.folderKey, MAX(m.folderName) AS folderName, MAX(m.relativePath) AS relativePath,
                COUNT(*) AS itemCount, SUM(m.isVideo) AS videoCount,
                MAX(m.dateModifiedMillis) AS lastModifiedMillis,
                (SELECT m2.id FROM media m2 WHERE m2.folderKey = m.folderKey
                    ORDER BY m2.dateMillis DESC, m2.id DESC LIMIT 1) AS coverId
            FROM media m GROUP BY m.folderKey
        ) f JOIN media c ON c.id = f.coverId
        """
    )
    fun folders(): Flow<List<FolderSummary>>

    /** The newest files of folders that are neither excluded nor hidden, newest first. */
    @Query(
        """
        SELECT m.* FROM media m LEFT JOIN folder_prefs p ON p.folderKey = m.folderKey
        WHERE COALESCE(p.visibility, 'Normal') = 'Normal'
        ORDER BY m.dateMillis DESC, m.id DESC LIMIT :limit
        """
    )
    fun newest(limit: Int): Flow<List<MediaEntity>>
}

@Dao
interface FolderPrefsDao {
    @Query("SELECT * FROM folder_prefs")
    fun all(): Flow<List<FolderPrefsEntity>>

    @Query("SELECT * FROM folder_prefs")
    suspend fun allOnce(): List<FolderPrefsEntity>

    @Upsert
    suspend fun upsert(prefs: List<FolderPrefsEntity>)

    @Query("SELECT * FROM folder_prefs WHERE folderKey = :folderKey")
    suspend fun get(folderKey: String): FolderPrefsEntity?

    @Query("SELECT MAX(pinnedOrder) FROM folder_prefs")
    suspend fun maxPinnedOrder(): Int?
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE volume = :volume")
    suspend fun get(volume: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE volume NOT IN (:volumes)")
    suspend fun deleteVolumesExcept(volumes: List<String>)
}

@Database(
    entities = [MediaEntity::class, FolderPrefsEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun folderPrefs(): FolderPrefsDao
    abstract fun syncState(): SyncStateDao

    companion object {
        fun create(context: Context): MediaDatabase =
            Room.databaseBuilder(context, MediaDatabase::class.java, "galleria.db").build()
    }
}

/** A folder is identified by its storage volume and its path, e.g. `external_primary:DCIM/Camera/`. */
fun folderKeyOf(volume: String, relativePath: String): String = "$volume:$relativePath"
