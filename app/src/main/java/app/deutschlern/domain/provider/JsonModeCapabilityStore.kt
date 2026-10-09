package app.deutschlern.domain.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class JsonMode {
    JSON_SCHEMA,
    JSON_OBJECT,
    PLAIN_TEXT;

    fun downgrade(): JsonMode? = when (this) {
        JSON_SCHEMA -> JSON_OBJECT
        JSON_OBJECT -> PLAIN_TEXT
        PLAIN_TEXT -> null
    }
}

private val Context.jsonModeDataStore: DataStore<Preferences> by preferencesDataStore(name = "json_mode_capabilities")

@Singleton
class JsonModeCapabilityStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun makeKey(providerId: String, model: String): Preferences.Key<String> =
        stringPreferencesKey("mode_${providerId}_${model.replace('/', '_').replace(':', '_')}")

    suspend fun getMode(providerId: String, model: String): JsonMode {
        val prefKey = makeKey(providerId, model)
        val stored = context.jsonModeDataStore.data.map { it[prefKey] }.first()
        return stored?.let { runCatching { JsonMode.valueOf(it) }.getOrNull() } ?: JsonMode.JSON_SCHEMA
    }

    suspend fun setMode(providerId: String, model: String, mode: JsonMode) {
        val prefKey = makeKey(providerId, model)
        context.jsonModeDataStore.edit { prefs ->
            prefs[prefKey] = mode.name
        }
    }

    suspend fun downgradeMode(providerId: String, model: String): JsonMode? {
        val current = getMode(providerId, model)
        val next = current.downgrade()
        if (next != null) {
            setMode(providerId, model, next)
        }
        return next
    }
}
