package app.deutschlern.domain.repository

import app.deutschlern.domain.model.PartnerLanguage
import kotlinx.coroutines.flow.Flow

data class UserSettings(
    val selectedWordProviderId: String = "gemini",
    val selectedTextProviderId: String = "gemini",
    val wordModels: Map<String, String> = emptyMap(),
    val textModels: Map<String, String> = emptyMap(),
    val isAutoFallbackEnabled: Boolean = true,
    val fallbackOrder: List<String> = listOf("gemini", "groq", "mistral", "cerebras", "openrouter"),
    val isOfflineMlKitEnabled: Boolean = true,
    val showModelCaption: Boolean = true,
    val useCrossModelCache: Boolean = true,
    val customProviderBaseUrl: String = "https://api.openai.com/v1",
    val customProviderModel: String = "gpt-4o-mini",
    val openRouterShowAllModels: Boolean = false,
    val selectedPartnerLanguageCode: String = "ru",
    val appLanguageCode: String = "",
    val sttPreferOnDevice: Boolean = false,
    val sttAutoDetect: Boolean = true,
    val sttTranslateImmediately: Boolean = false,
    val selectedCheckProviderId: String = "gemini",
    val hasAcceptedCheckConsent: Boolean = false,
    val coloredArticlesEnabled: Boolean = true,
    val hasRepairedNonNounArticles: Boolean = false,
    val repairedArticlesBackupJson: String = "",
    val hasNormalizedPosInDb: Boolean = false,
    val repairedPosBackupJson: String = ""
) {
    val selectedPartnerLanguage: PartnerLanguage
        get() = PartnerLanguage.fromCode(selectedPartnerLanguageCode)

    val selectedAppLanguage: app.deutschlern.domain.model.AppLanguage
        get() = app.deutschlern.domain.model.AppLanguage.fromCode(appLanguageCode)

    fun getSelectedModel(providerId: String, isWordMode: Boolean, defaultModel: String): String {
        val map = if (isWordMode) wordModels else textModels
        return map[providerId]?.ifBlank { null } ?: defaultModel
    }
}

interface SettingsRepository {
    val settingsFlow: Flow<UserSettings>
    suspend fun getSettings(): UserSettings
    suspend fun setSelectedWordProvider(providerId: String)
    suspend fun setSelectedTextProvider(providerId: String)
    suspend fun setSelectedModel(providerId: String, isWordMode: Boolean, model: String)
    suspend fun setAutoFallbackEnabled(enabled: Boolean)
    suspend fun setFallbackOrder(order: List<String>)
    suspend fun setOfflineMlKitEnabled(enabled: Boolean)
    suspend fun setShowModelCaption(show: Boolean)
    suspend fun setUseCrossModelCache(use: Boolean)
    suspend fun setCustomProviderConfig(baseUrl: String, model: String)
    suspend fun setOpenRouterShowAllModels(showAll: Boolean)
    suspend fun setSelectedPartnerLanguage(languageCode: String)
    suspend fun setAppLanguage(languageCode: String)

    val sttPreferOnDevice: Flow<Boolean>
    suspend fun setSttPreferOnDevice(prefer: Boolean)

    val sttAutoDetect: Flow<Boolean>
    suspend fun setSttAutoDetect(auto: Boolean)

    val sttTranslateImmediately: Flow<Boolean>
    suspend fun setSttTranslateImmediately(immediate: Boolean)

    suspend fun setSelectedCheckProvider(providerId: String)
    suspend fun setHasAcceptedCheckConsent(accepted: Boolean)
    suspend fun setColoredArticlesEnabled(enabled: Boolean)
    suspend fun markArticlesRepaired(backupJson: String)
    suspend fun resetArticlesRepairedFlag()
    suspend fun markPosNormalized(backupJson: String)
    suspend fun resetPosNormalizedFlag()
}
