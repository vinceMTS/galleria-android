package app.galleria.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.galleria.folder.GridColumns
import app.galleria.home.HomeLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Simple settings (D-030). The settings page comes later; for now only defaults are used. */
class Settings(private val context: Context) {
    private object Keys {
        val firstRunDone = booleanPreferencesKey("first_run_done")
        val recentRows = intPreferencesKey("recent_rows")
        val gridColumns = intPreferencesKey("grid_columns")
    }

    val firstRunDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.firstRunDone] ?: false }

    val recentRows: Flow<Int> =
        context.dataStore.data.map { it[Keys.recentRows] ?: HomeLayout.DEFAULT_RECENT_ROWS }

    /** Columns of every grid except Recents (D-027). */
    val gridColumns: Flow<Int> =
        context.dataStore.data.map { GridColumns.sanitize(it[Keys.gridColumns] ?: GridColumns.DEFAULT) }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[Keys.gridColumns] = columns }
    }

    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[Keys.firstRunDone] = true }
    }
}
