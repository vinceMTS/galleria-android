package app.galleria.data

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import androidx.room.withTransaction
import app.galleria.home.FolderRules
import app.galleria.media.FileDates

/**
 * Keeps the index (D-030) in line with MediaStore, reading only what changed since last time
 * (`GENERATION_MODIFIED`), per storage volume. If MediaStore was rebuilt (its version changed)
 * the volume is read again from scratch. Files are only read, never touched.
 *
 * Folders with a `.nomedia` file are invisible to MediaStore and so to the app (decided for now).
 * Trashed and pending files are left out by MediaStore by default.
 */
class MediaIndexer(
    private val context: Context,
    private val db: MediaDatabase,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun sync() {
        val volumes = MediaStore.getExternalVolumeNames(context).toList()
        db.media().deleteVolumesExcept(volumes)
        db.syncState().deleteVolumesExcept(volumes)
        for (volume in volumes) syncVolume(volume)
        addDefaultPrefsForNewFolders()
    }

    private suspend fun syncVolume(volume: String) {
        val version = MediaStore.getVersion(context, volume)
        // Read the generation before querying: anything changed meanwhile is picked up next time.
        val generation = MediaStore.getGeneration(context, volume)
        val state = db.syncState().get(volume)
        val fullRead = state == null || state.mediaStoreVersion != version
        val since = if (fullRead) 0L else state.generation

        val changed = queryMedia(volume, since)
        val presentIds = queryIds(volume)

        db.withTransaction {
            if (fullRead) db.media().deleteVolume(volume)
            changed.chunked(500).forEach { db.media().upsert(it) }
            val gone = db.media().idsOfVolume(volume).filterNot { it in presentIds }
            gone.chunked(500).forEach { db.media().deleteByIds(it) }
            db.syncState().upsert(SyncStateEntity(volume, version, generation))
        }
    }

    /** D-009: folders seen for the first time get the default, silently. */
    private suspend fun addDefaultPrefsForNewFolders() {
        val known = db.folderPrefs().allOnce().mapTo(HashSet()) { it.folderKey }
        val new = db.media().folderKeys().filterNot { it in known }.map { key ->
            FolderPrefsEntity(key, FolderRules.defaultVisibility(key.substringAfter(':')))
        }
        if (new.isNotEmpty()) db.folderPrefs().upsert(new)
    }

    private fun mediaSelection(extra: String? = null): String =
        listOfNotNull(
            "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO})",
            extra,
        ).joinToString(" AND ")

    private fun queryIds(volume: String): Set<Long> {
        val ids = HashSet<Long>()
        resolver.query(
            MediaStore.Files.getContentUri(volume),
            arrayOf(FileColumns._ID),
            Bundle().apply { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, mediaSelection()) },
            null,
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        return ids
    }

    private fun queryMedia(volume: String, sinceGeneration: Long): List<MediaEntity> {
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            FileColumns.DISPLAY_NAME,
            FileColumns.RELATIVE_PATH,
            FileColumns.BUCKET_DISPLAY_NAME,
            FileColumns.MIME_TYPE,
            FileColumns.DATE_TAKEN,
            FileColumns.DATE_MODIFIED,
            FileColumns.SIZE,
            FileColumns.DURATION,
            FileColumns.WIDTH,
            FileColumns.HEIGHT,
        )
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                mediaSelection("${FileColumns.GENERATION_MODIFIED} > ?"),
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(sinceGeneration.toString()))
        }
        val result = ArrayList<MediaEntity>()
        resolver.query(MediaStore.Files.getContentUri(volume), projection, args, null)?.use { c ->
            fun Int.longOrNull() = if (c.isNull(this)) null else c.getLong(this)
            fun Int.intOrNull() = if (c.isNull(this)) null else c.getInt(this)
            while (c.moveToNext()) {
                val name = c.getString(2) ?: continue
                val relativePath = c.getString(3) ?: ""
                val dateTaken = 6.longOrNull()?.takeIf { it > 0 }
                val dateModified = (7.longOrNull() ?: 0L) * 1000 // MediaStore stores seconds
                result += MediaEntity(
                    id = c.getLong(0),
                    volume = volume,
                    folderKey = folderKeyOf(volume, relativePath),
                    folderName = c.getString(4) ?: relativePath.trimEnd('/').substringAfterLast('/'),
                    relativePath = relativePath,
                    name = name,
                    isVideo = c.getInt(1) == FileColumns.MEDIA_TYPE_VIDEO,
                    mimeType = c.getString(5),
                    dateTakenMillis = dateTaken,
                    dateModifiedMillis = dateModified,
                    dateMillis = FileDates.resolve(dateTaken, name, dateModified),
                    sizeBytes = 8.longOrNull() ?: 0L,
                    durationMillis = 9.longOrNull(),
                    width = 10.intOrNull(),
                    height = 11.intOrNull(),
                )
            }
        }
        return result
    }
}
