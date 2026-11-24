package app.deutschlern.data.exam

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.examProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "exam_progress")

/** Best result per exam variant, in percent. A variant missing from the map was never finished. */
@Singleton
class ExamProgressStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val bestScores: Flow<Map<String, Int>>
        get() = context.examProgressDataStore.data.map { prefs ->
            prefs.asMap().entries
                .filter { it.key.name.startsWith(PREFIX) }
                .associate { it.key.name.removePrefix(PREFIX) to (it.value as Int) }
        }

    /** Writing and speaking tasks have no score: record them with 100 to mark them as done. */
    suspend fun recordResult(variantId: String, percent: Int) {
        context.examProgressDataStore.edit { prefs ->
            val key = intPreferencesKey(PREFIX + variantId)
            prefs[key] = maxOf(prefs[key] ?: -1, percent.coerceIn(0, 100))
        }
    }

    private companion object {
        const val PREFIX = "best_"
    }
}
