package app.deutschlern.data.trainer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.trainerStatsDataStore: DataStore<Preferences> by preferencesDataStore(name = "trainer_stats")

data class TrainerStats(
    /** Consecutive days with at least one answer, including today or yesterday. */
    val streakDays: Int = 0,
    val answersToday: Int = 0
)

/** Daily streak and answer counter: a small, visible reason to come back every day. */
@Singleton
class TrainerStatsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val stats: Flow<TrainerStats>
        get() = context.trainerStatsDataStore.data.map { prefs ->
            val today = LocalDate.now().toEpochDay()
            val lastDay = prefs[KEY_LAST_DAY] ?: Long.MIN_VALUE
            TrainerStats(
                streakDays = if (lastDay >= today - 1) prefs[KEY_STREAK] ?: 0 else 0,
                answersToday = if (lastDay == today) prefs[KEY_TODAY_COUNT] ?: 0 else 0
            )
        }

    suspend fun recordAnswer(today: Long = LocalDate.now().toEpochDay()) {
        context.trainerStatsDataStore.edit { prefs ->
            val lastDay = prefs[KEY_LAST_DAY]
            when (lastDay) {
                today -> prefs[KEY_TODAY_COUNT] = (prefs[KEY_TODAY_COUNT] ?: 0) + 1
                today - 1 -> {
                    prefs[KEY_STREAK] = (prefs[KEY_STREAK] ?: 0) + 1
                    prefs[KEY_TODAY_COUNT] = 1
                }
                else -> {
                    prefs[KEY_STREAK] = 1
                    prefs[KEY_TODAY_COUNT] = 1
                }
            }
            prefs[KEY_LAST_DAY] = today
        }
    }

    private companion object {
        val KEY_LAST_DAY = longPreferencesKey("last_training_day")
        val KEY_STREAK = intPreferencesKey("streak_days")
        val KEY_TODAY_COUNT = intPreferencesKey("answers_today")
    }
}
