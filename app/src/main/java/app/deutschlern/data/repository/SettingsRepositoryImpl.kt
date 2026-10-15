package app.deutschlern.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    companion object {
        private val KEY_WORD_PROVIDER = stringPreferencesKey("selected_word_provider")
        private val KEY_TEXT_PROVIDER = stringPreferencesKey("selected_text_provider")
        private val KEY_AUTO_FALLBACK = booleanPreferencesKey("auto_fallback_enabled")
        private val KEY_FALLBACK_ORDER = stringPreferencesKey("fallback_order")
        private val KEY_OFFLINE_MLKIT = booleanPreferencesKey("offline_mlkit_enabled")
        private val KEY_SHOW_MODEL_CAPTION = booleanPreferencesKey("show_model_caption")
        private val KEY_CROSS_MODEL_CACHE = booleanPreferencesKey("cross_model_cache_enabled")
        private val KEY_CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        private val KEY_CUSTOM_MODEL = stringPreferencesKey("custom_model")
        private val KEY_OPENROUTER_SHOW_ALL = booleanPreferencesKey("openrouter_show_all")
        private val KEY_SELECTED_PARTNER_LANG = stringPreferencesKey("selected_partner_language")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language_code")
        private val KEY_STT_PREFER_ON_DEVICE = booleanPreferencesKey("stt_prefer_on_device")
        private val KEY_STT_AUTO_DETECT = booleanPreferencesKey("stt_auto_detect")
        private val KEY_STT_TRANSLATE_IMMEDIATELY = booleanPreferencesKey("stt_translate_immediately")
        private val KEY_CHECK_PROVIDER = stringPreferencesKey("selected_check_provider")
        private val KEY_CHECK_CONSENT = booleanPreferencesKey("has_accepted_check_consent")
        private val KEY_COLORED_ARTICLES = booleanPreferencesKey("colored_articles_enabled")
        private val KEY_REPAIRED_ARTICLES = booleanPreferencesKey("has_repaired_non_noun_articles")
        private val KEY_REPAIRED_BACKUP = stringPreferencesKey("repaired_articles_backup_json")

        private const val MODEL_PREFIX_WORD = "model_word_"
        private const val MODEL_PREFIX_TEXT = "model_text_"
    }

    override val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        mapPreferencesToSettings(prefs)
    }

    override suspend fun getSettings(): UserSettings {
        return settingsFlow.first()
    }

    override suspend fun setSelectedWordProvider(providerId: String) {
        context.settingsDataStore.edit { it[KEY_WORD_PROVIDER] = providerId }
    }

    override suspend fun setSelectedTextProvider(providerId: String) {
        context.settingsDataStore.edit { it[KEY_TEXT_PROVIDER] = providerId }
    }

    override suspend fun setSelectedModel(providerId: String, isWordMode: Boolean, model: String) {
        val key = stringPreferencesKey(
            (if (isWordMode) MODEL_PREFIX_WORD else MODEL_PREFIX_TEXT) + providerId
        )
        context.settingsDataStore.edit { it[key] = model }
    }

    override suspend fun setAutoFallbackEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_FALLBACK] = enabled }
    }

    override suspend fun setFallbackOrder(order: List<String>) {
        context.settingsDataStore.edit { it[KEY_FALLBACK_ORDER] = order.joinToString(",") }
    }

    override suspend fun setOfflineMlKitEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_OFFLINE_MLKIT] = enabled }
    }

    override suspend fun setShowModelCaption(show: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_MODEL_CAPTION] = show }
    }

    override suspend fun setUseCrossModelCache(use: Boolean) {
        context.settingsDataStore.edit { it[KEY_CROSS_MODEL_CACHE] = use }
    }

    override suspend fun setCustomProviderConfig(baseUrl: String, model: String) {
        context.settingsDataStore.edit {
            it[KEY_CUSTOM_BASE_URL] = baseUrl
            it[KEY_CUSTOM_MODEL] = model
        }
    }

    override suspend fun setOpenRouterShowAllModels(showAll: Boolean) {
        context.settingsDataStore.edit { it[KEY_OPENROUTER_SHOW_ALL] = showAll }
    }

    override suspend fun setSelectedPartnerLanguage(languageCode: String) {
        context.settingsDataStore.edit { it[KEY_SELECTED_PARTNER_LANG] = languageCode }
    }

    override suspend fun setAppLanguage(languageCode: String) {
        context.settingsDataStore.edit { it[KEY_APP_LANGUAGE] = languageCode }
    }

    override val sttPreferOnDevice: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_STT_PREFER_ON_DEVICE] ?: false
    }

    override val sttAutoDetect: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_STT_AUTO_DETECT] ?: true
    }

    override val sttTranslateImmediately: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_STT_TRANSLATE_IMMEDIATELY] ?: false
    }

    override suspend fun setSttPreferOnDevice(prefer: Boolean) {
        context.settingsDataStore.edit { it[KEY_STT_PREFER_ON_DEVICE] = prefer }
    }

    override suspend fun setSttAutoDetect(auto: Boolean) {
        context.settingsDataStore.edit { it[KEY_STT_AUTO_DETECT] = auto }
    }

    override suspend fun setSttTranslateImmediately(immediate: Boolean) {
        context.settingsDataStore.edit { it[KEY_STT_TRANSLATE_IMMEDIATELY] = immediate }
    }

    override suspend fun setSelectedCheckProvider(providerId: String) {
        context.settingsDataStore.edit { it[KEY_CHECK_PROVIDER] = providerId }
    }

    override suspend fun setHasAcceptedCheckConsent(accepted: Boolean) {
        context.settingsDataStore.edit { it[KEY_CHECK_CONSENT] = accepted }
    }

    override suspend fun setColoredArticlesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_COLORED_ARTICLES] = enabled }
    }

    override suspend fun markArticlesRepaired(backupJson: String) {
        context.settingsDataStore.edit {
            it[KEY_REPAIRED_ARTICLES] = true
            it[KEY_REPAIRED_BACKUP] = backupJson
        }
    }

    private fun mapPreferencesToSettings(prefs: Preferences): UserSettings {
        val wordModels = mutableMapOf<String, String>()
        val textModels = mutableMapOf<String, String>()

        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith(MODEL_PREFIX_WORD) && value is String) {
                wordModels[name.removePrefix(MODEL_PREFIX_WORD)] = value
            } else if (name.startsWith(MODEL_PREFIX_TEXT) && value is String) {
                textModels[name.removePrefix(MODEL_PREFIX_TEXT)] = value
            }
        }

        val fallbackOrderStr = prefs[KEY_FALLBACK_ORDER]
        val fallbackOrder = if (!fallbackOrderStr.isNullOrBlank()) {
            fallbackOrderStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf("gemini", "groq", "mistral", "cerebras", "openrouter")
        }

        return UserSettings(
            selectedWordProviderId = prefs[KEY_WORD_PROVIDER] ?: "gemini",
            selectedTextProviderId = prefs[KEY_TEXT_PROVIDER] ?: "gemini",
            wordModels = wordModels,
            textModels = textModels,
            isAutoFallbackEnabled = prefs[KEY_AUTO_FALLBACK] ?: true,
            fallbackOrder = fallbackOrder,
            isOfflineMlKitEnabled = prefs[KEY_OFFLINE_MLKIT] ?: true,
            showModelCaption = prefs[KEY_SHOW_MODEL_CAPTION] ?: true,
            useCrossModelCache = prefs[KEY_CROSS_MODEL_CACHE] ?: true,
            customProviderBaseUrl = prefs[KEY_CUSTOM_BASE_URL] ?: "https://api.openai.com/v1",
            customProviderModel = prefs[KEY_CUSTOM_MODEL] ?: "gpt-4o-mini",
            openRouterShowAllModels = prefs[KEY_OPENROUTER_SHOW_ALL] ?: false,
            selectedPartnerLanguageCode = prefs[KEY_SELECTED_PARTNER_LANG] ?: "ru",
            appLanguageCode = prefs[KEY_APP_LANGUAGE] ?: "",
            sttPreferOnDevice = prefs[KEY_STT_PREFER_ON_DEVICE] ?: false,
            sttAutoDetect = prefs[KEY_STT_AUTO_DETECT] ?: true,
            sttTranslateImmediately = prefs[KEY_STT_TRANSLATE_IMMEDIATELY] ?: false,
            selectedCheckProviderId = prefs[KEY_CHECK_PROVIDER] ?: prefs[KEY_TEXT_PROVIDER] ?: "gemini",
            hasAcceptedCheckConsent = prefs[KEY_CHECK_CONSENT] ?: false,
            coloredArticlesEnabled = prefs[KEY_COLORED_ARTICLES] ?: true,
            hasRepairedNonNounArticles = prefs[KEY_REPAIRED_ARTICLES] ?: false,
            repairedArticlesBackupJson = prefs[KEY_REPAIRED_BACKUP] ?: ""
        )
    }
}
